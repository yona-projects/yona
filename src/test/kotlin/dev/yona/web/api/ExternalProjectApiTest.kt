package dev.yona.web.api

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부용 프로젝트 API를 검증한다: 프로젝트 만들기, 라벨 만들기, 마일스톤 만들기, 내보내기, 구독자 목록, 상태 확인.
 *
 * 원본: `app/controllers/api/ProjectApi.java`, `MilestoneApi.java`, `WatcherApi.java`, `GlobalApi.java`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-external-project"])
class ExternalProjectApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    /** 구독자 목록 API는 토큰 전용이다(X-5, D-36). 시험용 토큰을 만든다. */
    private fun watchToken(): String = newUser("wt${++sequence}").token!!

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "xp$n", name = "xp$n", email = "xp$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), token = token))
        token?.let { dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, it) }
        return saved
    }

    private fun admin(): User = newUser("admin-tk-${++sequence}").also { siteAdmins.save(SiteAdmin(admin = it)) }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "xpo$n", name = "xpp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0, overview = "개요", createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun member(project: Project, role: RoleType = RoleType.MEMBER, token: String = "tk${++sequence}"): User =
        newUser(token).also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(role.id).get())) }

    private fun api(path: String) = "/-_-api/v1/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 상태 확인 ---

    @Test
    fun `hello는 살아 있다고 답한다`() {
        mockMvc.perform(get(api("hello"))).andExpect(status().isOk).andExpect(jsonPath("$.message").value("I'm alive!")).andExpect(jsonPath("$.ok").value(true))
    }

    // --- 프로젝트 만들기 ---

    @Test
    fun `사이트 관리자는 프로젝트를 만들고 구성원을 넣는다`() {
        val a = admin(); val manager = newUser(); val member = newUser()
        val body = """{"projectName":"imported","projectDescription":"가져온 프로젝트","projectVcs":"GIT","projectScope":"PUBLIC","projectCreatedDate":"2020-01-02 PM 03:04:05 +0900",
            "members":[{"email":"${manager.email}","role":"manager"},{"email":"${member.email}","role":"MEMBER"},{"email":"nobody@example.com","role":"member"},{"email":"${a.email}","role":"owner"}]}"""
        json(post(api("owners/xpnew/projects")), a.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("imported")).andExpect(jsonPath("$.owner").value("xpnew")).andExpect(jsonPath("$.overview").value("가져온 프로젝트")).andExpect(jsonPath("$.vcs").value("GIT")).andExpect(jsonPath("$.id").isNumber)
        entityManager.flush(); entityManager.clear()
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase("xpnew", "imported")!!
        assertEquals(ProjectScope.PUBLIC, project.projectScope); assertEquals("가져온 프로젝트", project.overview)
        // v1.6 `Project.create`가 만든 날짜를 지금으로 덮어쓴다(R-36). 전달한 `projectCreatedDate`는 쓰이지 않는다.
        assertTrue(kotlin.math.abs(System.currentTimeMillis() - project.createdDate!!.time) < 60_000)
        val roleOf = projectUsers.findAll().filter { it.project?.id == project.id }.associate { it.user!!.id to it.role!!.id }
        assertEquals(RoleType.SITEMANAGER.id, roleOf[1L]); assertEquals(RoleType.MANAGER.id, roleOf[manager.id]); assertEquals(RoleType.MEMBER.id, roleOf[member.id])
        assertNull(roleOf[a.id])
        val menu = menuSettings.findAll().single { it.project?.id == project.id }
        assertTrue(menu.code == true && menu.issue == true && menu.pullRequest == true && menu.review == true && menu.milestone == true && menu.board == true)
    }

    @Test
    fun `REST로 만든 프로젝트에는 SITEMANAGER 역할 행이 두 개 들어간다 (v1_6 결함 재현)`() {
        val a = admin()
        json(post(api("owners/xpnew3/projects")), a.token, """{"projectName":"twice"}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase("xpnew3", "twice")!!
        // v1.6 ProjectApi.newProject는 Project.create(안에서 assignRole)와 밖의 assignRole을 모두 부르고, findByIds가 SITEMANAGER 행을 제외해 매번 새 행이 들어간다 (R-82)
        val rows = (entityManager.createNativeQuery("select count(*) from project_user where project_id = :id and role_id = :role")
            .setParameter("id", project.id).setParameter("role", RoleType.SITEMANAGER.id).singleResult as Number).toInt()
        assertEquals(2, rows)
    }

    @Test
    fun `범위를 정하지 않으면 비공개로 만든다`() {
        val a = admin()
        json(post(api("owners/xpnew2/projects")), a.token, """{"projectName":"plain"}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase("xpnew2", "plain")!!
        assertEquals(ProjectScope.PRIVATE, project.projectScope); assertEquals("GIT", project.vcs); assertEquals("", project.overview)
    }

    @Test
    fun `프로젝트 만들기의 오류와 권한`() {
        val a = admin(); val plain = newUser("plain-tk")
        json(post(api("owners/xpx/projects")), plain.token, """{"projectName":"p"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("User creation with api is allowed by Site admin only."))
        mockMvc.perform(post(api("owners/xpx/projects")).header("Authorization", "token ${a.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
        val existing = newProject()
        json(post(api("owners/${existing.owner}/projects")), a.token, """{"projectName":"${existing.name}"}""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.reason").value("Conflict")).andExpect(jsonPath("$.project.name").value(existing.name))
        val org = organizations.save(Organization(name = "xporg${++sequence}", descr = "d", created = Date()))
        json(post(api("owners/${org.name}/projects")), a.token, """{"projectName":"o"}""").andExpect(status().isForbidden)
    }

    // --- 라벨 만들기 ---

    @Test
    fun `라벨과 분류를 만들고 이미 있는 라벨은 409로 알린다`() {
        val project = newProject(); val m = member(project)
        val body = """{"labels":[{"labelName":"bug","labelColor":"#f00","category":"Type","isExclusive":true},{"labelName":"bug","labelColor":"#f00","category":"Type","isExclusive":true},{"labelName":"ui","labelColor":"#0f0","category":"Area","isExclusive":false}]}"""
        json(post(api("owners/${project.owner}/projects/${project.name}/labels")), m.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].status").value(201)).andExpect(jsonPath("$[0].label").value("bug")).andExpect(jsonPath("$[0].category").value("Type")).andExpect(jsonPath("$[0].labelColor").value("#f00")).andExpect(jsonPath("$[0].isExclusive").value(false))
            .andExpect(jsonPath("$[1].status").value(409)).andExpect(jsonPath("$[1].reason").value("Conflict")).andExpect(jsonPath("$[1].user.labelName").value("bug")).andExpect(jsonPath("$[2].label").value("ui"))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf("bug", "ui"), labels.findAll().filter { it.project?.id == project.id }.map { it.name }.toSet())
        assertEquals(setOf("Type", "Area"), categories.findAll().filter { it.project?.id == project.id }.map { it.name }.toSet())
    }

    @Test
    fun `라벨 만들기의 오류와 권한`() {
        val project = newProject(); val m = member(project)
        val path = api("owners/${project.owner}/projects/${project.name}/labels")
        json(post(path), m.token, """{"nope":1}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No issues key exists or value wasn't array!"))
        mockMvc.perform(post(path).header("Authorization", "token ${m.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
        json(post(path), newUser("outsider-tk").token, """{"labels":[]}""").andExpect(status().isForbidden)
    }

    // --- 마일스톤 만들기 ---

    @Test
    fun `마일스톤을 만들고 같은 제목은 알린다`() {
        val project = newProject(); val m = member(project)
        val body = """{"milestones":[{"title":"v1","description":"설명","due_on":"2030-01-02T12:00:00Z","state":"closed"},{"title":"v1"},{"title":"v2"}]}"""
        json(post(api("owners/${project.owner}/projects/${project.name}/milestones")), m.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].title").value("v1")).andExpect(jsonPath("$[0].state").value("closed")).andExpect(jsonPath("$[0].description").value("설명")).andExpect(jsonPath("$[0].id").isNumber)
            .andExpect(jsonPath("$[0].due_on").value("2030-01-02T23:59:59Z"))
            .andExpect(jsonPath("$[1].message").isString).andExpect(jsonPath("$[1].milestone.title").value("v1"))
            .andExpect(jsonPath("$[2].title").value("v2")).andExpect(jsonPath("$[2].state").value("open"))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf("v1", "v2"), milestones.findAll().filter { it.project?.id == project.id }.map { it.title }.toSet())
    }

    @Test
    fun `마감일은 날짜만 있거나 오프셋이 없는 시각도 받는다`() {
        val project = newProject(); val m = member(project)
        // v1.6 MilestoneApi는 Joda `new DateTime(text)`로 읽어 하루의 마지막 초로 맞춘다. 날짜만, 오프셋 없는 시각, 오프셋 있는 시각을 모두 받는다.
        val body = """{"milestones":[{"title":"a","due_on":"2030-01-02"},{"title":"b","due_on":"2030-01-03T12:00:00"},{"title":"c","due_on":"2030-01-04T12:00:00+09:00"},{"title":"d","due_on":"엉터리"}]}"""
        json(post(api("owners/${project.owner}/projects/${project.name}/milestones")), m.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].due_on").value("2030-01-02T23:59:59Z"))
            .andExpect(jsonPath("$[1].due_on").value("2030-01-03T23:59:59Z"))
            .andExpect(jsonPath("$[2].due_on").exists())
            .andExpect(jsonPath("$[3].due_on").doesNotExist())
    }

    @Test
    fun `마일스톤 만들기의 오류와 권한`() {
        val project = newProject(); val m = member(project)
        val path = api("owners/${project.owner}/projects/${project.name}/milestones")
        json(post(path), m.token, """{"nope":1}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No milestones key exists or value wasn't array!"))
        json(post(path), newUser("outsider2-tk").token, """{"milestones":[]}""").andExpect(status().isForbidden)
    }

    // --- 내보내기 ---

    @Test
    fun `프로젝트를 내보낸다`() {
        val project = newProject(); val manager = member(project, RoleType.MANAGER, "mgr-tk")
        val category = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = true))
        labels.save(IssueLabel(project = project, category = category, name = "bug", color = "#f00"))
        milestones.save(Milestone(project = project, title = "v1", contents = "c", state = State.OPEN))
        issues.save(Issue(project = project, number = 1, title = "이슈", body = "본문", state = State.OPEN, authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        postings.save(Posting(project = project, number = 1, title = "글", body = "본문", authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, notice = false, readme = false, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        json(get(api("owners/${project.owner}/projects/${project.name}/exports")), manager.token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.owner").value(project.owner)).andExpect(jsonPath("$.projectName").value(project.name)).andExpect(jsonPath("$.projectDescription").value("개요"))
            .andExpect(jsonPath("$.projectVcs").value("GIT")).andExpect(jsonPath("$.projectScope").value("PUBLIC")).andExpect(jsonPath("$.projectCreatedDate").isString)
            .andExpect(jsonPath("$.memberCount").value(1)).andExpect(jsonPath("$.members[?(@.loginId == '${manager.loginId}')].role").value("manager"))
            .andExpect(jsonPath("$.issueCount").value(1)).andExpect(jsonPath("$.postCount").value(1)).andExpect(jsonPath("$.milestoneCount").value(1))
            .andExpect(jsonPath("$.labels[0].labelName").value("bug")).andExpect(jsonPath("$.labels[0].isExclusive").value(true))
            .andExpect(jsonPath("$.issues[0].title").value("이슈")).andExpect(jsonPath("$.posts[0].title").value("글")).andExpect(jsonPath("$.milestones[0].title").value("v1")).andExpect(jsonPath("$.milestones[0].dueDate").doesNotExist())
            .andExpect(jsonPath("$.authors[0].loginId").value(manager.loginId))
    }

    @Test
    fun `내보내기의 authors에는 풀 리퀘스트를 보낸 사용자도 들어간다`() {
        val project = newProject(); val manager = member(project, RoleType.MANAGER, "mgr-pr-tk"); val contributor = newUser()
        // v1.6 Project.findAuthors = 이슈 작성자 + 글 작성자 + 이 프로젝트로 보낸 풀 리퀘스트의 보낸 사람
        pullRequests.save(PullRequest(toProject = project, fromProject = newProject(), number = 1, state = State.OPEN, contributor = contributor))
        json(get(api("owners/${project.owner}/projects/${project.name}/exports")), manager.token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.authors.length()").value(1)).andExpect(jsonPath("$.authors[0].loginId").value(contributor.loginId))
    }

    @Test
    fun `내보내기는 프로젝트를 지울 수 있는 사용자만 한다`() {
        val project = newProject(); val m = member(project)
        json(get(api("owners/${project.owner}/projects/${project.name}/exports")), m.token, null).andExpect(status().isForbidden)
    }

    // --- 구독자 목록 ---

    @Test
    fun `이슈와 글의 구독자를 돌려준다`() {
        val project = newProject(); val author = member(project); val watcher = member(project)
        val issue = issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        watches.save(Watch(user = watcher, resourceType = ResourceType.ISSUE_POST, resourceId = issue.id.toString()))
        mockMvc.perform(get(api("owners/${project.owner}/projects/${project.name}/posts/1/watchers")).header("Authorization", "token ${watchToken()}").param("type", "issues")).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalWatchers").value(2)).andExpect(jsonPath("$.watchersInList").value(2))
            .andExpect(jsonPath("$.watchers[?(@.name == '${watcher.name}')].url").value("/${watcher.loginId}"))
        mockMvc.perform(get(api("owners/${project.owner}/projects/${project.name}/posts/1/watchers")).header("Authorization", "token ${watchToken()}").param("type", "posts")).andExpect(status().isNotFound)
        mockMvc.perform(get(api("owners/${project.owner}/projects/${project.name}/posts/1/watchers")).header("Authorization", "token ${watchToken()}")).andExpect(status().isOk).andExpect(content().string(""))
    }

    @Test
    fun `이슈의 구독자에는 담당자와 추천자가 기본으로 들어간다`() {
        val project = newProject(); val author = member(project); val assigneeUser = member(project); val voter = member(project)
        val assignee = Assignee(user = assigneeUser, project = project).also { entityManager.persist(it) }
        issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date(), assignee = assignee, voters = mutableSetOf(voter)))
        entityManager.flush(); entityManager.clear()
        // v1.6 Issue.getWatchers는 담당자와 추천자를 기본 구독자로 두고 작성자를 더한다.
        mockMvc.perform(get(api("owners/${project.owner}/projects/${project.name}/posts/1/watchers")).header("Authorization", "token ${watchToken()}").param("type", "issues")).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalWatchers").value(3))
    }

    @Test
    fun `비공개 프로젝트의 구독자는 구성원이 아니면 볼 수 없다`() {
        val project = newProject(ProjectScope.PRIVATE); val m = member(project)
        issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = m.id, authorLoginId = m.loginId, authorName = m.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        val denied = mockMvc.perform(get(api("owners/${project.owner}/projects/${project.name}/posts/1/watchers")).header("Authorization", "token ${watchToken()}").param("type", "issues")).andReturn().response.status
        assertTrue(denied in listOf(302, 303, 403), denied.toString())
        assertNotNull(m.id)
    }
}
