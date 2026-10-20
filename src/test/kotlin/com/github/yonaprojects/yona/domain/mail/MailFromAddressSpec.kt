package com.github.yonaprojects.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

// yona Config.getEmailFromSmtp() 대응: v1.6은 모든 발신 메일의 From을 SMTP 설정(smtp.user@smtp.domain)에서
// 가져왔다. next는 "no-reply@yona.io"로 하드코딩돼 있어 SMTP가 From을 제한하는 환경에서 발송이 거부된다.
class MailFromAddressSpec : DescribeSpec({
    describe("resolveMailFromAddress") {
        it("yona.mail.from이 있으면 그것을 쓴다") {
            resolveMailFromAddress("noreply@corp.example", "smtp-user@corp.example") shouldBe "noreply@corp.example"
        }
        it("yona.mail.from이 비어 있고 SMTP 사용자명이 이메일 형식이면 그것을 쓴다") {
            resolveMailFromAddress("", "smtp-user@corp.example") shouldBe "smtp-user@corp.example"
        }
        it("둘 다 쓸 수 없으면 기존 기본값 no-reply@yona.io를 쓴다") {
            resolveMailFromAddress("", "") shouldBe "no-reply@yona.io"
            resolveMailFromAddress("  ", "plainuser") shouldBe "no-reply@yona.io"
        }
    }

    describe("MailServiceImpl 발신 주소") {
        val mailSender = mockk<JavaMailSender>()
        val session = Session.getInstance(Properties())

        fun capture(block: (MailServiceImpl) -> Unit, configuredFrom: String, smtpUsername: String): MimeMessage {
            val captured = slot<MimeMessage>()
            every { mailSender.createMimeMessage() } answers { MimeMessage(session) }
            every { mailSender.send(any<MimeMessage>()) } answers { captured.captured = firstArg(); captured.captured.saveChanges() }
            block(MailServiceImpl(mailSender, configuredFrom, smtpUsername))
            return captured.captured
        }

        it("sendHtmlMailWithReplyTo는 설정된 발신 주소를 From으로 쓴다") {
            val message = capture({ it.sendHtmlMailWithReplyTo("to@x.io", "수신", "제목", "<p>x</p>", null) }, "noreply@corp.example", "")
            message.from.first().toString() shouldContain "noreply@corp.example"
        }

        it("sendReply는 SMTP 사용자명이 이메일이면 그것을 From으로 쓴다") {
            val message = capture({ it.sendReply("to@x.io", "수신", "제목", "본문", "") }, "", "smtp-user@corp.example")
            message.from.first().toString() shouldContain "smtp-user@corp.example"
        }

        it("sendNotificationMail도 설정된 발신 주소를 From으로 쓴다") {
            val message = capture({
                it.sendNotificationMail(
                    listOf(MailRecipient("to@x.io", "수신")), emptyList(), "발신자", "제목", "<p>x</p>", "x",
                    null, null, null, java.util.Date()
                )
            }, "noreply@corp.example", "")
            message.from.first().toString() shouldContain "noreply@corp.example"
        }
    }
})
