package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.mail.ImapMailboxPoller
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import javax.sql.DataSource

// yona utils/Config.java:26-39 "application.port가 설정됐는데 application.hostname이 없으면
// application.port가 무시될 수 있다" 경고 대응 (P1-137 2번 항목). yona는 hostname/port 조합 대신
// yona.base-url 단일 설정으로 웹훅/알림메일의 모든 절대경로 URL을 만들므로, 그 값이 비어있는지로
// 이식했다(개념적으로 같은 "URL이 깨지는 설정 실수"를 검출).
class DiagnosticServiceSpec : DescribeSpec({
    fun validDataSource(): DataSource {
        val dataSource = mockk<DataSource>()
        val connection = mockk<Connection>(relaxed = true)
        every { dataSource.connection } returns connection
        every { connection.isValid(5) } returns true
        return dataSource
    }

    describe("checkAll - yona.base-url 설정 점검") {
        it("yona.base-url이 비어있으면 경고 메시지를 포함해야 한다") {
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "")

            val errors = service.checkAll()

            errors shouldContain
                "yona.base-url is not configured. Links in webhook payloads and notification emails " +
                "may be relative or broken."
        }

        it("yona.base-url이 설정돼 있으면 해당 경고 메시지를 포함하지 않아야 한다") {
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "https://yona.example.com")

            val errors = service.checkAll()

            errors.filter { it.contains("yona.base-url") } shouldBe emptyList()
        }

        it("baseUrl 인자를 생략하면 기본값 빈 문자열이 적용되어 경고 메시지를 포함해야 한다") {
            val service = DiagnosticService(dataSource = validDataSource())

            val errors = service.checkAll()

            errors shouldContain
                "yona.base-url is not configured. Links in webhook payloads and notification emails " +
                "may be relative or broken."
        }
    }

    describe("checkAll - IMAP 메일 수신기 헬스체크 (P1-137)") {
        it("ImapMailboxPoller가 비정상이면 해당 메시지를 그대로 포함해야 한다") {
            val poller = mockk<ImapMailboxPoller>()
            every { poller.healthCheckMessage() } returns "The Email Receiver is not running"

            val service = DiagnosticService(
                dataSource = validDataSource(), imapMailboxPoller = poller, baseUrl = "https://yona.example.com"
            )

            service.checkAll() shouldContain "The Email Receiver is not running"
        }

        it("ImapMailboxPoller 빈 자체가 없으면(비활성화) 관련 에러를 추가하지 않아야 한다") {
            val service = DiagnosticService(
                dataSource = validDataSource(), imapMailboxPoller = null, baseUrl = "https://yona.example.com"
            )

            service.checkAll() shouldNotContain "The Email Receiver is not initialized"
        }

        it("ImapMailboxPoller 빈은 있지만 healthCheckMessage가 null이면(정상) 관련 에러를 추가하지 않아야 한다") {
            val poller = mockk<ImapMailboxPoller>()
            every { poller.healthCheckMessage() } returns null

            val service = DiagnosticService(
                dataSource = validDataSource(), imapMailboxPoller = poller, baseUrl = "https://yona.example.com"
            )

            service.checkAll() shouldNotContain "The Email Receiver is not running"
        }
    }

    describe("checkAll - DB 및 스토리지 예외 및 에러 점검") {
        it("DB connection이 유효하지 않으면 에러를 포함해야 한다") {
            val ds = mockk<DataSource>()
            val conn = mockk<Connection>(relaxed = true)
            every { ds.connection } returns conn
            every { conn.isValid(any()) } returns false
            val service = DiagnosticService(dataSource = ds, baseUrl = "http://localhost")
            service.checkAll() shouldContain "Database Connection is invalid (isValid returned false)"
        }
        it("DB connection 중 예외가 발생하면 에러를 포함해야 한다") {
            val ds = mockk<DataSource>()
            every { ds.connection } throws RuntimeException("DB down")
            val service = DiagnosticService(dataSource = ds, baseUrl = "http://localhost")
            service.checkAll() shouldContain "Database Connection Check Failed: DB down"
        }
        it("Git 저장소 권한이 없으면 에러를 포함해야 한다") {
            // JVM/OS 환경에 따라 canWrite() 등을 mock 하기 어려우므로 예외를 발생시키도록
            System.setProperty("yona.data", "/dev/null/invalid_path")
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "http://localhost")
            val errors = service.checkAll()
            // Git 저장소 에러가 하나는 있어야 함 (canWrite false 이거나 mkdirs 실패 후 엑세스 거부)
            val hasGitError = errors.any { it.contains("Git Repository Storage Directory is not writable") || it.contains("Git Storage Check Failed") }
            hasGitError shouldBe true
        }
        it("SVN 저장소 예외/권한 에러 테스트") {
            System.setProperty("yona.data", "/dev/null/invalid_path")
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "http://localhost")
            val errors = service.checkAll()
            val hasSvnError = errors.any { it.contains("SVN Repository Storage Directory is not writable") || it.contains("SVN Storage Check Failed") }
            hasSvnError shouldBe true
        }
        it("JavaMailSender가 없으면 에러를 포함해야 한다") {
            val service = DiagnosticService(dataSource = validDataSource(), mailSender = null, baseUrl = "http://localhost")
            service.checkAll() shouldContain "JavaMailSender is not configured. Email notifications may not work."
        }
        it("JavaMailSender가 설정돼 있으면 관련 에러를 추가하지 않아야 한다") {
            val mailSender = mockk<org.springframework.mail.javamail.JavaMailSender>()
            val service = DiagnosticService(dataSource = validDataSource(), mailSender = mailSender, baseUrl = "http://localhost")
            service.checkAll() shouldNotContain "JavaMailSender is not configured. Email notifications may not work."
        }
    }
})
