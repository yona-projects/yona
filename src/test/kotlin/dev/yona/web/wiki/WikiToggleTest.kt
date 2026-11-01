package dev.yona.web.wiki

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 프로젝트 설정의 위키 켜기와 끄기(로드맵 X-17, `decisions.md` D-61)를 검증한다. 끄면 메뉴, 화면, REST를 모두 막고 내용은 보존한다. 다시 켜면 그대로 돌아온다.
 * 새 프로젝트는 켜짐, 기존 프로젝트(컬럼 기본값)는 꺼짐이다.
 */
@AutoConfigureMockMvc
@Transactional
class WikiToggleTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun newProject(wiki: Boolean?): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "wto$n-$stamp", name = "wtp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true).also { if (wiki != null) it.wiki = wiki })
        return p
    }

    private fun setWiki(p: Project, on: Boolean) { menuSettings.save(menuSettings.findFirstByProjectId(p.id!!)!!.also { it.wiki = on }) }

    private fun member(p: Project): Pair<MockHttpSession, String> {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "wt$n-$stamp", name = "토글$n", email = "wt$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(RoleType.MEMBER.id).get()))
        dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, u, "tk$n$stamp")
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }
        return session to "tk$n$stamp"
    }

    private fun base(p: Project) = "/${p.owner}/${p.name}/wiki"

    private fun api(p: Project, path: String = "pages") = "/-_-api/v1/owners/${p.owner}/projects/${p.name}/wiki/$path"

    private fun hasWikiTab(p: Project, session: MockHttpSession): Boolean =
        Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
            .selectFirst(".project-menu-nav a[href=${base(p)}]") != null

    @Test
    fun `위키가 켜진 프로젝트만 메뉴에 위키 탭이 있다`() {
        val on = newProject(true); val off = newProject(false)
        assertEquals(true, hasWikiTab(on, member(on).first))
        assertEquals(false, hasWikiTab(off, member(off).first))
    }

    @Test
    fun `기존 프로젝트처럼 값이 없으면 꺼짐이다`() {
        val p = newProject(null)
        assertEquals(false, menuSettings.findFirstByProjectId(p.id!!)!!.wiki)
        assertEquals(false, hasWikiTab(p, member(p).first))
    }

    @Test
    fun `꺼진 위키는 화면을 모두 404로 막는다`() {
        val p = newProject(false); val (session, _) = member(p)
        for (path in listOf(base(p), "${base(p)}/Home", "${base(p)}/_new", "${base(p)}/Home/_edit", "${base(p)}/Home/_history", "${base(p)}/Home/_compare?commit=abc")) {
            mockMvc.perform(get(path).session(session)).andExpect(status().isNotFound)
        }
        mockMvc.perform(post(base(p)).session(session).param("title", "Home").param("content", "x")).andExpect(status().isNotFound)
        mockMvc.perform(post("${base(p)}/Home/_delete").session(session)).andExpect(status().isNotFound)
    }

    @Test
    fun `꺼진 위키는 REST도 404로 막는다`() {
        val p = newProject(false); val (_, token) = member(p)
        fun call(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
            mockMvc.perform(builder.header("Authorization", "token $token").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
        call(get(api(p))).andExpect(status().isNotFound).andExpect(jsonPath("$.message").value("wiki is disabled"))
        call(get(api(p, "pages/Home"))).andExpect(status().isNotFound)
        call(post(api(p)).content("""{"title":"Home","content":"x"}""")).andExpect(status().isNotFound)
        call(get(api(p, "pages/Home/history"))).andExpect(status().isNotFound)
        call(get(api(p, "pages/Home/diff?commit=abc"))).andExpect(status().isNotFound)
    }

    @Test
    fun `끄면 내용은 보존하고 다시 켜면 그대로 돌아온다`() {
        val p = newProject(true); val (session, _) = member(p)
        mockMvc.perform(post(base(p)).session(session).param("title", "Home").param("content", "보존할 내용")).andExpect(status().isSeeOther)
        setWiki(p, false)
        mockMvc.perform(get("${base(p)}/Home").session(session)).andExpect(status().isNotFound)
        setWiki(p, true)
        val page = Jsoup.parse(mockMvc.perform(get("${base(p)}/Home").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotNull(page.selectFirst(".wiki-body"))
        assertEquals(true, page.selectFirst(".wiki-body")!!.text().contains("보존할 내용"))
        assertNull(null)
    }
}
