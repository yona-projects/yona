package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.misc.TemporaryFileCleaner
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.text.Normalizer
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 첨부 파일 올리기(`POST /files`), 목록(`GET /files`), 지우기(`POST /files/:id`)와 임시 파일 정리를 검증한다.
 *
 * 원본: `app/controllers/AttachmentApp.java:60-130, 174-279`, `app/models/Attachment.java:440-484`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-upload"])
class AttachmentUploadTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var attachmentRepository: AttachmentRepository
    @Autowired private lateinit var attachments: AttachmentService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var cleaner: TemporaryFileCleaner

    private var sequence = 0
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @AfterEach
    fun clean() {
        File("build/tmp/it-upload").deleteRecursively()
    }

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "up$n", name = "up$n", email = "up$n@example.com", state = UserState.ACTIVE, token = token,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun upload(session: MockHttpSession?, name: String = "사진.png", bytes: ByteArray = png, accept: String? = null, token: String? = null, field: String = "filePath", params: Map<String, String> = emptyMap()): ResultActions =
        mockMvc.perform(
            multipart("/files").file(MockMultipartFile(field, name, "image/png", bytes)).apply {
                params.forEach { (key, value) -> param(key, value) }
                session?.let { session(it) }
                accept?.let { accept(it) }
                token?.let { header("Yona-Token", it) }
            }
        )

    // --- 올리기 ---

    @Test
    fun `로그인한 사용자가 올리면 임시 첨부로 저장하고 201과 파일 정보를 돌려준다`() {
        val user = newUser()
        upload(login(user), accept = "application/json")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("사진.png"))
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.size").value("${png.size}"))
        val stored = attachmentRepository.findAll().single()
        assertEquals(ResourceType.USER, stored.containerType)
        assertEquals(user.id, stored.containerId)
        assertEquals(user.loginId, stored.ownerLoginId)
    }

    @Test
    fun `만든 날짜는 시각이 없는 날짜이다 (v1_6 Ebean이 java util Date를 DATE로 저장한다)`() {
        upload(login(newUser())).andExpect(status().isCreated)
        val created = java.util.Calendar.getInstance().apply { time = attachmentRepository.findAll().single().createdDate!! }
        assertEquals(listOf(0, 0, 0, 0), listOf(created.get(java.util.Calendar.HOUR_OF_DAY), created.get(java.util.Calendar.MINUTE), created.get(java.util.Calendar.SECOND), created.get(java.util.Calendar.MILLISECOND)))
    }

    @Test
    fun `응답 JSON의 필드 순서는 v1_6의 HashMap 순서이다`() {
        val body = upload(login(newUser()), accept = "application/json").andReturn().response.contentAsString
        assertTrue(Regex("""^\{"size":"\d+","name":"[^"]+","id":"\d+","mimeType":"[^"]+","url":"/files/\d+"}$""").matches(body), body)
    }

    @Test
    fun `파일 이름에서 역슬래시 앞의 경로만 뗀다 (Play 멀티파트는 슬래시를 그대로 둔다)`() {
        val session = login(newUser())
        upload(session, name = "dir\\back.txt", bytes = byteArrayOf(1)).andExpect(status().isCreated)
        upload(session, name = "../evil.txt", bytes = byteArrayOf(2)).andExpect(status().isCreated)
        assertEquals(setOf("back.txt", "../evil.txt"), attachmentRepository.findAll().map { it.name }.toSet())
    }

    @Test
    fun `폼의 authorEmail로 다른 사용자 명의로 올린다 (v1_6 findUploader)`() {
        val uploader = newUser()
        val author = newUser()
        upload(login(uploader), params = mapOf("authorEmail" to author.email!!)).andExpect(status().isCreated)
        val stored = attachmentRepository.findAll().single()
        assertEquals(author.id, stored.containerId)
        // 소유자 아이디는 명의가 아니라 올린 요청의 로그인 사용자이다
        assertEquals(uploader.loginId, stored.ownerLoginId)
    }

    @Test
    fun `이메일을 못 찾으면 authorLoginId로 찾고 그래도 없으면 올린 사용자이다`() {
        val uploader = newUser()
        val author = newUser()
        val session = login(uploader)
        upload(session, bytes = byteArrayOf(1), params = mapOf("authorEmail" to "nobody@example.com", "authorLoginId" to author.loginId!!)).andExpect(status().isCreated)
        upload(session, bytes = byteArrayOf(2), params = mapOf("authorEmail" to "nobody@example.com", "authorLoginId" to "nobody")).andExpect(status().isCreated)
        assertEquals(setOf(author.id, uploader.id), attachmentRepository.findAll().map { it.containerId }.toSet())
    }

    @Test
    fun `응답에는 위치 헤더와 주소가 들어 있다`() {
        val result = upload(login(newUser()), accept = "application/json").andReturn()
        val id = attachmentRepository.findAll().single().id
        assertEquals("/files/$id", result.response.getHeader("Location"))
        assertTrue(result.response.contentAsString.contains("\"url\":\"/files/$id\""))
        assertEquals("Accept", result.response.getHeader("Vary"))
    }

    @Test
    fun `JSON을 원하지 않으면 본문은 JSON이지만 종류는 text html이다`() {
        upload(login(newUser()), accept = "text/html")
            .andExpect(status().isCreated)
            // v1.6는 Content-Type을 직접 정해 문자 집합이 없다. 본문은 UTF-8이다
            .andExpect(header().string("Content-Type", "text/html"))
            .andExpect { assertTrue(String(it.response.contentAsByteArray, Charsets.UTF_8).contains("\"name\":\"사진.png\"")) }
    }

    @Test
    fun `같은 파일을 다시 올리면 200이고 새로 만들지 않는다`() {
        val session = login(newUser())
        upload(session).andExpect(status().isCreated)
        upload(session).andExpect(status().isOk)
        assertEquals(1, attachmentRepository.findAll().size)
    }

    @Test
    fun `파일 이름은 NFC로 정규화한다`() {
        val decomposed = Normalizer.normalize("한글.png", Normalizer.Form.NFD)
        upload(login(newUser()), name = decomposed)
        assertEquals(Normalizer.normalize("한글.png", Normalizer.Form.NFC), attachmentRepository.findAll().single().name)
    }

    @Test
    fun `파일이 없으면 400이다`() {
        mockMvc.perform(multipart("/files").session(login(newUser()))).andExpect(status().isBadRequest)
        upload(login(newUser()), field = "other").andExpect(status().isBadRequest)
    }

    @Test
    fun `익명은 올릴 수 없다`() {
        upload(null).andExpect(status().isForbidden)
    }

    @Test
    fun `웹 경로 files는 세션 전용이라 토큰 헤더를 인정하지 않는다 (X-5, D-36)`() {
        newUser(token = "tok-abc")
        upload(null, token = "tok-abc").andExpect(status().isForbidden)
        assertEquals(0, attachmentRepository.findAll().size)
    }

    // --- 목록 ---

    @Test
    fun `컨테이너의 첨부 목록을 JSON으로 돌려준다`() {
        val owner = newUser()
        val project = projects.save(Project(owner = "uo${++sequence}", name = "pp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val file = File.createTempFile("att-a", ".png").apply { writeBytes(png) }
        val stored = attachments.store(file, "a.png", ResourceType.PROJECT, project.id!!, owner.loginId).attachment
        mockMvc.perform(get("/files").param("containerType", "PROJECT").param("containerId", project.id.toString()))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
            .andExpect(jsonPath("$.attachments[0].id").value("${stored.id}"))
            .andExpect(jsonPath("$.attachments[0].name").value("a.png"))
            .andExpect(jsonPath("$.attachments[0].url").value("/files/${stored.id}"))
    }

    @Test
    fun `컨테이너 번호가 숫자가 아니면 빈 목록이다 (v1_6은 문자열로 조회한다)`() {
        mockMvc.perform(get("/files").param("containerType", "ISSUE_POST").param("containerId", "abc"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"attachments":[]}"""))
    }

    @Test
    fun `파라미터가 없으면 빈 객체이다`() {
        mockMvc.perform(get("/files")).andExpect(status().isOk).andExpect(content().json("{}"))
    }

    @Test
    fun `읽을 수 없는 첨부가 있으면 403이다`() {
        val project = projects.save(Project(owner = "uo${++sequence}", name = "pv$sequence", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        val file = File.createTempFile("att-b", ".png").apply { writeBytes(png) }
        attachments.store(file, "b.png", ResourceType.PROJECT, project.id!!, "x")
        mockMvc.perform(get("/files").param("containerType", "PROJECT").param("containerId", project.id.toString())).andExpect(status().isForbidden)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
    }

    // --- 지우기 ---

    private fun remove(id: Long?, session: MockHttpSession?, method: String? = "delete"): ResultActions =
        mockMvc.perform(
            multipart("/files/$id").apply { session?.let { session(it) } }.apply { method?.let { param("_method", it) } }
        )

    @Test
    fun `올린 사람은 임시 첨부를 지운다`() {
        val user = newUser()
        val session = login(user)
        upload(session)
        val stored = attachmentRepository.findAll().single()
        remove(stored.id, session).andExpect(status().isOk).andExpect(content().string("Both the attachment and its origin file are removed successfully."))
        assertTrue(attachmentRepository.findAll().isEmpty())
    }

    @Test
    fun `같은 내용을 쓰는 다른 첨부가 있으면 원본 파일이 남는다는 안내를 한다`() {
        val a = newUser()
        val b = newUser()
        upload(login(a))
        upload(login(b))
        val first = attachmentRepository.findAll().first { it.containerId == a.id }
        remove(first.id, login(a)).andExpect(content().string("The attachment is removed successfully, but its origin file still exists."))
    }

    @Test
    fun `원본 파일이 없는데 다른 첨부가 같은 해시를 쓰면 오류 로그를 남긴다 (v1_6 logIfOriginFileIsNotValid)`() {
        val logger = org.slf4j.LoggerFactory.getLogger(dev.yona.web.AttachmentController::class.java) as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().also { it.start(); logger.addAppender(it) }
        try {
            val a = newUser(); val b = newUser()
            upload(login(a)); upload(login(b))
            val first = attachmentRepository.findAll().first { it.containerId == a.id }
            attachments.fileOf(first).delete()
            remove(first.id, login(a)).andExpect(status().isOk)
            assertTrue(appender.list.any { it.level == ch.qos.logback.classic.Level.ERROR && it.formattedMessage.contains("cannot be found") }, appender.list.map { it.formattedMessage }.toString())
        } finally { logger.detachAppender(appender) }
    }

    @Test
    fun `남이 올린 임시 첨부는 지울 수 없다`() {
        val owner = newUser()
        upload(login(owner))
        val stored = attachmentRepository.findAll().single()
        remove(stored.id, login(newUser())).andExpect(status().isForbidden)
        remove(stored.id, null).andExpect(status().isForbidden)
        assertEquals(1, attachmentRepository.findAll().size)
    }

    @Test
    fun `없는 첨부는 404이고 메서드 값이 delete가 아니면 400이다`() {
        val session = login(newUser())
        remove(999999, session).andExpect(status().isNotFound)
        upload(session)
        val stored = attachmentRepository.findAll().single()
        remove(stored.id, session, method = "put").andExpect(status().isBadRequest)
        remove(stored.id, session, method = null).andExpect(status().isBadRequest)
        assertEquals(1, attachmentRepository.findAll().size)
    }

    // --- 임시 파일 정리 ---

    @Test
    fun `정리 기준 시각은 지금이 아니라 오늘 0시에서 보관 기간을 뺀 시각이다`() {
        // v1.6 JodaDateUtil.beforeByMillis는 LocalDate.now()(오늘 0시)에서 보관 기간을 뺀다.
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDateTime.of(2026, 10, 26, 20, 0).atZone(zone).toInstant().toEpochMilli()
        val user = newUser()
        fun at(h: Long, hash: String) = attachmentRepository.save(dev.yona.domain.misc.Attachment(name = hash, hash = hash, containerType = ResourceType.USER, containerId = user.id!!, createdDate = Date(java.time.LocalDateTime.of(2026, 10, 25, 0, 0).plusHours(h).atZone(zone).toInstant().toEpochMilli())))
        val yesterdayMorning = at(6, "t1")      // 어제 6시: 기준(어제 0시) 이후라 남긴다
        val dayBefore = attachmentRepository.save(dev.yona.domain.misc.Attachment(name = "t2", hash = "t2", containerType = ResourceType.USER, containerId = user.id!!, createdDate = Date(java.time.LocalDateTime.of(2026, 10, 24, 23, 0).atZone(zone).toInstant().toEpochMilli())))
        assertEquals(1, cleaner.cleanUp(now))
        val remaining = attachmentRepository.findAll().map { it.id }
        assertTrue(yesterdayMorning.id in remaining)
        assertTrue(dayBefore.id !in remaining)
    }

    @Test
    fun `정리 작업은 보관 기간이 지난 임시 첨부만 지우고 최근에 올린 것은 남긴다 (C154, C162)`() {
        // v1.6는 `createdDate >= (지금 - 보관 기간)`인 첨부를 지워서 오래된 것이 아니라 최근 것을 지웠다. 보관 기간이 지난 것만 지운다.
        val user = newUser()
        val recent = attachmentRepository.save(dev.yona.domain.misc.Attachment(name = "recent", hash = "h1", containerType = ResourceType.USER, containerId = user.id!!, createdDate = Date()))
        val old = attachmentRepository.save(dev.yona.domain.misc.Attachment(name = "old", hash = "h2", containerType = ResourceType.USER, containerId = user.id!!, createdDate = Date(System.currentTimeMillis() - 3 * 86_400_000L)))
        val project = attachmentRepository.save(dev.yona.domain.misc.Attachment(name = "proj", hash = "h3", containerType = ResourceType.PROJECT, containerId = 1, createdDate = Date()))
        assertEquals(1, cleaner.cleanUp())
        val remaining = attachmentRepository.findAll().map { it.id }
        assertTrue(recent.id in remaining)
        assertTrue(old.id !in remaining)
        assertTrue(project.id in remaining)
    }
}
