package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.Date

// yona-wiki P3-14(yona를 OAuth2 서버로 제공) 1라운드 — `/api/v1/**`에도 OAuth2 JWT 인증 +
// [[p3-02]] Fine-grained 스코프 인가를 추가했다(원래 `/mcp/**`만 지원). McpOAuth2SecurityIntegrationSpec의
// "다른 audience로 서명된 토큰 거부" 기법을 그대로 재사용해 실제 서명된 JWT로 리소스 서버 쪽
// 동작(오디언스 격리 + 스코프 인가)만 집중 검증한다 — 인가 서버 쪽 플로우(DCR/PKCE/동의)는
// McpOAuth2SecurityIntegrationSpec이 이미 충분히 검증했으므로 여기서 반복하지 않는다.
class ApiV1OAuth2SecurityIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val jwkKeyPairProvider: JwkKeyPairProvider
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private val apiResourceUri = "http://localhost:8080/api/v1"
    private val mcpResourceUri = "http://localhost:8080/mcp"

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(loginId = "api-oauth-owner", name = "API OAuth 소유자", email = "api-oauth-owner@example.com")
            )
        }

        afterSpec {
            userRepository.delete(owner)
        }

        fun signedJwt(audience: String, scope: String): String {
            val keyPair = jwkKeyPairProvider.keyPair
            val now = Instant.now()
            val claims = JWTClaimsSet.Builder()
                .subject(owner.loginId)
                .issuer("http://localhost:8080")
                .audience(audience)
                .claim("scope", scope)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build()
            val signedJwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims)
            signedJwt.sign(RSASSASigner(keyPair.private as RSAPrivateKey))
            return signedJwt.serialize()
        }

        describe("/api/v1/** 리소스 서버 — 오디언스 격리") {
            it("MCP 리소스용으로 서명된 토큰은 /api/v1/**에서 401로 거부돼야 한다") {
                val token = signedJwt(mcpResourceUri, "issues:read")

                val result = mockMvc.perform(
                    get("/api/v1/projects/someowner").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                ).andReturn()

                result.response.status shouldBe 401
            }

            it("API 리소스용으로 서명된 토큰은 /mcp/**에서 401로 거부돼야 한다(반대 방향도 동일)") {
                val token = signedJwt(apiResourceUri, "issues:read")

                val result = mockMvc.perform(
                    get("/mcp/anything").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                ).andReturn()

                result.response.status shouldBe 401
            }
        }

        describe("/api/v1/** 리소스 서버 — 스코프 인가(OAuthApiScopeAuthorizationFilter)") {
            it("issues:read 스코프만 있는 토큰으로 이슈 쓰기 엔드포인트를 호출하면 403이어야 한다") {
                val token = signedJwt(apiResourceUri, "issues:read")

                val result = mockMvc.perform(
                    post("/api/v1/projects/someowner/someproject/issues")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues:write 스코프가 있으면 스코프 필터를 통과해야 한다(그 뒤 컨트롤러 처리 결과는 403이 아니어야 함)") {
                val token = signedJwt(apiResourceUri, "issues:write")

                val result = mockMvc.perform(
                    post("/api/v1/projects/someowner/someproject/issues")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}")
                ).andReturn()

                // 존재하지 않는 프로젝트라 컨트롤러 단계에서 다른 오류(404/400 등)로 끝나겠지만,
                // 스코프 필터에서 막히는 게 아니라는 것만 확인한다.
                result.response.status shouldNotBe 403
            }

            it("owner 전용 목록 조회(GET)는 스코프 단일 판정 대상이 아니라 통과해야 한다") {
                val token = signedJwt(apiResourceUri, "issues:read")

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                ).andReturn()

                result.response.status shouldNotBe 403
                result.response.status shouldNotBe 401
            }
        }
    }
}
