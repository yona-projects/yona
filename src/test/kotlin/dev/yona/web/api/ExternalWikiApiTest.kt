package dev.yona.web.api

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
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 위키 REST(로드맵 X-17 W3, `decisions.md` D-60)를 검증한다: 목록과 검색, 한 건, 만들기, 고치기와 이름 바꾸기, 지우기, 이력, diff와 비교.
 * 읽기는 프로젝트를 읽을 수 있어야 하고 쓰기는 프로젝트 구성원이어야 한다. 범위는 `repo`나 공개 프로젝트의 `public_repo`이다.
 * 위키 저장소 디렉터리가 실행 사이에 남으므로 소유자 이름에 시각을 넣는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalWikiApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "wk$n-$stamp", name = "위키$n", email = "wk$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "wt$n$stamp"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "wko$n-$stamp", name = "wkp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true))
        return project
    }

    private fun roleOn(project: Project, user: User, role: RoleType) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun api(project: Project, path: String = "pages") = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/wiki/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun memberOf(project: Project): String { val u = newUser(); roleOn(project, u, RoleType.MEMBER); return tokenOf(u) }

    // --- 만들기와 읽기 ---

    @Test
    fun `페이지를 만들고 목록과 한 건으로 읽는다`() {
        val project = newProject(); val token = memberOf(project)
        json(get(api(project)), token, null).andExpect(status().isOk).andExpect(jsonPath("$.pages.length()").value(0))
        json(post(api(project)), token, """{"title":"Home","content":"# 안녕"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.page.name").value("Home")).andExpect(jsonPath("$.page.title").value("Home"))
        json(post(api(project)), token, """{"title":"My Page","content":"본문","message":"첫 글"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.page.name").value("My-Page")).andExpect(jsonPath("$.page.title").value("My Page"))
        json(get(api(project)), token, null).andExpect(jsonPath("$.pages.length()").value(2)).andExpect(jsonPath("$.pages[0].name").value("Home")).andExpect(jsonPath("$.pages[1].name").value("My-Page"))
        json(get(api(project, "pages/My-Page")), token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.page.content").value("본문")).andExpect(jsonPath("$.page.title").value("My Page"))
            .andExpect(jsonPath("$.page.lastCommit.message").value("첫 글")).andExpect(jsonPath("$.page.lastCommit.author.name").isNotEmpty)
    }

    @Test
    fun `한글 이름도 경로로 읽는다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"시작 하기","content":"x"}""").andExpect(status().isCreated).andExpect(jsonPath("$.page.name").value("시작-하기"))
        json(get(api(project, "pages/시작-하기")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.page.content").value("x"))
    }

    @Test
    fun `만들기의 오류`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"A","content":"1"}""").andExpect(status().isCreated)
        json(post(api(project)), token, """{"title":"A","content":"2"}""").andExpect(status().isConflict)
        json(post(api(project)), token, """{"title":"a/b","content":"2"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"title":"","content":"2"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"content":"2"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"title":5}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"title":"B","content":5}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"title":"B","message":5}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, null).andExpect(status().isBadRequest)
    }

    @Test
    fun `내용을 생략하면 빈 페이지이다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"Empty"}""").andExpect(status().isCreated)
        json(get(api(project, "pages/Empty")), token, null).andExpect(jsonPath("$.page.content").value(""))
    }

    @Test
    fun `없는 페이지는 404이다`() {
        val project = newProject(); val token = memberOf(project)
        json(get(api(project, "pages/Nope")), token, null).andExpect(status().isNotFound)
        json(patch(api(project, "pages/Nope")), token, """{"content":"x"}""").andExpect(status().isNotFound)
        json(delete(api(project, "pages/Nope")), token, null).andExpect(status().isNotFound)
        json(get(api(project, "pages/Nope/history")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.history.length()").value(0))
    }

    // --- 고치기와 이름 바꾸기와 지우기 ---

    @Test
    fun `내용을 고치고 이전 리비전을 읽는다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"Home","content":"v1"}""").andExpect(status().isCreated)
        val first = json(get(api(project, "pages/Home")), token, null).andReturn().response.contentAsString.let { Regex("\"id\":\"([0-9a-f]{40})\"").find(it)!!.groupValues[1] }
        json(patch(api(project, "pages/Home")), token, """{"content":"v2","message":"고침"}""").andExpect(status().isOk).andExpect(jsonPath("$.page.name").value("Home"))
        json(get(api(project, "pages/Home")), token, null).andExpect(jsonPath("$.page.content").value("v2"))
        json(get(api(project, "pages/Home?revision=$first")), token, null).andExpect(jsonPath("$.page.content").value("v1"))
        json(get(api(project, "pages/Home?revision=zzzz")), token, null).andExpect(status().isNotFound)
    }

    @Test
    fun `제목만 주면 이름만 바뀌고 내용은 그대로이다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"Old","content":"body"}""").andExpect(status().isCreated)
        json(patch(api(project, "pages/Old")), token, """{"title":"New Name"}""").andExpect(status().isOk).andExpect(jsonPath("$.page.name").value("New-Name"))
        json(get(api(project, "pages/Old")), token, null).andExpect(status().isNotFound)
        json(get(api(project, "pages/New-Name")), token, null).andExpect(jsonPath("$.page.content").value("body"))
    }

    @Test
    fun `고치기의 오류`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"A","content":"1"}""").andExpect(status().isCreated)
        json(post(api(project)), token, """{"title":"B","content":"2"}""").andExpect(status().isCreated)
        json(patch(api(project, "pages/A")), token, """{"title":"B"}""").andExpect(status().isConflict)
        json(patch(api(project, "pages/A")), token, """{"title":"a/b"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "pages/A")), token, """{"content":5}""").andExpect(status().isBadRequest)
        json(patch(api(project, "pages/A")), token, null).andExpect(status().isBadRequest)
    }

    @Test
    fun `지우면 204이고 메시지를 줄 수 있다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"A","content":"1"}""").andExpect(status().isCreated)
        json(delete(api(project, "pages/A?message=정리")), token, null).andExpect(status().isNoContent)
        json(get(api(project, "pages/A")), token, null).andExpect(status().isNotFound)
        json(get(api(project, "pages/A/history")), token, null).andExpect(jsonPath("$.history[0].message").value("정리"))
    }

    // --- 이력, diff, 비교, 검색 ---

    @Test
    fun `이력은 최신순이고 쪽으로 나눈다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"Home","content":"1"}""").andExpect(status().isCreated)
        (2..4).forEach { json(patch(api(project, "pages/Home")), token, """{"content":"$it","message":"v$it"}""").andExpect(status().isOk) }
        json(get(api(project, "pages/Home/history?size=2")), token, null).andExpect(jsonPath("$.history.length()").value(2)).andExpect(jsonPath("$.history[0].message").value("v4")).andExpect(jsonPath("$.history[0].id").isNotEmpty).andExpect(jsonPath("$.history[0].author.name").isNotEmpty).andExpect(jsonPath("$.history[0].date").isNotEmpty)
        json(get(api(project, "pages/Home/history?size=2&page=1")), token, null).andExpect(jsonPath("$.history.length()").value(2)).andExpect(jsonPath("$.history[1].message").value("Create Home"))
    }

    @Test
    fun `커밋 하나의 diff와 두 리비전의 비교`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"Home","content":"a\n"}""").andExpect(status().isCreated)
        json(patch(api(project, "pages/Home")), token, """{"content":"b\n"}""").andExpect(status().isOk)
        json(patch(api(project, "pages/Home")), token, """{"content":"c\n"}""").andExpect(status().isOk)
        val ids = Regex("\"id\":\"([0-9a-f]{40})\"").findAll(json(get(api(project, "pages/Home/history")), token, null).andReturn().response.contentAsString).map { it.groupValues[1] }.toList()
        json(get(api(project, "pages/Home/diff?commit=${ids[0]}")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.diff").value(org.hamcrest.Matchers.containsString("+c")))
        json(get(api(project, "pages/Home/diff?from=${ids[2]}&to=${ids[0]}")), token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.diff").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("-a"), org.hamcrest.Matchers.containsString("+c"))))
        json(get(api(project, "pages/Home/diff")), token, null).andExpect(status().isBadRequest)
        json(get(api(project, "pages/Home/diff?commit=${"0".repeat(40)}")), token, null).andExpect(status().isNotFound)
    }

    @Test
    fun `제목으로 검색한다`() {
        val project = newProject(); val token = memberOf(project)
        listOf("Install Guide", "Upgrade guide", "FAQ").forEach { json(post(api(project)), token, """{"title":"$it","content":"x"}""").andExpect(status().isCreated) }
        json(get(api(project, "pages?q=GUIDE")), token, null).andExpect(jsonPath("$.pages.length()").value(2)).andExpect(jsonPath("$.pages[0].title").value("Install Guide"))
    }

    @Test
    fun `특수 페이지는 목록에서 빼고 이름으로 읽는다`() {
        val project = newProject(); val token = memberOf(project)
        json(post(api(project)), token, """{"title":"_Sidebar","content":"menu"}""").andExpect(status().isCreated)
        json(get(api(project)), token, null).andExpect(jsonPath("$.pages.length()").value(0))
        json(get(api(project, "pages/_Sidebar")), token, null).andExpect(jsonPath("$.page.content").value("menu"))
    }

    // --- 권한과 범위 ---

    @Test
    fun `공개 프로젝트는 구성원이 아니어도 읽고 쓰지는 못한다`() {
        val project = newProject(); val member = memberOf(project); val outsider = tokenOf(newUser())
        json(post(api(project)), member, """{"title":"Home","content":"x"}""").andExpect(status().isCreated)
        json(get(api(project)), outsider, null).andExpect(status().isOk).andExpect(jsonPath("$.pages.length()").value(1))
        json(get(api(project, "pages/Home")), outsider, null).andExpect(status().isOk)
        json(get(api(project, "pages/Home/history")), outsider, null).andExpect(status().isOk)
        json(post(api(project)), outsider, """{"title":"X","content":"x"}""").andExpect(status().isForbidden)
        json(patch(api(project, "pages/Home")), outsider, """{"content":"y"}""").andExpect(status().isForbidden)
        json(delete(api(project, "pages/Home")), outsider, null).andExpect(status().isForbidden)
        json(get(api(project)), null, null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `비공개 프로젝트는 구성원만 읽는다`() {
        val project = newProject(ProjectScope.PRIVATE); val member = memberOf(project); val outsider = tokenOf(newUser())
        json(post(api(project)), member, """{"title":"Home","content":"x"}""").andExpect(status().isCreated)
        json(get(api(project)), member, null).andExpect(status().isOk)
        json(get(api(project)), outsider, null).andExpect(status().isForbidden)
        json(get(api(project, "pages/Home")), outsider, null).andExpect(status().isForbidden)
    }

    @Test
    fun `공개 프로젝트의 위키는 public_repo로 읽고 쓰고 비공개는 repo가 필요하다`() {
        val publicProject = newProject(); val secret = newProject(ProjectScope.PRIVATE)
        val limited = newUser(listOf("public_repo")); roleOn(publicProject, limited, RoleType.MEMBER); roleOn(secret, limited, RoleType.MEMBER)
        val other = newUser(listOf("read:org")); roleOn(publicProject, other, RoleType.MEMBER)
        json(post(api(publicProject)), tokenOf(limited), """{"title":"ok","content":"x"}""").andExpect(status().isCreated)
        json(get(api(secret)), tokenOf(limited), null).andExpect(status().isForbidden)
        json(get(api(publicProject)), tokenOf(other), null).andExpect(status().isForbidden)
    }

    @Test
    fun `이름이 점 wiki 인 프로젝트와 겹치는 위키는 쓸 수 없다`() {
        val project = newProject(); val token = memberOf(project)
        projects.save(Project(owner = project.owner, name = "${project.name}.wiki", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0, lastPostingNumber = 0))
        json(get(api(project)), token, null).andExpect(status().isOk).andExpect(jsonPath("$.pages.length()").value(0))
        json(post(api(project)), token, """{"title":"Home","content":"x"}""").andExpect(status().isConflict)
    }
}
