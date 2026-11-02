package dev.yona.web.site

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 사이트 관리의 "Git LFS" 화면(X-8, `decisions.md` D-79 5번, 6번)을 검증한다. 사이트 관리자만 쓰고, LFS 사용 여부와 객체 하나의 최대 크기(MiB), 프로젝트별 용량 한도(MiB, 0이면 없음)를
 * 정하며, 사이트 전체의 저장 사용량을 보인다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-site-lfs"])
class SiteLfsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var storage: LfsStorage

    private var n = 0

    @BeforeEach
    fun defaults() { settings.setLfsEnabled(true); settings.setLfsMaxObjectSize(0); settings.setLfsProjectQuota(0) }

    @AfterEach
    fun clean() { File("build/tmp/it-site-lfs").deleteRecursively() }

    private fun user(admin: Boolean = false): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "sl$k-${System.nanoTime()}", name = "sl$k", email = "sl$k@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    @Test
    fun `사이트 관리자만 열 수 있다`() {
        mockMvc.perform(get("/sites/lfs")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/lfs").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/lfs").session(login(user())).param("enabled", "true")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/lfs").session(login(user(admin = true)))).andExpect(status().isOk)
    }

    @Test
    fun `현재 설정과 사이트 전체 사용량을 보인다`() {
        settings.setLfsMaxObjectSize(5L * 1024 * 1024); settings.setLfsProjectQuota(100L * 1024 * 1024)
        val a = Project(id = 1, owner = "slo", name = "a", vcs = "GIT"); val b = Project(id = 2, owner = "slo", name = "b", vcs = "GIT")
        fun put(p: Project, text: String) = text.toByteArray().let { data -> storage.store(p, java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }, data.inputStream(), Long.MAX_VALUE) }
        put(a, "aaa"); put(b, "bbbbbb")
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/lfs").session(login(user(admin = true)))).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst("input#lfs-enabled[checked]")); assertEquals("5", doc.selectFirst("input#lfs-max-object")!!.attr("value")); assertEquals("100", doc.selectFirst("input#lfs-quota")!!.attr("value"))
        assertTrue(doc.selectFirst("#lfs-site-usage")!!.text().contains("2") && doc.selectFirst("#lfs-site-usage")!!.text().contains("9"), doc.selectFirst("#lfs-site-usage")!!.text())
        assertNotNull(doc.selectFirst("ul li a[href\$=/sites/lfs]"))
    }

    @Test
    fun `저장하면 설정이 바뀌고 잘못된 값은 400이다`() {
        val s = login(user(admin = true))
        mockMvc.perform(post("/sites/lfs").session(s).param("maxObjectMb", "10").param("quotaMb", "2048")).andExpect(status().is3xxRedirection)
        assertFalse(settings.lfsEnabled()); assertEquals(10L * 1024 * 1024, settings.lfsMaxObjectSize()); assertEquals(2048L * 1024 * 1024, settings.lfsProjectQuota())
        mockMvc.perform(post("/sites/lfs").session(s).param("enabled", "true").param("maxObjectMb", "1").param("quotaMb", "0")).andExpect(status().is3xxRedirection)
        assertTrue(settings.lfsEnabled()); assertEquals(0L, settings.lfsProjectQuota())
        for ((max, quota) in listOf("abc" to "0", "0" to "0", "-1" to "0", "10" to "-5", "10" to "x")) mockMvc.perform(post("/sites/lfs").session(s).param("maxObjectMb", max).param("quotaMb", quota)).andExpect(status().isBadRequest)
    }
}
