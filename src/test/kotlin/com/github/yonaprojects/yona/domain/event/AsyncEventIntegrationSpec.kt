package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.mail.MailService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Import(AsyncEventTestConfig::class)
class AsyncEventIntegrationSpec @Autowired constructor(
    private val eventPublisher: ApplicationEventPublisher,
    private val mailService: MailService
) : AbstractIntegrationTest() {

    init {
        beforeTest {
            clearMocks(mailService)
        }

        describe("비동기 이벤트 발행 및 리스너 연동") {
            it("EmailVerificationEvent를 발행하면 비동기로 메일 발송 서비스가 호출되어야 한다") {
                // Given
                val latch = CountDownLatch(1)
                val event = EmailVerificationEvent("test@yona.io", "홍길동", "http://confirm.url")
                
                var threadName = ""
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } answers {
                    threadName = Thread.currentThread().name
                    latch.countDown()
                }

                // When
                eventPublisher.publishEvent(event)
                val completed = latch.await(5, TimeUnit.SECONDS)

                // Then
                completed shouldBe true
                threadName shouldStartWith "yona-async-"
                verify(exactly = 1) { mailService.sendHtmlMail("test@yona.io", "홍길동", any(), any()) }
            }
        }
    }
}
