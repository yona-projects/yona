package dev.yona.mailbox

import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 받은 메일의 주소, 메시지 ID, 본문을 읽는 부분을 검증한다.
 * 원본: `mailbox.EmailAddressWithDetail`, `EmailHandler.parseMessageIds`, `IMAPMessageUtil.getIdLeftFromMessageId`,
 * `CreationViaEmail.extractContent`와 `Content`.
 */
class MailParsingTest {

    private val session = Session.getInstance(Properties())

    // --- 주소 ---

    @Test
    fun `주소의 세부 부분을 더하기 기호 뒤에서 읽는다`() {
        val address = EmailAddressWithDetail("yona+owner/project@mail.com")
        assertEquals("yona", address.user)
        assertEquals("owner/project", address.detail)
        assertEquals("mail.com", address.domain)
    }

    @Test
    fun `더하기 기호가 없으면 세부 부분은 빈 문자열이다`() {
        val address = EmailAddressWithDetail("yona@mail.com")
        assertEquals("yona", address.user)
        assertEquals("", address.detail)
    }

    @Test
    fun `이메일 형식이 아니면 예외이다`() {
        assertFailsWith<IllegalArgumentException> { EmailAddressWithDetail("not-an-address") }
    }

    @Test
    fun `세부 부분만 다르면 같은 주소로 본다`() {
        val a = EmailAddressWithDetail("yona+a/b@mail.com")
        assertTrue(a.equalsExceptDetails(EmailAddressWithDetail("yona@mail.com")))
        assertFalse(a.equalsExceptDetails(EmailAddressWithDetail("other@mail.com")))
        assertFalse(a.equalsExceptDetails(EmailAddressWithDetail("yona@other.com")))
    }

    @Test
    fun `문자열로 바꾸면 세부 부분 앞에 더하기 기호가 붙는다`() {
        assertEquals("yona+dlab/hive/issue@mail.com", EmailAddressWithDetail("yona@mail.com").also { it.detail = "dlab/hive/issue" }.toString())
        // v1.6은 세부 부분이 비어 있어도 더하기 기호를 붙인다.
        assertEquals("yona+@mail.com", EmailAddressWithDetail("yona@mail.com").toString())
    }

    // --- 메시지 ID ---

    @Test
    fun `헤더 값에서 꺾쇠로 감싼 메시지 ID를 모두 읽는다`() {
        assertEquals(listOf("<a@b>", "<c@d>"), MessageIds.parse("<a@b> <c@d>"))
        assertEquals(listOf("<a@b>", "<c@d>"), MessageIds.parse("  <a@b>\r\n\t(comment) <c@d> "))
        assertEquals(emptyList(), MessageIds.parse("no ids here"))
    }

    @Test
    fun `메시지 ID의 왼쪽 부분은 리소스 경로이다`() {
        assertEquals("issue_post/12", MessageIds.idLeft("<issue_post/12@yona.example>"))
        assertEquals("owner/p/issue_post/1", MessageIds.idLeft("</owner/p/issue_post/1@x>"))
        assertFailsWith<Exception> { MessageIds.idLeft("<no-at-sign>") }
    }

    // --- 본문 ---

    private fun message(configure: MimeMessage.() -> Unit) = MimeMessage(session).apply(configure).also { it.saveChanges() }

    private fun textPart(text: String, subtype: String = "plain") = MimeBodyPart().apply { setText(text, "utf-8", subtype) }

    private fun multipart(subtype: String, vararg parts: MimeBodyPart, parameters: String = "") = MimeMultipart(subtype).also { mp -> parts.forEach { mp.addBodyPart(it) } }

    @Test
    fun `단순 텍스트 메일은 본문과 형식을 읽는다`() {
        val content = MailContent.extract(message { setText("안녕하세요", "utf-8") })
        assertEquals("안녕하세요", content.body)
        assertTrue(content.type!!.startsWith("text/plain"))
        assertTrue(content.attachments.isEmpty())
    }

    @Test
    fun `HTML 메일은 형식을 HTML로 읽는다`() {
        val content = MailContent.extract(message { setText("<p>hi</p>", "utf-8", "html") })
        assertEquals("<p>hi</p>", content.body)
        assertTrue(content.isHtml)
    }

    @Test
    fun `대체 본문은 텍스트를 우선한다`() {
        val content = MailContent.extract(message { setContent(multipart("alternative", textPart("plain body"), textPart("<b>html body</b>", "html"))) })
        assertEquals("plain body", content.body)
        assertFalse(content.isHtml)
    }

    @Test
    fun `관련 파트 안의 대체 본문은 HTML을 우선하고 나머지는 첨부이다`() {
        val alternative = MimeBodyPart().apply { setContent(multipart("alternative", textPart("plain body"), textPart("<img src=\"cid:pic1\">", "html"))) }
        val image = MimeBodyPart().apply {
            setContent("PNGDATA", "image/png")
            contentID = "<pic1>"
        }
        val content = MailContent.extract(message { setContent(multipart("related", alternative, image)) })
        assertEquals("<img src=\"cid:pic1\">", content.body)
        assertTrue(content.isHtml)
        assertEquals(1, content.attachments.size)
        assertEquals("<pic1>", content.attachments.single().contentID)
    }

    @Test
    fun `파일 이름이 있는 파트는 첨부이고 본문 파트는 이어 붙인다`() {
        val file = MimeBodyPart().apply {
            setContent("data", "application/octet-stream")
            fileName = "report.txt"
        }
        val content = MailContent.extract(message { setContent(multipart("mixed", textPart("첫째 "), textPart("둘째"), file)) })
        assertEquals("첫째 둘째", content.body)
        assertEquals(listOf("report.txt"), content.attachments.map { it.fileName })
    }

    @Test
    fun `알 수 없는 형식은 빈 내용이다`() {
        val content = MailContent.extract(message { setContent("data", "application/octet-stream") })
        assertEquals("", content.body)
        assertNull(content.type)
        assertTrue(content.attachments.isEmpty())
    }

    @Test
    fun `보낸 사람 주소를 읽는다`() {
        val msg = message { setFrom(InternetAddress("a@b.com", "에이")) }
        assertEquals("a@b.com", (msg.from.single() as InternetAddress).address)
    }
}
