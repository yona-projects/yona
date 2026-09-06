package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step2 — RegisteredClientRepository의 JPA 구현체가 저장/조회를 왕복해도
// RegisteredClient의 필드가 그대로 보존되는지 검증한다(공식 JdbcRegisteredClientRepository를 쓰지
// 않고 커스텀 구현을 새로 짠 근거는 OAuthRegisteredClient.kt 상단 주석 참고).
class JpaRegisteredClientRepositorySpec @Autowired constructor(
    private val jpaRegisteredClientRepository: JpaRegisteredClientRepository,
    private val entityRepository: OAuthRegisteredClientRepository
) : AbstractIntegrationTest() {

    init {
        describe("JpaRegisteredClientRepository") {
            beforeEach { entityRepository.deleteAll() }
            afterSpec { entityRepository.deleteAll() }

            it("PKCE 전용 공개 클라이언트를 저장하고 clientId로 조회하면 모든 필드가 그대로 보존돼야 한다") {
                val id = UUID.randomUUID().toString()
                val client = RegisteredClient.withId(id)
                    .clientId("mcp-client-$id")
                    .clientName("Claude Code")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:12345/callback")
                    .scope("issues:read")
                    .scope("issues:write")
                    .clientSettings(
                        ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(true)
                            .build()
                    )
                    .tokenSettings(
                        TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofHours(1))
                            .refreshTokenTimeToLive(Duration.ofDays(30))
                            .reuseRefreshTokens(false)
                            .build()
                    )
                    .build()

                jpaRegisteredClientRepository.save(client)

                val found = jpaRegisteredClientRepository.findByClientId("mcp-client-$id")
                found.shouldNotBeNull()
                found.id shouldBe id
                found.clientName shouldBe "Claude Code"
                found.clientAuthenticationMethods shouldBe setOf(ClientAuthenticationMethod.NONE)
                found.authorizationGrantTypes shouldBe setOf(
                    AuthorizationGrantType.AUTHORIZATION_CODE,
                    AuthorizationGrantType.REFRESH_TOKEN
                )
                found.redirectUris shouldBe setOf("http://localhost:12345/callback")
                found.scopes shouldBe setOf("issues:read", "issues:write")
                found.clientSettings.isRequireProofKey shouldBe true
                found.clientSecret shouldBe null

                val byId = jpaRegisteredClientRepository.findById(id)
                byId.shouldNotBeNull()
                byId.clientId shouldBe "mcp-client-$id"
            }

            it("존재하지 않는 clientId로 조회하면 null을 반환해야 한다") {
                jpaRegisteredClientRepository.findByClientId("no-such-client") shouldBe null
            }
        }
    }
}
