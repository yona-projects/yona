package dev.yona.domain.user

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** v1.6 `User.getDisplayName(User)`, `extractDepartmentPart`(`app/models/User.java:1166-1197`)의 표시 이름 규칙을 검증한다. */
class UserDisplayTest {

    private fun user(name: String, english: String? = null, lang: String? = "ko-KR") =
        User(name = name, englishName = english, lang = lang)

    @Test
    fun `영문 이름이 없으면 이름 그대로 보여 준다`() {
        assertEquals("홍길동", UserDisplay.of(user("홍길동"), "en-US"))
    }

    @Test
    fun `보는 사람이 영어 사용자이면 영문 이름에 부서를 붙인다`() {
        assertEquals("Gildong (개발팀)", UserDisplay.of(user("홍길동 (개발팀)", "Gildong"), "en-US"))
        assertEquals("Gildong [개발팀]", UserDisplay.of(user("홍길동 [개발팀]", "Gildong"), "en"))
    }

    @Test
    fun `보는 사람이 영어 사용자가 아니면 이름 그대로이다`() {
        assertEquals("홍길동 (개발팀)", UserDisplay.of(user("홍길동 (개발팀)", "Gildong"), "ko-KR"))
        assertEquals("홍길동", UserDisplay.of(user("홍길동", "Gildong"), null))
    }

    @Test
    fun `대상 사용자의 언어가 없으면 영문 이름을 쓰지 않는다`() {
        assertEquals("홍길동", UserDisplay.of(user("홍길동", "Gildong", lang = null), "en-US"))
    }

    @Test
    fun `영문 이름이 공백이면 쓰지 않는다`() {
        assertEquals("홍길동", UserDisplay.of(user("홍길동", "  "), "en-US"))
    }

    @Test
    fun `부서 구분자가 둘 다 있으면 나중에 찾은 괄호 기준이 이긴다`() {
        // 이름 "홍길동 [팀] (개발)": "["부터 자르면 "[팀] (개발)", 이어서 "("부터 자르면 "(개발)"이 된다.
        assertEquals("(개발)", UserDisplay.departmentPart("홍길동 [팀] (개발)"))
    }

    @Test
    fun `구분자가 없으면 이름 전체가 부서 부분이다`() {
        assertEquals("홍길동", UserDisplay.departmentPart("홍길동"))
    }
}
