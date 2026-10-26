package dev.yona.web.access

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 요청 한 건마다 `access` 로거에 v1.6 형식의 한 줄이 남는지 검증한다. */
@AutoConfigureMockMvc
class AccessLogFilterTest : AbstractIntegrationTest() {

    @Autowired lateinit var mvc: MockMvc
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger("access") as Logger

    @BeforeEach fun attach() { appender.start(); logger.addAppender(appender); logger.level = Level.INFO }

    @AfterEach fun detach() { logger.detachAppender(appender) }

    @Test
    fun `요청 한 건에 한 줄이 남는다`() {
        mvc.perform(get("/nope-path?x=1"))
        val lines = appender.list.map { it.formattedMessage }
        assertEquals(1, lines.size, lines.toString())
        val pattern = Regex("""^\S+ - \S+ \[\d{2}/\w{3}/\d{4}:\d{2}:\d{2}:\d{2} [+-]\d{4}\] "GET /nope-path\?x=1 HTTP/1\.1" \d{3} - (- |".*" )(- |".*" )(-|\d+ms)$""")
        assertTrue(pattern.matches(lines[0]), lines[0])
    }
}
