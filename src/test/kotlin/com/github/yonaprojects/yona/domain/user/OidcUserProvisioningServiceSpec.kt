package com.github.yonaprojects.yona.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import java.time.Instant
import java.util.Optional

// yona-wiki P3-06(엔터프라이즈 SSO) Step2 — LdapUserProvisioningServiceSpec와 동일한 방식으로, 실제 IdP
// 없이 Spring Security가 파싱해서 넘겨주는 OidcUser 결과값을 직접 만들어 reconcile()에 넣는 순수 단위테스트.
class OidcUserProvisioningServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val service = OidcUserProvisioningService(userRepository)

    beforeTest {
        clearMocks(userRepository)
    }

    fun oidcUser(claims: Map<String, Any>): DefaultOidcUser {
        val idToken = OidcIdToken(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        )
        return DefaultOidcUser(emptyList(), idToken)
    }

    describe("OidcUserProvisioningService.reconcile") {
        it("이메일로 로컬 유저를 찾지 못하면 OIDC 클레임으로 신규 유저를 생성해야 한다") {
            val user = oidcUser(mapOf("sub" to "abc123", "email" to "gildong@example.com", "name" to "홍길동"))
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(user)

            result.loginId shouldBe "gildong"
            result.email shouldBe "gildong@example.com"
            result.name shouldBe "홍길동"
            result.state shouldBe UserState.ACTIVE
            result.password shouldBe null
        }

        it("이메일로 기존 유저를 찾으면 신규 생성 없이 이름만 동기화해야 한다") {
            val existingUser = User(
                id = 7L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val user = oidcUser(mapOf("sub" to "abc123", "email" to "gildong@example.com", "name" to "새이름"))

            val result = service.reconcile(user)

            result.id shouldBe 7L
            result.name shouldBe "새이름"
        }

        it("name 클레임이 없으면 이메일 로컬파트를 이름으로 사용해야 한다") {
            val user = oidcUser(mapOf("sub" to "abc123", "email" to "noname@example.com"))
            every { userRepository.findByEmail("noname@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(user)

            result.loginId shouldBe "noname"
            result.name shouldBe "noname"
        }

        it("이메일 클레임이 없으면 예외를 던져야 한다") {
            val user = oidcUser(mapOf("sub" to "abc123", "name" to "이메일없음"))

            shouldThrow<IllegalStateException> {
                service.reconcile(user)
            }
        }
    }
})
