package dev.yona.web.user

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 로그인 화면(`GET /users/loginform`)과 그 레이아웃을 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:132-152`, `app/views/user/login.scala.html`, `app/views/siteLayout.scala.html`,
 * `app/views/layout.scala.html`, `app/views/common/{navbar,footer,usermenu,scripts,loginDialog}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class LoginFormPageTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    private fun page(path: String = "/users/loginform", acceptLanguage: String? = null, referer: String? = null): Document {
        val request = get(path)
        acceptLanguage?.let { request.header("Accept-Language", it) }
        referer?.let { request.header("Referer", it) }
        val html = mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
        return Jsoup.parse(html)
    }

    // --- 문서 머리 ---

    @Test
    fun `제목과 메타 정보가 v1_6 레이아웃과 같다`() {
        val doc = page()
        assertEquals("Log in", doc.title())
        assertEquals("Log in", doc.selectFirst("meta[property=og:title]")?.attr("content"))
        assertEquals("/users/loginform", doc.selectFirst("meta[property=og:url]")?.attr("content"))
        assertEquals("summary", doc.selectFirst("meta[name=twitter:card]")?.attr("content"))
        assertTrue(doc.html().startsWith("<!doctype html>") || doc.childNode(0) is org.jsoup.nodes.DocumentType)
    }

    @Test
    fun `레이아웃이 참조하는 스타일시트와 스크립트가 순서대로 들어 있다`() {
        val doc = page()
        val styles = doc.select("head link[rel=stylesheet]").map { it.attr("href") }
        assertEquals(
            listOf(
                "/assets/stylesheets/bootstrap.css", "/assets/stylesheets/yobicon/style.css",
                "/assets/javascripts/lib/select2/select2.css", "/assets/javascripts/lib/pikaday/pikaday.css",
                "/assets/stylesheets/usermenu.css", "/assets/stylesheets/yobi.css",
                "/assets/javascripts/lib/nprogress/nprogress.css", "/assets/javascripts/lib/viewerjs/viewer.css"
            ),
            styles
        )
        assertEquals("/favicon.ico", doc.selectFirst("link[rel=shortcut icon]")?.attr("href"))
        assertNotNull(doc.selectFirst("script[src=/assets/javascripts/yona-layout.js]"))
    }

    // --- 본문 ---

    @Test
    fun `로그인 폼의 입력 필드와 전송 대상이 v1_6과 같다`() {
        val form = page("/users/loginform?redirectUrl=/some/where").selectFirst(".login-form-wrap form[action=/users/login]")
        assertNotNull(form)
        assertEquals("POST", form.attr("method").uppercase())
        assertEquals("/some/where", form.selectFirst("input[name=redirectUrl]")?.attr("value"))
        assertEquals("Login ID or E-mail", form.selectFirst("input[name=loginIdOrEmail]")?.attr("placeholder"))
        assertEquals("Password", form.selectFirst("input[name=password]")?.attr("placeholder"))
        assertEquals("password", form.selectFirst("input[name=password]")?.attr("type"))
        assertEquals("Log in", form.selectFirst("button[type=submit]")?.text())
        assertTrue(form.selectFirst("input[name=rememberMe]")!!.hasAttr("checked"))
        assertEquals("/lostPassword", form.selectFirst(".links-wrap a")?.attr("href"))
    }

    @Test
    fun `제목 영역에 사이트 이름과 설명이 들어 있다`() {
        val doc = page()
        assertEquals("Log in to Yona", doc.selectFirst(".tag-line-wrap h1.title")?.text())
        assertEquals("Web-based platform for collaborative software development", doc.selectFirst("p.tag-line")?.text())
        assertNotNull(doc.selectFirst(".tag-line-wrap h1.title .highlight"), "사이트 이름 강조 요소")
    }

    @Test
    fun `redirectUrl이 없으면 Referer를 이동 대상으로 쓴다`() {
        val doc = page(referer = "http://localhost/yona/projects")
        assertEquals("http://localhost/yona/projects", doc.selectFirst("input[name=redirectUrl]")?.attr("value"))
    }

    @Test
    fun `Referer가 로그인 폼 자신이면 이동 대상을 비운다`() {
        val doc = page(referer = "/users/loginform")
        assertEquals("", doc.selectFirst("input[name=redirectUrl]")?.attr("value"))
    }

    @Test
    fun `브라우저가 보내는 절대 주소 Referer도 로그인 폼 자신이면 이동 대상을 비운다 (C142)`() {
        // v1.6은 절대 주소 Referer를 경로 문자열과 비교해 항상 달랐고, 로그인 화면 자신이 이동 대상이 되었다
        val doc = page(referer = "http://localhost/users/loginform")
        assertEquals("", doc.selectFirst("input[name=redirectUrl]")?.attr("value"))
    }

    // --- 공통 영역 ---

    @Test
    fun `익명 사용자의 상단 메뉴에 로그인과 가입 링크가 있다`() {
        val doc = page()
        assertEquals("/users/loginform", doc.selectFirst("ul.gnb-usermenu #required-logged-in a")?.attr("href"))
        assertEquals("Log in", doc.selectFirst("ul.gnb-usermenu #required-logged-in a")?.text())
        val signup = doc.selectFirst("ul.gnb-usermenu a.ybtn-success")
        assertEquals("/users/signupform", signup?.attr("href"))
        assertEquals("Sign up", signup?.text())
    }

    @Test
    fun `상단 내비게이션에 로고와 목록 링크와 검색 폼이 있다`() {
        val doc = page()
        assertEquals("/", doc.selectFirst("header.gnb-outer a.logo")?.attr("href"))
        assertEquals("/projects", doc.selectFirst("header.gnb-outer .gnb-nav a.show-progress-bar")?.attr("href"))
        val search = doc.selectFirst("form[name=gnb-search-form]")
        assertEquals("/search", search?.attr("action"))
        assertEquals("auto", search?.selectFirst("input[name=searchType]")?.attr("value"))
        assertNotNull(search?.selectFirst("input[name=keyword]"))
    }

    @Test
    fun `익명 사용자에게는 로그인 대화상자가 포함된다`() {
        val dialog = page().selectFirst("#loginDialog")
        assertNotNull(dialog)
        assertEquals("/users/login", dialog.selectFirst("form")?.attr("action"))
        assertNotNull(dialog.selectFirst("#loginIdOrEmailD"))
        assertNotNull(dialog.selectFirst("#passwordD"))
    }

    @Test
    fun `푸터가 있다`() {
        val footer = page().selectFirst("footer.page-footer-outer")
        assertNotNull(footer)
        assertTrue(footer.text().contains("Yona authors"))
        assertTrue(footer.text().contains("NAVER Corp."))
    }

    @Test
    fun `공통 스크립트가 순서대로 로드된다`() {
        val sources = page().select("script[src]").map { it.attr("src") }
        val expected = listOf(
            "/assets/javascripts/yona-layout.js", "/messages.js", "/assets/javascripts/yona-common.js",
            "/assets/bootstrap/js/bootstrap-better-typeahead.js", "/assets/bootstrap/js/bootstrap-switch.js",
            "/assets/javascripts/yona-lib.js", "/assets/javascripts/common/yona.Usermenu.js"
        )
        expected.forEach { assertTrue(sources.contains(it), "$it 누락: $sources") }
        assertTrue(sources.indexOf("/messages.js") < sources.indexOf("/assets/javascripts/yona-common.js"))
    }

    /** 인라인 스크립트의 `var 이름 = "값";` 선언에서 값을 읽는다. JS 이스케이프(`\/`)는 풀어 준다. */
    private fun scriptVariable(html: String, name: String): String? =
        Regex("""var $name = "([^"]*)";""").find(html)?.groupValues?.get(1)?.replace("\\/", "/")

    @Test
    fun `사용자 메뉴 스크립트에 필요한 URL 변수가 선언된다`() {
        val html = page().html()
        assertEquals("/user/favoriteProjects/", scriptVariable(html, "UsermenuToggleFavoriteProjectUrl"))
        assertEquals("/user/favoriteOrganizations/", scriptVariable(html, "UsermenuToggleFoveriteOrganizationUrl"))
        assertEquals("/user/favoriteProjects", scriptVariable(html, "UsermenuGetFoveriteProjectsUrl"))
        assertEquals("/user/favoriteIssues/", scriptVariable(html, "UsermenuToggleFavoriteIssueUrl"))
        assertEquals("/user/favoriteIssues", scriptVariable(html, "UsermenuGetFoveriteIssuesUrl"))
        assertEquals("/user/usermenuTabContentList", scriptVariable(html, "UsermenuUrl"))
    }

    // --- 동작 ---

    @Test
    fun `flash 경고 메시지는 번역되어 알림 스크립트로 출력된다`() {
        val html = mockMvc.perform(get("/users/loginform").flashAttr("warning", "user.login.invalid"))
            .andReturn().response.contentAsString
        assertTrue(html.contains("\$yobi.notify(\"") && html.contains("5000"), "알림 호출 누락")
        assertTrue(Regex("""\${'$'}yobi\.notify\("[^"]+", 5000\)""").containsMatchIn(html), html.lines().filter { "notify" in it }.toString())
    }

    @Test
    fun `이미 로그인한 사용자는 홈으로 이동한다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(
            User(
                loginId = "already", email = "already@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "already").param("password", "1234"))
        mockMvc.perform(get("/users/loginform").session(session))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
    }

    @Test
    fun `로그인 폼은 캐시되지 않는다`() {
        mockMvc.perform(get("/users/loginform"))
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(header().string("Expires", "0"))
    }

    @Test
    fun `한국어 요청은 한국어 화면을 돌려준다`() {
        val doc = page(acceptLanguage = "ko-KR")
        assertEquals("로그인", doc.title())
        assertEquals("로그인", doc.selectFirst(".login-form-wrap button[type=submit]")?.text())
    }

    @Test
    fun `입력이 빠진 로그인 요청은 400과 함께 로그인 화면을 다시 보여준다`() {
        val result = mockMvc.perform(post("/users/login").param("loginIdOrEmail", "x"))
            .andExpect(status().isBadRequest).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        assertEquals("Log in", doc.title())
        assertTrue(result.response.contentAsString.contains("\$yobi.notify("), "경고 알림")
    }

    @Test
    fun `입력이 빠진 로그인 요청은 이동 주소를 비운 화면을 다시 그린다 (v1_6은 redirectUrl에 null을 넘긴다)`() {
        val result = mockMvc.perform(post("/users/login").param("loginIdOrEmail", "x").param("redirectUrl", "/a/b")).andExpect(status().isBadRequest).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        assertEquals("", doc.selectFirst("input[name=redirectUrl]")?.attr("value").orEmpty())
    }

    @Test
    fun `이메일 인증을 쓰지 않는 사이트의 로그인 화면에는 확인 메일 안내가 없다`() {
        val doc = org.jsoup.Jsoup.parse(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/loginform")).andReturn().response.contentAsString)
        kotlin.test.assertTrue(doc.select(".email-verification-help").isEmpty())
    }
}
