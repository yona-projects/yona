package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 비밀번호를 만들고 확인하는 유일한 입구 [UserPasswordService]를 검증한다(로드맵 X-43 1단계 작업 1-2, `decisions.md` D-129).
 * 해시 알고리즘은 v1.6과 같고(`LegacyPasswordHasherTest`의 고정 벡터), 솔트와 해시를 `n4user`의 두 열에 따로 두는 형식도 그대로이다.
 */
class UserPasswordServiceTest {

    private val service = UserPasswordService()

    private fun user(salt: String? = "salt", password: String? = null) =
        User(loginId = "u", name = "u", email = "u@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = password)

    @Test
    fun `새 비밀번호 정보는 새 솔트와 그 솔트로 만든 해시이고 호출마다 솔트가 다르다`() {
        val a = service.newCredentials("pw"); val b = service.newCredentials("pw")
        assertEquals(24, a.salt.length, "솔트 열의 형식은 그대로 24글자이다(argon2id는 솔트가 해시 문자열 안에 있어 이 값은 쓰이지 않는다)")
        assertTrue(a.hash.startsWith("\$argon2id\$"), "새 해시는 argon2id이다: ${a.hash}")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertTrue(service.matches(user(salt = a.salt, password = a.hash), "pw"))
        assertFalse(service.matches(user(salt = a.salt, password = a.hash), "PW"))
    }

    @Test
    fun `비밀번호를 정하면 솔트를 유지하고 해시만 바꾼다`() {
        val user = user(salt = "kept-salt", password = "old-hash")
        service.set(user, "new-pass")
        assertEquals("kept-salt", user.passwordSalt)
        assertTrue(user.password!!.startsWith("\$argon2id\$"), "바꾼 해시는 argon2id이다")
        assertTrue(service.matches(user, "new-pass"))
        assertFalse(service.matches(user, "old"))
    }

    @Test
    fun `확인은 맞는 비밀번호만 통과시킨다`() {
        val user = user(salt = "s", password = LegacyPasswordHasher.hash("right", "s"))
        assertTrue(service.matches(user, "right"))
        assertFalse(service.matches(user, "wrong"))
        assertFalse(service.matches(user, ""))
        assertFalse(service.matches(user, "RIGHT"))
    }

    @Test
    fun `솔트나 해시가 없으면 v1_6처럼 예외를 던진다`() {
        val noSalt = assertFailsWith<IllegalArgumentException> { service.matches(user(salt = null, password = "h"), "x") }
        assertEquals("Bad password or passwordSalt!", noSalt.message)
        assertFailsWith<IllegalArgumentException> { service.matches(user(salt = "s", password = null), "x") }
        assertFailsWith<IllegalArgumentException> { service.set(user(salt = null, password = "h"), "x") }
    }

    @Test
    fun `v1_6 초기 관리자 해시는 지금도 평문 admin00으로 확인된다`() {
        val admin = user(salt = "[B@1032a4", password = "5v4TVjzLo1bqullT1CU4/bENUNOUfX97WpdunGLvJvw=")
        assertTrue(service.matches(admin, "admin00"))
    }

    @Test
    fun `v1_6 해시는 갱신이 필요하고 argon2id 해시는 필요하지 않다`() {
        assertTrue(service.needsUpgrade(user(salt = "s", password = LegacyPasswordHasher.hash("pw", "s"))))
        assertFalse(service.needsUpgrade(user(salt = "s", password = service.newCredentials("pw").hash)))
        assertFalse(service.needsUpgrade(user(salt = "s", password = null)), "해시가 없으면 갱신할 것이 없다")
    }

    @Test
    fun `v1_6 해시는 새 방식으로 바꾼 뒤에도 같은 비밀번호로 확인된다`() {
        val user = user(salt = "s", password = LegacyPasswordHasher.hash("pw", "s"))
        assertTrue(service.matches(user, "pw"))
        service.set(user, "pw")
        assertTrue(user.password!!.startsWith("\$argon2id\$"))
        assertTrue(service.matches(user, "pw"))
        assertFalse(service.needsUpgrade(user))
        assertTrue(service.matchesLenient(user, "pw"))
        assertFalse(service.matchesLenient(user, "other"))
    }

    @Test
    fun `관대한 확인은 솔트나 해시가 없어도 예외 없이 거짓이다`() {
        assertFalse(service.matchesLenient(user(salt = null, password = null), "x"))
        assertFalse(service.matchesLenient(user(salt = "s", password = null), "x"))
        assertFalse(service.matchesLenient(user(salt = null, password = "h"), "x"))
    }

    @Test
    fun `관대한 확인도 맞는 비밀번호는 통과시키고 틀리면 거부한다`() {
        val user = user(salt = "s", password = LegacyPasswordHasher.hash("right", "s"))
        assertTrue(service.matchesLenient(user, "right"))
        assertFalse(service.matchesLenient(user, "wrong"))
    }
}
