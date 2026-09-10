package com.github.yonaprojects.yona.domain.twofactor

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import org.springframework.boot.DefaultApplicationArguments

// 법적 컴플라이언스 감사 #2 대응 — 커밋된 placeholder 암호화 키가 그대로 운영에 올라가도
// 애플리케이션 기동을 막지 않으면서(env var 없이 h2로 기동하는 이 저장소의 표준 검증
// 워크플로/6,400여 건 테스트가 전부 이 기본값으로 컨텍스트를 띄움), 무시하기 어려운 ERROR
// 로그로 알린다.
class TotpEncryptionKeyGuardSpec : DescribeSpec({
    val logger = LoggerFactory.getLogger(TotpEncryptionKeyGuard::class.java) as Logger
    val appender = ListAppender<ILoggingEvent>()

    beforeSpec {
        appender.start()
        logger.addAppender(appender)
    }

    afterSpec {
        logger.detachAppender(appender)
        appender.stop()
    }

    beforeTest {
        appender.list.clear()
    }

    describe("TotpEncryptionKeyGuard.isUsingDefaultEncryptionKey") {
        it("커밋된 기본 password/salt와 완전히 같으면 true를 반환해야 한다") {
            TotpEncryptionKeyGuard.isUsingDefaultEncryptionKey(
                TotpEncryptionKeyGuard.DEFAULT_PASSWORD,
                TotpEncryptionKeyGuard.DEFAULT_SALT
            ) shouldBe true
        }

        it("password 또는 salt 중 하나라도 다르면 false를 반환해야 한다") {
            TotpEncryptionKeyGuard.isUsingDefaultEncryptionKey(
                "custom-password", TotpEncryptionKeyGuard.DEFAULT_SALT
            ) shouldBe false
            TotpEncryptionKeyGuard.isUsingDefaultEncryptionKey(
                TotpEncryptionKeyGuard.DEFAULT_PASSWORD, "deadbeef"
            ) shouldBe false
            TotpEncryptionKeyGuard.isUsingDefaultEncryptionKey("custom-password", "deadbeef") shouldBe false
        }
    }

    describe("TotpEncryptionKeyGuard.run") {
        it("기본값 그대로면 ERROR 레벨로 눈에 띄는 경고를 로깅해야 한다") {
            val guard = TotpEncryptionKeyGuard(
                TotpEncryptionKeyGuard.DEFAULT_PASSWORD,
                TotpEncryptionKeyGuard.DEFAULT_SALT
            )

            guard.run(DefaultApplicationArguments())

            appender.list.size shouldBe 1
            val event = appender.list.first()
            event.level shouldBe Level.ERROR
            event.formattedMessage shouldContain "PRODUCTION"
            event.formattedMessage shouldContain "YONA_TOTP_ENCRYPTION_PASSWORD"
            event.formattedMessage shouldContain "YONA_TOTP_ENCRYPTION_SALT"
        }

        it("환경변수로 덮어쓴 값이면 아무 로그도 남기지 않아야 한다") {
            val guard = TotpEncryptionKeyGuard("real-production-password", "deadbeef00")

            guard.run(DefaultApplicationArguments())

            appender.list.size shouldBe 0
        }
    }
})
