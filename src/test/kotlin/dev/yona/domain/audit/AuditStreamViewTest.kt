package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 사이트 감사 화면의 외부 스트리밍 상태 표(X-9 B6, `decisions.md` D-63)를 검증한다. 웹훅 주소만 설정하고 보내지는 않는다. */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.audit-stream-webhook-url=http://127.0.0.1:1/none", "yona.audit-file-enabled=false"])
class AuditStreamViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var cursors: AuditStreamCursorRepository

    private val stamp = System.nanoTime()

    private fun admin(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "asv-$stamp", name = "스트림", email = "asv-$stamp@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        siteAdmins.save(SiteAdmin(admin = u))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }
    }

    private fun page(session: MockHttpSession) = Jsoup.parse(mockMvc.perform(get("/sites/audit").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `설정된 대상이 있으면 상태 표에 이름과 밀린 건수와 마지막 오류가 보인다`() {
        cursors.save(AuditStreamCursor(name = "webhook", lastId = 0, lastError = "웹훅이 HTTP 500로 응답했습니다", lastErrorAt = System.currentTimeMillis(), failures = 3))
        val row = page(admin()).selectFirst("tr.audit-stream")
        assertNotNull(row)
        assertEquals("webhook", row.selectFirst(".stream-name")!!.text())
        assertTrue(row.selectFirst(".stream-error")!!.text().contains("HTTP 500"), row.html())
        assertTrue(row.selectFirst(".stream-failures")!!.text().contains("3"))
    }

    @Test
    fun `대상은 있지만 아직 보낸 적이 없으면 보낸 시각이 비어 있다고 알린다`() {
        cursors.save(AuditStreamCursor(name = "webhook", lastId = 0))
        val row = page(admin()).selectFirst("tr.audit-stream")!!
        assertEquals("-", row.selectFirst(".stream-last-sent")!!.text()); assertEquals("-", row.selectFirst(".stream-error")!!.text())
    }
}
