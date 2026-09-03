package com.github.yonaprojects.yona.config

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.PrintStream

// yona-wiki P3-01(Observability) 대응 — logback-spring.xml이 실제로 유효한 JSON 한 줄짜리 로그를
// 만들어내는지 검증한다. <springProfile> 등 Spring 확장 태그를 쓰지 않았으므로(logback-spring.xml
// 자체 주석 참고) 전체 Spring 컨텍스트를 기동하지 않고 별도 LoggerContext에 순수 Logback
// JoranConfigurator로 직접 로드해 빠르게(통합 테스트 없이) 검증한다.
class LogbackJsonLoggingSpec : DescribeSpec({

    describe("logback-spring.xml 구조화 JSON 로깅") {
        it("로그 한 줄이 유효한 JSON으로 파싱되고 메시지/레벨/로거명을 담는다") {
            val captured = ByteArrayOutputStream()
            val originalOut = System.out
            // ConsoleAppender는 start() 시점(=doConfigure() 내부)에 System.out 참조를 캡처하므로,
            // 리다이렉트는 doConfigure()보다 먼저 해야 한다 — 나중에 하면 원래 콘솔로 새어나가
            // captured가 항상 비어있게 된다(실제로 이 순서로 처음 실패해 확인).
            System.setOut(PrintStream(captured))
            val context = LoggerContext()
            // 독립 LoggerContext는 전역 기본 컨텍스트와 달리 MDCAdapter가 자동으로 배선되지 않아
            // LoggingEvent.prepareForDeferredProcessing()에서 NPE가 난다(실제로 이렇게 처음 실패해
            // 확인) — 명시적으로 달아준다.
            context.mdcAdapter = ch.qos.logback.classic.util.LogbackMDCAdapter()
            try {
                val configurator = JoranConfigurator()
                configurator.context = context
                context.reset()

                val resource = requireNotNull(
                    LogbackJsonLoggingSpec::class.java.classLoader.getResource("logback-spring.xml")
                )
                configurator.doConfigure(resource)

                val logger = context.getLogger("com.github.yonaprojects.yona.ObservabilityTestLogger")
                logger.info("yona-p3-01-json-logging-check")
            } finally {
                System.setOut(originalOut)
                context.stop()
            }

            val line = captured.toString(Charsets.UTF_8).trim().lines().last()
            val json = ObjectMapper().readTree(line)

            json.path("message").asString() shouldBe "yona-p3-01-json-logging-check"
            json.path("logger_name").asString() shouldBe "com.github.yonaprojects.yona.ObservabilityTestLogger"
            json.path("level").asString() shouldBe "INFO"
            line shouldContain "\"@timestamp\""
        }
    }
})
