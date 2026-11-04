package dev.yona.web.social

import dev.yona.domain.social.SocialIdentity
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 소셜 세션의 만료 판정을 검증한다(`architecture/v16-safe-fix-candidates.md` R-59-13). */
class SocialSessionExpiryTest {

    private val session = SocialSession()

    private fun started(expires: Long): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        session.start(request, SocialIdentity("github", "1", "a@example.com", "이름", expires))
        return request
    }

    @Test
    fun `만료되지 않은 소셜 세션은 소셜 로그인 상태이다`() {
        assertTrue(session.hasKeys(started(System.currentTimeMillis() + 60_000)))
        assertTrue(session.hasKeys(started(-1)))
    }

    @Test
    fun `만료된 소셜 세션은 메뉴와 로그인 화면에서도 소셜 로그인 상태가 아니다 (R-59-13)`() {
        // v1.6은 키만 있으면 소셜 로그인 상태로 보여 컨트롤러가 만료로 보는 것과 어긋났다
        val request = started(System.currentTimeMillis() - 60_000)
        assertFalse(session.hasKeys(request))
    }
}
