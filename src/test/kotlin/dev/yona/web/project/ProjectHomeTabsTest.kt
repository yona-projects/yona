package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 프로젝트 홈의 이력 탭(`tabId=history`)과 대시보드 탭(`tabId=dashboard`)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:105-150`, `app/models/History.java`, `app/views/project/partial_history.scala.html`,
 * `partial_dashboard*.scala.html`, `app/models/Issue.java`(`countIssues`, `countOpenIssuesBy*`), `app/models/PullRequest.java`(`findRecentlyReceivedOpen`).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-home-tabs"])
class ProjectHomeTabsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labelCategories: IssueLabelCategoryRepository
    @Autowired private lateinit var issueLabels: IssueLabelRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0
    private var number = 0L
    private var clock = 1_700_000_000_000L

    @AfterEach
    fun clean() {
        File("build/tmp/it-home-tabs").deleteRecursively()
    }

    private fun user(email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ht$n", name = "홈탭$n", email = email ?: "ht$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(vcs: String = "GIT", issue: Boolean = true, pullRequest: Boolean = true, milestone: Boolean = true, git: Boolean = true): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "hto$n", name = "htp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = issue, pullRequest = pullRequest, review = true, milestone = milestone, board = true))
        if (git) repositories.createRepository(p)
        return p
    }

    private fun issue(
        project: Project, title: String, author: User = user(), assignee: User? = null, state: State = State.OPEN, parent: Issue? = null,
        milestone: Milestone? = null, labels: Set<IssueLabel> = emptySet(), created: Long = ++clock
    ): Issue = issues.save(Issue(
        project = project, number = ++number, title = title, body = "본문", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
        assignee = assignee?.let { assignees.findFirstByUserIdAndProjectId(it.id!!, project.id!!) ?: assignees.save(Assignee(user = it, project = project)) },
        numOfComments = 0, weight = 0, parent = parent, milestone = milestone, labels = labels.toMutableSet(), createdDate = Date(created), updatedDate = Date(created)
    ))

    private fun posting(project: Project, title: String, author: User, created: Long = ++clock): Posting {
        val n = (project.lastPostingNumber ?: 0) + 1
        project.lastPostingNumber = n; projects.save(project)
        return postings.save(Posting(
            project = project, number = n, title = title, body = "본문", authorId = author.id, authorLoginId = author.loginId, authorName = author.name, notice = false, readme = false,
            numOfComments = 0, createdDate = Date(created), updatedDate = Date(created)
        ))
    }

    private fun pullRequest(to: Project, title: String, contributor: User, state: State = State.OPEN, created: Long = ++clock): PullRequest =
        pullRequests.save(PullRequest(
            title = title, body = "본문", toProject = to, fromProject = to, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = contributor,
            created = Date(created), updated = Date(created), state = state, number = ++number
        ))

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun home(p: Project, tab: String, owner: String = p.owner!!) = page("/$owner/${p.name}?tabId=$tab")

    // --- 이력 탭 ---

    @Test
    fun `이력 탭은 이슈와 게시글과 풀 리퀘스트와 커밋을 시각 내림차순으로 모아 보인다`() {
        val p = project(); val writer = user("writer@example.com")
        GitTestSupport.pushCommits(repositories, p, GitTestSupport.TestCommit("첫 커밋", mapOf("a.txt" to "a".toByteArray()), authorName = "외부인", authorEmail = "outsider@example.com"))
        issue(p, "이슈 하나", writer, created = 1_800_000_000_000L)
        posting(p, "글 하나", writer, created = 1_800_000_002_000L)
        pullRequest(p, "요청 하나", writer, created = 1_800_000_001_000L)
        val doc = home(p, "history")
        val kinds = doc.select("ul.activity-streams li.activity-stream .whereis a.title").map { it.text() }
        assertEquals(listOf("글 하나", "요청 하나", "이슈 하나", "첫 커밋"), kinds)
    }

    @Test
    fun `이슈 이력의 한 줄에는 작성자와 종류와 짧은 제목과 주소가 있다`() {
        val p = project(); val writer = user()
        val issue = issue(p, "이력 이슈", writer)
        val item = home(p, "history").selectFirst("li.activity-stream")!!
        assertEquals("/${writer.loginId}", item.selectFirst("a.actor")!!.attr("href"))
        assertEquals(writer.name, item.selectFirst("a.actor")!!.text())
        assertEquals("/${writer.loginId}", item.selectFirst("a.avatar-wrap")!!.attr("href"))
        assertEquals("#${issue.number}", item.selectFirst(".whereis a.where")!!.text())
        assertEquals("/${p.owner}/${p.name}/issue/${issue.number}", item.selectFirst(".whereis a.where")!!.attr("href"))
        assertEquals("/${p.owner}/${p.name}/issue/${issue.number}", item.selectFirst(".whereis a.title")!!.attr("href"))
        assertEquals("${writer.name} New issue added. #${issue.number} 이력 이슈", item.selectFirst("p.header-text")!!.text())
    }

    @Test
    fun `게시글과 풀 리퀘스트 이력의 종류 문구와 주소`() {
        val p = project(); val writer = user()
        val post = posting(p, "글", writer); val pr = pullRequest(p, "요청", writer)
        val items = home(p, "history").select("li.activity-stream")
        val prItem = items.first { it.text().contains("요청") }
        val postItem = items.first { it.text().contains("글") && !it.text().contains("요청") }
        assertTrue(prItem.selectFirst("p.header-text")!!.text().contains("New code sent."))
        assertEquals("/${p.owner}/${p.name}/pullRequest/${pr.number}", prItem.selectFirst(".whereis a.title")!!.attr("href"))
        assertTrue(postItem.selectFirst("p.header-text")!!.text().contains("New post added."))
        assertEquals("/${p.owner}/${p.name}/post/${post.number}", postItem.selectFirst(".whereis a.title")!!.attr("href"))
    }

    @Test
    fun `커밋 이력은 등록된 사용자이면 사용자 정보를 쓰고 아니면 커밋 작성자 이름과 기본 아바타를 쓴다`() {
        val p = project(); val known = user("known@example.com")
        GitTestSupport.pushCommits(
            repositories, p,
            GitTestSupport.TestCommit("알려진 사람", mapOf("a.txt" to "a".toByteArray()), authorName = "깃이름", authorEmail = "known@example.com"),
            GitTestSupport.TestCommit("모르는 사람", mapOf("b.txt" to "b".toByteArray()), authorName = "외부인", authorEmail = "who@example.com")
        )
        val items = home(p, "history").select("li.activity-stream")
        val unknown = items.first { it.text().contains("모르는 사람") }
        assertEquals("외부인", unknown.selectFirst("a.actor")!!.text())
        assertEquals("#", unknown.selectFirst("a.actor")!!.attr("href"))
        assertEquals("/assets/images/default-avatar-64.png", unknown.selectFirst("a.avatar-wrap img")!!.attr("src"))
        val registered = items.first { it.text().contains("알려진 사람") }
        assertEquals(known.name, registered.selectFirst("a.actor")!!.text())
        assertEquals("/${known.loginId}", registered.selectFirst("a.actor")!!.attr("href"))
        assertTrue(registered.selectFirst("p.header-text")!!.text().contains("Committed"))
        assertTrue(registered.selectFirst(".whereis a.where")!!.attr("href").startsWith("/${p.owner}/${p.name}/commit/"))
        assertEquals(7, registered.selectFirst(".whereis a.where")!!.text().length)
    }

    @Test
    fun `종류마다 최근 10개만 보이고 커밋이 없는 저장소도 오류가 아니다`() {
        val p = project(); val writer = user()
        (1..11).forEach { issue(p, "이슈$it", writer) }
        val titles = home(p, "history").select("li.activity-stream .whereis a.title").map { it.text() }
        assertEquals(10, titles.size)
        assertTrue("이슈1" !in titles && "이슈11" in titles)
    }

    @Test
    fun `이력 주소의 소유자는 요청 주소에 대문자로 써도 프로젝트의 실제 소유자 이름이다 (R-68-2)`() {
        val p = project(); val writer = user()
        val issue = issue(p, "대소문자", writer)
        val href = home(p, "history", owner = p.owner!!.uppercase()).selectFirst(".whereis a.title")!!.attr("href")
        // v1.6은 요청 주소에 쓴 소유자(대소문자 포함)를 그대로 이력 주소에 썼다
        assertEquals("/${p.owner}/${p.name}/issue/${issue.number}", href)
    }

    @Test
    fun `이력이 없으면 목록이 비어 있고 History 탭이 활성이다`() {
        val p = project()
        val doc = home(p, "history")
        assertTrue(doc.select("li.activity-stream").isEmpty())
        assertEquals("History", doc.selectFirst(".project-page-wrap .nav-tabs a.nav-link.active")!!.text().trim())
    }

    // --- 대시보드 탭 ---

    private fun rows(doc: Document, selector: String) = doc.select("$selector .row")

    @Test
    fun `담당자별 열린 이슈는 하위 이슈를 포함해 세고 분모는 하위 이슈를 뺀 열린 이슈 수이다`() {
        val p = project(); val a = user(); val b = user()
        val parent = issue(p, "부모", assignee = a); issue(p, "자식", assignee = a, parent = parent); issue(p, "남의 것", assignee = b); issue(p, "담당 없음")
        issue(p, "닫힘", assignee = a, state = State.CLOSED)
        val doc = home(p, "dashboard")
        val rows = rows(doc, ".overview-assignee")
        // 분모는 하위 이슈를 뺀 열린 이슈 3개(부모, 남의 것, 담당 없음). 담당자 a는 2개이다.
        val rowA = rows.first { it.text().contains("@${a.loginId}") }
        assertEquals("2", rowA.selectFirst("div.num strong")!!.text())
        assertEquals("66%", rowA.selectFirst(".progress")!!.attr("title"))
        assertEquals("/${p.owner}/${p.name}/issues?state=open&assigneeId=${a.id}", rowA.selectFirst("a.usf-group")!!.attr("href"))
        val none = rows.last()!!
        assertEquals("1", none.selectFirst("div.num strong")!!.text())
        assertEquals("/${p.owner}/${p.name}/issues?state=open&assigneeId=-1", none.selectFirst("a.usf-group")!!.attr("href"))
        assertEquals(3, rows.size)
    }

    @Test
    fun `열린 이슈가 없으면 담당자별 영역에 안내와 새 이슈 링크가 나온다`() {
        val p = project()
        val empty = home(p, "dashboard").selectFirst(".overview-assignee .empty")!!
        assertEquals("No issue found", empty.selectFirst("p")!!.text())
        assertEquals("/${p.owner}/${p.name}/issueform", empty.selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `마일스톤별 열린 이슈는 열린 이슈가 있는 마일스톤과 마일스톤 없음 줄을 보인다`() {
        val p = project()
        val m1 = milestones.save(Milestone(project = p, title = "일차", state = State.OPEN, dueDate = Date(1_900_000_000_000L)))
        milestones.save(Milestone(project = p, title = "이슈 없는 마일스톤", state = State.OPEN, dueDate = Date(1_950_000_000_000L)))
        milestones.save(Milestone(project = p, title = "닫힌 마일스톤", state = State.CLOSED))
        issue(p, "하나", milestone = m1); issue(p, "둘", milestone = m1, state = State.CLOSED); issue(p, "마일스톤 없음")
        val rows = rows(home(p, "dashboard"), ".overview-milestone")
        assertEquals(2, rows.size)
        assertEquals("일차", rows[0].selectFirst("a")!!.text())
        assertEquals("/${p.owner}/${p.name}/issues?state=open&milestoneId=${m1.id}", rows[0].selectFirst("a")!!.attr("href"))
        assertEquals("1", rows[0].selectFirst("div.num strong")!!.text())
        assertEquals("50%", rows[0].selectFirst(".progress")!!.attr("title"))
        assertEquals("/${p.owner}/${p.name}/issues?state=open&milestoneId=-1", rows[1].selectFirst("a")!!.attr("href"))
        assertEquals("1", rows[1].selectFirst("div.num strong")!!.text())
    }

    @Test
    fun `열린 마일스톤이 없으면 안내와 새 마일스톤 링크가 나온다`() {
        val p = project()
        val empty = home(p, "dashboard").selectFirst(".overview-milestone .empty")!!
        assertEquals("/${p.owner}/${p.name}/newMilestoneForm", empty.selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `라벨별 열린 이슈는 분류와 라벨 이름순으로 열린 이슈 수와 링크를 보인다`() {
        val p = project()
        val kind = labelCategories.save(IssueLabelCategory(project = p, name = "종류", isExclusive = false))
        val bug = issueLabels.save(IssueLabel(project = p, category = kind, name = "버그", color = "#f00"))
        val feature = issueLabels.save(IssueLabel(project = p, category = kind, name = "기능", color = "#0f0"))
        issue(p, "버그1", labels = setOf(bug)); issue(p, "버그2", labels = setOf(bug)); issue(p, "닫힌 버그", labels = setOf(bug), state = State.CLOSED); issue(p, "기능1", labels = setOf(feature))
        val dl = home(p, "dashboard").selectFirst("dl.overview-label")!!
        assertEquals("종류", dl.selectFirst("dt")!!.text())
        val rows = dl.select("dd .row")
        assertEquals(listOf("기능", "버그"), rows.map { it.selectFirst("a span")!!.text().trim() })
        assertEquals(listOf("1", "2"), rows.map { it.selectFirst("div.num strong")!!.text() })
        assertEquals("/${p.owner}/${p.name}/issues?state=open&labelIds=${bug.id}", rows[1].selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `풀 리퀘스트 영역은 최근 받은 열린 요청 10개와 더 보기 링크를 보인다`() {
        val p = project(); val w = user()
        pullRequest(p, "오래된 요청", w, created = 1_800_000_000_000L); pullRequest(p, "닫힌 요청", w, state = State.CLOSED); pullRequest(p, "최근 요청", w, created = 1_800_000_100_000L)
        val doc = home(p, "dashboard")
        val rows = rows(doc, ".overview-pullrequest")
        val titles = rows.mapNotNull { it.selectFirst(".title a:not(.usf-group)")?.text() }
        assertEquals(listOf("최근 요청", "오래된 요청"), titles)
        assertEquals("/${p.owner}/${p.name}/pullRequests?contributorId=${w.id}", rows[0].selectFirst("a.usf-group")!!.attr("href"))
        val more = doc.selectFirst(".overview-pullrequest .right-txt a")!!
        assertEquals("/${p.owner}/${p.name}/pullRequests", more.attr("href"))
        assertEquals("See 2 more", more.text())
    }

    @Test
    fun `풀 리퀘스트가 없으면 안내와 새 풀 리퀘스트 링크가 나온다`() {
        val p = project()
        val empty = home(p, "dashboard").selectFirst(".overview-pullrequest .empty")!!
        assertEquals("/${p.owner}/${p.name}/newPullRequestForm", empty.selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `메뉴 설정에 따라 영역이 빠진다`() {
        val noIssue = home(project(issue = false), "dashboard")
        assertTrue(noIssue.select(".overview-assignee, .overview-milestone, dl.overview-label").isEmpty())
        assertEquals(1, noIssue.select(".overview-pullrequest").size)
        val noPullRequest = home(project(pullRequest = false), "dashboard")
        assertTrue(noPullRequest.select(".overview-pullrequest").isEmpty())
        assertEquals(1, noPullRequest.select(".overview-assignee").size)
        val svn = home(project(vcs = "Subversion", git = false), "dashboard")
        assertTrue(svn.select(".overview-pullrequest").isEmpty())
    }

    @Test
    fun `대시보드 탭이 활성이고 README 내용은 나오지 않는다`() {
        val doc = home(project(), "dashboard")
        assertEquals("Dashboard", doc.selectFirst(".project-page-wrap .nav-tabs a.nav-link.active")!!.text().trim())
        assertEquals(1, doc.select(".project-overview-home").size)
    }
}
