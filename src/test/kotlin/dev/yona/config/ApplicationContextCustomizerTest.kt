package dev.yona.config

import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import kotlin.test.assertEquals

/**
 * `application.context`(v2 설정 `yona.application-context`) 하나로 사이트 전체를 하위 경로에 올리는지 검증한다.
 *
 * 원본: Play 2.3은 이 값을 라우터 접두어로 써서 모든 주소가 `/myroot` 아래에 놓인다(`conf/application.conf.default`의 `application.context = /myroot`).
 */
class ApplicationContextCustomizerTest {

    private fun contextPathFor(configured: String, explicit: String = ""): String {
        val factory = TomcatServletWebServerFactory().apply { if (explicit.isNotEmpty()) contextPath = explicit }
        ApplicationContextCustomizer(YonaProperties(applicationContext = configured)).customize(factory)
        return factory.contextPath
    }

    @Test
    fun `설정한 경로가 서블릿 컨텍스트 경로가 된다`() {
        assertEquals("/myroot", contextPathFor("/myroot"))
        assertEquals("/myroot", contextPathFor("myroot"))
        assertEquals("/myroot", contextPathFor("/myroot/"))
    }

    @Test
    fun `설정이 없으면 루트이다`() {
        assertEquals("", contextPathFor(""))
        assertEquals("", contextPathFor("/"))
    }

    @Test
    fun `서버의 컨텍스트 경로를 따로 정했으면 그것을 존중한다`() {
        assertEquals("/other", contextPathFor("/myroot", explicit = "/other"))
    }
}
