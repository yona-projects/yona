package dev.yona.web.error

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 프로젝트 화면 안에서 보여 주는 오류 화면을 검증한다.
 * 원본: `app/utils/ErrorViews.java`(프로젝트 변형), `app/views/error/{forbidden,notfound,badrequest}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectErrorPagesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var users: UserRepository

    private fun project(): Project {
        val project = projects.save(Project(owner = "errowner", name = "errproj", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun render(path: String, expectedStatus: Int, session: MockHttpSession? = null): Document {
        val request = get(path)
        session?.let { request.session(it) }
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().`is`(expectedStatus)).andReturn().response.contentAsString)
    }

    private fun loggedIn(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "erruser", email = "erruser@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        return MockHttpSession().also {
            mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "erruser").param("password", "1234"))
        }
    }

    @Test
    fun `프로젝트 안의 403 화면은 프로젝트 레이아웃과 메뉴 안에서 안내한다`() {
        val project = project()
        val doc = render("/__test/err/project-forbidden/${project.id}", 403)
        assertEquals("You are not authorized - errowner/errproj", doc.title())
        assertNotNull(doc.selectFirst("header.gnb-outer.project-header"))
        assertNotNull(doc.selectFirst(".project-header-outer"))
        assertNotNull(doc.selectFirst(".project-menu-outer"))
        val box = doc.selectFirst(".error-wrap")
        assertNotNull(box?.selectFirst("i.ico.ico-err2"))
        assertEquals("You are not authorized", box?.selectFirst("p")?.text())
    }

    @Test
    fun `익명 사용자의 403 화면에는 로그인 버튼이 나온다`() {
        val project = project()
        val login = render("/__test/err/project-forbidden/${project.id}", 403).selectFirst(".error-wrap a.ybtn-primary")
        assertNotNull(login)
        assertEquals("/users/loginform?redirectUrl=/__test/err/project-forbidden/${project.id}", login.attr("href"))
        assertEquals("required", login.attr("data-login"))
        assertEquals("Log in", login.text())
    }

    @Test
    fun `로그인한 사용자의 403 화면에는 로그인 버튼이 없다`() {
        val project = project()
        assertNull(render("/__test/err/project-forbidden/${project.id}", 403, loggedIn()).selectFirst(".error-wrap a.ybtn-primary"))
    }

    @Test
    fun `프로젝트 안의 404 화면은 종류에 맞는 문구와 목록 링크를 보여 준다`() {
        val project = project()
        val doc = render("/__test/err/project-notfound/${project.id}?type=issue_post", 404)
        assertEquals("Issue does not exist", doc.selectFirst(".error-wrap p")?.text())
        val back = doc.selectFirst(".error-wrap a.ybtn-primary")
        assertEquals("/errowner/errproj/issues?state=all", back?.attr("href"))
        assertEquals("List", back?.text())
        assertNotNull(doc.selectFirst(".project-menu-nav li.active .menu-name"))
        assertEquals("Issue", doc.selectFirst(".project-menu-nav li.active .menu-name")?.text())
    }

    @Test
    fun `종류가 없으면 일반 404 문구와 뒤로 가기 링크를 쓴다`() {
        val project = project()
        val doc = render("/__test/err/project-notfound/${project.id}", 404)
        assertEquals("Page not found", doc.selectFirst(".error-wrap p")?.text())
        assertEquals("javascript:history.back();", doc.selectFirst(".error-wrap a.ybtn-primary")?.attr("href"))
    }

    @Test
    fun `모르는 종류이면 문구 자리에 메시지 키가 그대로 보인다 (v1_6 Messages는 없는 키를 그대로 돌려준다)`() {
        val project = project()
        val doc = render("/__test/err/project-notfound/${project.id}?type=/errowner/errproj/commit/abc/comments", 404)
        assertEquals("error.notfound./errowner/errproj/commit/abc/comments", doc.selectFirst(".error-wrap p")?.text())
        assertEquals("javascript:history.back();", doc.selectFirst(".error-wrap a.ybtn-primary")?.attr("href"))
    }

    @Test
    fun `게시글과 마일스톤과 코드 종류도 각자의 목록으로 돌아간다`() {
        val project = project()
        val links = listOf("board_post", "milestone", "code").associateWith {
            render("/__test/err/project-notfound/${project.id}?type=$it", 404).selectFirst(".error-wrap a.ybtn-primary")?.attr("href")
        }
        assertEquals("/errowner/errproj/posts", links["board_post"])
        assertEquals("/errowner/errproj/milestones", links["milestone"])
        assertEquals("/errowner/errproj/settingform", links["code"])
    }

    @Test
    fun `프로젝트 안의 400 화면은 메시지만 보여 준다`() {
        val project = project()
        val doc = render("/__test/err/project-badrequest/${project.id}", 400)
        assertEquals("The request cannot be fulfilled due to bad syntax", doc.selectFirst(".error-wrap p")?.text())
        assertNotNull(doc.selectFirst(".project-menu-outer"))
        assertNull(doc.selectFirst(".error-wrap a"))
    }
}
