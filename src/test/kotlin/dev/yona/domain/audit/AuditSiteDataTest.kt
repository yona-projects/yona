package dev.yona.domain.audit

import dev.yona.support.TestDatabase
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 감사 로그의 사이트 데이터 내보내기와 가져오기 이벤트(로드맵 X-9 A5, `decisions.md` D-62)를 검증한다. 행위자는 요청 스레드에서 정해지므로 사이트 관리자로 남는다. */
@AutoConfigureMockMvc
@Transactional
class AuditSiteDataTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun admin(): Pair<User, MockHttpSession> {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "ad$n-$stamp", name = "관리$n", email = "ad$n-$stamp@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        siteAdmins.save(SiteAdmin(admin = user))
        return user to MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    @Test
    fun `웹 내려받기는 사이트 관리자 행위자로 남는다`() {
        val (user, session) = admin()
        mockMvc.perform(get("/sites/export").session(session)).andExpect(status().isOk)
        val e = eventsOf("site.data_exported").single()
        assertEquals(AuditActorType.USER, e.actorType); assertEquals(user.loginId, e.actorLogin)
        assertTrue(e.details!!.contains("\"via\":\"web\""))
    }

    @Test
    fun `웹 올리기는 결과를 남긴다`() {
        TestDatabase.assumeOtherConnectionsIgnoreUncommittedRows()
        val (user, session) = admin()
        mockMvc.perform(multipart("/sites/import").file(MockMultipartFile("data", "d.json", "application/json", "{}".toByteArray())).session(session)).andExpect(status().isSeeOther)
        val e = eventsOf("site.data_imported").single()
        assertEquals(user.loginId, e.actorLogin)
        assertTrue(e.details!!.contains("\"imported\"") && e.details!!.contains("\"failed\""), e.details)
    }

    @Test
    fun `사이트 관리자가 아니면 남기지 않고 거부만 남는다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val plain = users.save(User(loginId = "pl-$stamp", name = "일반", email = "pl-$stamp@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", plain.loginId!!).param("password", "1234")) }
        mockMvc.perform(get("/sites/export").session(session)).andExpect(status().isForbidden)
        assertTrue(eventsOf("site.data_exported").isEmpty())
        assertEquals("GET /sites/export", eventsOf("access.denied").single().targetLabel)
    }
}
