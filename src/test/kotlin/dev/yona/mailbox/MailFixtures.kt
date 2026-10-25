package dev.yona.mailbox

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.util.Properties

/** 메일 처리 테스트에서 쓰는 받은 메일 조립 도구이다. */
object MailFixtures {

    private val session = Session.getInstance(Properties())

    /** 지정한 메시지 ID를 가진 메일이다. */
    class Mail(val id: String) : MimeMessage(session) {
        override fun updateMessageID() = setHeader("Message-ID", id)
    }

    /**
     * 받은 메일을 만든다.
     *
     * @param from 보낸 사람 주소
     * @param to 받는 사람 주소들
     * @param content 본문(문자열이면 텍스트, [MimeMultipart]이면 그대로)
     * @param headers 추가할 헤더
     * @param fromName 보낸 사람의 표시 이름
     */
    fun mail(
        id: String, from: String, to: List<String>, subject: String, content: Any = "본문", cc: List<String> = emptyList(),
        headers: Map<String, String> = emptyMap(), subtype: String = "plain", fromName: String? = null
    ): Mail = Mail(id).apply {
        setFrom(InternetAddress(from, fromName, "utf-8"))
        to.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
        cc.forEach { addRecipient(Message.RecipientType.CC, InternetAddress(it)) }
        setSubject(subject, "utf-8")
        when (content) {
            is MimeMultipart -> setContent(content)
            else -> setText(content as String, "utf-8", subtype)
        }
        headers.forEach { (k, v) -> setHeader(k, v) }
        saveChanges()
    }

    /** 첨부 파트이다. */
    fun file(name: String, data: String, contentId: String? = null) = MimeBodyPart().apply {
        dataHandler = jakarta.activation.DataHandler(jakarta.mail.util.ByteArrayDataSource(data.toByteArray(), "application/octet-stream"))
        fileName = name
        contentId?.let { this.contentID = it }
    }

    /** 인라인 이미지 파트이다(파일 이름이 없다). */
    fun inline(contentId: String, data: String) = MimeBodyPart().apply {
        dataHandler = jakarta.activation.DataHandler(jakarta.mail.util.ByteArrayDataSource(data.toByteArray(), "image/png"))
        contentID = contentId
    }

    fun html(text: String) = MimeBodyPart().apply { setText(text, "utf-8", "html") }

    fun multipart(subtype: String, vararg parts: MimeBodyPart) = MimeMultipart(subtype).also { mp -> parts.forEach { mp.addBodyPart(it) } }
}
