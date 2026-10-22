package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * v1.6 `UserApp.loginByFormRequest`의 계정 판정 규칙을 검증한다.
 *
 * 판정 순서: (가입 승인제이면) LOCKED 거부 -> DELETED 거부 -> 패스워드 검사 -> 검사 통과 후 LOCKED 거부.
 */
@Transactional
class LocalAuthenticatorTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var authenticator: LocalAuthenticator

    @Autowired
    private lateinit var users: UserRepository

    private fun newUser(
        loginId: String, email: String, password: String = "1234", state: UserState = UserState.ACTIVE
    ): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                name = loginId, loginId = loginId, email = email, state = state,
                passwordSalt = salt, password = LegacyPasswordHasher.hash(password, salt)
            )
        )
    }

    @Test
    fun `로그인 ID와 올바른 패스워드로 인증된다`() {
        val user = newUser("tester", "tester@example.com")
        val result = authenticator.authenticate("tester", "1234")
        assertEquals(LoginResult.Success(user), result)
    }

    @Test
    fun `로그인 ID는 대소문자를 구별하지 않는다`() {
        newUser("MixedCase", "mixed@example.com")
        assertIs<LoginResult.Success>(authenticator.authenticate("mixedcase", "1234"))
    }

    @Test
    fun `이메일로도 로그인할 수 있다`() {
        val user = newUser("byemail", "byemail@example.com")
        assertEquals(LoginResult.Success(user), authenticator.authenticate("byemail@example.com", "1234"))
    }

    @Test
    fun `패스워드가 틀리면 거부한다`() {
        newUser("wrongpw", "wrongpw@example.com")
        assertEquals(LoginResult.InvalidCredentials, authenticator.authenticate("wrongpw", "nope"))
    }

    @Test
    fun `없는 계정은 거부한다`() {
        assertEquals(LoginResult.InvalidCredentials, authenticator.authenticate("nobody", "1234"))
    }

    @Test
    fun `삭제된 계정은 패스워드가 맞아도 거부한다`() {
        newUser("gone", "gone@example.com", state = UserState.DELETED)
        assertEquals(LoginResult.Deleted, authenticator.authenticate("gone", "1234"))
    }

    @Test
    fun `잠긴 계정은 패스워드가 맞으면 인증 후 잠김으로 알린다`() {
        newUser("locked", "locked@example.com", state = UserState.LOCKED)
        assertEquals(LoginResult.LockedAfterAuthentication, authenticator.authenticate("locked", "1234"))
    }

    @Test
    fun `잠긴 계정이 패스워드를 틀리면 일반 실패로 처리한다`() {
        newUser("locked2", "locked2@example.com", state = UserState.LOCKED)
        assertEquals(LoginResult.InvalidCredentials, authenticator.authenticate("locked2", "wrong"))
    }

    /** v1.6 결함 재현: 솔트나 패스워드가 비어 있는 계정은 `IllegalArgumentException`(HTTP 500)을 낸다. */
    @Test
    fun `솔트가 없는 계정은 v1_6처럼 예외를 던진다`() {
        users.save(User(loginId = "nosalt", email = "nosalt@example.com", state = UserState.ACTIVE))
        assertFailsWith<IllegalArgumentException> { authenticator.authenticate("nosalt", "1234") }
    }
}

/**
 * 가입 승인제(`signup.require.admin.confirm`)가 켜진 경우에는 패스워드를 검사하기 전에 잠긴 계정을 거부한다.
 */
@Transactional
@TestPropertySource(properties = ["yona.signup.require-admin-confirm=true"])
class LocalAuthenticatorSignUpConfirmTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var authenticator: LocalAuthenticator

    @Autowired
    private lateinit var users: UserRepository

    @Test
    fun `승인 대기 계정은 패스워드가 틀려도 잠김으로 알린다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(
            User(
                loginId = "pending", email = "pending@example.com", state = UserState.LOCKED,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
        assertEquals(LoginResult.Locked, authenticator.authenticate("pending", "wrong"))
    }
}
