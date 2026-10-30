package dev.yona.web.pullrequest

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.PushedBranch
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.pullrequest.ForkService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 만들기(`newPullRequestForm`, `mergeResult`, `newPullRequest`)와 그 뒤의 병합 검사를 검증한다.
 *
 * 원본: `app/controllers/PullRequestApp.java`, `app/views/git/create.scala.html`, `partial_merge_result.scala.html`,
 * `app/actors/PullRequestActor.java`, `app/models/PullRequest.java`, `PullRequestMergeResult.java`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestCreateTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var pullRequestCommits: PullRequestCommitRepository
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository
    @Autowired private lateinit var pushedBranches: PushedBranchRepository

    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pc$n", name = "이름pc$n", email = "pc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 첫 커밋이 하나 있는 원본과 그것을 포크한 프로젝트, 포크의 주인을 만든다. */
    private class Setup(val origin: Project, val fork: Project, val owner: User)

    private fun setup(): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "pco$n", name = "pcp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val owner = user()
        val fork = forks.fork(origin, owner.loginId!!, "pcf$n", ProjectScope.PUBLIC, owner)
        return Setup(origin, fork, owner)
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun send(s: Setup, session: MockHttpSession, title: String = "요청 제목", body: String = "요청 본문", fromBranch: String = "refs/heads/master") =
        post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(session)
            .param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
            .param("fromBranch", fromBranch).param("toBranch", "refs/heads/master").param("title", title).param("body", body)

    @Test
    fun `폼은 로그인이 필요하고 게스트는 403이다`() {
        val s = setup()
        mockMvc.perform(get("/${s.fork.owner}/${s.fork.name}/newPullRequestForm")).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/newPullRequestForm").session(login(user()))).andExpect(status().isForbidden)
    }

    @Test
    fun `게스트 오류 화면은 프로젝트 홈 메뉴가 활성이다 (v1_6 BadRequest 오버로드는 PROJECT_HOME)`() {
        val s = setup()
        val doc = org.jsoup.Jsoup.parse(mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/newPullRequestForm").session(login(user()))).andExpect(status().isForbidden).andReturn().response.contentAsString)
        val active = doc.select(".project-menu-nav li.active")
        assertEquals(1, active.size)
        assertEquals("/${s.origin.owner}/${s.origin.name}", active.single().selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `포크에서 열면 보내는 곳은 포크, 받는 곳은 원본이고 받는 브랜치는 현재 프로젝트의 기본 브랜치이다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("기능", mapOf("b.txt" to "b".toByteArray())), branch = "feature")
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequestForm", login(s.owner))
        assertEquals(s.fork.id.toString(), doc.selectFirst("#fromProjectId option[selected]")!!.attr("value"))
        assertEquals(listOf("", s.fork.id.toString()), doc.select("#fromProjectId option").map { it.attr("value") })
        assertEquals(s.origin.id.toString(), doc.selectFirst("#toProjectId option[selected]")!!.attr("value"))
        assertEquals(listOf("", s.fork.id.toString(), s.origin.id.toString()), doc.select("#toProjectId option").map { it.attr("value") })
        assertEquals("refs/heads/feature", doc.select("#fromBranch option").map { it.attr("value") }.filter { it.isNotEmpty() }.first())
        assertEquals("refs/heads/feature", doc.selectFirst("#fromBranch option[selected]")!!.attr("value"))
        assertEquals("refs/heads/master", doc.selectFirst("#toBranch option[selected]")!!.attr("value"))
        assertEquals("OPEN", doc.selectFirst("#pullRequestState")!!.attr("data-value"))
        assertEquals("/${s.fork.owner}/${s.fork.name}/pullRequests", doc.selectFirst("form[enctype]")!!.attr("action"))
    }

    @Test
    fun `보내는 곳과 받는 곳의 브랜치 목록에는 태그와 원격 참조가 없다 (D-30)`() {
        val s = setup()
        GitTestSupport.createRef(repositories, s.origin, "refs/tags/v1"); GitTestSupport.createRef(repositories, s.fork, "refs/tags/v1")
        GitTestSupport.createRef(repositories, s.fork, "refs/remotes/origin/x")
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequestForm", login(s.owner))
        listOf("#fromBranch", "#toBranch").forEach { select ->
            val values = doc.select("$select option").map { it.attr("value") }.filter { it.isNotEmpty() }
            assertTrue(values.isNotEmpty() && values.all { it.startsWith("refs/heads/") }, "$select: $values")
        }
    }

    @Test
    fun `쿼리로 브랜치를 고를 수 있다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("기능", mapOf("b.txt" to "b".toByteArray())), branch = "feature")
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequestForm?fromBranch=refs/heads/master&toBranch=refs/heads/master", login(s.owner))
        assertEquals("refs/heads/master", doc.selectFirst("#fromBranch option[selected]")!!.attr("value"))
    }

    @Test
    fun `보내는 저장소가 비어 있으면 400이다`() {
        val s = setup()
        val empty = projects.save(Project(owner = s.owner.loginId, name = "empty${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = empty, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(empty)
        projectUsers.save(ProjectUser(user = s.owner, project = empty, role = roles.findById(RoleType.MANAGER.id).get()))
        mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/newPullRequestForm").param("fromProjectId", empty.id.toString()).session(login(s.owner)))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/${empty.owner}/${empty.name}/newPullRequestForm").session(login(s.owner))).andExpect(status().isBadRequest)
    }

    @Test
    fun `병합 결과는 커밋 표와 제목, 본문 제안과 충돌 여부를 담는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("한 줄 요약\n\n자세한 설명\n둘째 줄", mapOf("b.txt" to "b".toByteArray())))
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequest/mergeResult?fromProjectId=${s.fork.id}&toProjectId=${s.origin.id}&fromBranch=refs/heads/master&toBranch=refs/heads/master", login(s.owner))
        val result = doc.selectFirst("#mergeResult")!!
        assertEquals("1", result.attr("data-commits")); assertEquals("false", result.attr("data-conflict"))
        assertEquals("한 줄 요약", result.attr("data-pullrequest-title")); assertEquals("자세한 설명\n둘째 줄", result.attr("data-pullrequest-body"))
        assertEquals("한 줄 요약", result.selectFirst("tbody tr a.commitMsg.short")!!.text())
        assertTrue(result.selectFirst("tbody tr td.commit-id a")!!.attr("href").startsWith("/${s.fork.owner}/${s.fork.name}/commit/"))
    }

    @Test
    fun `커밋이 여럿이면 제목은 비우고 본문에 첫 줄들을 나열하며 변경이 없으면 안내한다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("첫째", mapOf("b.txt" to "b".toByteArray())), TestCommit("둘째\n상세", mapOf("c.txt" to "c".toByteArray())))
        val base = "/${s.fork.owner}/${s.fork.name}/newPullRequest/mergeResult?fromProjectId=${s.fork.id}&toProjectId=${s.origin.id}&fromBranch=refs/heads/master&toBranch=refs/heads/master"
        val doc = page(base, login(s.owner))
        assertTrue(doc.selectFirst("#mergeResult")!!.hasAttr("data-pullrequest-title"), "비어 있어도 속성은 남는다 (v1.6)")
        assertEquals("", doc.selectFirst("#mergeResult")!!.attr("data-pullrequest-title"))
        assertEquals("둘째\n첫째", doc.selectFirst("#mergeResult")!!.attr("data-pullrequest-body"))
        val none = page("/${s.fork.owner}/${s.fork.name}/newPullRequest/mergeResult?fromProjectId=${s.origin.id}&toProjectId=${s.origin.id}&fromBranch=refs/heads/master&toBranch=refs/heads/master", login(s.owner))
        assertTrue(none.selectFirst("#mergeResult")!!.hasAttr("data-pullrequest-body"), "본문 속성도 비어 있어도 남는다")
        assertNotNull(none.selectFirst("#mergeResult h5")); assertNull(none.selectFirst("#mergeResult")!!.attr("data-conflict").takeIf { it.isNotEmpty() })
    }

    @Test
    fun `충돌하는 변경은 data-conflict가 true이다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequest/mergeResult?fromProjectId=${s.fork.id}&toProjectId=${s.origin.id}&fromBranch=refs/heads/master&toBranch=refs/heads/master", login(s.owner))
        assertEquals("true", doc.selectFirst("#mergeResult")!!.attr("data-conflict"))
    }

    @Test
    fun `만들면 번호를 받고 병합 검사로 커밋과 병합 기준이 저장되며 이벤트와 알림이 남는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        val mentioned = user()
        pushedBranches.save(PushedBranch(pushedDate = Date(), name = "refs/heads/master", project = s.fork))
        mockMvc.perform(send(s, login(s.owner), body = "본문 @${mentioned.loginId}")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${s.origin.owner}/${s.origin.name}/pullRequest/1"))

        val pr = pullRequests.findByToProjectIdAndNumber(s.origin.id!!, 1)!!
        assertEquals("요청 제목", pr.title); assertEquals(State.OPEN, pr.state); assertEquals(s.owner.id, pr.contributor?.id)
        assertEquals(false, pr.isMerging); assertEquals(false, pr.isConflict); assertNotNull(pr.created); assertNull(pr.updated)
        assertNotNull(pr.mergedCommitIdFrom); assertNotNull(pr.mergedCommitIdTo)

        val commits = pullRequestCommits.findByPullRequestId(pr.id!!)
        assertEquals(listOf("새 파일"), commits.map { it.commitMessage?.trim() }); assertEquals(PullRequestCommitState.CURRENT, commits.single().state)
        assertEquals(listOf(EventType.NEW_PULL_REQUEST, EventType.PULL_REQUEST_COMMIT_CHANGED), pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).map { it.eventType })
        assertEquals(commits.single().id.toString(), pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).last().newValue)
        assertNull(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).last().oldValue)

        repositories.openRepository(s.origin).use { assertNotNull(it.resolve("refs/yobi/pull/${pr.id}/merged")) }
        assertNull(pushedBranches.findFirstByProjectIdAndName(s.fork.id!!, "refs/heads/master"))
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_PULL_REQUEST }
        assertEquals("[${s.origin.name}] 요청 제목 (#1)", note.title); assertEquals(pr.id.toString(), note.resourceId)
        assertTrue(note.receivers.any { it.id == mentioned.id }); assertTrue(note.receivers.none { it.id == s.owner.id })
    }

    @Test
    fun `같은 조건의 열린 요청이 있으면 새로 만들지 않고 그 요청으로 이동한다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        val session = login(s.owner)
        mockMvc.perform(send(s, session)).andExpect(status().isSeeOther)
        mockMvc.perform(send(s, session, title = "다른 제목")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${s.origin.owner}/${s.origin.name}/pullRequest/1"))
        assertEquals(1, pullRequests.count())
    }

    @Test
    fun `충돌하는 요청은 충돌 표시와 충돌 이벤트가 남는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val mentioned = user()
        mockMvc.perform(send(s, login(s.owner), body = "@${mentioned.loginId}")).andExpect(status().isSeeOther)
        val pr = pullRequests.findAll().single()
        assertEquals(true, pr.isConflict); assertNull(pr.mergedCommitIdTo); assertEquals(false, pr.isMerging)
        val timeline = pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!)
        assertEquals(listOf(EventType.NEW_PULL_REQUEST, EventType.PULL_REQUEST_COMMIT_CHANGED, EventType.PULL_REQUEST_MERGED), timeline.map { it.eventType })
        assertEquals("conflict", timeline.last().newValue)
        assertTrue(notificationEvents.findAll().any { it.eventType == EventType.PULL_REQUEST_MERGED && it.newValue == "conflict" })
    }

    @Test
    fun `가져올 커밋이 없으면 병합됨으로 바뀌고 v1_6처럼 이벤트는 쌓이지 않는다`() {
        val s = setup()
        mockMvc.perform(send(s, login(s.owner))).andExpect(status().isSeeOther)
        val pr = pullRequests.findAll().single()
        assertEquals(State.MERGED, pr.state); assertEquals(s.owner.id, pr.receiver?.id); assertEquals(false, pr.isConflict)
        assertEquals(listOf(EventType.NEW_PULL_REQUEST), pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).map { it.eventType })
    }

    @Test
    fun `제목과 본문의 번호가 가리키는 이슈에 언급 이벤트가 남는다`() {
        val s = setup()
        val issue = issues.save(Issue(project = s.origin, number = 7, title = "이슈"))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        mockMvc.perform(send(s, login(s.owner), title = "고침 #7", body = "그리고 #99")).andExpect(status().isSeeOther)
        val events = issueEvents.findByIssueIdOrderByCreatedAscIdAsc(issue.id!!)
        assertEquals(listOf(EventType.ISSUE_REFERRED_FROM_PULL_REQUEST), events.map { it.eventType })
        assertEquals(pullRequests.findAll().single().id.toString(), events.single().newValue)
    }

    @Test
    fun `제목이 비어 있으면 400이고 만들지 않는다`() {
        val s = setup()
        mockMvc.perform(send(s, login(s.owner), title = "")).andExpect(status().isBadRequest)
        assertEquals(0, pullRequests.count())
    }

    @Test
    fun `게스트는 만들 수 없다`() {
        val s = setup()
        mockMvc.perform(send(s, login(user()))).andExpect(status().isForbidden)
    }

    // --- 담당자와 라벨 (추가 항목 X-14, D-34 20번: 받는 프로젝트에 수정 권한이 있는 사람만) ---

    private fun label(p: Project, name: String): IssueLabel {
        val c = IssueLabelCategory(project = p, name = "분류${++sequence}").also { em.persist(it) }
        return IssueLabel(category = c, color = "#33aa77", name = name, project = p).also { em.persist(it) }
    }

    /** 포크 주인이 원본의 구성원이기도 한 상황이다(원본에 수정 권한이 있다). */
    private fun withMemberOwner(): Setup = setup().also { projectUsers.save(ProjectUser(user = it.owner, project = it.origin, role = roles.findById(RoleType.MEMBER.id).get())) }

    @Test
    fun `받는 프로젝트에 수정 권한이 있으면 생성 폼에 담당자와 라벨 선택이 보인다`() {
        val s = withMemberOwner(); label(s.origin, "bug")
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequestForm", login(s.owner))
        assertNotNull(doc.selectFirst("input#assignee[name=assigneeLoginId]")); assertTrue(doc.select("select#labelIds option").any { it.text() == "bug" })
        assertEquals("/${s.origin.owner}/${s.origin.name}/assignableUsers", doc.selectFirst("#pr-meta")!!.attr("data-candidates-url"))
        // v1.6 이슈 작성 폼과 같은 배치: 제목, 본문은 왼쪽 col-md-9, 담당자와 라벨은 오른쪽 col-md-3 열(세로로 쌓음)
        assertNotNull(doc.selectFirst(".row > .col-lg-9.span-left-pane #title")); assertNotNull(doc.selectFirst(".row > .col-lg-9.span-left-pane textarea[name=body]"))
        assertNotNull(doc.selectFirst(".row > .col-lg-3.span-hard-wrap.right-menu #pr-meta #assignee")); assertNotNull(doc.selectFirst(".col-lg-3.right-menu #labelIds"))
        // 라벨 색 CSS는 받는 프로젝트(원본)의 것이다. 보내는 프로젝트(포크)의 것이면 칩 색이 빠진다
        assertTrue(doc.select("link[href]").any { it.attr("href") == "/${s.origin.owner}/${s.origin.name}/issue/labels.css" }, doc.select("link").toString())
    }

    @Test
    fun `수정 권한이 없으면 생성 폼에 담당자와 라벨 선택이 없다`() {
        val s = setup(); label(s.origin, "bug")
        val doc = page("/${s.fork.owner}/${s.fork.name}/newPullRequestForm", login(s.owner))
        assertNull(doc.selectFirst("#pr-meta")); assertNull(doc.selectFirst("#labelIds"))
    }

    @Test
    fun `만들 때 고른 담당자와 라벨이 붙고 타임라인 이벤트가 남는다`() {
        val s = withMemberOwner(); val bug = label(s.origin, "bug")
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        mockMvc.perform(send(s, login(s.owner)).param("assigneeLoginId", s.owner.loginId!!).param("labelIds", bug.id.toString())).andExpect(status().isSeeOther)
        val pr = pullRequests.findByToProjectIdAndNumber(s.origin.id!!, 1)!!
        assertEquals(listOf(s.owner.id), pr.assignees.map { it.id }); assertEquals(listOf(bug.id), pr.labels.map { it.id })
        val types = pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).map { it.eventType }
        assertTrue(EventType.PULL_REQUEST_ASSIGNEE_CHANGED in types && EventType.PULL_REQUEST_LABEL_CHANGED in types, types.toString())
    }

    @Test
    fun `수정 권한이 없으면 보낸 담당자와 라벨은 무시한다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        mockMvc.perform(send(s, login(s.owner)).param("assigneeLoginId", s.owner.loginId!!).param("labelIds", bug.id.toString())).andExpect(status().isSeeOther)
        val pr = pullRequests.findByToProjectIdAndNumber(s.origin.id!!, 1)!!
        assertTrue(pr.assignees.isEmpty()); assertTrue(pr.labels.isEmpty())
    }

    @Test
    fun `후보가 아닌 담당자나 다른 프로젝트의 라벨이면 400이고 만들지 않는다`() {
        val s = withMemberOwner(); val foreign = label(s.fork, "other")
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일", mapOf("b.txt" to "b".toByteArray())))
        mockMvc.perform(send(s, login(s.owner)).param("assigneeLoginId", user().loginId!!)).andExpect(status().isBadRequest)
        mockMvc.perform(send(s, login(s.owner)).param("labelIds", foreign.id.toString())).andExpect(status().isBadRequest)
        assertNull(pullRequests.findByToProjectIdAndNumber(s.origin.id!!, 1))
    }
}
