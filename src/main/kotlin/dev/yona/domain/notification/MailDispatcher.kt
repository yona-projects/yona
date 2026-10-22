package dev.yona.domain.notification

import jakarta.annotation.PreDestroy
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 메일을 요청과 별개의 스레드에서 만들고 보낸다. v1.6 `play2-mailplugin`의 `Mailer.send`가 메일 전용 액터에 맡겨 비동기로 보내던 것에
 * 해당한다(D-19). 메일 서버가 느리거나 본문을 만드는 데 시간이 걸려도 가입이나 비밀번호 재설정 요청이 지연되지 않는다.
 *
 * 한 스레드에서 차례로 처리하므로 메일 순서가 지켜진다. 요청 스레드의 언어와 주소 같은 정보는 호출하는 쪽이 미리 구해 넘긴다.
 */
@Component
class MailDispatcher(private val mailSenderProvider: ObjectProvider<JavaMailSender>) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "mail-dispatcher").apply { isDaemon = true } }

    /**
     * [compose]로 메일을 만들어 비동기로 보낸다. 만들기와 보내기 모두 전용 스레드에서 한다. [compose]가 `null`을 주면 보내지 않는다.
     * 메일 서버를 설정하지 않았으면 경고만 남기고, 만들거나 보내다 실패해도 경고만 남긴다.
     */
    fun dispatch(description: String, compose: () -> MimeMessage?) {
        val sender = mailSenderProvider.ifAvailable
        if (sender == null) {
            log.warn("메일 서버(spring.mail.host)가 설정되지 않아 메일을 보내지 않았다: {}", description)
            return
        }
        executor.execute {
            try {
                val message = compose() ?: return@execute
                sender.send(message)
                LoggerFactory.getLogger("mail").info("\"{}\" {}", (message.subject ?: "").replace("\"", "\\\""), message.allRecipients?.toList() ?: emptyList<Any>())
            } catch (e: Exception) {
                log.warn("메일을 만들거나 보내지 못했다: {}", description, e)
            }
        }
    }

    @PreDestroy
    fun shutdown() { executor.shutdown() }
}
