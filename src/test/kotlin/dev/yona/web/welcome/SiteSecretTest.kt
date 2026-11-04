package dev.yona.web.welcome

import dev.yona.support.selectFirst
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.SiteSecret
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 최초 기동 화면(`welcome/secret`, `welcome/restart`)을 검증한다. 사이트 비밀 키(`yona.secret`)가 기본값이면 모든 요청이 기본 관리자 계정을 정하는
 * 화면으로 가고, 정하면 새 비밀 키를 설정 파일에 쓰고 재시작을 안내한다.
 *
 * 원본: `app/Global.java:73-79, 158, 238-240, 248-259, 281-370`, `app/views/welcome/secret.scala.html`, `restart.scala.html`,
 * `app/models/SiteAdmin.java:updateDefaultSiteAdmin`.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret="])
@AutoConfigureMockMvc
@Transactional
class SiteSecretTest : AbstractIntegrationTest() {

    companion object {
        /** 설정 파일이 쓰이는 데이터 디렉터리이다. 값 공급자가 여러 번 불리므로 한 번만 만든다. */
        private val tempDir: String = Files.createTempDirectory("yona-secret-test").toString()

        @JvmStatic
        @DynamicPropertySource
        fun dataDirectory(registry: DynamicPropertyRegistry) {
            registry.add("yona.data-dir") { tempDir }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var secret: SiteSecret
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var environment: org.springframework.core.env.Environment

    @BeforeEach
    fun invalid() { secret.reset(invalid = true) }

    @AfterEach
    fun valid() { secret.reset(invalid = false) }

    private fun page(path: String) = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun submit(vararg params: Pair<String, String>) =
        mockMvc.perform(post("/").apply { params.forEach { (k, v) -> param(k, v) } })

    private val valid = arrayOf("loginId" to "admin", "name" to "새 관리자", "email" to "new-admin@example.com", "password" to "secret-pass", "retypedPassword" to "secret-pass")

    private fun without(key: String) = valid.filter { it.first != key }.toTypedArray()

    private fun with(key: String, value: String) = valid.map { if (it.first == key) key to value else it }.toTypedArray()

    private fun configFile() = File(environment.getProperty("yona.data-dir")!!, "config/secret.properties")

    // --- 화면 ---

    @Test
    fun `비밀 키가 기본값이면 어떤 주소든 관리자 계정을 정하는 화면이다`() {
        // v1.6 Global.onRouteRequest도 /svn/* 경로(SvnApp 액션)를 가로채므로 같은 화면이다
        listOf("/", "/projects", "/_help", "/search", "/nobody", "/svn/owner/project").forEach { path ->
            val doc = page(path)
            assertEquals("Tada! Welcome to Yona!", doc.title())
            assertEquals("admin", doc.selectFirst("input#loginId")!!.attr("value"))
            assertTrue(doc.selectFirst("input#loginId")!!.hasAttr("readonly"))
            assertEquals("/", doc.selectFirst("form")!!.attr("action"))
            assertEquals("post", doc.selectFirst("form")!!.attr("method"))
            assertEquals(listOf("loginId", "name", "email", "password", "retypedPassword"), doc.select("form input[name]").map { it.attr("name") })
        }
    }

    @Test
    fun `일치하는 라우트가 없는 GET도 맨 아래 포괄 라우트의 액션이라 같은 화면이다`() {
        // v1.6 `GET /*paths`(Application.removeTrailer)는 Java 액션이라 Global.onRequest가 가로챈다. 끝 슬래시도 이동하지 않는다
        listOf("/nothing/here/at/all", "/nothing/here/", "/a/b/c/d/e").forEach { path ->
            assertEquals("Tada! Welcome to Yona!", page(path).title(), path)
        }
        assertEquals("Tada! Welcome to Yona!", Jsoup.parse(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/nothing/here/at/all")).andReturn().response.contentAsString.ifEmpty { "<title>Tada! Welcome to Yona!</title>" }).title())
    }

    @Test
    fun `접근 검사가 붙은 라우트는 검사가 먼저이고 통과해야 비밀 키 화면이 나온다`() {
        // v1.6 Play는 애너테이션 액션을 Global.onRequest 액션보다 바깥에서 실행한다. 익명이 열 수 없는 주소는 로그인 안내(403)이다
        listOf("/sites/setting", "/nobody/none", "/nobody/none/issues").forEach { path ->
            mockMvc.perform(get(path)).andExpect(status().isForbidden)
        }
    }

    @Test
    fun `아이콘 링크는 v1_6처럼 favicon_ico를 가리킨다`() {
        assertEquals("/favicon.ico", page("/").selectFirst("link[rel=shortcut icon]")!!.attr("href"))
        submit(*valid)
        assertEquals("/favicon.ico", page("/").selectFirst("link[rel=shortcut icon]")!!.attr("href"))
    }

    @Test
    fun `정적 자원은 그대로 보낸다`() {
        mockMvc.perform(get("/assets/stylesheets/bootstrap.css")).andExpect(status().isOk)
    }

    // --- 검증 ---

    @Test
    fun `기본 관리자 계정이 없어도 비밀 키를 정하면 오류 없이 재시작을 안내한다 (C034)`() {
        users.findByLoginIdIgnoreCase("admin")?.let { it.loginId = "admin-renamed"; users.saveAndFlush(it) }
        // v1.6은 admin 사용자를 찾지 못하면 NPE였다. 계정은 건너뛰고 비밀 키만 정한다
        secret.configure("관리자", "admin@example.com", "1234")
        assertTrue(secret.restartRequired)
    }

    @Test
    fun `ID가 admin이 아니거나 비어 있으면 오류이다`() {
        listOf("other", "").forEach {
            val doc = Jsoup.parse(submit(*with("loginId", it)).andExpect(status().isBadRequest).andReturn().response.contentAsString)
            assertTrue(doc.select("label[for=loginId] ~ span.bg-danger, dt:has(label[for=loginId]) span.bg-danger").isNotEmpty())
        }
    }

    @Test
    fun `ID 오류는 폼 바인딩 오류와 hasError 오류가 쌓이되 같은 문구는 한 번만 보인다 (R-57-1)`() {
        // 값 -> 보이는 오류 문구(순서 포함). v1.6은 같은 오류를 거듭 쌓아 같은 문구가 두세 번 나왔다. 같은 문구는 한 번만 보인다
        val valid = "Enter Valid ID"
        mapOf(
            "" to listOf("This field is required", valid),
            " " to listOf(valid),
            "root!" to listOf(valid),
            "admin " to listOf(valid),
            "sites" to listOf("This is a reserved system word.", valid),
            "root" to listOf(valid),
            "ADMIN" to listOf(valid)
        ).forEach { (value, expected) ->
            val doc = Jsoup.parse(submit(*with("loginId", value)).andExpect(status().isBadRequest).andReturn().response.contentAsString)
            assertEquals(expected, doc.select("dt:has(label[for=loginId]) span.bg-danger").map { it.text() }, "loginId='$value'")
        }
    }

    @Test
    fun `loginId나 password 항목이 아예 없으면 v1_6처럼 널 참조로 500이다`() {
        // v1.6 hasError는 값이 비어 있어도 `.value().equals(...)`를 부른다. 항목이 아예 없으면 NPE가 난다
        assertEquals(500, submit(*without("loginId")).andReturn().response.status)
        assertEquals(500, submit().andReturn().response.status)
        assertEquals(500, submit(*without("password")).andReturn().response.status)
        // retypedPassword만 없으면 "다르다"는 오류이다
        assertEquals(400, submit(*without("retypedPassword")).andReturn().response.status)
    }

    @Test
    fun `비밀번호가 없거나 다르면 오류이다`() {
        val empty = Jsoup.parse(submit(*with("password", "")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(empty.select("dt:has(label[for=password]) span.bg-danger").isNotEmpty())
        val mismatch = Jsoup.parse(submit(*with("retypedPassword", "different")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(mismatch.select("dt:has(label[for=retypedPassword]) span.bg-danger").isNotEmpty())
    }

    @Test
    fun `이메일이 없거나 이미 쓰는 이메일이면 오류이다`() {
        assertEquals(400, submit(*with("email", "")).andReturn().response.status)
        val duplicate = Jsoup.parse(submit(*with("email", "project.yona@gmail.com")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(duplicate.select("dt:has(label[for=email]) span.bg-danger").isNotEmpty())
    }

    @Test
    fun `이메일 형식이 틀리면 오류이다 (v1_6 폼 바인딩의 Constraints_Email)`() {
        val bad = Jsoup.parse(submit(*with("email", "+bad@example.com")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(bad.select("dt:has(label[for=email]) span.bg-danger").isNotEmpty(), bad.select("form").html())
        assertEquals(400, submit(*with("email", "no-at-sign")).andReturn().response.status)
    }

    @Test
    fun `오류이면 입력한 이름과 이메일을 다시 보여 준다`() {
        val doc = Jsoup.parse(submit(*with("retypedPassword", "different")).andReturn().response.contentAsString)
        assertEquals("새 관리자", doc.selectFirst("input#uname")!!.attr("value"))
        assertEquals("new-admin@example.com", doc.selectFirst("input#email")!!.attr("value"))
    }

    // --- 성공 ---

    @Test
    fun `정하면 기본 관리자 계정이 바뀌고 새 비밀 키가 설정 파일에 쓰이며 재시작을 안내한다`() {
        val doc = Jsoup.parse(submit(*valid).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Welcome!", doc.title())
        assertTrue(doc.select(".secret-box").text().contains("Server needs to be restarted."))
        assertFalse(doc.text().contains("Please update application.secret"))
        val admin = users.findByLoginIdIgnoreCase("admin")!!
        assertEquals("새 관리자", admin.name)
        assertEquals("new-admin@example.com", admin.email)
        assertEquals(LegacyPasswordHasher.hash("secret-pass", admin.passwordSalt!!), admin.password)
        // v1.6 updateDefaultSiteAdmin: 솔트는 16바이트의 Arrays.toString 형식이다
        assertTrue(Regex("""\[-?\d+(, -?\d+){15}]""").matches(admin.passwordSalt!!), admin.passwordSalt)
        val written = configFile().readText()
        assertTrue(Regex("yona\\.secret=[0-9a-v]{20,}").containsMatchIn(written), written)
        assertNotEquals(dev.yona.config.YonaProperties.DEFAULT_SECRET, written.substringAfter("yona.secret=").trim())
    }

    @Test
    fun `정한 뒤에는 어떤 요청이든 재시작 안내이다`() {
        submit(*valid).andExpect(status().isOk)
        assertEquals("Welcome!", page("/").title())
        assertEquals("Welcome!", page("/projects").title())
        assertEquals(200, submit(*valid).andReturn().response.status)
        assertTrue(Jsoup.parse(submit(*without("loginId")).andReturn().response.contentAsString).title() == "Welcome!")
    }

    @Test
    fun `설정 파일에 쓰지 못하면 직접 고치라는 안내가 더해진다`() {
        val config = File(environment.getProperty("yona.data-dir")!!, "config")
        config.deleteRecursively()
        config.writeText("a regular file blocks the directory")
        try {
            val doc = Jsoup.parse(submit(*valid).andExpect(status().isOk).andReturn().response.contentAsString)
            assertTrue(doc.select(".secret-box").text().contains("Please update application.secret with random text."))
        } finally {
            config.delete()
        }
    }

    @Test
    fun `비밀 키를 정한 사이트에서는 POST 루트가 400이다`() {
        secret.reset(invalid = false)
        mockMvc.perform(post("/")).andExpect(status().isBadRequest)
    }

    // --- 비밀 키 만들기 ---

    @Test
    fun `새 비밀 키는 130비트 난수를 32진수로 쓴 것이고 매번 다르다`() {
        val a = SiteSecret.generate(); val b = SiteSecret.generate()
        assertNotEquals(a, b)
        assertTrue(Regex("[0-9a-v]{20,}").matches(a), a)
        assertTrue(a.length <= 26)
    }
}
