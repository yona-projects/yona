package dev.yona.mailbox

import dev.yona.domain.site.DiagnosticCheck
import dev.yona.security.SiteSecret
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** 서버가 준비되면 메일 수신을 시작한다. 비밀 키를 정하기 전에는 시작하지 않는다 (v1.6 `Global.onStart`). */
@Component
class MailboxStarter(private val mailbox: MailboxService, private val secret: SiteSecret) {

    @EventListener
    fun onReady(event: ApplicationReadyEvent) {
        if (!secret.invalid) mailbox.start()
    }
}

/** 진단 화면의 "Email Receiver" 점검이다. */
@Component
class MailboxDiagnostic(private val mailbox: MailboxService) : DiagnosticCheck {
    override fun check(): List<String> = mailbox.diagnose()
}
