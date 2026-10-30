package dev.yona.web.api

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 사이트 백업 REST(로드맵 X-37g, `decisions.md` D-52)를 검증한다. `GET /admin/export`는 웹 `/sites/export`와 같은 JSON 파일을 내려주고, `POST /admin/import`는 웹 `/sites/import`와
 * 같은 서비스로 DB를 교체하되 리다이렉트 대신 JSON 결과를 준다. 둘 다 `site_admin` 범위와 사이트 관리자 권한이 모두 있어야 한다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalSiteBackupApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: javax.sql.DataSource

    private var sequence = 0

    @AfterEach
    fun cleanUp() { jdbc.update("delete from label") }

    /** 가져오기는 다른 연결에서 커밋하므로 시험 트랜잭션 밖의 새 연결로 읽는다. */
    private fun labelNames(): List<String> = dataSource.connection.use { c ->
        c.createStatement().use { st -> st.executeQuery("select name from label").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } }
    }

    private fun newUser(siteAdmin: Boolean, scopes: Collection<String>? = null): Pair<User, String> {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "bk$n", name = "bk$n", email = "bk$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (siteAdmin) siteAdmins.save(SiteAdmin(admin = user))
        val token = "bkt$n"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, user, token) else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, user, token, scopes)
        return user to token
    }

    private fun upload(token: String?, content: String?) = mockMvc.perform(
        multipart("/-_-api/v1/admin/import").apply {
            content?.let { file(MockMultipartFile("data", "data.json", "application/json", it.toByteArray())) }
            token?.let { header("Authorization", "token $it") }
        }
    )

    @Test
    fun `내려받기는 사이트 관리자에게 날짜가 붙은 JSON 파일을 보낸다`() {
        val (_, token) = newUser(siteAdmin = true)
        val result = mockMvc.perform(get("/-_-api/v1/admin/export").header("Authorization", "token $token")).andExpect(status().isOk).andReturn()
        assertTrue(Regex("attachment; filename=yobi-data-\\d{12}\\.json").matches(result.response.getHeader("Content-disposition")!!), result.response.getHeader("Content-disposition"))
        val body = result.response.contentAsString
        assertTrue(body.trimStart().startsWith("{") && body.contains("\"N4USER\""))
    }

    @Test
    fun `올리기는 DB를 교체하고 가져온 테이블과 실패한 테이블을 JSON으로 알려준다`() {
        val (_, token) = newUser(siteAdmin = true)
        upload(token, """{"LABEL": [{"id": 1, "name": "uploaded", "category": "c"}], "LABEL_SEQ": 10}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.imported").isArray).andExpect(jsonPath("$.failed.length()").value(0))
        assertEquals(listOf("uploaded"), labelNames())
        // 도중에 잘린 파일은 그 테이블을 되돌리고 failed에 담긴다(v1.6은 302로 숨긴다)
        upload(token, """{"LABEL": [{"id": 1, "name": "a", "category": "c"}, {"id": 2, "name": "b"""").andExpect(status().isOk).andExpect(jsonPath("$.failed[0]").value("LABEL"))
        assertEquals(emptyList(), labelNames())
    }

    @Test
    fun `파일이 없거나 첫 토큰부터 JSON이 아니면 400이다`() {
        val (_, token) = newUser(siteAdmin = true)
        upload(token, null).andExpect(status().isBadRequest)
        upload(token, "abc").andExpect(status().isBadRequest)
    }

    @Test
    fun `일반 사용자와 site_admin 범위가 없는 토큰과 익명은 쓸 수 없다`() {
        val (_, plain) = newUser(siteAdmin = false)
        mockMvc.perform(get("/-_-api/v1/admin/export").header("Authorization", "token $plain")).andExpect(status().isForbidden)
        upload(plain, "{}").andExpect(status().isForbidden)
        val (_, noScope) = newUser(siteAdmin = true, scopes = listOf("repo"))
        mockMvc.perform(get("/-_-api/v1/admin/export").header("Authorization", "token $noScope")).andExpect(status().isForbidden)
        upload(noScope, "{}").andExpect(status().isForbidden)
        mockMvc.perform(get("/-_-api/v1/admin/export")).andExpect(status().isUnauthorized)
        upload(null, "{}").andExpect(status().isUnauthorized)
    }
}
