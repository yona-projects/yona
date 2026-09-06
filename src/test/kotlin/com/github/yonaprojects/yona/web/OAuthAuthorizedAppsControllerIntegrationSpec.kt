package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorization
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsent
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step6 — "Authorized OAuth Apps" 화면(사용자 지시 1번: "필요한 UI는 반드시
// 구현한다")이 실제 Spring 컨텍스트+실제 Thymeleaf 템플릿 엔진으로 정상 렌더링되는지 확인한다.
// UserViewControllerSpec(mockk 기반 단위테스트)은 뷰 이름만 검증하고 템플릿 문법 자체는 렌더링하지
// 않으므로, 이 스펙이 실제 HTML 렌더링(오타/Thymeleaf 문법 오류 검출)과 revoke의 실제 DB 부수효과
// (동의 레코드 + 토큰 레코드 둘 다 삭제됨)를 커버한다.
class OAuthAuthorizedAppsControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val clientRepository: OAuthRegisteredClientRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository,
    private val authorizationRepository: OAuthAuthorizationRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(loginId = "oauth-apps-ui-owner", name = "OAuth UI 소유자", email = "oauth-apps-ui-owner@example.com")
            )
        }

        afterSpec {
            authorizationRepository.deleteAll()
            consentRepository.deleteAll()
            clientRepository.deleteAll()
            userRepository.delete(owner)
        }

        fun userDetails() = YonaUserDetails(
            id = owner.id!!,
            loginId = owner.loginId,
            passwordVal = "",
            passwordSalt = "",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        describe("GET /user/editform/oauth-apps") {
            // yona-wiki P3-07 Step6 — 이 컨트롤러의 다른 계정 설정 엔드포인트(editApiTokensForm 등)와
            // 동일한 기존 관례: SecurityConfig가 /user/editform/**를 별도로 보호하지 않고, 각
            // 컨트롤러 메서드가 authentication == null을 직접 확인해 "error/403" 뷰(200 OK로 렌더링되는
            // 일반 오류 페이지 — HTTP 상태코드 자체를 403으로 바꾸는 게 아니다)를 반환한다. 실측
            // 확인 결과 302 리다이렉트가 아니라 200으로 렌더링됨을 확인했다.
            it("비로그인 상태로 접근하면 error/403 페이지를 200으로 렌더링해야 한다(기존 계정설정 화면과 동일한 관례)") {
                val result = mockMvc.perform(get("/user/editform/oauth-apps")).andReturn()
                result.response.status shouldBe 200
                result.response.contentAsString.shouldContain("권한이 없습니다")
            }

            it("인가한 앱이 없으면 빈 상태 메시지를 렌더링해야 한다") {
                val result = mockMvc.perform(get("/user/editform/oauth-apps").with(user(userDetails()))).andReturn()

                result.response.status shouldBe 200
                result.response.contentAsString.shouldContain("인가한 OAuth 애플리케이션이 없습니다")
            }

            it("인가한 앱이 있으면 이름/스코프/취소 버튼을 렌더링해야 한다") {
                val client = clientRepository.save(
                    OAuthRegisteredClient(
                        id = UUID.randomUUID().toString(),
                        clientId = "test-authorized-client-${UUID.randomUUID()}",
                        clientName = "테스트 MCP 클라이언트",
                        clientAuthenticationMethods = "none",
                        authorizationGrantTypes = "authorization_code,refresh_token",
                        scopes = "issues:read,issues:write",
                        dynamicallyRegistered = true
                    )
                )
                consentRepository.save(
                    OAuthAuthorizationConsent(
                        id = "${client.id}:${owner.loginId}",
                        registeredClientId = client.id,
                        principalName = owner.loginId,
                        authorities = "SCOPE_issues:read,SCOPE_issues:write"
                    )
                )

                val result = mockMvc.perform(get("/user/editform/oauth-apps").with(user(userDetails()))).andReturn()

                result.response.status shouldBe 200
                result.response.contentAsString.shouldContain("테스트 MCP 클라이언트")
                result.response.contentAsString.shouldContain("issues:read")
                result.response.contentAsString.shouldContain("issues:write")
            }
        }

        describe("POST /user/editform/oauth-apps/{clientId}/revoke") {
            it("동의 레코드와 발급된 토큰 레코드를 모두 삭제하고 목록으로 리다이렉트해야 한다") {
                val client = clientRepository.save(
                    OAuthRegisteredClient(
                        id = UUID.randomUUID().toString(),
                        clientId = "test-revoke-client-${UUID.randomUUID()}",
                        clientName = "취소 테스트 클라이언트",
                        clientAuthenticationMethods = "none",
                        authorizationGrantTypes = "authorization_code,refresh_token",
                        scopes = "issues:read",
                        dynamicallyRegistered = true
                    )
                )
                consentRepository.save(
                    OAuthAuthorizationConsent(
                        id = "${client.id}:${owner.loginId}",
                        registeredClientId = client.id,
                        principalName = owner.loginId,
                        authorities = "SCOPE_issues:read"
                    )
                )
                authorizationRepository.save(
                    OAuthAuthorization(
                        id = UUID.randomUUID().toString(),
                        registeredClientId = client.id,
                        principalName = owner.loginId,
                        authorizationGrantType = "authorization_code",
                        accessTokenValue = "live-access-token-${UUID.randomUUID()}",
                        accessTokenIssuedAt = Instant.now(),
                        accessTokenExpiresAt = Instant.now().plusSeconds(3600)
                    )
                )

                val result = mockMvc.perform(
                    post("/user/editform/oauth-apps/${client.clientId}/revoke").with(user(userDetails()))
                ).andReturn()

                result.response.status shouldBe 302
                result.response.getHeader("Location") shouldBe "/user/editform/oauth-apps"
                // 이 spec의 다른 "it" 블록들도 같은 owner로 별개의 클라이언트를 인가해두므로
                // (afterSpec에서만 일괄 정리), 전체가 비었는지가 아니라 "이 클라이언트에 대한
                // 레코드만" 사라졌는지를 확인한다.
                consentRepository.findByPrincipalName(owner.loginId).none { it.registeredClientId == client.id } shouldBe true
                authorizationRepository.findAll().none { it.registeredClientId == client.id } shouldBe true
            }
        }
    }
}
