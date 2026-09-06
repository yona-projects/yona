package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.time.Instant
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2AuthorizationService의 JPA 구현체가 저장/조회를 왕복해도
// 액세스/리프레시 토큰과 attributes가 그대로 보존되는지 검증(설계 근거는 OAuthAuthorization.kt 상단
// 주석 참고 — JdbcOAuth2AuthorizationService 공식 소스의 컬럼 구성/직렬화 방식을 그대로 따랐다).
class JpaOAuth2AuthorizationServiceSpec @Autowired constructor(
    private val authorizationService: JpaOAuth2AuthorizationService,
    private val entityRepository: OAuthAuthorizationRepository,
    private val jpaRegisteredClientRepository: JpaRegisteredClientRepository,
    private val clientEntityRepository: OAuthRegisteredClientRepository
) : AbstractIntegrationTest() {

    private fun newClient(): RegisteredClient {
        val id = UUID.randomUUID().toString()
        val client = RegisteredClient.withId(id)
            .clientId("mcp-client-$id")
            .clientName("Claude Code")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:1/callback")
            .scope("issues:read")
            .build()
        jpaRegisteredClientRepository.save(client)
        return client
    }

    init {
        describe("JpaOAuth2AuthorizationService") {
            beforeEach {
                entityRepository.deleteAll()
                clientEntityRepository.deleteAll()
            }
            afterSpec {
                entityRepository.deleteAll()
                clientEntityRepository.deleteAll()
            }

            it("액세스+리프레시 토큰과 attributes를 저장 후 findByToken(access token)으로 그대로 복원해야 한다") {
                val client = newClient()
                val now = Instant.now()
                val accessToken = OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "at-${UUID.randomUUID()}",
                    now,
                    now.plusSeconds(3600),
                    setOf("issues:read", "issues:write")
                )
                val refreshToken = OAuth2RefreshToken("rt-${UUID.randomUUID()}", now)

                val authorization = OAuth2Authorization.withRegisteredClient(client)
                    .id(UUID.randomUUID().toString())
                    .principalName("scope-owner")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizedScopes(setOf("issues:read", "issues:write"))
                    .token(accessToken) { it["resource"] = "https://yona.example/mcp" }
                    .token(refreshToken)
                    .attribute("custom-key", "custom-value")
                    .build()

                authorizationService.save(authorization)

                val found = authorizationService.findByToken(accessToken.tokenValue, OAuth2TokenType.ACCESS_TOKEN)
                found.shouldNotBeNull()
                found.principalName shouldBe "scope-owner"
                found.authorizedScopes shouldBe setOf("issues:read", "issues:write")
                found.getAttribute<String>("custom-key") shouldBe "custom-value"
                found.accessToken!!.token.tokenValue shouldBe accessToken.tokenValue
                found.accessToken!!.token.scopes shouldBe setOf("issues:read", "issues:write")
                found.accessToken!!.metadata["resource"] shouldBe "https://yona.example/mcp"
                found.refreshToken.shouldNotBeNull()
                found.refreshToken!!.token.tokenValue shouldBe refreshToken.tokenValue

                // 토큰 종류를 지정하지 않아도(tokenType=null) 값만으로 찾을 수 있어야 한다.
                val foundByUnknownType = authorizationService.findByToken(refreshToken.tokenValue, null)
                foundByUnknownType.shouldNotBeNull()
                foundByUnknownType.id shouldBe authorization.id
            }

            it("존재하지 않는 토큰 값으로 조회하면 null을 반환해야 한다") {
                authorizationService.findByToken("no-such-token", OAuth2TokenType.ACCESS_TOKEN) shouldBe null
            }
        }
    }
}
