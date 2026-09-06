package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
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
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step2 — "사람이 브라우저로 직접 눌러보는 것"과 실질적으로 동등한
// 신뢰도를 확보하기 위한 자동화된 통합 테스트(계획 문서 DoD 1번의 대체 검증, 사용자 지시 3번 참고).
// 실제 HTTP 요청(MockMvc, 전체 SecurityFilterChain 포함)으로 다음을 전부 태운다:
// (a) 토큰 없이 /mcp 호출 -> 401 + WWW-Authenticate(RFC9728 위치 포함)
// (b) /.well-known/oauth-protected-resource, /.well-known/oauth-authorization-server 메타데이터 스펙 준수
// (c) DCR로 실제 클라이언트 등록
// (d) 발급받은 client_id로 PKCE 포함 authorization code flow 전체(동의 화면 포함) -> 토큰 발급
// (e) 그 토큰으로 /mcp 호출 성공(인증 통과 확인 — 실제 도구는 Step3에서 추가)
// 추가 보안 검증: 다른 audience로 서명된 토큰은 리소스 서버가 거부하는지(RFC8707 리소스 서버 쪽 절반).
class McpOAuth2SecurityIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val jwkKeyPairProvider: JwkKeyPairProvider,
    private val clientRepository: OAuthRegisteredClientRepository,
    private val authorizationRepository: OAuthAuthorizationRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private val objectMapper = ObjectMapper()
    private val mcpResourceUri = "http://localhost:8080/mcp"

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(loginId = "mcp-oauth-owner", name = "MCP 소유자", email = "mcp-oauth-owner@example.com")
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

        // RFC7591 DCR은 클라이언트가 등록 시점에 scope를 직접 지정하는 걸 Spring이 기본 정책상
        // 거부한다(McpOAuthScopes.kt 주석 참고) — 등록된 모든 클라이언트는 전체 스코프를 자동으로
        // 부여받고, 실제 발급 스코프는 /oauth2/authorize 요청에서 결정된다.
        fun registerClient(): String {
            val body = """
                {
                  "client_name": "Test MCP Client ${UUID.randomUUID()}",
                  "redirect_uris": ["http://127.0.0.1:0/callback"],
                  "grant_types": ["authorization_code", "refresh_token"],
                  "response_types": ["code"],
                  "token_endpoint_auth_method": "none"
                }
            """.trimIndent()

            val result = mockMvc.perform(
                post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(body)
            ).andReturn()

            result.response.status shouldBe 201
            val json = objectMapper.readTree(result.response.contentAsString)
            return json["client_id"].asText()
        }

        fun sha256Base64Url(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — /oauth2/authorize가 동의 화면으로 리다이렉트할
        // 때 붙이는 "state" 쿼리 파라미터는 원래 인가 요청의 state 값이 아니라 Spring Authorization
        // Server가 동의 단계 CSRF 방지를 위해 새로 발급하는 별도의 불투명한 값이다(McpToolsEndToEndSpec
        // 작성 중 실측 확인 — 원래 state를 그대로 재사용해 동의 POST를 보내면 매번
        // "invalid_request: state"로 거부된다, OAuth2AuthorizationConsentAuthenticationProvider가
        // 이 값으로 OAuth2Authorization을 다시 조회하기 때문). 그래서 실제 브라우저 폼 제출과 동일하게
        // 리다이렉트 URL에서 이 값을 그대로 꺼내 동의 POST에 되돌려줘야 한다.
        fun extractQueryParam(uri: String, name: String): String {
            val query = URI.create(uri).query
            return query.split("&")
                .map { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                .first { it.first == name }.second
        }

        // yona-wiki P3-07 Step2 — MockMvc의 get("/x").param(...)는 파라미터를 request의
        // parameterMap에만 채우고 getQueryString()은 비워둔다(실측 확인). Spring Authorization
        // Server의 OAuth2EndpointUtils.getQueryParameters()는 GET 요청의 파라미터를
        // request.getQueryString()에 실제로 포함돼 있는지로 다시 한번 걸러내므로(공식 소스 확인),
        // .param()만 쓰면 모든 파라미터가 빈 것으로 취급돼 "response_type 없음" 오류가 난다 — 쿼리
        // 문자열을 URL에 직접 인코딩해 넘겨야 한다.
        fun authorizeGetUrl(vararg params: Pair<String, String>): URI {
            // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — URLEncoder.encode()는 공백을 "+"로
            // 인코딩하는데, McpToolsEndToEndSpec을 작성하며 실측 확인한 결과 이 필터 체인(Spring
            // Authorization Server의 OAuth2EndpointUtils 쿼리 파싱)에서 스코프처럼 공백으로 구분된
            // 다중 값 파라미터의 "+"가 공백으로 복원되지 않고 그대로 리터럴 문자로 남아
            // invalid_scope로 거부되는 경우가 있어(스코프 문자열 하나에 공백이 여럿 섞인 경우에서
            // 재현), 이식성이 더 확실한 %20으로 명시 치환한다.
            val query = params.joinToString("&") { (k, v) ->
                "$k=" + java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
            }
            return URI.create("/oauth2/authorize?$query")
        }

        describe("RFC9728 Protected Resource Metadata") {
            it("/.well-known/oauth-protected-resource가 resource/authorization_servers/scopes_supported를 포함해야 한다") {
                val result = mockMvc.perform(get("/.well-known/oauth-protected-resource")).andReturn()
                result.response.status shouldBe 200
                val json = objectMapper.readTree(result.response.contentAsString)
                json["resource"].asText() shouldBe mcpResourceUri
                json["authorization_servers"][0].asText() shouldBe "http://localhost:8080"
                json["scopes_supported"].toList().map { it.asText() } shouldContain "issues:read"
            }

            it("리소스 경로가 붙은 /.well-known/oauth-protected-resource/mcp 에서도 동일한 문서를 제공해야 한다") {
                val result = mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andReturn()
                result.response.status shouldBe 200
            }
        }

        describe("RFC8414 Authorization Server Metadata") {
            it("/.well-known/oauth-authorization-server가 인가/토큰/DCR 엔드포인트를 광고해야 한다") {
                val result = mockMvc.perform(get("/.well-known/oauth-authorization-server")).andReturn()
                result.response.status shouldBe 200
                val json = objectMapper.readTree(result.response.contentAsString)
                json["issuer"].asText() shouldBe "http://localhost:8080"
                json["authorization_endpoint"].asText() shouldBe "http://localhost:8080/oauth2/authorize"
                json["token_endpoint"].asText() shouldBe "http://localhost:8080/oauth2/token"
                json["registration_endpoint"].asText() shouldBe "http://localhost:8080/oauth2/register"
            }
        }

        describe("MCP 리소스 서버 — 토큰 없이 호출") {
            it("Authorization 헤더 없이 /mcp/** 호출 시 401과 RFC9728 위치를 담은 WWW-Authenticate를 반환해야 한다") {
                val result = mockMvc.perform(get("/mcp/anything")).andReturn()
                result.response.status shouldBe 401
                val header = result.response.getHeader(HttpHeaders.WWW_AUTHENTICATE)
                header.shouldStartWith("Bearer")
                header.shouldContain("resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\"")
            }
        }

        describe("Dynamic Client Registration(RFC7591)") {
            it("클라이언트를 등록하면 PKCE 전용 공개 클라이언트로 저장되고 client_secret이 없어야 한다") {
                val body = """
                    {
                      "client_name": "DCR Test Client",
                      "redirect_uris": ["http://127.0.0.1:0/callback"],
                      "grant_types": ["authorization_code"],
                      "response_types": ["code"],
                      "token_endpoint_auth_method": "none"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(body)
                ).andReturn()

                result.response.status shouldBe 201
                val json = objectMapper.readTree(result.response.contentAsString)
                json.has("client_secret") shouldBe false
                val clientId = json["client_id"].asText()

                val entity = clientRepository.findByClientId(clientId).orElseThrow()
                entity.requireProofKey shouldBe true
                entity.requireAuthorizationConsent shouldBe true
            }
        }

        describe("PKCE 포함 authorization code flow 전체 (동의 화면 포함) -> 토큰 발급 -> MCP 호출") {
            it("전체 플로우를 성공적으로 완료하고 발급된 토큰으로 /mcp 호출이 인증을 통과해야 한다") {
                val clientId = registerClient()
                val codeVerifier = "verifier-" + UUID.randomUUID().toString().replace("-", "") + "-0123456789"
                val codeChallenge = sha256Base64Url(codeVerifier)
                val redirectUri = "http://127.0.0.1:0/callback"
                // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — state는 매 호출마다 유일해야 한다:
                // OAuthAuthorizationRepository.findByState()가 client_id로 좁히지 않고 state 문자열
                // 하나만으로 조회하므로, 고정 리터럴을 재사용하면 이 컨테이너가 재사용되는 로컬
                // 반복 실행 중 이전(특히 비정상 종료된) 실행이 남긴 행과 충돌해 무작위로 실패할 수
                // 있다(McpToolsEndToEndSpec 작성 중 실측 확인).
                val state = "xyz-state-" + UUID.randomUUID()

                // 1) /oauth2/authorize — 동의 화면으로 리다이렉트돼야 한다(requireAuthorizationConsent
                //    강제 적용, JpaRegisteredClientRepository.toEntity() 참고).
                val authorizeResult = mockMvc.perform(
                    get(
                        authorizeGetUrl(
                            "response_type" to "code",
                            "client_id" to clientId,
                            "redirect_uri" to redirectUri,
                            "scope" to "issues:read issues:write",
                            "state" to state,
                            "code_challenge" to codeChallenge,
                            "code_challenge_method" to "S256",
                            "resource" to mcpResourceUri
                        )
                    ).with(user(userDetails()))
                ).andReturn()

                authorizeResult.response.status shouldBe 302
                val consentLocation = authorizeResult.response.getHeader(HttpHeaders.LOCATION)!!
                consentLocation.shouldContain("/oauth2/consent")
                // 동의 단계 CSRF 토큰(위 extractQueryParam() 주석 참고) — 이후 동의 GET/POST 모두
                // 이 값을 써야 한다(원래 인가 요청의 state가 아니다).
                val consentState = extractQueryParam(consentLocation, "state")

                // 2) 동의 화면이 실제로 렌더링되는지 확인(GitHub 방식 동의 화면, OAuthConsentController).
                val consentUri = URI(consentLocation)
                val consentGet = mockMvc.perform(
                    get(consentUri.path).queryParam(
                        "client_id", clientId
                    ).queryParam("scope", "issues:read issues:write").queryParam("state", consentState)
                        .with(user(userDetails()))
                ).andReturn()
                consentGet.response.status shouldBe 200
                consentGet.response.contentAsString.shouldContain("issues:read")

                // 3) 동의 제출 — consent.html 폼과 동일하게 client_id/state/scope(체크된 것만)를
                //    /oauth2/authorize로 POST.
                val consentPost = mockMvc.perform(
                    post("/oauth2/authorize")
                        .param("client_id", clientId)
                        .param("state", consentState)
                        .param("scope", "issues:read")
                        .param("scope", "issues:write")
                        .with(user(userDetails()))
                ).andReturn()

                consentPost.response.status shouldBe 302
                val redirectLocation = URI(consentPost.response.getHeader(HttpHeaders.LOCATION)!!)
                redirectLocation.toString().shouldStartWith(redirectUri)
                val code = URI.create(redirectLocation.toString()).query
                    .split("&").associate { it.substringBefore("=") to it.substringAfter("=") }["code"]!!

                // 4) 토큰 교환 — resource 파라미터(RFC8707) 포함.
                val tokenResult = mockMvc.perform(
                    post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", codeVerifier)
                        .param("resource", mcpResourceUri)
                ).andReturn()

                tokenResult.response.status shouldBe 200
                val tokenJson = objectMapper.readTree(tokenResult.response.contentAsString)
                val accessToken = tokenJson["access_token"].asText()
                tokenJson["scope"].asText().shouldContain("issues:read")

                // 5) 발급된 토큰으로 MCP 엔드포인트 호출 — 인증은 통과해야 한다. 이 스펙은 인가
                //    레이어만 검증하는 게 목적이라(실제 도구 호출 검증은 McpToolsEndToEndSpec
                //    담당) 정확한 상태코드를 못박지 않고 401(인증 실패)이 아니라는 것만 확인한다 —
                //    Step3에서 /mcp에 실제 Spring AI MCP 서버가 붙은 뒤로 GET에 대한 정확한
                //    응답(200/404/405 등)은 Spring AI 라이브러리의 트랜스포트 구현 세부사항이라
                //    이 스펙이 못박을 이유가 없다.
                val mcpCallResult = mockMvc.perform(
                    get("/mcp/anything").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                ).andReturn()
                mcpCallResult.response.status shouldNotBe 401

                // 동의 기록이 "Authorized OAuth Apps" 화면의 데이터 소스에 실제로 남았는지 확인.
                consentRepository.findByPrincipalName(owner.loginId).size shouldBe 1
            }

            it("resource 파라미터 없이 토큰을 교환하려 하면 invalid_target으로 거부해야 한다") {
                val clientId = registerClient()
                val codeVerifier = "verifier-" + UUID.randomUUID().toString().replace("-", "") + "-0123456789"
                val codeChallenge = sha256Base64Url(codeVerifier)
                val redirectUri = "http://127.0.0.1:0/callback"
                val state = "no-resource-state-" + UUID.randomUUID()

                val authorizeResult = mockMvc.perform(
                    get(
                        authorizeGetUrl(
                            "response_type" to "code",
                            "client_id" to clientId,
                            "redirect_uri" to redirectUri,
                            "scope" to "issues:read",
                            "state" to state,
                            "code_challenge" to codeChallenge,
                            "code_challenge_method" to "S256"
                        )
                    ).with(user(userDetails()))
                ).andReturn()
                authorizeResult.response.status shouldBe 302
                val consentState = extractQueryParam(authorizeResult.response.getHeader(HttpHeaders.LOCATION)!!, "state")

                val consentPost = mockMvc.perform(
                    post("/oauth2/authorize")
                        .param("client_id", clientId)
                        .param("state", consentState)
                        .param("scope", "issues:read")
                        .with(user(userDetails()))
                ).andReturn()
                consentPost.response.status shouldBe 302
                val redirectLocation = consentPost.response.getHeader(HttpHeaders.LOCATION)!!
                val code = redirectLocation.substringAfter("code=").substringBefore("&")

                val tokenResult = mockMvc.perform(
                    post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", codeVerifier)
                ).andReturn()

                tokenResult.response.status shouldBe 400
                tokenResult.response.contentAsString.shouldContain("invalid_target")
            }

            it("PKCE code_verifier 없이 토큰 교환을 시도하면 거부해야 한다(PKCE 강제)") {
                val clientId = registerClient()
                val codeVerifier = "verifier-" + UUID.randomUUID().toString().replace("-", "") + "-0123456789"
                val codeChallenge = sha256Base64Url(codeVerifier)
                val redirectUri = "http://127.0.0.1:0/callback"
                val state = "pkce-state-" + UUID.randomUUID()

                val authorizeResult = mockMvc.perform(
                    get(
                        authorizeGetUrl(
                            "response_type" to "code",
                            "client_id" to clientId,
                            "redirect_uri" to redirectUri,
                            "scope" to "issues:read",
                            "state" to state,
                            "code_challenge" to codeChallenge,
                            "code_challenge_method" to "S256",
                            "resource" to mcpResourceUri
                        )
                    ).with(user(userDetails()))
                ).andReturn()
                authorizeResult.response.status shouldBe 302
                val consentState = extractQueryParam(authorizeResult.response.getHeader(HttpHeaders.LOCATION)!!, "state")

                val consentPost = mockMvc.perform(
                    post("/oauth2/authorize")
                        .param("client_id", clientId)
                        .param("state", consentState)
                        .param("scope", "issues:read")
                        .with(user(userDetails()))
                ).andReturn()
                val redirectLocation = consentPost.response.getHeader(HttpHeaders.LOCATION)!!
                check(redirectLocation.contains("code=")) { "consent redirect had no code: $redirectLocation" }
                val code = redirectLocation.substringAfter("code=").substringBefore("&")

                // code_verifier를 아예 생략 — PKCE가 실제로 강제된다면 거부돼야 한다. "none" 인증
                // 방식(공개 클라이언트)에서는 PKCE code_verifier가 사실상 클라이언트 인증 수단을
                // 겸하므로(Spring의 PublicClientAuthenticationProvider가 이를 검증), 이걸 생략하면
                // 토큰 발급 로직(400 invalid_grant)까지 가지도 못하고 그 앞 단계인 클라이언트 인증
                // 자체가 실패해 403으로 거부된다(실측 확인) — 이 테스트가 검증해야 할 건 정확한
                // 상태코드가 아니라 "액세스 토큰이 발급되지 않는다"는 사실이므로 400/403 둘 다 허용한다.
                val tokenResult = mockMvc.perform(
                    post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("resource", mcpResourceUri)
                ).andReturn()

                tokenResult.response.status shouldBeIn listOf(400, 403)
            }
        }

        describe("MCP 리소스 서버 — 다른 audience로 서명된 토큰 거부(RFC8707 리소스 서버 쪽 검증)") {
            it("이 MCP 리소스 서버가 아닌 다른 audience로 서명된(같은 서명키) JWT는 401로 거부해야 한다") {
                val keyPair = jwkKeyPairProvider.keyPair
                val now = Instant.now()
                val claims = JWTClaimsSet.Builder()
                    .subject(owner.loginId)
                    .issuer("http://localhost:8080")
                    .audience("http://other-resource-server.example/mcp")
                    .claim("scope", "issues:read")
                    .issueTime(java.util.Date.from(now))
                    .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                    .build()
                val signedJwt = SignedJWT(
                    JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
                )
                signedJwt.sign(RSASSASigner(keyPair.private as RSAPrivateKey))
                val forgedToken = signedJwt.serialize()

                val result = mockMvc.perform(
                    get("/mcp/anything").header(HttpHeaders.AUTHORIZATION, "Bearer $forgedToken")
                ).andReturn()

                result.response.status shouldBe 401
            }
        }
    }
}
