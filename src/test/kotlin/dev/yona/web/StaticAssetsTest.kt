package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6의 `public/` 폴더가 같은 URL(`/assets/` 아래 모든 경로, `/favicon.ico`)로 제공되는지 검증한다.
 *
 * v1.6 routes: `GET /assets/:file`은 `Assets.at("/public", file)`로, `GET /favicon.ico`는 `images/favicon.ico`로 연결된다.
 * 템플릿이 참조하는 CSS와 JS 경로가 그대로 유효해야 화면이 v1.6과 같게 보인다.
 */
@AutoConfigureMockMvc
class StaticAssetsTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `레이아웃이 참조하는 스타일시트가 assets 경로로 제공된다`() {
        listOf(
            "bootstrap/css/bootstrap.css", "stylesheets/yobi.css", "stylesheets/usermenu.css",
            "stylesheets/yobicon/style.css", "javascripts/lib/select2/select2.css"
        ).forEach { path ->
            mockMvc.perform(get("/assets/$path")).andExpect(status().isOk)
        }
    }

    @Test
    fun `자바스크립트도 assets 경로로 제공된다`() {
        mockMvc.perform(get("/assets/javascripts/yona-layout.js")).andExpect(status().isOk)
    }

    @Test
    fun `favicon이 루트 경로로 제공된다`() {
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk)
    }

    @Test
    fun `LESS에서 컴파일한 CSS에 v1_6의 선택자가 들어 있다`() {
        // 로그인 화면(login.scala.html)과 사용자 메뉴가 쓰는 선택자이다.
        mockMvc.perform(get("/assets/stylesheets/yobi.css"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(".login-form-wrap")))
        mockMvc.perform(get("/assets/stylesheets/usermenu.css")).andExpect(status().isOk)
        mockMvc.perform(get("/assets/stylesheets/filetype.css")).andExpect(status().isOk)
    }

    @Test
    fun `하위 폴더의 LESS도 CSS와 소스 맵으로 컴파일해 제공한다`() {
        // v1.6 sbt-less는 less/font 아래 파일도 따로 컴파일했다(filetype.less가 가져다 쓰는 파일이다)
        listOf("colours", "fonts", "icons").forEach { name ->
            mockMvc.perform(get("/assets/stylesheets/less/font/$name.css"))
                .andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.endsWith("/*# sourceMappingURL=$name.css.map */")))
            mockMvc.perform(get("/assets/stylesheets/less/font/$name.css.map"))
                .andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.startsWith("{\"version\":3,\"file\":\"$name.css\",\"sources\":[\"$name.less\"]")))
        }
    }

    @Test
    fun `public 폴더의 compiler jar도 v1_6처럼 내려 준다`() {
        val size = mockMvc.perform(get("/assets/compiler.jar")).andExpect(status().isOk).andReturn().response.contentAsByteArray.size
        assertEquals(6577863, size)
    }

    @Test
    fun `없는 자원은 404이다`() {
        mockMvc.perform(get("/assets/no/such/file.css")).andExpect(status().isNotFound)
    }

    @Test
    fun `CSS는 text css 형식으로 응답한다`() {
        mockMvc.perform(get("/assets/stylesheets/yobi.css"))
            .andExpect(content().contentTypeCompatibleWith("text/css"))
    }

    @Test
    fun `자원 응답은 v1_6 Assets처럼 공개 캐시와 ETag와 Last-Modified를 싣고 Accept-Ranges는 없다`() {
        val result = mockMvc.perform(get("/assets/images/default-avatar-128.png")).andExpect(status().isOk).andReturn().response
        assertEquals("public, max-age=3600", result.getHeader("Cache-Control"))
        assertEquals("image/png", result.contentType)
        assertTrue(Regex("\"[0-9a-f]{40}\"").matches(result.getHeader("ETag").orEmpty()), result.getHeader("ETag"))
        assertNotNull(result.getHeader("Last-Modified"))
        assertNull(result.getHeader("Accept-Ranges"))
        assertEquals(result.contentAsByteArray.size.toString(), result.getHeader("Content-Length"))
    }

    @Test
    fun `ETag가 같으면 Cache-Control과 ETag와 Last-Modified만 담은 304이다`() {
        val first = mockMvc.perform(get("/assets/images/default-avatar-128.png")).andReturn().response
        val etag = first.getHeader("ETag")!!
        val second = mockMvc.perform(get("/assets/images/default-avatar-128.png").header("If-None-Match", etag)).andReturn().response
        assertEquals(304, second.status)
        assertEquals("public, max-age=3600", second.getHeader("Cache-Control")); assertEquals(etag, second.getHeader("ETag")); assertNotNull(second.getHeader("Last-Modified"))
        assertEquals(0, second.contentAsByteArray.size)
        val future = mockMvc.perform(get("/assets/images/default-avatar-128.png").header("If-Modified-Since", "Fri, 01 Jan 2100 00:00:00 GMT")).andReturn().response
        assertEquals(304, future.status)
    }

    @Test
    fun `내용 형식은 v1_6 Assets의 확장자 표를 따른다`() {
        val expected = mapOf(
            "stylesheets/yobi.css" to "text/css; charset=utf-8", "stylesheets/yobi.css.map" to "application/x-navimap", "stylesheets/yobi.less" to "application/octet-stream",
            "javascripts/yona-layout.js" to "application/javascript; charset=utf-8", "images/favicon.ico" to "image/x-icon"
        )
        expected.forEach { (path, type) -> assertEquals(type, mockMvc.perform(get("/assets/$path")).andReturn().response.getHeader("Content-Type"), path) }
    }

    @Test
    fun `없는 자원과 폴더는 본문도 헤더도 없는 404이다`() {
        listOf("/assets/nope.js", "/assets/stylesheets/", "/assets/../conf/application.conf").forEach { path ->
            val response = mockMvc.perform(get(path)).andReturn().response
            assertEquals(404, response.status, path)
            assertEquals(0, response.contentAsByteArray.size, path)
            assertNull(response.getHeader("Content-Type"), path); assertNull(response.getHeader("Cache-Control"), path)
        }
    }

    @Test
    fun `assets 바로 뒤의 슬래시만 있는 주소는 끝 슬래시를 떼어 이동하고 favicon은 자원과 같은 헤더이다`() {
        mockMvc.perform(get("/assets/")).andExpect(status().isSeeOther).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/assets"))
        val icon = mockMvc.perform(get("/favicon.ico")).andReturn().response
        val asset = mockMvc.perform(get("/assets/images/favicon.ico")).andReturn().response
        assertEquals(asset.getHeader("Cache-Control"), icon.getHeader("Cache-Control")); assertEquals(asset.getHeader("ETag"), icon.getHeader("ETag"))
        assertEquals(asset.contentType, icon.contentType)
    }
}
