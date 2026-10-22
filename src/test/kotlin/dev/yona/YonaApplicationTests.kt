package dev.yona

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import dev.yona.support.AbstractIntegrationTest
import org.springframework.core.env.Environment
import kotlin.test.assertEquals

/**
 * 애플리케이션 컨텍스트가 기본 설정(H2)으로 기동되는지 확인하는 스모크 테스트이다.
 */
class YonaApplicationTests : AbstractIntegrationTest() {

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun `컨텍스트가 H2 기본 설정으로 기동된다`() {
        assertEquals("yona", environment.getProperty("spring.application.name"))
    }
}
