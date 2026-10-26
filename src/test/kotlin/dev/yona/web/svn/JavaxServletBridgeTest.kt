package dev.yona.web.svn

import dev.yona.config.YonaProperties
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * SVNKit DAV 서블릿 컨텍스트가 알려 주는 서버 이름을 검증한다.
 *
 * 원본: `app/utils/PlayServletContext.java`의 `getServerInfo()`는 설정 `application.server`를 돌려준다(`conf/application.conf.default`의 값은 `Play/2.3`).
 * SVNKit이 만드는 HTML 오류 본문의 `<address>`에만 쓰인다.
 */
class JavaxServletBridgeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var properties: YonaProperties

    @Test
    fun `서버 이름은 주입한 값을 돌려준다`() {
        assertEquals("Play/2.3", JavaxServletBridge.config("/x", "Play/2.3").servletContext.serverInfo)
        assertEquals("Foo/1", JavaxServletBridge.config("/x", "Foo/1").servletContext.serverInfo)
    }

    @Test
    fun `설정하지 않으면 서버 이름은 Play 2_3이다`() {
        assertEquals("Play/2.3", properties.server)
    }
}
