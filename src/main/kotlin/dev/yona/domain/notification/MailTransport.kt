package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * 메일을 실제로 내보내는 마지막 단계이다. 모의 발송(`yona.smtp.mock`, v1.6 `smtp.mock`)을 여기서 한 번에 처리해 모든 메일이 같은 규칙을 따른다.
 *
 * v1.6의 메일 플러그인(`play-2-mailplugin 0.9.1`의 `MailPlugin.send`)은 모의 발송이면 SMTP 접속 설정을 적용하지 않고 `email.send()`도 부르지 않은 채
 * "mock email send:" 다음에 내용을 `Logger.debug`로 남기고 빈 문자열(성공)을 돌려주었다. 호출한 쪽에는 예외가 없으므로 보낸 것으로 보인다.
 * v2도 같게 한다: 모의 발송이면 메일 서버가 없어도 [canSend]가 참이고, [send]는 아무것도 보내지 않고 참을 돌려준다.
 */
@Component
class MailTransport(
    private val senderProvider: ObjectProvider<JavaMailSender>,
    private val properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 모의 발송이 켜져 있으면 `true`이다. */
    val mock: Boolean get() = properties.smtp.mock

    /** 보낼 수 있으면 `true`이다. 모의 발송이거나 메일 서버(`spring.mail.host`)가 설정되어 있어야 한다. */
    val canSend: Boolean get() = mock || senderProvider.ifAvailable != null

    /**
     * [message]를 보낸다. 모의 발송이면 보내지 않고 내용을 debug 로그로만 남기며 `true`를 돌려준다. 메일 서버가 없으면 `false`이다.
     * 실제로 보내다 실패하면 예외를 그대로 던지므로 호출한 쪽이 처리한다.
     */
    fun send(message: MimeMessage): Boolean {
        if (mock) {
            if (log.isDebugEnabled) log.debug("mock email send:\n{}", describe(message))
            return true
        }
        val sender = senderProvider.ifAvailable ?: return false
        sender.send(message)
        return true
    }

    private fun describe(message: MimeMessage): String =
        "From: ${message.from?.joinToString().orEmpty()}\nTo: ${message.allRecipients?.joinToString().orEmpty()}\nSubject: ${message.subject.orEmpty()}"
}
