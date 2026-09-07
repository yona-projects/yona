package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.UUID

// yona-wiki P3-14 2라운드 — OIDC("Sign in with yona") E2E 검증. McpOAuth2SecurityIntegrationSpec의
// "실제 HTTP 요청(MockMvc, 전체 SecurityFilterChain 포함)으로 사람이 브라우저로 눌러보는 것과
// 동등한 신뢰도를 확보" 방침을 그대로 따른다. 여기서는 confidential 클라이언트(사용자 셀프서비스
// 등록, OAuthAppRegistrationService)로 authorization_code 플로우를 완주해 ID 토큰/`/userinfo`
// 응답이 실제 yona User 데이터와 일치하는지, 그리고 스코프가 클레임을 게이트하는지(핵심 보안
// 요구사항)를 검증한다.
class OidcSignInIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val jwkKeyPairProvider: JwkKeyPairProvider,
    private val oAuthAppRegistrationService: OAuthAppRegistrationService,
    private val clientRepository: OAuthRegisteredClientRepository,
    private val authorizationRepository: OAuthAuthorizationRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private val objectMapper = ObjectMapper()
    private val apiResourceUri = "http://localhost:8080/api/v1"
    private val redirectUri = "https://example.com/callback"

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(
                    loginId = "oidc-signin-owner",
                    name = "OIDC 로그인 사용자",
                    email = "oidc-signin-owner@example.com"
                )
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

        fun registerConfidentialClient(scopes: List<String>): Triple<String, String, String> {
            val registered = oAuthAppRegistrationService.register(
                clientName = "OIDC Test App ${UUID.randomUUID()}",
                redirectUri = redirectUri,
                confidential = true,
                scopes = scopes,
                ownerId = owner.id
            )
            return Triple(registered.client.id, registered.client.clientId, registered.plainSecret!!)
        }

        fun basicAuthHeader(clientId: String, clientSecret: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())

        fun extractQueryParam(uri: String, name: String): String {
            val query = URI.create(uri).query
            return query.split("&")
                .map { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                .first { it.first == name }.second
        }

        fun authorizeGetUrl(vararg params: Pair<String, String>): URI {
            val query = params.joinToString("&") { (k, v) ->
                "$k=" + java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
            }
            return URI.create("/oauth2/authorize?$query")
        }

        // 반환값: (accessToken, idToken)
        //
        // yona-wiki P3-14 2라운드 — 실제로 통합테스트를 돌려보다 발견한 진짜 프레임워크 동작(공식
        // 소스 OAuth2AuthorizationCodeRequestAuthenticationProvider.isAuthorizationConsentRequired()
        // 로 재확인): "openid 스코프가 요청 스코프 전체이자 유일한 스코프일 때는 동의 화면을 건너뛴다"
        // ("'openid' scope does not require consent" — 공식 소스 주석 그대로). requireAuthorizationConsent
        // =true로 강제 등록한 클라이언트라도 예외가 아니다 — 순수 "로그인만"(추가 데이터 접근 없음)
        // 요청은 GitHub/Google 등 표준 OIDC IdP도 동의 화면 없이 로그인만 확인하는 관례와 일치하는
        // 의도된 설계다. 그래서 이 헬퍼는 응답이 곧장 redirect_uri로 가는지(동의 생략) /oauth2/consent로
        // 가는지 분기해서 처리한다.
        fun completeAuthorizationCodeFlow(clientId: String, clientSecret: String, requestedScope: String): Pair<String, String> {
            val state = "oidc-state-" + UUID.randomUUID()

            val authorizeResult = mockMvc.perform(
                get(
                    authorizeGetUrl(
                        "response_type" to "code",
                        "client_id" to clientId,
                        "redirect_uri" to redirectUri,
                        "scope" to requestedScope,
                        "state" to state,
                        "resource" to apiResourceUri
                    )
                ).with(user(userDetails()))
            ).andReturn()
            authorizeResult.response.status shouldBe 302
            val firstLocation = authorizeResult.response.getHeader(HttpHeaders.LOCATION)!!

            val code = if (firstLocation.contains("/oauth2/consent")) {
                val consentState = extractQueryParam(firstLocation, "state")

                val consentPostRequest = post("/oauth2/authorize")
                    .param("client_id", clientId)
                    .param("state", consentState)
                    .with(user(userDetails()))
                requestedScope.split(" ").forEach { consentPostRequest.param("scope", it) }

                val consentPost = mockMvc.perform(consentPostRequest).andReturn()
                consentPost.response.status shouldBe 302
                val redirectLocation = consentPost.response.getHeader(HttpHeaders.LOCATION)!!
                redirectLocation.substringAfter("code=").substringBefore("&")
            } else {
                // openid 단독 스코프 — 동의 화면 없이 곧장 redirect_uri로 code가 온다(위 주석 참고).
                firstLocation.substringAfter("code=").substringBefore("&")
            }

            val tokenResult = mockMvc.perform(
                post("/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "authorization_code")
                    .param("code", code)
                    .param("redirect_uri", redirectUri)
                    .param("resource", apiResourceUri)
            ).andReturn()
            tokenResult.response.status shouldBe 200
            val tokenJson = objectMapper.readTree(tokenResult.response.contentAsString)
            return tokenJson["access_token"].asText() to tokenJson["id_token"].asText()
        }

        describe("OIDC discovery 문서") {
            it("/.well-known/openid-configuration이 issuer/userinfo_endpoint/jwks_uri/scopes_supported/claims_supported를 포함해야 한다") {
                val result = mockMvc.perform(get("/.well-known/openid-configuration")).andReturn()
                result.response.status shouldBe 200

                val json = objectMapper.readTree(result.response.contentAsString)
                json["issuer"].asText() shouldBe "http://localhost:8080"
                json["userinfo_endpoint"].asText() shouldBe "http://localhost:8080/userinfo"
                json["jwks_uri"].asText() shouldBe "http://localhost:8080/oauth2/jwks"
                val scopesSupported = json["scopes_supported"].toList().map { it.asText() }
                scopesSupported shouldBe listOf("openid", "profile", "email")
                val claimsSupported = json["claims_supported"].toList().map { it.asText() }
                claimsSupported.shouldContainAll(listOf("sub", "name", "email", "email_verified", "picture"))
            }
        }

        describe("openid profile email 전체 동의 — ID 토큰과 /userinfo") {
            it("ID 토큰과 /userinfo 응답 모두 sub/name/email/email_verified/picture가 실제 User와 일치해야 한다") {
                val (_, clientRawId, clientSecret) = registerConfidentialClient(listOf("openid", "profile", "email"))
                val (accessToken, idToken) = completeAuthorizationCodeFlow(clientRawId, clientSecret, "openid profile email")

                val signedIdToken = SignedJWT.parse(idToken)
                signedIdToken.verify(RSASSAVerifier(jwkKeyPairProvider.keyPair.public as RSAPublicKey)) shouldBe true
                val idClaims = signedIdToken.jwtClaimsSet

                idClaims.subject shouldBe owner.loginId
                idClaims.getStringClaim("name") shouldBe owner.name
                idClaims.getStringClaim("email") shouldBe owner.email
                idClaims.getBooleanClaim("email_verified") shouldBe true
                idClaims.getStringClaim("picture") shouldBe "http://localhost:8080/assets/images/default-avatar-128.png"

                val userInfoResult = mockMvc.perform(
                    get("/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                ).andReturn()
                userInfoResult.response.status shouldBe 200
                val userInfoJson = objectMapper.readTree(userInfoResult.response.contentAsString)
                userInfoJson["sub"].asText() shouldBe owner.loginId
                userInfoJson["name"].asText() shouldBe owner.name
                userInfoJson["email"].asText() shouldBe owner.email
                userInfoJson["email_verified"].asBoolean() shouldBe true
                userInfoJson["picture"].asText() shouldBe "http://localhost:8080/assets/images/default-avatar-128.png"
            }
        }

        describe("스코프가 클레임을 게이트한다 — openid만 동의한 경우") {
            it("openid 스코프만 있으면 ID 토큰과 /userinfo 둘 다 name/email/picture가 없어야 한다(sub만 있어야 함)") {
                val (_, clientRawId, clientSecret) = registerConfidentialClient(listOf("openid", "profile", "email"))
                val (accessToken, idToken) = completeAuthorizationCodeFlow(clientRawId, clientSecret, "openid")

                val signedIdToken = SignedJWT.parse(idToken)
                val idClaims = signedIdToken.jwtClaimsSet
                idClaims.subject shouldBe owner.loginId
                idClaims.getStringClaim("name").shouldBeNull()
                idClaims.getStringClaim("email").shouldBeNull()
                idClaims.getStringClaim("picture").shouldBeNull()

                val userInfoResult = mockMvc.perform(
                    get("/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                ).andReturn()
                userInfoResult.response.status shouldBe 200
                val userInfoJson = objectMapper.readTree(userInfoResult.response.contentAsString)
                userInfoJson["sub"].asText() shouldBe owner.loginId
                userInfoJson.has("name") shouldBe false
                userInfoJson.has("email") shouldBe false
                userInfoJson.has("picture") shouldBe false
            }
        }
    }
}
