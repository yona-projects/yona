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
        val doc = page(base(p) + "/issues", s)
        val header = doc.selectFirst(".shell-main > .project-header-outer")
        assertNotNull(header, "머리는 사이드바 오른쪽 본문(.shell-main)의 첫 요소이다(A-485)")
        assertTrue(doc.select(".shell-main").first()!!.child(0) === header || doc.selectFirst(".shell-main > span[data-swap-safe]") != null, "본문의 첫 자리에 있다")
        assertNull(doc.selectFirst("#main > .project-header-outer"), "본문 밖(사이드바 위 전체 폭)에는 머리가 없다")
        assertTrue(header.attr("style").isBlank(), "머리에 인라인 배경 이미지가 없어야 한다: ${header.attr("style")}")
        assertNotNull(header.selectFirst(".project-header-avatar img"))
        assertEquals(p.name, header.selectFirst(".project-name a")!!.text())
        assertEquals(p.owner, header.selectFirst(".project-author a")!!.text())
    }

    @Test
    fun `올린 이미지가 없으면 프로젝트 홈에도 표지가 없다(기본 벽돌 이미지는 쓰지 않는다)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val home = page(base(p), s)
        assertNull(home.selectFirst(".project-cover"))
        assertTrue(!home.html().contains("project_default.jpg"), "기본 벽돌 배경 이미지를 더는 불러오지 않는다")
    }

    @Test
    fun `올린 이미지가 있으면 프로젝트 홈 본문 맨 위에만 표지로 보인다`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val file = attachments.save(Attachment(containerType = ResourceType.PROJECT, containerId = p.id!!, name = "cover.png"))
        val home = page(base(p), s)
        val cover = home.selectFirst(".shell-main > .project-cover")
        assertNotNull(cover, "표지는 사이드바 오른쪽 본문(.shell-main)의 첫 요소이다")
        assertTrue(cover.attr("style").contains("/files/${file.id}"))
        assertEquals("/files/${file.id}", home.selectFirst(".project-header-avatar img")!!.attr("src"), "로고와 배경은 같은 첨부이다")
        // 홈이 아닌 화면에는 표지가 없다
        listOf("/issues", "/posts", "/milestones").forEach { sub -> assertNull(page(base(p) + sub, s).selectFirst(".project-cover"), sub) }
    }

    @Test
    fun `머리는 프로젝트의 모든 화면 본문 맨 위에 있고 표지는 홈에서 머리 바로 아래에 있다`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        attachments.save(Attachment(containerType = ResourceType.PROJECT, containerId = p.id!!, name = "cover.png"))
        listOf("", "/issues", "/posts", "/milestones", "/pullRequests", "/reviews", "/members", "/wiki").forEach { sub ->
            val doc = runCatching { page(base(p) + sub, s) }.getOrNull() ?: return@forEach
            assertNotNull(doc.selectFirst(".shell-main > .project-header-outer"), "$sub 에 머리가 본문 안에 있어야 한다")
        }
        val home = page(base(p), s)
        val children = home.selectFirst(".shell-main")!!.children().filter { it.tagName() != "span" && it.tagName() != "script" }
        assertTrue(children[0].hasClass("project-header-outer"), "본문의 첫 요소는 머리이다: ${children[0].className()}")
        assertTrue(children[1].hasClass("project-cover"), "표지는 머리 바로 아래이다: ${children[1].className()}")
    }
}
