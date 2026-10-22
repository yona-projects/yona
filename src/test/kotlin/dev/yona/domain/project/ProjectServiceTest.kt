package dev.yona.domain.project

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6 `Project`의 조회와 소속 판정, 프로젝트 메뉴의 개수 조회를 검증한다.
 *
 * 원본: `app/models/Project.java:161-177`(조회), `:220-228`(`hasMember`), `:905-922`(이전 위치 조회),
 * `Issue.countIssues`, `PullRequest.countOpenedPullRequests`, `Posting.countPostings`, `CommentThread.countReviewsBy`.
 */
@Transactional
class ProjectServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ProjectService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var threads: CommentThreadRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        return users.save(User(loginId = "svc$n", email = "svc$n@example.com", state = UserState.ACTIVE))
    }

    private fun project(owner: String = "Owner${++sequence}", name: String = "Name$sequence", configure: Project.() -> Unit = {}): Project =
        projects.save(Project(owner = owner, name = name, projectScope = ProjectScope.PUBLIC).apply(configure))

    // --- 조회 ---

    @Test
    fun `소유자와 이름으로 대소문자 구별 없이 찾는다`() {
        val project = project("Alice", "My-Project")
        assertEquals(project.id, service.findByOwnerAndProjectName("alice", "my-project")?.id)
        assertEquals(project.id, service.findByOwnerAndProjectName("ALICE", "MY-PROJECT")?.id)
    }

    @Test
    fun `URL로 인코딩된 이름도 풀어서 찾는다`() {
        val project = project("bob", "my project")
        assertEquals(project.id, service.findByOwnerAndProjectName("bob", "my%20project")?.id)
        assertEquals(project.id, service.findByOwnerAndProjectName("bob", "my+project")?.id, "플러스는 공백으로 풀린다")
    }

    @Test
    fun `한글 이름도 URL 인코딩으로 찾는다`() {
        val project = project("carol", "한글프로젝트")
        assertEquals(project.id, service.findByOwnerAndProjectName("carol", "%ED%95%9C%EA%B8%80%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8")?.id)
    }

    @Test
    fun `없는 프로젝트는 null이다`() {
        assertNull(service.findByOwnerAndProjectName("nobody", "nothing"))
    }

    @Test
    fun `바뀌기 전 이름으로도 새 프로젝트를 찾는다`() {
        val renamed = project("dave", "new-name") {
            previousOwnerLoginId = "dave"
            previousName = "old-name"
            previousNameChangedTime = 100
        }
        assertEquals(renamed.id, service.findByOwnerAndProjectName("dave", "old-name")?.id)
    }

    @Test
    fun `이전 위치가 여러 프로젝트에 남아 있으면 가장 최근에 바뀐 것을 고른다`() {
        project("erin", "first") { previousOwnerLoginId = "erin"; previousName = "same-old"; previousNameChangedTime = 100 }
        val latest = project("erin", "second") { previousOwnerLoginId = "erin"; previousName = "same-old"; previousNameChangedTime = 200 }
        assertEquals(latest.id, service.findByPreviousPlaceOf("erin", "same-old")?.id)
    }

    @Test
    fun `현재 이름이 이전 이름보다 우선한다`() {
        val current = project("frank", "taken")
        project("frank", "moved") { previousOwnerLoginId = "frank"; previousName = "taken"; previousNameChangedTime = 300 }
        assertEquals(current.id, service.findByOwnerAndProjectName("frank", "taken")?.id)
    }

    @Test
    fun `이전 위치가 없으면 null이다`() {
        assertNull(service.findByPreviousPlaceOf("x", "y"))
    }

    // --- 소속 판정 ---

    @Test
    fun `멤버나 매니저나 사이트 관리자는 프로젝트의 구성원이다`() {
        val project = project()
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        val admin = user().also { siteAdmins.save(SiteAdmin(admin = it)) }
        assertTrue(service.hasMember(project, member))
        assertTrue(service.hasMember(project, manager))
        assertTrue(service.hasMember(project, admin))
        assertFalse(service.hasMember(project, user()))
        assertFalse(service.hasMember(project, null), "익명은 구성원이 아니다")
    }

    // --- 메뉴 개수 ---

    @Test
    fun `열린 이슈 개수는 부모 이슈만 센다`() {
        val project = project()
        val parent = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        issues.save(Issue(project = project, number = 2, state = State.OPEN, parent = parent)) // 부모가 있는 이슈(자식)는 세지 않는다
        issues.save(Issue(project = project, number = 3, state = State.CLOSED))
        issues.save(Issue(project = project(), number = 1, state = State.OPEN)) // 다른 프로젝트
        assertEquals(1, service.countIssues(project, State.OPEN))
        assertEquals(1, service.countIssues(project, State.CLOSED))
    }

    @Test
    fun `전체 이슈 개수는 초안을 뺀다`() {
        val project = project()
        issues.save(Issue(project = project, number = 1, state = State.OPEN))
        issues.save(Issue(project = project, number = 2, state = State.CLOSED))
        issues.save(Issue(project = project, number = 3, state = State.DRAFT))
        assertEquals(2, service.countIssues(project, State.ALL))
    }

    @Test
    fun `게시글 개수를 센다`() {
        val project = project()
        postings.save(Posting(project = project, number = 1))
        postings.save(Posting(project = project, number = 2))
        postings.save(Posting(project = project(), number = 1))
        assertEquals(2, service.countPostings(project))
    }

    @Test
    fun `열린 풀 리퀘스트는 받는 프로젝트 기준으로 센다`() {
        val to = project()
        val from = project()
        pullRequests.save(PullRequest(toProject = to, fromProject = from, state = State.OPEN))
        pullRequests.save(PullRequest(toProject = to, fromProject = from, state = State.CLOSED))
        pullRequests.save(PullRequest(toProject = from, fromProject = to, state = State.OPEN))
        assertEquals(1, service.countOpenPullRequests(to))
    }

    @Test
    fun `열린 리뷰 스레드 개수를 센다`() {
        val project = project()
        threads.save(CodeCommentThread().apply { this.project = project; state = ThreadState.OPEN })
        threads.save(CodeCommentThread().apply { this.project = project; state = ThreadState.CLOSED })
        assertEquals(1, service.countOpenReviewThreads(project))
        assertNotNull(project.id)
    }
}
