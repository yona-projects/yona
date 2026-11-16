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
class DavServletAdapterTest : AbstractIntegrationTest() {

    @Autowired private lateinit var properties: YonaProperties

    @Test
    fun `서버 이름은 주입한 값을 돌려준다`() {
        assertEquals("Play/2.3", DavServletAdapter.config("/x", "Play/2.3").servletContext.serverInfo)
        assertEquals("Foo/1", DavServletAdapter.config("/x", "Foo/1").servletContext.serverInfo)
    }

    @Test
    fun `서블릿 설정은 SVNParentPath만 초기화 값으로 알려 준다`() {
        val config = DavServletAdapter.config("/data/repos/owner/", "Play/2.3")
        assertEquals("/data/repos/owner/", config.getInitParameter("SVNParentPath"))
        assertEquals(null, config.getInitParameter("SVNPath"))
    }

    @Test
    fun `응답 헤더의 ASCII 바깥 글자는 UTF-8 퍼센트 인코딩으로 바꾼다`() {
        assertEquals("1//a.txt", DavServletAdapter.headerValue("1//a.txt"))
        assertEquals("1//%ED%95%9C%EA%B8%80 a.txt", DavServletAdapter.headerValue("1//한글 a.txt"))
        assertEquals(null, DavServletAdapter.headerValue(null))
    }

    @Test
    fun `설정하지 않으면 서버 이름은 Play 2_3이다`() {
        assertEquals("Play/2.3", properties.server)
    }
}
