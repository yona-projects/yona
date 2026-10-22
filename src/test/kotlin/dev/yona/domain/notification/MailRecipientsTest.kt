package dev.yona.domain.notification

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 메일을 받을 사용자를 거르고 나누는 규칙을 검증한다.
 *
 * 원본: `NotificationMail.sendNotification`, `getUsersUsingNoAcceptableEmails`, `getPartialRecipientSize`
 * (`app/models/NotificationMail.java:350-460`).
 */
class MailRecipientsTest {

    private fun user(id: Long, email: String?, state: UserState = UserState.ACTIVE, lang: String? = null) =
        User(id = id, loginId = "u$id", name = "u$id", email = email, state = state, lang = lang)

    @Test
    fun `활성 사용자만 남긴다`() {
        val active = user(1, "a@x.com"); val locked = user(2, "b@x.com", UserState.LOCKED); val deleted = user(3, "c@x.com", UserState.DELETED)
        assertEquals(listOf(active), MailRecipients.eligible(listOf(active, locked, deleted), ""))
    }

    @Test
    fun `허용 도메인을 정했으면 그 도메인이 아닌 사용자는 뺀다`() {
        val inside = user(1, "a@yona.io"); val outside = user(2, "b@other.com"); val noEmail = user(3, null); val upper = user(4, "d@YONA.IO")
        assertEquals(listOf(inside, upper), MailRecipients.eligible(listOf(inside, outside, noEmail, upper), " yona.io , example.org "))
    }

    @Test
    fun `허용 도메인이 비어 있으면 모두 허용한다`() {
        assertEquals(true, MailRecipients.isAllowedDomain("a@anything.com", ""))
        assertEquals(false, MailRecipients.isAllowedDomain("a@anything.com", "yona.io"))
        assertEquals(false, MailRecipients.isAllowedDomain("not-an-email", "yona.io"))
        assertEquals(true, MailRecipients.isAllowedDomain("a@Yona.IO", "yona.io"))
    }

    @Test
    fun `메일 한 통의 받는 사람 수는 제한이 없으면 전체이고 주소를 숨기면 사이트 주소 몫을 뺀다`() {
        assertEquals(7, MailRecipients.partialSize(7, limit = 0, hideAddress = true))
        assertEquals(49, MailRecipients.partialSize(7, limit = 50, hideAddress = true))
        assertEquals(50, MailRecipients.partialSize(7, limit = 50, hideAddress = false))
        assertEquals(0, MailRecipients.partialSize(7, limit = 1, hideAddress = true))
    }

    @Test
    fun `선호 언어가 없으면 서버 기본 언어로 묶는다`() {
        val ko = user(1, "a@x.com", lang = "ko-KR"); val en1 = user(2, "b@x.com", lang = "en-US"); val en2 = user(3, "c@x.com", lang = "en-US"); val none = user(4, "d@x.com")
        val grouped = MailRecipients.byLanguage(listOf(ko, en1, en2, none))
        assertEquals(listOf(en1, en2), grouped["en-US"])
        assertEquals(listOf(ko), grouped["ko-KR"])
        assertEquals(listOf(none), grouped[java.util.Locale.getDefault().language])
    }
}
