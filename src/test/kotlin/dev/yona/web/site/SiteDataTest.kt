package dev.yona.web.site

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

    private var sequence = 0

    @AfterEach
    fun cleanUp() { jdbc.update("delete from label") }

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
        assertEquals("/sites/export", doc.select(".span10 a.ybtn-primary").attr("href"))
        val form = doc.selectFirst(".span10 form")!!
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
        assertEquals(listOf("uploaded"), jdbc.queryForList("select name from label", String::class.java))
    }

    @Test
    fun `파일을 고르지 않으면 데이터 화면으로 돌아간다`() {
        upload(admin(), null).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/data"))
        upload(admin(), "").andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/data"))
    }

    @Test
    fun `객체가 아닌 파일도 오류 없이 처음 화면으로 이동한다`() {
        upload(admin(), "[1,2]").andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))
    }

    @Test
    fun `JSON이 깨진 파일은 400이다`() {
        upload(admin(), "{\"LABEL\": [{\"id\": ").andExpect(status().isBadRequest)
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
