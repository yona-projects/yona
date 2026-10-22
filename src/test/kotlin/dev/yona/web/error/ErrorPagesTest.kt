package dev.yona.web.error

import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

/**
 * 오류 화면을 검증한다.
 *
 * 원본: `app/utils/ErrorViews.java`, `app/views/error/{notfound,forbidden,badrequest,internalServerError}_default.scala.html`,
 * `app/Global.java:385-411`(`onHandlerNotFound`, `onError`, `onBadRequest`).
 */
@AutoConfigureMockMvc
class ErrorPagesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun render(path: String, expectedStatus: Int): Document {
        val result = mockMvc.perform(get(path)).andExpect(status().`is`(expectedStatus))
            .andExpect(content().contentTypeCompatibleWith("text/html")).andReturn()
        return Jsoup.parse(result.response.contentAsString)
    }

    // --- 찾을 수 없음 ---

    @Test
    fun `일치하는 주소가 없으면 404 화면을 보여 준다`() {
        val doc = render("/__missing__/deeper/path", 404)
        assertEquals("Page not found", doc.title())
        val box = doc.selectFirst(".page-wrap-outer .error-wrap")
        assertNotNull(box)
        assertNotNull(box.selectFirst("i.ico.ico-err2"))
        assertEquals("Page not found", box.selectFirst("p")?.text())
        val home = box.selectFirst("a.ybtn.ybtn-info")
        assertEquals("/", home?.attr("href"))
        assertEquals("Home", home?.text())
    }

    @Test
    fun `404 화면은 자체 상단 메뉴와 푸터를 가진다`() {
        val doc = render("/__missing__", 404)
        val header = doc.selectFirst("header.gnb-outer")
        assertNotNull(header)
        assertEquals("/", header.selectFirst("a.logo")?.attr("href"))
        assertEquals("Yona", header.selectFirst("a.logo h1.blind")?.text())
        assertEquals(
            listOf("/projects", "/_help", "https://github.com/nforge/yobi/issues?state=open"),
            header.select(".gnb-nav a").map { it.attr("href") }
        )
        assertEquals(listOf("Project list", "Help", "Feedback"), header.select(".gnb-nav a").map { it.text() })
        assertNotNull(doc.selectFirst("footer.page-footer-outer .provider"))
        assertNull(doc.selectFirst("form[name=gnb-search-form]"), "사이트 레이아웃의 검색 폼은 없다")
    }

    @Test
    fun `404 도우미는 메시지 키를 번역해 404로 응답한다`() {
        val doc = render("/__test/err/notfound", 404)
        assertEquals("Project does not exist", doc.selectFirst(".error-wrap p")?.text())
    }

    // --- 권한 없음 ---

    @Test
    fun `403 화면은 사이트 레이아웃 안에 안내와 홈 링크를 보여 준다`() {
        val doc = render("/__test/err/forbidden", 403)
        assertEquals("You are not authorized", doc.title())
        val box = doc.selectFirst(".error-wrap")
        assertNotNull(box?.selectFirst("i.ico.ico-err2"))
        assertEquals("You are not authorized", box?.selectFirst("p")?.text())
        assertEquals("/", box?.selectFirst("a.ybtn.ybtn-primary")?.attr("href"))
        assertNotNull(doc.selectFirst("header.gnb-outer form[name=gnb-search-form]"), "사이트 레이아웃")
        assertNotNull(doc.selectFirst("footer.page-footer-outer"))
    }

    @Test
    fun `403 화면은 다른 메시지 키도 번역한다`() {
        val doc = render("/__test/err/forbidden-custom", 403)
        assertEquals("You are not authorized to access this page or not logged in.", doc.selectFirst(".error-wrap p")?.text())
    }

    // --- 잘못된 요청 ---

    @Test
    fun `400 화면은 사이트 레이아웃 안에서 404 아이콘으로 안내한다`() {
        val doc = render("/__test/err/badrequest", 400)
        assertEquals("The request cannot be fulfilled due to bad syntax", doc.selectFirst(".error-wrap p")?.text())
        assertNotNull(doc.selectFirst(".error-wrap i.ico-404"))
        assertEquals("/", doc.selectFirst(".error-wrap a.ybtn.ybtn-info")?.attr("href"))
    }

    // --- 서버 오류 ---

    @Test
    fun `처리하지 못한 예외는 500 화면으로 보여 준다`() {
        val doc = render("/__test/err/boom", 500)
        assertEquals("Server error occurred; service is not available", doc.selectFirst(".error-wrap p")?.text())
        assertNotNull(doc.selectFirst(".error-wrap i.ico-404"))
        assertEquals("/", doc.selectFirst(".error-wrap a.ybtn.ybtn-primary")?.attr("href"))
    }

    @Test
    fun `쿼리 제한 시간 초과는 전용 문구로 알린다`() {
        val doc = render("/__test/err/timeout", 500)
        assertEquals("Your request could not be processed because of a server timeout", doc.selectFirst(".error-wrap p")?.text())
    }
}
