package dev.yona.web.site

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 사이트 관리의 데이터 화면과 내려받기, 올리기(`/sites/data`, `/sites/export`, `/sites/import`), 아바타 없는 사용자 목록과 아바타 지정
 * (`/sites/noAvatarUsers`, `/sites/setAttachmentToUserAvatar`)을 검증한다.
 *
 * 원본: `app/controllers/SiteApp.java:352-431`, `app/views/site/data.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class SiteDataTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: javax.sql.DataSource

    private var sequence = 0

    /** 테스트 트랜잭션 밖의 새 연결로 읽는다. 가져오기는 다른 연결에서 커밋하므로, 반복 읽기 격리(MariaDB)에서는 테스트 트랜잭션이 그 결과를 보지 못한다. */
    private fun labelNames(): List<String> = dataSource.connection.use { c ->
        c.createStatement().use { st -> st.executeQuery("select name from label").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } }
    }

    private fun mentionResources(): List<String> = dataSource.connection.use { c ->
        c.createStatement().use { st -> st.executeQuery("select resource_id from mention order by resource_id").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } }
    }

    @AfterEach
    fun cleanUp() { jdbc.update("delete from label"); jdbc.update("delete from mention where resource_type = 'SD_TEST'") }

    private fun newUser(email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sd$n", name = "sd$n", email = email ?: "sd$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun admin(): MockHttpSession = login(newUser().also { siteAdmins.save(SiteAdmin(admin = it)) })

    // --- 화면 ---

    @Test
    fun `데이터 화면에는 경고 세 줄과 내보내기 링크와 올리기 폼이 있다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/data").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(3, doc.select(".cu-desc li.notice").size)
        assertEquals("/sites/export", doc.select(".col-lg-10 a.ybtn-primary").attr("href"))
        val form = doc.selectFirst(".col-lg-10 form")!!
        assertEquals("/sites/import", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("post", form.attr("method"))
        assertTrue(form.select("input[type=file][name=data]").isNotEmpty())
        assertTrue(form.select("input[type=submit]").isNotEmpty())
    }

    @Test
    fun `데이터 화면과 내려받기와 올리기는 사이트 관리자만 쓴다`() {
        val session = login(newUser())
        mockMvc.perform(get("/sites/data").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/export").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(multipart("/sites/import").file(MockMultipartFile("data", "d.json", "application/json", "{}".toByteArray())).session(session)).andExpect(status().isForbidden)
    }

    // --- 내려받기 ---

    @Test
    fun `내려받기는 날짜가 붙은 JSON 파일을 보낸다`() {
        val started = mockMvc.perform(get("/sites/export").session(admin())).andExpect(request().asyncStarted()).andReturn()
        val result = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/x-download")).andReturn()
        assertTrue(Regex("attachment; filename=yobi-data-\\d{12}\\.json").matches(result.response.getHeader("Content-disposition")!!), result.response.getHeader("Content-disposition"))
        val body = result.response.contentAsString
        assertTrue(body.trimStart().startsWith("{") && body.contains("\"N4USER\""))
    }

    // --- 올리기 ---

    private fun upload(session: MockHttpSession, content: String?) =
        mockMvc.perform(multipart("/sites/import").apply { content?.let { file(MockMultipartFile("data", "data.json", "application/json", it.toByteArray())) } }.session(session))

    @Test
    fun `올리기는 성공하면 처음 화면으로 이동한다`() {
        upload(admin(), """{"LABEL": [{"id": 1, "name": "uploaded", "category": "c"}], "LABEL_SEQ": 10}""").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
        assertEquals(listOf("uploaded"), labelNames())
    }

    @Test
    fun `data 파트가 없으면 데이터 화면으로 돌아가고 빈 파일은 처음 화면으로 간다`() {
        upload(admin(), null).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/data"))
        // v1.6: getFile("data")가 null일 때만 /sites/data이다. 0바이트 파일은 첫 토큰이 없어 아무것도 하지 않고 302 /이다.
        upload(admin(), "").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
    }

    @Test
    fun `객체가 아닌 파일도 오류 없이 처음 화면으로 이동한다`() {
        upload(admin(), "[1,2]").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
    }

    @Test
    fun `첫 토큰부터 JSON이 아니면 400이다`() {
        upload(admin(), "abc").andExpect(status().isBadRequest)
    }

    private fun seedOld() { upload(admin(), """{"LABEL": [{"id": 1, "name": "old", "category": "c"}]}""").andExpect(redirectedUrl("/")); assertEquals(listOf("old"), labelNames()) }

    // v1.6은 테이블을 TRUNCATE로 비운다. TRUNCATE는 DDL이라 즉시 확정되므로, 행을 넣다 실패해 되돌려도 테이블은 빈 채로 남는다.
    @Test
    fun `파일이 도중에 잘리면 그 테이블의 행 넣기를 되돌리고 처음 화면으로 이동한다`() {
        seedOld()
        // v1.6 DefaultExchanger는 테이블마다 트랜잭션을 잡고 예외를 삼킨다. 실패한 테이블만 되돌리고 302 /이다.
        upload(admin(), """{"LABEL": [{"id": 1, "name": "a", "category": "c"}, {"id": 2, "name": "b"""").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
        // 되돌린 뒤 자동 커밋으로 바꿀 때 부분 행이 확정되지 않아야 한다(같은 입력을 한 번 더)
        upload(admin(), """{"LABEL": [{"id": 1, "name": "a", "category": "c"}, {"id": 2, "name": "b"""").andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
    }

    @Test
    fun `파일 중간의 구문 오류도 그 테이블의 행 넣기를 되돌리고 처음 화면으로 이동한다`() {
        seedOld()
        upload(admin(), """{"LABEL": [{"id": 1, "name": "a", "category": "c"}, {"id": ##}]}""").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
    }

    @Test
    fun `행에 필수 값이 없거나 기본 키가 겹치면 그 테이블은 빈 채로 남는다`() {
        seedOld()
        upload(admin(), """{"LABEL": [{"id": 1}]}""").andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
        seedOld()
        upload(admin(), """{"LABEL": [{"id": 1, "name": "a", "category": "c"}, {"id": 1, "name": "b", "category": "c"}]}""").andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
    }

    @Test
    fun `테이블 이름의 값이 배열이 아니면 테이블만 비워진다 (v1_6 TRUNCATE)`() {
        seedOld()
        upload(admin(), """{"LABEL": 5}""").andExpect(redirectedUrl("/"))
        assertEquals(emptyList(), labelNames())
    }

    @Test
    fun `앞서 끝낸 테이블은 남고 뒤에서 깨진 테이블은 빈 채로 남는다`() {
        seedOld()
        upload(admin(), """{"MENTION": [{"id": 9001, "resource_type": "SD_TEST", "resource_id": "sd-new", "user_id": null}], "LABEL": [{"id": 1, "name": "x", "category": "c"}, {"id": 2, "name": """").andExpect(redirectedUrl("/"))
        assertEquals(listOf("sd-new"), mentionResources())
        assertEquals(emptyList(), labelNames())
    }

    // --- 아바타 없는 사용자 ---

    @Test
    fun `아바타가 없는 활성 사용자를 JSON으로 알려 준다`() {
        val me = admin()
        val withAvatar = newUser(); val without = newUser()
        attachments.save(Attachment(name = "a.png", hash = "hh", containerType = ResourceType.USER_AVATAR, containerId = withAvatar.id!!, mimeType = "image/png", size = 1, createdDate = Date(), ownerLoginId = withAvatar.loginId))
        val body = mockMvc.perform(get("/sites/noAvatarUsers").session(me)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("\"loginId\":\"${without.loginId}\""), body)
        assertTrue(!body.contains("\"loginId\":\"${withAvatar.loginId}\""))
        assertTrue(body.startsWith("{\"users\":["))
    }

    @Test
    fun `이미지 첨부를 사용자의 아바타로 지정한다`() {
        val me = admin(); val target = newUser(email = "target-avatar@example.com")
        val old = attachments.save(Attachment(name = "old.png", hash = "old-h", containerType = ResourceType.USER_AVATAR, containerId = target.id!!, mimeType = "image/png", size = 1, createdDate = Date(), ownerLoginId = target.loginId))
        val fresh = attachments.save(Attachment(name = "new.png", hash = "new-h", containerType = ResourceType.ISSUE_POST, containerId = 1, mimeType = "image/png", size = 1, createdDate = Date(), ownerLoginId = target.loginId))
        mockMvc.perform(post("/sites/setAttachmentToUserAvatar").session(me).contentType(MediaType.APPLICATION_JSON).content("""{"avatarFileId": ${fresh.id}, "email": "target-avatar@example.com"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.message").value("OK"))
        assertEquals(ResourceType.USER_AVATAR, attachments.findById(fresh.id!!).get().containerType)
        assertEquals(target.id, attachments.findById(fresh.id!!).get().containerId)
        assertTrue(!attachments.existsById(old.id!!))
    }

    @Test
    fun `이미지가 아니거나 없는 사용자이면 아무것도 하지 않고 성공으로 답한다`() {
        val me = admin(); val target = newUser(email = "target2@example.com")
        val text = attachments.save(Attachment(name = "a.txt", hash = "txt-h", containerType = ResourceType.ISSUE_POST, containerId = 1, mimeType = "text/plain", size = 1, createdDate = Date(), ownerLoginId = target.loginId))
        listOf("""{"avatarFileId": ${text.id}, "email": "target2@example.com"}""", """{"avatarFileId": ${text.id}, "email": "nobody@example.com"}""").forEach {
            mockMvc.perform(post("/sites/setAttachmentToUserAvatar").session(me).contentType(MediaType.APPLICATION_JSON).content(it)).andExpect(status().isOk).andExpect(jsonPath("$.status").value(200))
        }
        assertEquals(ResourceType.ISSUE_POST, attachments.findById(text.id!!).get().containerType)
    }

    @Test
    fun `JSON이 아닌 본문은 400이다`() {
        mockMvc.perform(post("/sites/setAttachmentToUserAvatar").session(admin()).contentType(MediaType.TEXT_PLAIN).content("not json"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
    }
}
