package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.LdapAuthResult
import com.github.yonaprojects.yona.domain.user.LdapService
import com.github.yonaprojects.yona.domain.user.LdapUser
import com.github.yonaprojects.yona.domain.user.LdapUserProvisioningService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import java.security.MessageDigest
import java.util.Base64
import org.springframework.security.authentication.TestingAuthenticationToken

class YonaAuthenticationProviderSpec : DescribeSpec({
    val userDetailsService = mockk<UserDetailsService>()
    val ldapService = mockk<LdapService>()
    val ldapUserProvisioningService = mockk<LdapUserProvisioningService>()
    val authenticationProvider = YonaAuthenticationProvider(userDetailsService, ldapService, ldapUserProvisioningService)

    beforeTest {
        every { ldapService.enabled } returns false
    }

    fun getLegacyHashedPassword(password: String, salt: String): String {
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

    describe("YonaAuthenticationProvider - supports") {
        it("UsernamePasswordAuthenticationToken을 지원해야 한다") {
            authenticationProvider.supports(UsernamePasswordAuthenticationToken::class.java) shouldBe true
        }

        it("다른 Authentication 구현체는 지원하지 않아야 한다") {
            authenticationProvider.supports(TestingAuthenticationToken::class.java) shouldBe false
        }
    }
})
