package dev.yona.domain.user

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Play 2.3.10 `Constraints.EmailValidator`의 정규식(`\\b...\\b`, `Matcher.matches`)과 같은 결과를 내는지 검증한다. 기대값은 Play 클래스로 직접 실행해 얻었다. */
class SignupServiceEmailTest {

    @Test
    fun `로컬 부분이 단어 문자로 시작하지 않으면 거부한다`() {
        for (address in listOf("+a@b.com", ".a@b.com", "-a@b.com")) assertEquals(false, SignupService.isValidEmailFormat(address), address)
    }

    @Test
    fun `일반 주소는 받는다`() {
        for (address in listOf("a@b.com", "a.b+c@sub.example.co.kr", "user_name@example.org", "a@b")) assertEquals(true, SignupService.isValidEmailFormat(address), address)
    }

    @Test
    fun `골뱅이가 없거나 도메인이 하이픈으로 끝나면 거부한다`() {
        for (address in listOf("no-at-sign", "a@b-", "a@-b.com", "a b@c.com")) assertEquals(false, SignupService.isValidEmailFormat(address), address)
    }
}
