package dev.yona.web.site

import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 데이터 가져오기의 TUS 이어 올리기(D-144, X-53, A-597)를 검증한다. 조각으로 올린 파일을 올리기가 끝나면 [DataService]가 가져오고, 올린 조각은 지운다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteTusImportTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private var seq = 0
    private fun newUser(): User = users.save(User().apply {
        loginId = "tus${System.nanoTime()}${seq++}"; name = "tus"; email = "$loginId@example.com"; state = dev.yona.domain.enumeration.UserState.ACTIVE
        val salt = LegacyPasswordHasher.generateSalt(); passwordSalt = salt; password = LegacyPasswordHasher.hash("1234", salt); createdDate = Date()
    })
    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }
    private fun admin() = login(newUser().also { siteAdmins.save(SiteAdmin(admin = it)) })

    private val exactOctetStream = org.springframework.test.web.servlet.request.RequestPostProcessor { r -> r.setContentType("application/offset+octet-stream"); r }

    private fun create(session: MockHttpSession, length: Long) = mockMvc.perform(
        post("/sites/tus").session(session).header("Tus-Resumable", "1.0.0").header("Upload-Length", length.toString()))

    private fun labelNames() = jdbc.queryForList("select name from label", String::class.java)

    @Test
    fun `사이트 관리자가 만든 올리기는 위치를 돌려준다`() {
        create(admin(), 10).andExpect(status().isCreated).andExpect(header().exists("Location")).andExpect(header().string("Tus-Resumable", "1.0.0"))
    }

    @Test
    fun `사이트 관리자가 아니면 올릴 수 없다`() {
        create(login(newUser()), 10).andExpect(status().isForbidden)
    }

    @Test
    fun `10GB를 넘는 파일은 만들지 않는다`() {
        create(admin(), 10L * 1024 * 1024 * 1024 + 1).andExpect(status().isPayloadTooLarge)
    }

    @Test
    fun `조각으로 나눠 올리면 이어서 받는다`() {
        val session = admin()
        val body = """{"LABEL": [{"id": 1, "name": "tus-uploaded", "category": "c"}], "LABEL_SEQ": 10}""".toByteArray()
        val location = create(session, body.size.toLong()).andReturn().response.getHeader("Location")!!
        val path = java.net.URI(location).path
        val half = body.size / 2
        mockMvc.perform(patch(path).session(session).header("Tus-Resumable", "1.0.0").header("Upload-Offset", "0").with(exactOctetStream).content(body.copyOfRange(0, half)))
            .andExpect(status().isNoContent).andExpect(header().string("Upload-Offset", half.toString()))
        mockMvc.perform(head(path).session(session).header("Tus-Resumable", "1.0.0")).andExpect(status().isNoContent).andExpect(header().string("Upload-Offset", half.toString()))
        mockMvc.perform(patch(path).session(session).header("Tus-Resumable", "1.0.0").header("Upload-Offset", half.toString()).with(exactOctetStream).content(body.copyOfRange(half, body.size)))
            .andExpect(status().isNoContent).andExpect(header().string("Upload-Offset", body.size.toString()))
    }

    @Test
    fun `올리기를 마친 파일로 가져오면 처음 화면으로 이동하고 조각은 지워진다`() {
        TestDatabase.assumeOtherConnectionsIgnoreUncommittedRows()
        val session = admin()
        val body = """{"LABEL": [{"id": 1, "name": "tus-uploaded", "category": "c"}], "LABEL_SEQ": 10}""".toByteArray()
        val path = java.net.URI(create(session, body.size.toLong()).andReturn().response.getHeader("Location")!!).path
        mockMvc.perform(patch(path).session(session).header("Tus-Resumable", "1.0.0").header("Upload-Offset", "0").with(exactOctetStream).content(body)).andExpect(status().isNoContent)
        mockMvc.perform(post("/sites/import/tus").session(session).param("uploadUri", path)).andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
        assertEquals(listOf("tus-uploaded"), labelNames())
        mockMvc.perform(head(path).session(session).header("Tus-Resumable", "1.0.0")).andExpect(status().isNotFound)
    }

    @Test
    fun `끝나지 않은 올리기는 가져오지 않는다`() {
        val session = admin()
        val path = java.net.URI(create(session, 100).andReturn().response.getHeader("Location")!!).path
        mockMvc.perform(post("/sites/import/tus").session(session).param("uploadUri", path)).andExpect(status().isBadRequest)
    }

    @Test
    fun `가져오기는 사이트 관리자만 쓴다`() {
        mockMvc.perform(post("/sites/import/tus").session(login(newUser())).param("uploadUri", "/sites/tus/x")).andExpect(status().isForbidden)
        assertTrue(true)
    }
}
