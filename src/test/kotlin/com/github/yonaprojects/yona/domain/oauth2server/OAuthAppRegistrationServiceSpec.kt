package com.github.yonaprojects.yona.domain.oauth2server

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.crypto.factory.PasswordEncoderFactories

// yona-wiki P3-14 2라운드(OIDC) — availableScopes()/register()가 identity 스코프(openid/profile/
// email)를 API 스코프(ApiTokenScopeGroup x READ/WRITE)와 동일한 화이트리스트 검증 경로로 다루는지
// 검증한다(별도 축이지만 검증 메커니즘은 공유 — OAuthAppRegistrationService 주석 참고).
class OAuthAppRegistrationServiceSpec : DescribeSpec({

    val clientRepository = mockk<OAuthRegisteredClientRepository>()
    val consentRepository = mockk<OAuthAuthorizationConsentRepository>()
    val authorizationRepository = mockk<OAuthAuthorizationRepository>()
    val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
    val service = OAuthAppRegistrationService(clientRepository, consentRepository, authorizationRepository, passwordEncoder)

    describe("availableScopes") {
        it("API 스코프에 더해 identity 스코프(openid/profile/email)를 포함해야 한다") {
            val scopes = service.availableScopes()

            scopes shouldContain "openid"
            scopes shouldContain "profile"
            scopes shouldContain "email"
            scopes shouldContain "issues:read"
            scopes shouldContain "issues:write"
        }
    }

    describe("identityScopes") {
        it("openid/profile/email 세 개만 반환해야 한다") {
            service.identityScopes() shouldBe listOf("openid", "profile", "email")
        }
    }

    describe("register") {
        it("identity 스코프를 화이트리스트로 허용해 저장해야 한다") {
            every { clientRepository.save(any()) } answers { firstArg() }

            val registered = service.register(
                clientName = "Sign in with yona Test App",
                redirectUri = "https://example.com/callback",
                confidential = true,
                scopes = listOf("openid", "profile", "email"),
                ownerId = 1L
            )

            val savedScopes = registered.client.scopes.split(",").toSet()
            savedScopes shouldBe setOf("openid", "profile", "email")
        }

        it("화이트리스트에 없는 임의의 문자열은 걸러내야 한다") {
            every { clientRepository.save(any()) } answers { firstArg() }

            val registered = service.register(
                clientName = "Test App",
                redirectUri = "https://example.com/callback",
                confidential = false,
                scopes = listOf("openid", "admin:god-mode"),
                ownerId = 1L
            )

            val savedScopes = registered.client.scopes.split(",").toSet()
            savedScopes shouldContain "openid"
            savedScopes shouldNotContain "admin:god-mode"
        }
    }
})
