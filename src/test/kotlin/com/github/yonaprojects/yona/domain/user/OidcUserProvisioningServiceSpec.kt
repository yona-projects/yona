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

            val user = oidcUser(
                mapOf("sub" to "abc123", "email" to "gildong@example.com", "email_verified" to true, "name" to "새이름")
            )

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

        // 보안: email_verified 클레임을 확인하지 않고 email만으로 기존 계정에 연결하면, 이메일
        // 검증을 강제하지 않는(또는 자유 가입이 가능한) 임의의 OIDC IdP에서 피해자의 이메일과
        // 같은 값을 자칭하는 계정을 만들어 로그인하는 것만으로 피해자의 기존 yona 계정을
        // 탈취(계정 인수)할 수 있다 — "범용 OIDC(임의 IdP)"를 지원 대상으로 명시한 이 계획의
        // 특성상 실제로 성립하는 위협이다(LDAP/특정 SAML IdP처럼 관리자가 이미 완전히 신뢰하는
        // 단일 디렉터리가 아니라, 신뢰 수준이 제각각인 임의 IdP를 붙이는 게 전제이기 때문).
        it("email_verified 클레임이 false면 기존 유저가 있어도 연결을 거부해야 한다(계정 탈취 방지)") {
            val existingUser = User(
                id = 7L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)

            val user = oidcUser(
                mapOf("sub" to "attacker-sub", "email" to "gildong@example.com", "email_verified" to false, "name" to "공격자")
            )

            shouldThrow<IllegalStateException> {
                service.reconcile(user)
            }
        }

        it("email_verified 클레임이 아예 없으면(생략) 기존 유저가 있어도 연결을 거부해야 한다(검증 여부 불명은 미검증으로 취급)") {
            val existingUser = User(
                id = 7L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)

            val user = oidcUser(mapOf("sub" to "attacker-sub", "email" to "gildong@example.com", "name" to "공격자"))

            shouldThrow<IllegalStateException> {
                service.reconcile(user)
            }
        }

        it("email_verified 클레임이 true면 기존 유저와 정상적으로 연결(동기화)해야 한다") {
            val existingUser = User(
                id = 7L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val user = oidcUser(
                mapOf("sub" to "abc123", "email" to "gildong@example.com", "email_verified" to true, "name" to "새이름")
            )

            val result = service.reconcile(user)

            result.id shouldBe 7L
            result.name shouldBe "새이름"
        }

        // email_verified 미확인은 "기존 계정에 몰래 연결"이 실제 위협인 경우에만 막는다 — 신규
        // 계정 생성은 뺏길 기존 계정이 없어 계정 탈취로 이어지지 않고, email_verified를 아예
        // 보내지 않는 정상적인 엔터프라이즈 IdP의 최초 SSO 로그인까지 막으면 과도한 제약이 된다.
        it("email_verified가 false여도 기존 유저가 없으면(신규 생성) 정상적으로 생성해야 한다") {
            every { userRepository.findByEmail("noone@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val user = oidcUser(
                mapOf("sub" to "abc123", "email" to "noone@example.com", "email_verified" to false, "name" to "새사람")
            )

            val result = service.reconcile(user)

            result.email shouldBe "noone@example.com"
            result.name shouldBe "새사람"
        }
    }
})
