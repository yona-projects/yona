package dev.yona.web.shell

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 머리를 GitHub, GitLab처럼 얇은 한 줄로 바꾼 것을 검증한다(A-483). 머리에는 배경 이미지를 깔지 않고,
 * 프로젝트에 올린 이미지(로고와 배경은 같은 첨부이다, v1.6)는 **프로젝트 홈의 본문 맨 위 표지**로만 보인다. 올린 이미지가 없으면 표지가 없다.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectHeaderShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var attachments: AttachmentRepository

    private var sequence = 0

    private fun newUser(): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = ++sequence
        return users.save(User(loginId = "hdr$n", name = "hdr$n", email = "hdr$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User): Project {
        val project = projects.save(Project(owner = owner.loginId, name = "hdrproj${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        return project
    }

    private fun page(path: String, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun base(p: Project) = "/${p.owner}/${p.name}"

    @Test
    fun `머리는 배경 이미지를 깔지 않는 얇은 한 줄이고 아바타, 이름, 동작 단추를 가진다`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val header = page(base(p) + "/issues", s).selectFirst(".project-header-outer")
        assertNotNull(header)
        assertTrue(header.attr("style").isBlank(), "머리에 인라인 배경 이미지가 없어야 한다: ${header.attr("style")}")
        assertNotNull(header.selectFirst(".project-header-avatar img"))
        assertEquals(p.name, header.selectFirst(".project-name a")!!.text())
        assertEquals(p.owner, header.selectFirst(".project-author a")!!.text())
    }

    @Test
    fun `올린 이미지가 없으면 프로젝트 홈 표지는 원래 요나 기본 배경 이미지이고 로고는 기본 로고이다(A-486)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val home = page(base(p), s)
        assertNull(home.selectFirst(".project-cover"), "본문 표지는 없앴다(머리 배경으로 대신한다, A-512)")
        assertEquals("/assets/images/project_default_logo.png", home.selectFirst(".project-header-avatar img")!!.attr("src"))
        val banner = home.selectFirst(".project-header-outer")!!
        assertTrue(banner.hasClass("has-banner") && banner.attr("style").contains("/assets/images/project_default.jpg"), "프로젝트 홈의 머리는 배경 이미지를 깐다: ${banner.className()} ${banner.attr("style")}")
    }

    @Test
    fun `올린 이미지가 있으면 프로젝트 홈 본문 맨 위에만 표지로 보인다`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val file = attachments.save(Attachment(containerType = ResourceType.PROJECT, containerId = p.id!!, name = "cover.png"))
        val home = page(base(p), s)
        assertNull(home.selectFirst(".project-cover"), "본문 표지는 없앴다(머리 배경으로 대신한다, A-512)")
        assertEquals("/files/${file.id}", home.selectFirst(".project-header-avatar img")!!.attr("src"), "로고와 배경은 같은 첨부이다")
        assertTrue(home.selectFirst(".project-header-outer.has-banner")!!.attr("style").contains("/files/${file.id}"), "머리 배경도 올린 이미지이다")
        listOf("/issues", "/posts", "/milestones").forEach { sub -> assertNull(page(base(p) + sub, s).selectFirst(".project-header-outer.has-banner"), "$sub 머리에는 배경이 없다") }
    }
}
