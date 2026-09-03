package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.security.MessageDigest
import java.util.Base64
import java.util.Optional

class LdapUserProvisioningServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val service = LdapUserProvisioningService(userRepository)

    fun legacyHash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    beforeTest {
        clearMocks(userRepository)
    }

    describe("LdapUserProvisioningService.reconcile") {
        it("이메일로 로컬 유저를 찾지 못하면 LDAP 정보로 신규 유저를 생성해야 한다") {
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
            result.password shouldNotBe null
            result.passwordSalt shouldNotBe null
            legacyHash("myPassword123!", result.passwordSalt!!) shouldBe result.password
        }

        it("이메일로 기존 유저를 찾으면 비밀번호가 다를 때만 재설정하고 이름/게스트 여부를 동기화해야 한다") {
            val oldSalt = "old-salt"
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = legacyHash("oldPassword", oldSalt), passwordSalt = oldSalt, isGuest = false
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
            legacyHash("newPassword456!", result.passwordSalt!!) shouldBe result.password
        }

        it("기존 유저의 비밀번호가 이미 동일하면 비밀번호/salt를 재발급하지 않아야 한다") {
            val salt = "same-salt"
            val existingUser = User(
                id = 5L, loginId = "gildong", name = "옛이름", email = "gildong@example.com",
                password = legacyHash("samePassword", salt), passwordSalt = salt, isGuest = false
            )
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val ldapUser = LdapUser(displayName = "홍길동", email = "gildong@example.com", loginId = "gildong")

            val result = service.reconcile(ldapUser, "samePassword")

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

        it("기존 유저의 passwordSalt가 null이면 비밀번호 불일치로 간주하고 재발급해야 한다") {
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

            result.passwordSalt shouldNotBe null
        }
    }
})
