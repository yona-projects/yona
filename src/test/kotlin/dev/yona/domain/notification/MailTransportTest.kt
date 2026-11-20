package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.javamail.JavaMailSender
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 모의 발송(`yona.smtp.mock`, v1.6 `smtp.mock`)을 검증한다.
 *
 * v1.6의 메일 플러그인(`play-2-mailplugin 0.9.1`)은 `smtp.mock=true`이면 접속 설정을 적용하지 않고 `email.send()`를 부르지 않은 채
 * 내용을 debug 로그로만 남기고 빈 문자열(성공)을 돌려주었다. 키가 없으면 기본값이 `true`였다.
 */
class MailTransportTest {

    private val message = MimeMessage(Session.getInstance(java.util.Properties()))

    private fun provider(sender: JavaMailSender?): ObjectProvider<JavaMailSender> {
        @Suppress("UNCHECKED_CAST")
        val provider = mock(ObjectProvider::class.java) as ObjectProvider<JavaMailSender>
        org.mockito.Mockito.`when`(provider.ifAvailable).thenReturn(sender)
        return provider
    }

    private fun properties(mock: Boolean) = YonaProperties(smtp = YonaProperties.Smtp(mock = mock))

    @Test
    fun `모의 발송이면 메일 서버가 있어도 보내지 않고 보낸 것으로 처리한다`() {
        val sender = mock(JavaMailSender::class.java)
        val transport = MailTransport(provider(sender), properties(mock = true))

        assertTrue(transport.canSend)
        assertTrue(transport.send(message), "v1.6 모의 발송기는 예외 없이 성공을 돌려주었다")
        verify(sender, never()).send(message)
    }

    @Test
    fun `모의 발송이면 메일 서버를 설정하지 않아도 보낸 것으로 처리한다`() {
        val transport = MailTransport(provider(null), properties(mock = true))

        assertTrue(transport.canSend, "접속 설정을 적용하지 않으므로 서버가 없어도 모의 발송은 된다")
        assertTrue(transport.send(message))
    }

    @Test
    fun `모의 발송이 아니고 메일 서버가 있으면 실제로 보낸다`() {
        val sender = mock(JavaMailSender::class.java)
        val transport = MailTransport(provider(sender), properties(mock = false))

        assertTrue(transport.canSend)
        assertTrue(transport.send(message))
        verify(sender).send(message)
    }

    @Test
    fun `모의 발송이 아니고 메일 서버가 없으면 보내지 못한다`() {
        val transport = MailTransport(provider(null), properties(mock = false))

        assertFalse(transport.canSend)
        assertFalse(transport.send(message), "서버가 없으면 보내지 못했다고 알린다")
    }

    @Test
    fun `모의 발송의 기본값은 끔이다`() {
        assertEquals(false, YonaProperties().smtp.mock, "새 설치에서 메일 서버를 설정하면 바로 보낸다(v1.6 기본 설정 파일의 true와 다르다. 이관은 v1.6 값을 그대로 옮긴다)")
    }
}
