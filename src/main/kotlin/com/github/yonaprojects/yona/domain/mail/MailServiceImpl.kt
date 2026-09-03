package com.github.yonaprojects.yona.domain.mail

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.Properties

// yona NotificationMail.EventEmail 대응. jakarta.mail.internet.MimeMessage.updateHeaders()는
// saveChanges() 시점에 Message-ID 헤더 존재 여부와 무관하게 updateMessageID()를 항상 호출해
// 새 값으로 덮어쓴다 — saveChanges() 전에 헤더를 미리 심어두는 것만으로는 유지되지 않으므로,
// legacy와 동일하게 updateMessageID()를 오버라이드해야 한다.
private class EventNotificationMimeMessage(session: Session, private val customMessageId: String?) : MimeMessage(session) {
    override fun updateMessageID() {
        if (!customMessageId.isNullOrBlank()) {
            setHeader("Message-ID", customMessageId)
        } else {
            super.updateMessageID()
        }
    }
}

@Service
class MailServiceImpl(
    private val mailSender: JavaMailSender
) : MailService {

    private val logger = LoggerFactory.getLogger(MailServiceImpl::class.java)

    private fun mailSession(): Session =
        (mailSender as? JavaMailSenderImpl)?.session ?: Session.getInstance(Properties())

    override fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String) {
        sendHtmlMailWithReplyTo(toEmail, toName, subject, htmlContent, null)
    }

    override fun sendHtmlMailWithReplyTo(toEmail: String, toName: String, subject: String, htmlContent: String, replyTo: String?) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)
            helper.setFrom("no-reply@yona.io")
            if (!replyTo.isNullOrBlank()) {
                helper.setReplyTo(replyTo)
            }

            mailSender.send(message)
            logger.info("Email sent to $toEmail ($toName) with subject '$subject'" + (replyTo?.let { ", replyTo=$it" } ?: ""))
        } catch (e: Exception) {
            logger.error("Failed to send email to $toEmail", e)
            throw e
        }
    }

    override fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)
            helper.setFrom(fromEmail)

            mailSender.send(message)
            logger.info("Email sent from $fromEmail to $toEmail ($toName) with subject '$subject'")
        } catch (e: Exception) {
            logger.error("Failed to send email to $toEmail", e)
            throw e
        }
    }

    override fun sendReply(toEmail: String, toName: String, subject: String, textContent: String, inReplyToMessageId: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, false, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(if (subject.lowercase().startsWith("re:")) subject else "Re: $subject")
            helper.setText(textContent, false)
            helper.setFrom("no-reply@yona.io")
            if (inReplyToMessageId.isNotBlank()) {
                message.setHeader("In-Reply-To", inReplyToMessageId)
                message.setHeader("References", inReplyToMessageId)
            }

            mailSender.send(message)
            logger.info("Reply email sent to $toEmail ($toName), inReplyTo=$inReplyToMessageId")
        } catch (e: Exception) {
            logger.error("Failed to send reply email to $toEmail", e)
        }
    }

    override fun sendNotificationMail(
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
    ) {
        if (toList.isEmpty()) {
            return
        }

        try {
            val message = EventNotificationMimeMessage(mailSession(), messageId)
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setFrom("no-reply@yona.io", fromName)
            for (recipient in toList) {
                helper.addTo(recipient.email, recipient.name)
            }
            for (recipient in bccList) {
                helper.addBcc(recipient.email, recipient.name)
            }
            if (!replyTo.isNullOrBlank()) {
                helper.setReplyTo(replyTo)
            }
            helper.setSubject(subject)
            // dual body(text/plain + text/html) — MimeMessageHelper가 multipart/alternative 서브파트로 구성한다.
            helper.setText(plainBody, htmlBody)
            helper.setSentDate(sentDate)

            if (!references.isNullOrBlank()) {
                message.addHeader("References", references)
            }

            mailSender.send(message)
            logger.info("Notification mail sent: subject='$subject', to=${toList.map { it.email }}, bcc=${bccList.size}")
        } catch (e: Exception) {
            logger.warn("Failed to send a notification mail: subject='$subject'", e)
        }
    }
}
