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
                // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — 이 테스트는 원래 "요청한 스코프
                // 그대로 보존"을 검증했지만, McpOAuthScopes.kt의 설계 결정(Spring이 DCR 시점의
                // 명시적 scope 지정을 기본 정책상 거부하므로, 등록된 모든 클라이언트에 항상 전체
                // 스코프 목록을 부여하고 실제 발급 스코프는 매 /oauth2/authorize 요청 + 동의 화면에서
                // 결정하게 함)이 이 동작을 의도적으로 바꿨다 — JpaRegisteredClientRepository.toEntity()
                // 참고. 이 테스트가 그 변경 이후 실제로 실행/검증되지 않은 채 남아있던 회귀였다
                // (McpToolsEndToEndSpec을 작성하며 전체 스위트를 처음 함께 돌려보다가 발견).
                found.scopes shouldBe McpOAuthScopes.ALL
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
