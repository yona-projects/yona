package dev.yona.web.social

import dev.yona.support.selectFirst
import com.sun.net.httpserver.HttpServer
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 소셜 로그인 화면 흐름을 스텁 제공자 서버와 함께 검증한다.
 * 원본: `Application.oAuth`, `oAuthLogout`, `oAuthDenied`(`app/controllers/Application.java:65-80`), `Global`의 play-authenticate 해석기
 * (`app/Global.java:184-235`), `YonaUserServicePlugin`, `UserApp.loginForm`.
 */
@AutoConfigureMockMvc
@Transactional
class SocialLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var twoFactorService: dev.yona.domain.twofactor.TwoFactorService

    /** 2단계 인증을 켜고 비밀 값을 돌려준다(X-2). */
    private fun enableTwoFactor(user: dev.yona.domain.user.User): ByteArray {
        val secret = dev.yona.domain.twofactor.Base32.decode(twoFactorService.beginTotp(user, "Yona", user.loginId!!)!!.secret)
        val past = java.time.Instant.now().minusSeconds(300)
        twoFactorService.confirmTotp(user, dev.yona.domain.twofactor.Totp.code(secret, past.epochSecond / 30), past)
        return secret
    }

    private fun nowCode(secret: ByteArray) = dev.yona.domain.twofactor.Totp.code(secret, java.time.Instant.now().epochSecond / 30)

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    companion object {
        @Volatile var githubUser = """{"id":1,"login":"octo","name":"옥토","email":"octo@example.com"}"""
        @Volatile var githubTokenBody = """{"access_token":"tok","token_type":"bearer"}"""
        @Volatile var googleUser = """{"id":"g1","email":"gu@example.com","verified_email":true,"name":"구글"}"""
        @Volatile var googleTokenBody = """{"access_token":"gtok","expires_in":3600}"""

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { s ->
            fun route(path: String, body: () -> String) = s.createContext(path) { ex ->
                ex.requestBody.readBytes()
                val bytes = body().toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            route("/github/token") { githubTokenBody }
            route("/github/user") { githubUser }
            route("/google/token") { googleTokenBody }
            route("/google/user") { googleUser }
            s.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun social(registry: DynamicPropertyRegistry) {
            val base = "http://127.0.0.1:${server.address.port}"
            registry.add("yona.social.login-support") { "github, google" }
            registry.add("yona.social.github.client-id") { "gh-id" }
            registry.add("yona.social.github.client-secret") { "gh-secret" }
            registry.add("yona.social.github.authorization-url") { "https://github.test/authorize" }
            registry.add("yona.social.github.access-token-url") { "$base/github/token" }
            registry.add("yona.social.github.user-info-url") { "$base/github/user" }
            registry.add("yona.social.google.client-id") { "g-id" }
            registry.add("yona.social.google.client-secret") { "g-secret" }
            registry.add("yona.social.google.authorization-url") { "https://google.test/authorize" }
            registry.add("yona.social.google.access-token-url") { "$base/google/token" }
            registry.add("yona.social.google.user-info-url") { "$base/google/user" }
        }
    }

    private fun begin(provider: String, session: MockHttpSession): String {
        val result = mockMvc.perform(get("/authenticate/$provider").session(session)).andExpect(status().isSeeOther).andReturn()
        val location = URI(result.response.getHeader("Location")!!)
        return location.rawQuery.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8) }.getValue("state")
    }

    private fun callback(provider: String, session: MockHttpSession, state: String? = null, code: String = "code-1") =
        mockMvc.perform(get("/authenticate/$provider").session(session).param("code", code).apply { state?.let { param("state", it) } })

    private fun signIn(provider: String = "github", session: MockHttpSession = MockHttpSession()) = callback(provider, session, begin(provider, session))

    private fun isLoggedIn(session: MockHttpSession) = mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status == 200

    // --- 인증 화면으로 보내기 ---

    @Test
    fun `코드 없이 부르면 제공자의 인증 화면으로 보낸다`() {
        val session = MockHttpSession()
        val result = mockMvc.perform(get("/authenticate/github").session(session)).andExpect(status().isSeeOther).andReturn()
        val location = result.response.getHeader("Location")!!
        assertTrue(location.startsWith("https://github.test/authorize?client_id=gh-id&redirect_uri=http%3A%2F%2Flocalhost%2Fauthenticate%2Fgithub&scope=user%3Aemail&response_type=code&state="), location)
    }

    @Test
    fun `state는 요청마다 다르다`() {
        assertTrue(begin("github", MockHttpSession()) != begin("github", MockHttpSession()))
    }

    @Test
    fun `알 수 없거나 허용되지 않은 제공자는 문구만 담은 404이다`() {
        // v1.6은 Play의 `notFound("Provider … could not be found!")`라 오류 화면이 아니라 text/plain 본문이고 캐시 금지 머리글이 붙는다
        mockMvc.perform(get("/authenticate/facebook")).andExpect(status().isNotFound)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("Provider facebook could not be found!"))
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/plain")))
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
        mockMvc.perform(get("/authenticate/GitHub")).andExpect(status().isNotFound)
    }

    // --- 콜백 ---

    @Test
    fun `콜백에서 사용자를 만들고 로그인시키고 로그인 유지 쿠키를 남긴다`() {
        githubUser = """{"id":${100 + ++sequence},"login":"octo$sequence","name":"옥토$sequence","email":"octo$sequence@example.com"}"""
        val session = MockHttpSession()
        val result = signIn("github", session).andExpect(status().isSeeOther).andExpect(header().string("Location", "/")).andReturn()
        val user = assertNotNull(users.findByLoginId("octo$sequence"))
        assertEquals("옥토$sequence", user.name)
        assertEquals(UserState.ACTIVE, user.state)
        val cookie = assertNotNull(result.response.getCookie("yobi.token"))
        assertEquals("octo$sequence:${user.password}", cookie.value)
        assertTrue(isLoggedIn(session))
    }

    @Test
    fun `이미 연결된 신원은 같은 사용자로 로그인한다`() {
        githubUser = """{"id":${200 + ++sequence},"login":"again$sequence","name":"다시","email":"again$sequence@example.com"}"""
        signIn()
        val session = MockHttpSession()
        signIn("github", session).andExpect(header().string("Location", "/"))
        assertTrue(isLoggedIn(session))
        assertEquals(1, users.findAll().count { it.email == "again$sequence@example.com" })
    }

    @Test
    fun `2단계 인증을 켠 사용자는 소셜 로그인 뒤에 코드를 확인해야 하고 그 뒤에 로그인 유지 쿠키가 생긴다 (X-2)`() {
        githubUser = """{"id":${300 + ++sequence},"login":"tfa$sequence","name":"이단계","email":"tfa$sequence@example.com"}"""
        signIn()
        val user = assertNotNull(users.findByLoginId("tfa$sequence"))
        val secret = enableTwoFactor(user)
        val session = MockHttpSession()
        val first = signIn("github", session).andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/login/2fa")).andReturn()
        assertEquals(null, first.response.getCookie("yobi.token"), "코드를 확인하기 전에는 쿠키가 없다")
        assertTrue(!isLoggedIn(session))
        val done = mockMvc.perform(post("/users/login/2fa").session(session).param("code", nowCode(secret))).andExpect(status().isSeeOther).andReturn()
        assertNotNull(done.response.getCookie("yobi.token"))
        assertTrue(isLoggedIn(session))
    }

    @Test
    fun `state가 없거나 다르면 오류 화면 500이다`() {
        // 공식 v1.16.0: play-authenticate가 state 불일치를 예외로 알려 Global.onError의 500 화면이 나온다
        val session = MockHttpSession()
        begin("github", session)
        val html = callback("github", session, state = "forged").andExpect(status().isInternalServerError).andReturn().response.contentAsString
        assertTrue(html.contains("Server error occurred"), html.take(200))
        assertFalse(isLoggedIn(session))
    }

    @Test
    fun `보낸 state가 사라졌으면 제공자로 다시 보낸다`() {
        val location = callback("github", MockHttpSession(), state = "anything").andExpect(status().isSeeOther).andReturn().response.getHeader("Location")!!
        assertTrue(location.startsWith("https://github.test/authorize?"))
    }

    @Test
    fun `제공자가 돌려준 거절 외의 오류는 오류 문구와 함께 로그인 화면으로 보낸다 (R-59-12)`() {
        // v1.6은 문구만 담은 500이었다
        val result = mockMvc.perform(get("/authenticate/github").param("error", "redirect_uri_mismatch")).andExpect(status().isSeeOther).andReturn()
        assertEquals("/users/loginform", result.response.getHeader("Location"))
        assertEquals("redirect_uri_mismatch", result.flashMap["warning"])
    }

    @Test
    fun `이름이 없는 GitHub 사용자의 이름은 문자열 null이 아니다 (R-59-11)`() {
        githubUser = """{"id":${1300 + ++sequence},"login":"nonamer$sequence","name":null,"email":"nonamer$sequence@example.com"}"""
        signIn()
        val created = users.findByLoginId("nonamer$sequence")
        assertNotNull(created)
        assertTrue(created.name != "null", "이름이 문자열 null이다: [${created.name}]")
    }

    @Test
    fun `state는 한 번만 쓸 수 있다`() {
        val session = MockHttpSession()
        val state = begin("github", session)
        githubUser = """{"id":${300 + ++sequence},"login":"once$sequence","name":"한번","email":"once$sequence@example.com"}"""
        callback("github", session, state).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        // 한 번 쓴 state는 사라져 같은 값으로는 로그인되지 않고 제공자로 다시 보낸다.
        val again = callback("github", session, state).andExpect(status().isSeeOther).andReturn().response.getHeader("Location")!!
        assertTrue(again.startsWith("https://github.test/authorize?"))
    }

    @Test
    fun `제공자와의 통신이 실패하면 오류 문구와 함께 로그인 화면으로 보낸다 (R-59-12)`() {
        githubTokenBody = """{"error":"bad_verification_code"}"""
        try {
            val result = signIn().andExpect(status().isSeeOther).andReturn()
            assertEquals("/users/loginform", result.response.getHeader("Location"))
            assertEquals("bad_verification_code", result.flashMap["warning"])
        } finally {
            githubTokenBody = """{"access_token":"tok","token_type":"bearer"}"""
        }
    }

    @Test
    fun `사용자 정보를 읽지 못하면 예외라 오류 화면 500이고 error 항목이 있으면 로그인 화면으로 보낸다`() {
        // error 항목이 있는 JSON만 문구 본문이고, 깨진 본문이나 id가 없는 응답은 v1.6에서 예외(Global.onError의 500 화면)이다
        val ok = githubUser
        try {
            for (body in listOf("garbage{", """{"message":"Bad credentials"}""")) {
                githubUser = body
                val html = signIn().andExpect(status().isInternalServerError).andReturn().response.contentAsString
                assertTrue(html.contains("Server error occurred"), "$body -> ${html.take(120)}")
            }
            githubUser = """{"error":"bad_creds"}"""
            val result = signIn().andExpect(status().isSeeOther).andReturn()
            assertEquals("bad_creds", result.flashMap["warning"])
        } finally {
            githubUser = ok
        }
    }

    @Test
    fun `이메일에 골뱅이가 없으면 로그인 ID를 만들다 예외가 나 500이다`() {
        // v1.6 createLocalUserWithOAuth는 `email.substring(0, email.indexOf("@"))`를 부른다
        val ok = githubUser
        try {
            githubUser = """{"id":${5000 + ++sequence},"login":"noat$sequence","name":"골뱅이 없음","email":"not-an-email"}"""
            signIn().andExpect(status().isInternalServerError)
        } finally {
            githubUser = ok
        }
    }

    @Test
    fun `사용자가 거절하면 거절 안내 화면 주소로 보낸다`() {
        mockMvc.perform(get("/authenticate/github").param("error", "access_denied")).andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/authenticate/github/denied"))
    }

    @Test
    fun `거절 안내는 홈으로 돌려보내며 안내를 남긴다`() {
        val result = mockMvc.perform(get("/authenticate/github/denied")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/")).andReturn()
        assertEquals("You need to accept the OAuth connection in order to use this website!", result.flashMap["error"])
        assertEquals("no-cache, no-store, must-revalidate", result.response.getHeader("Cache-Control"))
    }

    @Test
    fun `이메일을 알 수 없으면 로그아웃으로 보내고 안내를 남긴다`() {
        githubUser = """{"id":${400 + ++sequence},"login":"noemail$sequence","name":"무메일","email":null}"""
        val result = signIn().andExpect(status().isSeeOther).andExpect(header().string("Location", "/logout")).andReturn()
        assertEquals("app.warn.cannot.access.email.information", result.flashMap["error"])
        assertNull(users.findByLoginId("noemail$sequence"))
    }

    @Test
    fun `잠긴 사용자로 연결된 신원은 안내와 함께 로그아웃으로 보낸다`() {
        githubUser = """{"id":${500 + ++sequence},"login":"locky$sequence","name":"잠김","email":"locky$sequence@example.com"}"""
        signIn()
        val user = users.findByLoginId("locky$sequence")!!
        user.state = UserState.LOCKED
        users.save(user)
        val session = MockHttpSession()
        val result = signIn("github", session).andExpect(header().string("Location", "/logout")).andReturn()
        assertEquals("user.locked", result.flashMap["warning"])
        assertFalse(isLoggedIn(session))
    }

    @Test
    fun `인증 전에 보던 주소가 있으면 그곳으로 돌려보낸다`() {
        githubUser = """{"id":${600 + ++sequence},"login":"back$sequence","name":"복귀","email":"back$sequence@example.com"}"""
        val session = MockHttpSession().also { it.setAttribute("pa.url.orig", "/some/where?x=1") }
        signIn("github", session).andExpect(header().string("Location", "/some/where?x=1"))
        assertNull(session.getAttribute("pa.url.orig"))
    }

    // --- 연결 ---

    @Test
    fun `소셜 세션이 있는 채로 다른 제공자로 인증하면 그 계정에 연결한다`() {
        githubUser = """{"id":${700 + ++sequence},"login":"linker$sequence","name":"연결","email":"linker$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        googleUser = """{"id":"g${sequence}","email":"other$sequence@example.com","verified_email":true,"name":"다른 이름"}"""
        signIn("google", session).andExpect(status().isSeeOther)
        assertNull(users.findByLoginId("other$sequence"))
        val fresh = MockHttpSession()
        signIn("google", fresh).andExpect(header().string("Location", "/"))
        assertTrue(isLoggedIn(fresh))
        assertEquals(1, users.findAll().count { it.email == "linker$sequence@example.com" })
    }

    // --- 로그인 화면 ---

    @Test
    fun `로그인 화면에 허용된 제공자의 버튼과 구분선을 보인다`() {
        val html = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString
        val doc = Jsoup.parse(html)
        assertEquals(listOf("/authenticate/github", "/authenticate/google"), doc.select(".page.full a.oauth-login-btn").map { it.attr("href") })
        assertTrue(doc.select(".page.full .social-login-title-line").isNotEmpty())
        assertTrue(doc.selectFirst(".page.full a.oauth-login-btn .provider-name")!!.text().contains("Sign in with github"))
        assertTrue(doc.select(".page.full a.oauth-login-btn img[alt=login with Google]").isNotEmpty())
    }

    @Test
    fun `소셜 세션이 있는 채로 로그인 화면에 오면 사용자를 이어받아 이동한다`() {
        githubUser = """{"id":${800 + ++sequence},"login":"resume$sequence","name":"이어","email":"resume$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        // 로컬 세션만 사라진 상황: 소셜 세션 속성만 남긴다.
        val social = MockHttpSession().also { s -> listOf("pa.p.id", "pa.u.id", "pa.u.exp").forEach { s.setAttribute(it, session.getAttribute(it)) } }
        mockMvc.perform(get("/users/loginform").session(social).param("redirectUrl", "/after")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/after"))
        assertTrue(isLoggedIn(social))
    }

    @Test
    fun `소셜 세션을 이어받을 때도 2단계 인증을 켠 사용자는 코드를 확인한다 (X-2)`() {
        githubUser = """{"id":${900 + ++sequence},"login":"tfr$sequence","name":"이어","email":"tfr$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        val secret = enableTwoFactor(assertNotNull(users.findByLoginId("tfr$sequence")))
        val social = MockHttpSession().also { s -> listOf("pa.p.id", "pa.u.id", "pa.u.exp").forEach { s.setAttribute(it, session.getAttribute(it)) } }
        mockMvc.perform(get("/users/loginform").session(social).param("redirectUrl", "/after")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/login/2fa"))
        assertTrue(!isLoggedIn(social))
        mockMvc.perform(post("/users/login/2fa").session(social).param("code", nowCode(secret))).andExpect(header().string("Location", "/after"))
        assertTrue(isLoggedIn(social))
    }

    @Test
    fun `이어받을 때 이동할 주소가 없으면 홈으로 보낸다`() {
        githubUser = """{"id":${900 + ++sequence},"login":"resume$sequence","name":"이어","email":"resume$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        val social = MockHttpSession().also { s -> listOf("pa.p.id", "pa.u.id", "pa.u.exp").forEach { s.setAttribute(it, session.getAttribute(it)) } }
        mockMvc.perform(get("/users/loginform").session(social)).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
    }

    // --- 로그아웃 ---

    @Test
    fun `로그아웃은 로컬 세션과 소셜 세션을 지우고 이전 화면으로 보낸다`() {
        githubUser = """{"id":${1000 + ++sequence},"login":"bye$sequence","name":"안녕","email":"bye$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        session.setAttribute("pa.url.orig", "/was/here")
        val result = mockMvc.perform(get("/logout").session(session).header("Referer", "/from/here")).andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/from/here")).andReturn()
        assertEquals("user.logout.success", result.flashMap["success"])
        assertNull(session.getAttribute("pa.u.id"))
        assertNull(session.getAttribute("pa.url.orig"))
        assertFalse(isLoggedIn(session))
        assertEquals(0, result.response.getCookie("yobi.token")?.maxAge)
    }

    @Test
    fun `로그아웃에 이전 화면이 없으면 v1_6처럼 결과 객체의 문자열이 이동 주소가 된다 (결함 재현)`() {
        mockMvc.perform(get("/logout")).andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "Result(200, Map(Content-Type -> text/html; charset=utf-8))"))
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andExpect(header().string("Pragma", "no-cache")).andExpect(header().string("Expires", "0"))
    }

    @Test
    fun `소셜 세션이 없으면 restricted는 홈으로 보내고 Cache-Control이 없다`() {
        mockMvc.perform(get("/restricted")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/")).andExpect(header().doesNotExist("Cache-Control"))
    }

    // --- 원래 주소 기억 ---

    @Test
    fun `익명 사용자가 전체 화면을 보면 그 주소를 인증 후 돌아올 곳으로 기억한다`() {
        val session = MockHttpSession()
        mockMvc.perform(get("/users/signupform?a=b").session(session)).andExpect(status().isOk)
        assertEquals("/users/signupform?a=b", session.getAttribute("pa.url.orig"))
    }

    @Test
    fun `홈이나 GET이 아닌 요청은 기억하지 않고 기억해 둔 주소도 지운다`() {
        val session = MockHttpSession().also { it.setAttribute("pa.url.orig", "/old") }
        mockMvc.perform(get("/").session(session))
        assertNull(session.getAttribute("pa.url.orig"))
    }

    @Test
    fun `redirectUrl이 있으면 그 값을 기억한다`() {
        val session = MockHttpSession()
        mockMvc.perform(get("/users/loginform").session(session).param("redirectUrl", "/x/y")).andExpect(status().isOk)
        assertEquals("/x/y", session.getAttribute("pa.url.orig"))
    }

    // --- /restricted ---

    @Test
    fun `소셜 세션이 없으면 restricted는 홈으로 보내며 안내를 남긴다`() {
        val result = mockMvc.perform(get("/restricted")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/")).andReturn()
        assertEquals("Nice try, but you need to log in first!", result.flashMap["message"])
    }

    @Test
    fun `소셜 세션이 있으면 restricted는 이름과 이메일과 제공자 정보를 보인다`() {
        githubUser = """{"id":${1100 + ++sequence},"login":"secret$sequence","name":"비밀$sequence","email":"secret$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        val body = Jsoup.parse(mockMvc.perform(get("/restricted").session(session)).andExpect(status().isOk).andReturn().response.contentAsString).body().text()
        assertTrue(body.contains("Sshhh...don't tell anyone!"))
        assertTrue(body.contains("Your name is 비밀$sequence and your email address is secret$sequence@example.com"))
        assertTrue(body.contains("(verified)"))
        assertTrue(body.contains("Logged in with provider 'github' and the user ID '${1100 + sequence}'"))
        assertTrue(body.contains("Your session expires never"))
    }

    @Test
    fun `구글 세션은 토큰 만료 시각을 보인다`() {
        googleUser = """{"id":"gx${++sequence}","email":"gx$sequence@example.com","verified_email":true,"name":"구글$sequence"}"""
        val session = MockHttpSession()
        signIn("google", session)
        val body = Jsoup.parse(mockMvc.perform(get("/restricted").session(session)).andExpect(status().isOk).andReturn().response.contentAsString).body().text()
        assertTrue(Regex("Your session expires at \\d{13} \\(UNIX timestamp\\)").containsMatchIn(body), body)
    }

    // --- 메뉴의 로그아웃 링크 ---

    @Test
    fun `소셜 세션이 있으면 메뉴의 로그아웃이 소셜 로그아웃 주소를 가리킨다`() {
        githubUser = """{"id":${1200 + ++sequence},"login":"menu$sequence","name":"메뉴","email":"menu$sequence@example.com"}"""
        val session = MockHttpSession()
        signIn("github", session)
        val doc = Jsoup.parse(mockMvc.perform(get("/user/editform").session(session)).andReturn().response.contentAsString)
        val hrefs = doc.select("a:has(.user-menu.logout)").map { it.attr("href") }
        assertTrue(hrefs.isNotEmpty() && hrefs.all { it == "/logout" }, hrefs.toString())
    }

    @Test
    fun `비밀번호로 로그인했으면 메뉴의 로그아웃은 일반 로그아웃 주소이다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "plainmenu", name = "일반", email = "plainmenu@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "plainmenu").param("password", "1234")) }
        val doc = Jsoup.parse(mockMvc.perform(get("/user/editform").session(session)).andReturn().response.contentAsString)
        assertTrue(doc.select("a:has(.user-menu.logout)").all { it.attr("href") == "/users/logout" })
    }
}
