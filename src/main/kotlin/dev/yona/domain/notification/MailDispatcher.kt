package dev.yona.domain.notification

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import jakarta.annotation.PreDestroy

/**
 * 만든 메일을 요청과 별개의 스레드에서 보낸다. v1.6 `play2-mailplugin`의 `Mailer.send`가 메일 전용 액터에 맡겨 비동기로 보내던 것에
 * 해당한다. 메일 서버가 느려도 가입이나 비밀번호 재설정 요청이 지연되지 않게 한다.
 *
 * 한 스레드에서 차례로 보내므로 메일 순서가 지켜진다.
 */
@Component
class MailDispatcher(private val mailSenderProvider: ObjectProvider<JavaMailSender>) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "mail-dispatcher").apply { isDaemon = true } }

    /** [message]를 비동기로 보낸다. 메일 서버를 설정하지 않았으면 경고만 남긴다. 보내다 실패해도 경고만 남긴다. */
    fun dispatch(message: MimeMessage, description: String) {
        val sender = mailSenderProvider.ifAvailable
        if (sender == null) {
            log.warn("메일 서버(spring.mail.host)가 설정되지 않아 메일을 보내지 않았다: {}", description)
            return
        }
        executor.execute {
            try {
                sender.send(message)
            } catch (e: Exception) {
                log.warn("메일을 보내지 못했다: {}", description, e)
            }
        }
    }

    @PreDestroy
    fun shutdown() { executor.shutdown() }
}
