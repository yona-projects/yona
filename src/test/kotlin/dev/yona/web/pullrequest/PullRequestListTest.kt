package dev.yona.web.pullrequest

import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.PushedBranch
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestCommit
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
 * 풀 리퀘스트 목록(열림, 닫힘, 보냄)을 검증한다.
 *
 * 원본: `app/controllers/PullRequestApp.java`(`pullRequests`, `closedPullRequests`, `sentPullRequests`, `SearchCondition`, `Category`),
 * `app/views/git/list.scala.html`, `partial_search.scala.html`, `partial_list.scala.html`, `partial_recently_pushed_branches.scala.html`,
 * `app/models/PullRequest.java`(`findPagingList`, `createSearchExpressionList`).
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var pullRequestCommits: PullRequestCommitRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var pushedBranches: PushedBranchRepository
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pl$n", name = "이름pl$n", email = "pl$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(original: Project? = null, scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "plo$n", name = "plp$n", vcs = "GIT", projectScope = scope, originalProject = original))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun pr(
        to: Project, from: Project = to, number: Long, title: String = "요청 $number", state: State = State.OPEN, contributor: User = user(),
        created: Date = Date(1_700_000_000_000L + number * 1000), received: Date? = null, body: String = "본문", toBranch: String = "refs/heads/master",
        conflict: Boolean = false, receiver: User? = null
    ): PullRequest = pullRequests.save(
        PullRequest(
            title = title, body = body, toProject = to, fromProject = from, toBranch = toBranch, fromBranch = "refs/heads/feature", contributor = contributor, receiver = receiver,
            created = created, updated = created, received = received, state = state, isConflict = conflict, number = number
        )
    )

    private fun page(path: String, session: MockHttpSession? = null, pjax: Boolean = false): Document = Jsoup.parse(
        mockMvc.perform(get(path).apply { session?.let { session(it) }; if (pjax) header("X-PJAX", "true") }).andExpect(status().isOk).andReturn().response.contentAsString
    )

    private fun titles(doc: Document) = doc.select("li.post-item a.title").map { it.text() }

    @Test
    fun `열림 목록은 받는 프로젝트의 열린 요청을 번호 내림차순으로 보여 준다`() {
        val p = project(); val other = project()
        pr(p, number = 1, title = "첫째"); pr(p, number = 3, title = "셋째"); pr(p, number = 2, title = "둘째", state = State.CLOSED)
        pr(other, number = 1, title = "다른 프로젝트")
        val doc = page("/${p.owner}/${p.name}/pullRequests")
        assertEquals(listOf("셋째", "첫째"), titles(doc))
        assertEquals("/${p.owner}/${p.name}/pullRequest/3", doc.selectFirst("li.post-item a.title")!!.attr("href"))
        assertEquals("3", doc.selectFirst("li.post-item .post-id")!!.text())
    }

    @Test
    fun `닫힘 목록은 닫힘과 병합 요청을 받은 시각 내림차순으로 보여 주고 받은 시각이 없는 것은 뒤로 간다`() {
        val p = project()
        pr(p, number = 1, title = "일", state = State.CLOSED, received = Date(1_700_000_100_000L))
        pr(p, number = 2, title = "이", state = State.MERGED, received = Date(1_700_000_300_000L))
        pr(p, number = 3, title = "삼", state = State.CLOSED, received = null)
        pr(p, number = 4, title = "사", state = State.OPEN)
        assertEquals(listOf("이", "일", "삼"), titles(page("/${p.owner}/${p.name}/closedPullRequests")))
    }

    @Test
    fun `보냄 목록은 보내는 프로젝트 기준이고 포크에만 탭이 나온다`() {
        val origin = project(); val fork = project(original = origin)
        pr(origin, from = fork, number = 1, title = "보낸 것"); pr(origin, from = fork, number = 2, title = "합쳐짐", state = State.MERGED)
        val forkDoc = page("/${fork.owner}/${fork.name}/sentPullRequests")
        assertEquals(listOf("합쳐짐", "보낸 것"), titles(forkDoc))
        val tabs = forkDoc.select("ul.pullrequeset-tab-menu li a[data-type=state]")
        assertEquals(3, tabs.size)
        assertEquals("1 / 2", tabs[2].selectFirst(".num-badge")!!.text())
        assertEquals(2, page("/${origin.owner}/${origin.name}/pullRequests").select("ul.pullrequeset-tab-menu li a[data-type=state]").size)
    }

    @Test
    fun `탭 숫자는 열림과 닫힘 개수이다`() {
        val p = project()
        pr(p, number = 1); pr(p, number = 2); pr(p, number = 3, state = State.MERGED, received = Date())
        val tabs = page("/${p.owner}/${p.name}/pullRequests").select("ul.pullrequeset-tab-menu li a[data-type=state] .num-badge")
        assertEquals(listOf("2", "1"), tabs.map { it.text() })
    }

    @Test
    fun `제목, 본문, 리뷰 댓글, 커밋 메시지로 검색한다`() {
        val p = project()
        val byTitle = pr(p, number = 1, title = "alpha 제목"); pr(p, number = 2, body = "alpha 본문 포함")
        val byComment = pr(p, number = 3)
        val byCommit = pr(p, number = 4); val byOldCommit = pr(p, number = 5); pr(p, number = 6, title = "무관")
        val me = user()
        val thread = threads.save(NonRangedCodeCommentThread(commitId = "c").also { it.project = p; it.pullRequest = byComment; it.state = ThreadState.OPEN; it.createdDate = Date() })
        reviewComments.save(ReviewComment(contents = "ALPHA 댓글", createdDate = Date(), authorId = me.id, authorLoginId = me.loginId, authorName = me.name, thread = thread))
        pullRequestCommits.save(PullRequestCommit(pullRequest = byCommit, commitId = "abc", commitMessage = "fix Alpha bug", state = PullRequestCommitState.CURRENT))
        pullRequestCommits.save(PullRequestCommit(pullRequest = byOldCommit, commitId = "def", commitMessage = "alpha old", state = PullRequestCommitState.PRIOR))
        val found = titles(page("/${p.owner}/${p.name}/pullRequests?filter=alpha")).toSet()
        assertEquals(setOf(byTitle.title, "요청 2", byComment.title, byCommit.title), found)
        assertTrue(titles(page("/${p.owner}/${p.name}/pullRequests?filter=100%25")).isEmpty())
    }

    @Test
    fun `보낸 사람으로 거르고 보낸 사람 선택지에는 나를 맨 위에 둔다`() {
        val p = project(); val a = user(); val b = user()
        pr(p, number = 1, title = "a의 것", contributor = a); pr(p, number = 2, title = "b의 것", contributor = b)
        val doc = page("/${p.owner}/${p.name}/pullRequests?contributorId=${a.id}", login(a))
        assertEquals(listOf("a의 것"), titles(doc))
        assertEquals(listOf("", a.id.toString(), a.id.toString(), b.id.toString()), doc.select("select#contributors option").map { it.attr("value") })
        assertEquals(a.id.toString(), doc.select("select#contributors option[selected]").single().attr("value"))
    }

    @Test
    fun `한 쪽은 열다섯 개이고 쪽 번호로 넘긴다`() {
        val p = project()
        (1L..16L).forEach { pr(p, number = it) }
        assertEquals(15, titles(page("/${p.owner}/${p.name}/pullRequests")).size)
        assertEquals(listOf("요청 1"), titles(page("/${p.owner}/${p.name}/pullRequests?pageNum=2")))
        assertTrue(page("/${p.owner}/${p.name}/pullRequests").html().contains("yobi.Pagination.update(\$(\"#pagination\"), 2)"))
    }

    @Test
    fun `PJAX 요청에는 검색 영역 조각만 돌려준다`() {
        val p = project()
        pr(p, number = 1)
        val doc = page("/${p.owner}/${p.name}/pullRequests", pjax = true)
        assertNotNull(doc.selectFirst("div[pjax-container]")); assertNull(doc.selectFirst("#main-menu, nav.navbar"))
    }

    @Test
    fun `목록 한 줄에는 리뷰 진행, 리뷰어 수, 대상 브랜치, 충돌, 받은 사람이 나온다`() {
        val p = project(); p.isUsingReviewerCount = true; projects.save(p)
        val receiver = user(); val reviewer = user()
        val r = pr(p, number = 1, title = "[긴급] 충돌 요청", conflict = true, receiver = receiver, toBranch = "refs/heads/dev")
        r.reviewers.add(reviewer); pullRequests.save(r)
        val t1 = threads.save(NonRangedCodeCommentThread(commitId = "c").also { it.project = p; it.pullRequest = r; it.state = ThreadState.CLOSED; it.createdDate = Date() })
        threads.save(NonRangedCodeCommentThread(commitId = "c").also { it.project = p; it.pullRequest = r; it.state = ThreadState.OPEN; it.createdDate = Date() })
        assertNotNull(t1.id)
        val item = page("/${p.owner}/${p.name}/pullRequests").selectFirst("li.post-item")!!
        assertTrue(item.selectFirst("a.title")!!.hasClass("conflict"))
        assertEquals("충돌 요청", item.selectFirst("a.title")!!.text())
        assertEquals("1", item.selectFirst(".infos a span")!!.text())
        assertEquals("/${p.owner}/${p.name}/pullRequest/1/changes", item.select(".infos a").first { it.attr("href").endsWith("/changes") }.attr("href"))
        assertEquals("1", item.selectFirst(".infos-icon.yobicon-preview")!!.parent()!!.selectFirst("a span")!!.text())
        assertEquals("dev", item.selectFirst("span.to-branch")!!.text())
        assertEquals("Conflict", item.selectFirst(".state")!!.text())
        assertTrue(item.selectFirst(".state")!!.hasClass("conflict"))
        assertEquals("/${receiver.loginId}", item.selectFirst("a.assinee")!!.attr("href"))
    }

    @Test
    fun `대상이 기본 브랜치이면 to-default-branch이고 목록이 비면 안내가 나온다`() {
        val p = project().also { repositories.createRepository(it) }
        pr(p, number = 1)
        assertEquals("master", page("/${p.owner}/${p.name}/pullRequests").selectFirst("span.to-default-branch")!!.text())
        val empty = project().also { repositories.createRepository(it) }
        assertTrue(page("/${empty.owner}/${empty.name}/pullRequests").selectFirst(".error-wrap")!!.text().contains("pull request"))
    }

    @Test
    fun `포크의 최근 푸시 브랜치와 원본의 내 포크 브랜치를 알려 주고 닫으면 지운다`() {
        val origin = project().also { repositories.createRepository(it) }; val fork = project(original = origin); val me = user()
        projectUsers.save(ProjectUser(user = me, project = fork, role = roles.findById(RoleType.MANAGER.id).get()))
        val recent = pushedBranches.save(PushedBranch(pushedDate = Date(), name = "refs/heads/topic", project = fork))
        pushedBranches.save(PushedBranch(pushedDate = Date(System.currentTimeMillis() - 2 * 3_600_000L), name = "refs/heads/old", project = fork))
        val alert = page("/${fork.owner}/${fork.name}/pullRequests", login(me)).selectFirst(".alert-info")!!
        assertEquals(1, alert.children().size)
        assertTrue(alert.text().contains("${fork.owner}/${fork.name}:topic"))
        assertEquals("/${fork.owner}/${fork.name}/newPullRequestForm?fromBranch=refs/heads/topic&toBranch=refs/heads/master", alert.selectFirst("a:not(.close)")!!.attr("href"))
        assertEquals("/${fork.owner}/${fork.name}/pushedBranch/${recent.id}/delete", alert.selectFirst("a.close")!!.attr("data-request-uri"))
        mockMvc.perform(delete("/${fork.owner}/${fork.name}/pushedBranch/${recent.id}/delete").session(login(me))).andExpect(status().isOk)
        assertNull(pushedBranches.findById(recent.id!!).orElse(null))
    }

    @Test
    fun `코드 접근을 구성원으로 제한한 프로젝트는 비구성원에게 403이다`() {
        val p = project(); p.isCodeAccessibleMemberOnly = true; projects.save(p)
        mockMvc.perform(get("/${p.owner}/${p.name}/pullRequests").session(login(user()))).andExpect(status().isForbidden)
    }
}
