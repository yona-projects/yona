package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.mail.MailService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
class EmailVerificationEventListener(
    private val mailService: MailService
) {
    private val logger = LoggerFactory.getLogger(EmailVerificationEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    fun handleEmailVerificationEvent(event: EmailVerificationEvent) {
        logger.info("Handling EmailVerificationEvent asynchronously for ${event.email}")
        
        val subject = "[Yona] 이메일 주소 확인"
        val htmlContent = """
            <pre>아래 링크를 클릭하면 이메일 주소 확인이 완료됩니다.</pre>
            <hr>
            <a href="${event.confirmUrl}">${event.confirmUrl}</a>
        """.trimIndent()

        mailService.sendHtmlMail(
            toEmail = event.email,
            toName = event.userName,
            subject = subject,
            htmlContent = htmlContent
        )
    }
}
