package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2AuthorizationConsentService의 JPA 구현체 검증. 이 테이블은
// 동시에 "Authorized OAuth Apps" 화면(principalName으로 조회)의 데이터 소스이므로
// findByPrincipalName()도 함께 검증한다.
class JpaOAuth2AuthorizationConsentServiceSpec @Autowired constructor(
    private val consentService: JpaOAuth2AuthorizationConsentService,
    private val consentRepository: OAuthAuthorizationConsentRepository
) : AbstractIntegrationTest() {

    init {
        describe("JpaOAuth2AuthorizationConsentService") {
            beforeEach { consentRepository.deleteAll() }
            afterSpec { consentRepository.deleteAll() }

            it("동의를 저장하고 (clientId, principalName)으로 조회하면 스코프 권한이 그대로 보존돼야 한다") {
                val consent = OAuth2AuthorizationConsent.withId("client-1", "consent-owner")
                    .authority(SimpleGrantedAuthority("SCOPE_issues:read"))
                    .authority(SimpleGrantedAuthority("SCOPE_issues:write"))
                    .build()

                consentService.save(consent)

                val found = consentService.findById("client-1", "consent-owner")
                found.shouldNotBeNull()
                found.authorities.map { it.authority }.toSet() shouldBe setOf("SCOPE_issues:read", "SCOPE_issues:write")

                val byPrincipal = consentRepository.findByPrincipalName("consent-owner")
                byPrincipal.size shouldBe 1
                byPrincipal[0].registeredClientId shouldBe "client-1"
            }

            it("동의를 제거하면 더 이상 조회되지 않아야 한다") {
                val consent = OAuth2AuthorizationConsent.withId("client-2", "consent-owner-2")
                    .authority(SimpleGrantedAuthority("SCOPE_issues:read"))
                    .build()
                consentService.save(consent)

                consentService.remove(consent)

                consentService.findById("client-2", "consent-owner-2") shouldBe null
            }
        }
    }
}
