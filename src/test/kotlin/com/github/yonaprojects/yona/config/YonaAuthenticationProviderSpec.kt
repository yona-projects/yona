package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.LdapAuthResult
import com.github.yonaprojects.yona.domain.user.LdapService
import com.github.yonaprojects.yona.domain.user.LdapUser
import com.github.yonaprojects.yona.domain.user.LdapUserProvisioningService
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import java.util.Optional
import org.springframework.security.authentication.TestingAuthenticationToken

class YonaAuthenticationProviderSpec : DescribeSpec({
    val userDetailsService = mockk<UserDetailsService>()
    val ldapService = mockk<LdapService>()
    val ldapUserProvisioningService = mockk<LdapUserProvisioningService>()
    val userRepository = mockk<UserRepository>()
    val passwordEncodingService = PasswordEncodingService()
    val authenticationProvider = YonaAuthenticationProvider(
        userDetailsService, ldapService, ldapUserProvisioningService, passwordEncodingService, userRepository
    )

    beforeTest {
        // userRepository는 이 스펙에서 verify(exactly = 0){...}로도 검증하므로, 이전 테스트의
        // 호출 기록이 다음 테스트로 새는 것을 막기 위해 매번 초기화한다.
        clearMocks(userRepository, answers = false)
        every { ldapService.enabled } returns false
        // 레거시 해시로 로그인 성공 시 자동 재해싱 업그레이드가 시도된다 — 이 스펙의 관심사가
        // 아닌 테스트에서는 대상 사용자가 없는 것으로 취급해 업그레이드를 조용히 건너뛴다.
        every { userRepository.findById(any()) } returns Optional.empty()
    }

    fun getLegacyHashedPassword(password: String, salt: String): String =
        PasswordEncodingService.legacyHash(password, salt)

    describe("YonaAuthenticationProvider") {
        it("올바른 비밀번호를 입력하면 인증이 정상적으로 완료되어야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            
            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "gildong",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails
            
            val authRequest = UsernamePasswordAuthenticationToken("gildong", rawPassword)

            // When
            val authResult = authenticationProvider.authenticate(authRequest)

            // Then
            authResult shouldNotBe null
            authResult.isAuthenticated shouldBe true
            authResult.name shouldBe "gildong"
            authResult.principal shouldBe userDetails
        }

        it("잘못된 비밀번호를 입력하면 BadCredentialsException 예외가 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            
            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "gildong",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails
            
            val authRequest = UsernamePasswordAuthenticationToken("gildong", "wrongPassword")

            // When & Then
            shouldThrow<BadCredentialsException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("계정 상태가 LOCKED인 사용자는 비밀번호가 맞아도 LockedException이 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "lockedUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_LOCKED")),
                state = UserState.LOCKED
            )

            every { userDetailsService.loadUserByUsername("lockedUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("lockedUser", rawPassword)

            // When & Then
            shouldThrow<LockedException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("계정 상태가 DELETED인 사용자는 비밀번호가 맞아도 DisabledException이 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "deletedUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_DELETED")),
                state = UserState.DELETED
            )

            every { userDetailsService.loadUserByUsername("deletedUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("deletedUser", rawPassword)

            // When & Then
            shouldThrow<DisabledException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("계정 상태가 ACTIVE가 아니어도 LOCKED/DELETED가 아니면(SITE_ADMIN 등) 정상 인증되어야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "adminUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_SITE_ADMIN")),
                state = UserState.SITE_ADMIN
            )

            every { userDetailsService.loadUserByUsername("adminUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("adminUser", rawPassword)

            // When
            val authResult = authenticationProvider.authenticate(authRequest)

            // Then
            authResult.isAuthenticated shouldBe true
        }
    }

    describe("YonaAuthenticationProvider - LDAP 인증 활성화") {
        it("LDAP 인증에 성공하면 재조정(reconcile)된 로컬 사용자로 인증되어야 한다") {
            val ldapUser = LdapUser(displayName = "홍길동", email = "gildong@example.com", loginId = "gildong")
            val reconciledUser = User(id = 7L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
            val userDetails = YonaUserDetails(
                id = 7L, loginId = "gildong", passwordVal = "x", passwordSalt = "y",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )

            every { ldapService.enabled } returns true
            every { ldapService.authenticate("gildong", "myPassword123!") } returns LdapAuthResult.Success(ldapUser)
            every { ldapUserProvisioningService.reconcile(ldapUser, "myPassword123!") } returns reconciledUser
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("gildong", "myPassword123!")
            val authResult = authenticationProvider.authenticate(authRequest)

            authResult.isAuthenticated shouldBe true
            authResult.principal shouldBe userDetails
            verify(exactly = 1) { ldapUserProvisioningService.reconcile(ldapUser, "myPassword123!") }
        }

        it("LDAP 인증 실패 + fallback 비활성화면 로컬 인증을 시도하지 않고 BadCredentialsException을 던져야 한다") {
            every { ldapService.enabled } returns true
            every { ldapService.fallbackToLocalLogin } returns false
            every { ldapService.authenticate("gildong", "wrongPassword") } returns LdapAuthResult.InvalidCredentials

            val authRequest = UsernamePasswordAuthenticationToken("gildong", "wrongPassword")

            // 로컬 인증(authenticateLocally)으로 넘어갔다면 loadUserByUsername("gildong")에
            // stub이 없어 MockKException이 발생해 shouldThrow<BadCredentialsException>이
            // 실패하므로, 아래 통과 자체가 fallback이 일어나지 않았음을 증명한다.
            shouldThrow<BadCredentialsException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("LDAP 인증 실패 + fallback 활성화면 로컬 비밀번호 인증으로 넘어가야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 1L, loginId = "gildong", passwordVal = expectedHashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )

            every { ldapService.enabled } returns true
            every { ldapService.fallbackToLocalLogin } returns true
            every { ldapService.authenticate("gildong", rawPassword) } returns LdapAuthResult.InvalidCredentials
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("gildong", rawPassword)
            val authResult = authenticationProvider.authenticate(authRequest)

            authResult.isAuthenticated shouldBe true
        }

        it("LDAP 서버 연결 실패 + fallback 비활성화면 AuthenticationServiceException을 던져야 한다") {
            every { ldapService.enabled } returns true
            every { ldapService.fallbackToLocalLogin } returns false
            every { ldapService.authenticate("gildong", "pw") } returns LdapAuthResult.ConnectionFailed(RuntimeException("timeout"))

            val authRequest = UsernamePasswordAuthenticationToken("gildong", "pw")

            shouldThrow<AuthenticationServiceException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("LDAP 서버 연결 실패 + fallback 활성화면 로컬 인증을 시도해야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 1L, loginId = "gildong", passwordVal = expectedHashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )

            every { ldapService.enabled } returns true
            every { ldapService.fallbackToLocalLogin } returns true
            every { ldapService.authenticate("gildong", rawPassword) } returns LdapAuthResult.ConnectionFailed(RuntimeException("timeout"))
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("gildong", rawPassword)
            val authResult = authenticationProvider.authenticate(authRequest)

            authResult.isAuthenticated shouldBe true
        }

        it("LDAP로 재조정된 사용자가 LOCKED 상태면 LockedException이 발생해야 한다") {
            val ldapUser = LdapUser(displayName = "잠긴유저", email = "locked@example.com", loginId = "lockeduser")
            val reconciledUser = User(id = 8L, loginId = "lockeduser", name = "잠긴유저", email = "locked@example.com")
            val userDetails = YonaUserDetails(
                id = 8L, loginId = "lockeduser", passwordVal = "x", passwordSalt = "y",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_LOCKED")), state = UserState.LOCKED
            )

            every { ldapService.enabled } returns true
            every { ldapService.authenticate("lockeduser", "pw") } returns LdapAuthResult.Success(ldapUser)
            every { ldapUserProvisioningService.reconcile(ldapUser, "pw") } returns reconciledUser
            every { userDetailsService.loadUserByUsername("lockeduser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("lockeduser", "pw")

            shouldThrow<LockedException> {
                authenticationProvider.authenticate(authRequest)
            }
        }
    }

    describe("YonaAuthenticationProvider - 비밀번호 해시 자동 업그레이드(법적 컴플라이언스 감사 #5)") {
        it("레거시 포맷으로 저장된 계정이 로그인에 성공하면 그 자리에서 Argon2 포맷으로 재해싱해 저장해야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val legacyHashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 42L, loginId = "upgrademe", passwordVal = legacyHashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            val storedUser = User(id = 42L, loginId = "upgrademe", password = legacyHashed, passwordSalt = salt)

            every { userDetailsService.loadUserByUsername("upgrademe") } returns userDetails
            every { userRepository.findById(42L) } returns Optional.of(storedUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val authRequest = UsernamePasswordAuthenticationToken("upgrademe", rawPassword)
            val authResult = authenticationProvider.authenticate(authRequest)

            authResult.isAuthenticated shouldBe true
            val savedUser = slot<User>()
            verify(exactly = 1) { userRepository.save(capture(savedUser)) }
            savedUser.captured.password!!.shouldStartWith("$")
            savedUser.captured.passwordSalt shouldBe null
            passwordEncodingService.matches(rawPassword, savedUser.captured.password, null) shouldBe true
        }

        it("이미 Argon2 포맷인 계정은 재해싱을 시도하지 않아야 한다") {
            val rawPassword = "myPassword123!"
            val argon2Hashed = passwordEncodingService.encode(rawPassword)
            val userDetails = YonaUserDetails(
                id = 99L, loginId = "already-argon2", passwordVal = argon2Hashed, passwordSalt = "",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )

            every { userDetailsService.loadUserByUsername("already-argon2") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("already-argon2", rawPassword)
            val authResult = authenticationProvider.authenticate(authRequest)

            authResult.isAuthenticated shouldBe true
            verify(exactly = 0) { userRepository.save(any()) }
        }
    }

    describe("YonaAuthenticationProvider - 브루트포스 자동 잠금(법적 컴플라이언스 감사 #4)") {
        it("비밀번호 실패가 5회 누적되면 계정을 15분간 자동 잠그고, 그 다음 시도는 올바른 비밀번호여도 LockedException을 던져야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val hashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 55L, loginId = "bruteforced", passwordVal = hashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            val storedUser = User(id = 55L, loginId = "bruteforced", password = hashed, passwordSalt = salt)

            every { userDetailsService.loadUserByUsername("bruteforced") } returns userDetails
            every { userRepository.findById(55L) } returns Optional.of(storedUser)
            every { userRepository.save(any()) } answers { firstArg() }

            repeat(5) {
                shouldThrow<BadCredentialsException> {
                    authenticationProvider.authenticate(UsernamePasswordAuthenticationToken("bruteforced", "wrong-password"))
                }
            }

            storedUser.failedLoginAttempts shouldBe 5
            storedUser.lockedUntil shouldNotBe null

            // 잠긴 뒤에는 loadUserByUsername이 최신 lockedUntil을 반영한 YonaUserDetails를
            // 반환한다고 가정한다(UserDetailsServiceImpl이 실제 User를 매번 다시 읽으므로).
            val lockedUserDetails = YonaUserDetails(
                id = 55L, loginId = "bruteforced", passwordVal = hashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE")), lockedUntil = storedUser.lockedUntil
            )
            every { userDetailsService.loadUserByUsername("bruteforced") } returns lockedUserDetails

            shouldThrow<LockedException> {
                authenticationProvider.authenticate(UsernamePasswordAuthenticationToken("bruteforced", rawPassword))
            }
        }

        it("잠기기 전이면 실패 도중이라도 올바른 비밀번호로 로그인에 성공하고 실패 횟수가 리셋되어야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val hashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 56L, loginId = "recovers", passwordVal = hashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            val storedUser = User(id = 56L, loginId = "recovers", password = hashed, passwordSalt = salt, failedLoginAttempts = 3)

            every { userDetailsService.loadUserByUsername("recovers") } returns userDetails
            every { userRepository.findById(56L) } returns Optional.of(storedUser)
            every { userRepository.save(any()) } answers { firstArg() }

            val authResult = authenticationProvider.authenticate(UsernamePasswordAuthenticationToken("recovers", rawPassword))

            authResult.isAuthenticated shouldBe true
            storedUser.failedLoginAttempts shouldBe 0
            storedUser.lockedUntil shouldBe null
        }

        it("lockedUntil이 이미 지난 계정은 자동으로 정상 로그인되어야 한다") {
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val hashed = getLegacyHashedPassword(rawPassword, salt)
            val userDetails = YonaUserDetails(
                id = 57L, loginId = "expiredlock", passwordVal = hashed, passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE")),
                lockedUntil = java.time.Instant.now().minusSeconds(60)
            )

            every { userDetailsService.loadUserByUsername("expiredlock") } returns userDetails

            val authResult = authenticationProvider.authenticate(UsernamePasswordAuthenticationToken("expiredlock", rawPassword))

            authResult.isAuthenticated shouldBe true
        }
    }

    describe("YonaAuthenticationProvider - supports") {
        it("UsernamePasswordAuthenticationToken을 지원해야 한다") {
            authenticationProvider.supports(UsernamePasswordAuthenticationToken::class.java) shouldBe true
        }

        it("다른 Authentication 구현체는 지원하지 않아야 한다") {
            authenticationProvider.supports(TestingAuthenticationToken::class.java) shouldBe false
        }
    }
})
