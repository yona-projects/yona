package com.github.yonaprojects.yona.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal
import java.util.Optional

// yona-wiki P3-06(엔터프라이즈 SSO) Step3 — 실제 IdP(예: Keycloak) 없이, SAML 어서션을 Spring
// Security가 이미 파싱해 만들어 준 결과값인 Saml2AuthenticatedPrincipal(DefaultSaml2AuthenticatedPrincipal)을
// 직접 만들어 reconcile()에 넣는 순수 단위테스트. LdapUserProvisioningServiceSpec과 동일한 패턴.
class Saml2UserProvisioningServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val service = Saml2UserProvisioningService(userRepository)

    beforeTest {
        clearMocks(userRepository)
    }

    describe("Saml2UserProvisioningService.reconcile") {
        it("이메일 속성으로 로컬 유저를 찾지 못하면 SAML 어서션 속성으로 신규 유저를 생성해야 한다") {
            val principal = DefaultSaml2AuthenticatedPrincipal(
                "gildong@example.com",
                mapOf("email" to listOf("gildong@example.com"), "displayName" to listOf("홍길동"))
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(principal, "email", "displayName")

            result.loginId shouldBe "gildong"
            result.email shouldBe "gildong@example.com"
            result.name shouldBe "홍길동"
            result.state shouldBe UserState.ACTIVE
            result.password shouldBe null
        }

        it("이메일 속성으로 기존 유저를 찾으면 신규 생성 없이 이름만 동기화해야 한다") {
            val existingUser = User(
                id = 3L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val principal = DefaultSaml2AuthenticatedPrincipal(
                "gildong@example.com",
                mapOf("email" to listOf("gildong@example.com"), "displayName" to listOf("새이름"))
            )

            val result = service.reconcile(principal, "email", "displayName")

            result.id shouldBe 3L
            result.name shouldBe "새이름"
        }

        it("displayName 속성이 없으면 이메일 로컬파트를 이름으로 사용해야 한다") {
            val principal = DefaultSaml2AuthenticatedPrincipal(
                "noname@example.com",
                mapOf("email" to listOf("noname@example.com"))
            )
            every { userRepository.findByEmail("noname@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(principal, "email", "displayName")

            result.loginId shouldBe "noname"
            result.name shouldBe "noname"
        }

        it("email 속성이 없고 NameID도 이메일 형식이 아니면 예외를 던져야 한다") {
            val principal = DefaultSaml2AuthenticatedPrincipal(
                "not-an-email-subject",
                mapOf("displayName" to listOf("이메일없음"))
            )

            shouldThrow<IllegalStateException> {
                service.reconcile(principal, "email", "displayName")
            }
        }

        it("email 속성이 없어도 NameID가 이메일 형식이면 그것을 이메일로 사용해야 한다") {
            val principal = DefaultSaml2AuthenticatedPrincipal(
                "subject-email@example.com",
                mapOf("displayName" to listOf("이메일 없는 속성"))
            )
            every { userRepository.findByEmail("subject-email@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(principal, "email", "displayName")

            result.email shouldBe "subject-email@example.com"
            result.loginId shouldBe "subject-email"
        }
    }
})
