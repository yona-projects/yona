package com.github.yonaprojects.yona.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

// yona utils/AccessLogger.java 대응 (P2-48). Apache Combined Log Format 로그 한 줄을 남기는지
// logback ListAppender로 실제 로그 이벤트를 캡처해 검증한다.
class AccessLogFilterSpec : DescribeSpec({
    val filter = AccessLogFilter()
    val accessLogger = LoggerFactory.getLogger("access") as Logger
    val appender = ListAppender<ILoggingEvent>()

    beforeSpec {
        appender.start()
        accessLogger.addAppender(appender)
    }

    afterSpec {
        accessLogger.detachAppender(appender)
        appender.stop()
    }

    beforeTest {
        appender.list.clear()
        SecurityContextHolder.clearContext()
    }

    describe("AccessLogFilter.doFilterInternal") {
        it("Combined Log Format으로 요청 정보를 한 줄 로깅해야 한다") {
            val request = MockHttpServletRequest("GET", "/owner/project")
            request.remoteAddr = "127.0.0.1"
            request.addHeader("Referer", "http://yona.example.com/")
            request.addHeader("User-Agent", "Mozilla/5.0")
            val response = MockHttpServletResponse()
            response.status = 200
            val filterChain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, filterChain)

            verify(exactly = 1) { filterChain.doFilter(request, response) }
            appender.list.size shouldBe 1
            val message = appender.list.first().formattedMessage
            message shouldContain "127.0.0.1"
            message shouldContain "\"GET /owner/project HTTP/1.1\""
            message shouldContain "200"
            message shouldContain "\"http://yona.example.com/\""
            message shouldContain "\"Mozilla/5.0\""
        }

        it("인증된 사용자가 없으면 사용자명 자리에 하이픈을 남겨야 한다") {
            val request = MockHttpServletRequest("GET", "/")
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, filterChain)

            val message = appender.list.first().formattedMessage
            message shouldContain "- - ["
        }

        it("인증된 사용자가 있으면 로그인 아이디를 남겨야 한다") {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken("loginuser", "password")
            val request = MockHttpServletRequest("GET", "/")
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, filterChain)

            val message = appender.list.first().formattedMessage
            message shouldContain "loginuser"
        }

        it("Referer/User-Agent 헤더가 없으면 하이픈으로 남겨야 한다") {
            val request = MockHttpServletRequest("GET", "/")
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, filterChain)

            val message = appender.list.first().formattedMessage
            message shouldNotContain "\"\""
        }

        it("다운스트림에서 예외가 발생해도 로그는 남기고 예외를 그대로 전파해야 한다") {
            val request = MockHttpServletRequest("GET", "/")
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>()
            io.mockk.every { filterChain.doFilter(request, response) } throws RuntimeException("boom")

            io.kotest.assertions.throwables.shouldThrow<RuntimeException> {
                filter.doFilter(request, response, filterChain)
            }

            appender.list.size shouldBe 1
        }
    }

    describe("AccessLogFilter.orHyphen / quotedOrHyphen") {
        it("orHyphen - null이나 빈 문자열이면 하이픈을 반환해야 한다") {
            AccessLogFilter.orHyphen(null) shouldBe "-"
            AccessLogFilter.orHyphen("") shouldBe "-"
            AccessLogFilter.orHyphen("value") shouldBe "value"
        }

        it("quotedOrHyphen - null이면 하이픈, 아니면 큰따옴표로 감싸야 한다") {
            AccessLogFilter.quotedOrHyphen(null) shouldBe "-"
            AccessLogFilter.quotedOrHyphen("hello") shouldBe "\"hello\""
            AccessLogFilter.quotedOrHyphen("has \"quote\"") shouldBe "\"has \\\"quote\\\"\""
        }
    }
})
