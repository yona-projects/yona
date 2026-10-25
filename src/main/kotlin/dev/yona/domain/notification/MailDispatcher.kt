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
 * 메일을 요청과 별개의 스레드에서 만들고 보낸다. v1.6에서 보조 이메일 확인은 Akka 액터가 만들고 보냈고, 가입 인증은
 * 본문을 요청 안에서 만든 뒤 발송만 비동기였다. 비밀번호 재설정과 이전 요청은 요청 안에서 동기였다(D-19). 메일 서버가 느리거나 본문을 만드는 데 시간이 걸려도 가입이나 비밀번호 재설정 요청이 지연되지 않는다.
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
                logSent(message)
            } catch (e: Exception) {
                log.warn("메일을 만들거나 보내지 못했다: {}", description, e)
            }
        }
    }

    /**
     * 이미 만든 [message]를 비동기로 보낸다. 본문은 요청 안에서 만들고 발송만 맡길 때 쓴다(v1.6 가입 인증 메일).
     */
    fun sendAsync(description: String, message: MimeMessage) = dispatch(description) { message }

    /**
     * 이미 만든 [message]를 호출한 스레드에서 바로 보낸다. v1.6 비밀번호 재설정과 프로젝트 이전 요청 메일처럼 요청 안에서 동기로 보내는 메일이다.
     * 메일 서버를 설정하지 않았거나 보내지 못하면 경고만 남기고 `false`를 돌려주며 요청은 그대로 진행한다.
     */
    fun sendNow(description: String, message: MimeMessage): Boolean {
        val sender = mailSenderProvider.ifAvailable
        if (sender == null) {
            log.warn("메일 서버(spring.mail.host)가 설정되지 않아 메일을 보내지 않았다: {}", description)
            return false
        }
        return try {
            sender.send(message)
            logSent(message)
            true
        } catch (e: Exception) {
            log.warn("메일을 보내지 못했다: {}", description, e)
            false
        }
    }

    private fun logSent(message: MimeMessage) {
        LoggerFactory.getLogger("mail").info("\"{}\" {}", (message.subject ?: "").replace("\"", "\\\""), message.allRecipients?.toList() ?: emptyList<Any>())
    }

    @PreDestroy
    fun shutdown() { executor.shutdown() }
}
