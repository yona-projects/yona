package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Optional

class LdapUserProvisioningServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val passwordEncodingService = PasswordEncodingService()
    val service = LdapUserProvisioningService(userRepository, passwordEncodingService)

    beforeTest {
        clearMocks(userRepository)
    }

    describe("LdapUserProvisioningService.reconcile") {
        it("이메일로 로컬 유저를 찾지 못하면 LDAP 정보로 신규 유저를 생성하고 비밀번호는 Argon2로 저장해야 한다") {
            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                department = "개발팀", isGuestUser = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(ldapUser, "myPassword123!")

            result.loginId shouldBe "gildong"
            result.email shouldBe "gildong@example.com"
            result.name shouldBe "홍길동 [개발팀]"
            result.state shouldBe UserState.ACTIVE
            result.passwordSalt shouldBe null
            passwordEncodingService.matches("myPassword123!", result.password, null) shouldBe true
        }

        it("이메일로 기존 유저를 찾으면 비밀번호가 다를 때만 Argon2로 재발급하고 이름/게스트 여부를 동기화해야 한다") {
            val oldSalt = "old-salt"
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = PasswordEncodingService.legacyHash("oldPassword", oldSalt), passwordSalt = oldSalt, isGuest = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                department = "개발팀", isGuestUser = true
            )

            val result = service.reconcile(ldapUser, "newPassword456!")

            result.id shouldBe 5L
            result.name shouldBe "홍길동 [개발팀]"
            result.isGuest shouldBe true
            result.passwordSalt shouldBe null
            passwordEncodingService.matches("newPassword456!", result.password, null) shouldBe true
        }

        it("기존 유저의 비밀번호가 이미 동일하면(레거시 포맷이라도) 비밀번호를 재발급하지 않아야 한다") {
            val salt = "same-salt"
            val legacyHashed = PasswordEncodingService.legacyHash("samePassword", salt)
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = legacyHashed, passwordSalt = salt, isGuest = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(displayName = "홍길동", email = "gildong@example.com", loginId = "gildong")

            val result = service.reconcile(ldapUser, "samePassword")

            result.password shouldBe legacyHashed
            result.passwordSalt shouldBe salt
        }

        it("englishName이 비어있지 않으면 기존 유저의 englishName을 갱신해야 한다") {
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = "x", passwordSalt = "y", isGuest = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                englishName = "Gildong Hong"
            )

            val result = service.reconcile(ldapUser, "pw")

            result.englishName shouldBe "Gildong Hong"
        }

        it("신규 유저 생성 시 englishName이 존재하면 설정해야 한다") {
            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                department = "개발팀", isGuestUser = false, englishName = "Gildong Hong"
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(ldapUser, "myPassword123!")

            result.englishName shouldBe "Gildong Hong"
        }

        // isNullOrBlank()는 null-체크와 isBlank-체크 두 서브 분기로 구성된다. 기존 테스트는
        // englishName이 없음(null 기본값)과 명확한 값("Gildong Hong")만 다뤄서, "non-null이지만
        // 공백뿐"인 케이스(isBlank()==true 서브 분기)가 신규 생성/기존 동기화 양쪽 호출부 모두 비어 있었다.
        it("신규 유저 생성 시 englishName이 공백뿐이면 설정하지 않아야 한다") {
            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                department = "개발팀", isGuestUser = false, englishName = "   "
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            val savedSlot = slot<User>()
            every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.reconcile(ldapUser, "myPassword123!")

            result.englishName shouldBe null
        }

        it("기존 유저 동기화 시 englishName이 공백뿐이면 갱신하지 않아야 한다") {
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = "x", passwordSalt = "y", isGuest = false, englishName = "Old English Name"
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                englishName = "   "
            )

            val result = service.reconcile(ldapUser, "pw")

            result.englishName shouldBe "Old English Name"
        }

        it("기존 유저의 passwordSalt가 null(레거시 포맷 판정 불가)이면 비밀번호 불일치로 간주하고 Argon2로 재발급해야 한다") {
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = "somePassword", passwordSalt = null, isGuest = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(
                displayName = "홍길동", email = "gildong@example.com", loginId = "gildong",
                department = "개발팀", isGuestUser = true
            )

            val result = service.reconcile(ldapUser, "newPassword456!")

            result.passwordSalt shouldBe null
            passwordEncodingService.matches("newPassword456!", result.password, null) shouldBe true
        }
    }
})
