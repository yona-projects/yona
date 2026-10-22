package dev.yona.domain.user

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 로그인 ID(그리고 같은 규칙을 쓰는 조직 이름)의 형식과 예약어를 검증한다.
 *
 * 원본: `app/models/User.java:66`, `app/utils/ReservedWordsValidator.java`.
 */
class LoginIdRulesTest {

    @Test
    fun `영문 숫자 한글 하이픈으로 시작하고 밑줄이나 점으로 이어 쓸 수 있다`() {
        listOf("alice", "Alice-1", "홍길동", "a_b", "a.b", "a-b_c.d", "a1_b2.c3", "a..b").forEach { assertTrue(LoginIdRules.isValidFormat(it), it) }
    }

    @Test
    fun `공백이나 허용하지 않는 기호가 있으면 형식 오류이다`() {
        listOf("", "a b", "a@b", "a/b", "_a", ".a", "a_", "a#", "a!").forEach { assertFalse(LoginIdRules.isValidFormat(it), it) }
    }

    @Test
    fun `예약어는 대소문자를 가리지 않고 거부한다`() {
        assertTrue(LoginIdRules.isReserved("projects"))
        assertTrue(LoginIdRules.isReserved("Projects"))
        assertTrue(LoginIdRules.isReserved("ORGS"))
        assertTrue(LoginIdRules.isReserved("new"))
        assertTrue(LoginIdRules.isReserved("-_-api"))
        assertTrue(LoginIdRules.isReserved("messages.js"))
    }

    @Test
    fun `예약어가 아닌 이름은 통과한다`() {
        assertFalse(LoginIdRules.isReserved("alice"))
        assertFalse(LoginIdRules.isReserved("projectsx"))
    }

    @Test
    fun `예약어 목록은 v1 6 라우트의 첫 구간과 네 개의 추가어이다`() {
        assertEquals(34, LoginIdRules.RESERVED_WORDS.size)
    }
}
