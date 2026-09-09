package com.github.yonaprojects.yona.domain.mail

import java.util.Date

// yona models/MailRecipient.java 대응.
data class MailRecipient(val email: String, val name: String)

interface MailService {
    fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String)
    fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String)

    // yona NotificationMail.getReplyTo() 대응. IMAP 답장 스레딩용 plus-address.
    // 기존 두 오버로드와 이름이 같으면 JVM 시그니처가 충돌해(둘 다 String 5개) 별도 메서드명을 쓴다.
    fun sendHtmlMailWithReplyTo(toEmail: String, toName: String, subject: String, htmlContent: String, replyTo: String?)

    // yona EmailHandler.reply() 대응. In-Reply-To/References를 원본 메일의 Message-ID로
    // 설정해 메일 클라이언트가 원본 스레드의 답장으로 인식하게 한다.
    fun sendReply(toEmail: String, toName: String, subject: String, textContent: String, inReplyToMessageId: String)

    // yona NotificationMail.sendMail()/EventEmail 대응. 다이제스트 알림 메일 발송 전용 —
    // 여러 수신자(To/Bcc), Message-ID/References(메일 스레딩), HTML+텍스트 dual body, 발신자
    // 표시 이름, 이벤트 발생 시각을 Date 헤더로 지정하는 기능을 모두 지원한다. toList가 비어있으면
    // 아무 것도 보내지 않는다(legacy sendMail()의 `if (toList.isEmpty()) return`과 동일).
    fun sendNotificationMail(
        toList: List<MailRecipient>,
        bccList: List<MailRecipient>,
        fromName: String,
        subject: String,
        htmlBody: String,
        plainBody: String,
        replyTo: String?,
        messageId: String?,
        references: String?,
        sentDate: Date
    )
}
