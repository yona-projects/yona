package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentService
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertTrue

/**
 * 첨부 파일 내려받기(`GET /files/:id`)를 검증한다.
 *
 * 원본: `app/controllers/AttachmentApp.java:131-172`, `app/models/Attachment.java:372-440` (권한 대상),
 * `app/utils/HttpUtil.java:64-70` (파일 이름 인코딩).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-files"])
class AttachmentControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var attachments: AttachmentService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0
    private val work = File("build/tmp/it-files-work").apply { mkdirs() }

    @AfterEach
    fun clean() {
        File("build/tmp/it-files").deleteRecursively()
        work.deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "fl$n", name = "fl$n", email = "fl$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC) =
        projects.save(Project(owner = "fo${++sequence}", name = "fp$sequence", projectScope = scope, vcs = "GIT"))

    private fun attach(type: ResourceType, containerId: Long, name: String = "사진.png", content: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)): Attachment {
        val file = File(work, "f${++sequence}").apply { writeBytes(content) }
        return attachments.store(file, name, type, containerId, "tester").attachment
    }

    @Test
    fun `없는 파일은 404이다`() {
        mockMvc.perform(get("/files/999999")).andExpect(status().isNotFound).andExpect(content().string("The file does not exist."))
    }

    @Test
    fun `공개 프로젝트의 파일은 익명도 내려받는다`() {
        val project = project()
        val attachment = attach(ResourceType.PROJECT, project.id!!)
        mockMvc.perform(get("/files/${attachment.id}"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "private, max-age=3600"))
            .andExpect(header().string("ETag", "\"${attachment.hash}-inline\""))
            .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''%EC%82%AC%EC%A7%84.png"))
    }

    @Test
    fun `파일 내용을 그대로 돌려준다`() {
        val project = project()
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7)
        val attachment = attach(ResourceType.PROJECT, project.id!!, content = bytes)
        val body = mockMvc.perform(get("/files/${attachment.id}")).andReturn().response.contentAsByteArray
        assertTrue(bytes.contentEquals(body))
    }

    @Test
    fun `action이 download이면 첨부 형식으로 내려준다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!, "a b.png")
        mockMvc.perform(get("/files/${attachment.id}?action=download"))
            .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''a%20b.png"))
            .andExpect(header().string("ETag", "\"${attachment.hash}-attachment\""))
    }

    @Test
    fun `파일 이름의 위험한 문자는 밑줄로 바꾼다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!, "a:b/c{d?.png")
        mockMvc.perform(get("/files/${attachment.id}"))
            .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''a_b_c_d_.png"))
    }

    @Test
    fun `ETag가 같으면 304이다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!)
        mockMvc.perform(get("/files/${attachment.id}").header("If-None-Match", "\"${attachment.hash}-inline\""))
            .andExpect(status().isNotModified)
            .andExpect(header().string("ETag", "\"${attachment.hash}-inline\""))
            .andExpect(header().string("Cache-Control", "private, max-age=3600"))
    }

    @Test
    fun `끝의 슬래시가 있어도 같은 주소이다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!)
        mockMvc.perform(get("/files/${attachment.id}/")).andExpect(status().isOk)
    }

    @Test
    fun `비공개 프로젝트의 파일은 구성원이 아니면 403이다`() {
        val attachment = attach(ResourceType.PROJECT, project(ProjectScope.PRIVATE).id!!)
        mockMvc.perform(get("/files/${attachment.id}")).andExpect(status().isForbidden)
            .andExpect(content().string("You have no permission to get the file."))
        mockMvc.perform(get("/files/${attachment.id}").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `비공개 프로젝트의 파일도 구성원은 내려받는다`() {
        val project = project(ProjectScope.PRIVATE)
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        val attachment = attach(ResourceType.PROJECT, project.id!!)
        mockMvc.perform(get("/files/${attachment.id}").session(login(member))).andExpect(status().isOk)
    }

    @Test
    fun `임시 첨부는 올린 사용자만 내려받는다`() {
        val owner = newUser()
        val attachment = attach(ResourceType.USER, owner.id!!)
        mockMvc.perform(get("/files/${attachment.id}").session(login(owner))).andExpect(status().isOk)
        mockMvc.perform(get("/files/${attachment.id}").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `아바타 첨부는 누구나 내려받는다`() {
        val owner = newUser()
        val attachment = attach(ResourceType.USER_AVATAR, owner.id!!)
        mockMvc.perform(get("/files/${attachment.id}")).andExpect(status().isOk)
    }

    @Test
    fun `저장소에서 파일이 사라졌으면 500이다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!)
        File("build/tmp/it-files/uploads/${attachment.hash}").delete()
        mockMvc.perform(get("/files/${attachment.id}")).andExpect(status().isInternalServerError)
            .andExpect(content().string("The file does not exist"))
    }

    @Test
    fun `RANGE 요청 헤더가 있으면 범위 지원 표시를 한다`() {
        val attachment = attach(ResourceType.PROJECT, project().id!!)
        mockMvc.perform(get("/files/${attachment.id}").header("RANGE", "bytes=0-1"))
            .andExpect(status().isOk)
            .andExpect(header().string("Accept-Ranges", "bytes"))
    }
}
