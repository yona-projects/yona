package dev.yona.domain.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.FavoriteIssue
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals

/**
 * 프로젝트 이슈 목록의 검색 조건을 검증한다.
 *
 * 원본: `app/models/support/SearchCondition.java`(`asExpressionList(Project)`), `app/models/Issue.java`(`countIssuesBy`).
 */
@Transactional
class IssueSearchServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: IssueSearchService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private lateinit var project: Project
    private lateinit var me: User
    private var clock = 1_700_000_000_000L

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "se$n", name = "se$n", email = "se$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun setUp() {
        project = projects.save(Project(owner = "so", name = "sp${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        me = newUser()
    }

    private fun issue(
        number: Long, title: String = "이슈 $number", body: String? = null, state: State = State.OPEN, author: User? = null, assignee: User? = null,
        milestone: Milestone? = null, weight: Int = 0, dueDate: Date? = null, comments: Int = 0, draft: Boolean = false, parent: Issue? = null,
        issueLabels: Set<IssueLabel> = emptySet(), createdAt: Long = clock + number
    ): Issue = issues.save(
        Issue(
            project = project, number = number, title = title, body = body, state = state, authorId = author?.id, authorLoginId = author?.loginId,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, weight = weight, dueDate = dueDate,
            numOfComments = comments, isDraft = draft, parent = parent, labels = issueLabels.toMutableSet(), createdDate = Date(createdAt), updatedDate = Date(createdAt)
        )
    )

    private fun numbers(condition: IssueSearchCondition = IssueSearchCondition(), viewer: User? = me): List<Long> =
        service.search(project, condition, viewer, 100).items.map { it.number!! }

    private fun day(text: String): Date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text)

    @Test
    fun `기본은 열린 이슈를 만든 날짜 내림차순으로 보여 주고 초안과 하위 이슈는 뺀다`() {
        setUp()
        val parent = issue(1)
        issue(2); issue(3, state = State.CLOSED); issue(4, draft = true); issue(5, parent = parent)
        assertEquals(listOf(2L, 1L), numbers())
    }

    @Test
    fun `가중치가 큰 이슈가 먼저이고 같으면 정렬 기준을 따른다`() {
        setUp()
        issue(1); issue(2, weight = 3); issue(3); issue(4, weight = -1)
        assertEquals(listOf(2L, 3L, 1L, 4L), numbers())
        assertEquals(listOf(2L, 1L, 3L, 4L), numbers(IssueSearchCondition(orderDir = "asc")))
    }

    @Test
    fun `마감일 정렬은 마감일이 없는 이슈를 항상 뒤에 둔다`() {
        setUp()
        issue(1, dueDate = day("2030-01-02 00:00:00")); issue(2); issue(3, dueDate = day("2030-01-01 00:00:00"))
        assertEquals(listOf(3L, 1L, 2L), numbers(IssueSearchCondition(orderBy = "dueDate", orderDir = "asc")))
        assertEquals(listOf(1L, 3L, 2L), numbers(IssueSearchCondition(orderBy = "dueDate", orderDir = "desc")))
    }

    @Test
    fun `댓글 수와 수정일로도 정렬한다`() {
        setUp()
        issue(1, comments = 2); issue(2, comments = 5); issue(3, comments = 0)
        assertEquals(listOf(2L, 1L, 3L), numbers(IssueSearchCondition(orderBy = "numOfComments", orderDir = "desc")))
        assertEquals(listOf(3L, 2L, 1L), numbers(IssueSearchCondition(orderBy = "updatedDate", orderDir = "desc")))
    }

    @Test
    fun `상태 조건은 열림과 닫힘만 거르고 그 밖의 값은 모두 보여 준다`() {
        setUp()
        issue(1); issue(2, state = State.CLOSED)
        assertEquals(listOf(2L), numbers(IssueSearchCondition(state = "closed")))
        assertEquals(listOf(2L, 1L), numbers(IssueSearchCondition(state = "all")))
    }

    @Test
    fun `작성자 조건과 작성자 없음`() {
        setUp()
        val other = newUser()
        issue(1, author = me); issue(2, author = other); issue(3)
        assertEquals(listOf(1L), numbers(IssueSearchCondition(authorId = me.id)))
        assertEquals(listOf(3L), numbers(IssueSearchCondition(authorId = -1L)))
    }

    @Test
    fun `담당자 조건과 담당자 없음`() {
        setUp()
        val other = newUser()
        issue(1, assignee = me); issue(2, assignee = other); issue(3)
        assertEquals(listOf(1L), numbers(IssueSearchCondition(assigneeId = me.id)))
        assertEquals(listOf(3L), numbers(IssueSearchCondition(assigneeId = -1L)))
    }

    @Test
    fun `마일스톤 조건과 마일스톤 없음`() {
        setUp()
        val m = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        issue(1, milestone = m); issue(2)
        assertEquals(listOf(1L), numbers(IssueSearchCondition(milestoneId = m.id)))
        assertEquals(listOf(2L), numbers(IssueSearchCondition(milestoneId = -1L)))
    }

    @Test
    fun `라벨 조건은 고른 라벨을 모두 가진 이슈만 찾는다`() {
        setUp()
        val c = categories.save(IssueLabelCategory(project = project, name = "t", isExclusive = false))
        val a = labels.save(IssueLabel(project = project, category = c, name = "a", color = "#fff"))
        val b = labels.save(IssueLabel(project = project, category = c, name = "b", color = "#fff"))
        issue(1, issueLabels = setOf(a)); issue(2, issueLabels = setOf(a, b)); issue(3, issueLabels = setOf(b))
        assertEquals(listOf(2L, 1L), numbers(IssueSearchCondition(labelIds = setOf(a.id!!))))
        assertEquals(listOf(2L), numbers(IssueSearchCondition(labelIds = setOf(a.id!!, b.id!!))))
        assertEquals(emptyList(), numbers(IssueSearchCondition(labelIds = setOf(999_999L))))
    }

    @Test
    fun `검색어는 제목, 본문, 댓글에서 대소문자 없이 찾는다`() {
        setUp()
        issue(1, title = "Login 오류"); issue(2, body = "로그인이 LOGIN 안 됨"); issue(3)
        val withComment = issue(4)
        entityManager.persist(IssueComment(contents = "다시 login 해 보세요", createdDate = Date(), issue = withComment, projectId = project.id!!))
        entityManager.flush()
        assertEquals(listOf(4L, 2L, 1L), numbers(IssueSearchCondition(filter = "login")))
    }

    @Test
    fun `검색어의 퍼센트와 밑줄은 그대로 찾는다`() {
        setUp()
        issue(1, title = "100% 완료"); issue(2, title = "1000 완료"); issue(3, title = "a_b"); issue(4, title = "axb")
        assertEquals(listOf(1L), numbers(IssueSearchCondition(filter = "100%")))
        assertEquals(listOf(3L), numbers(IssueSearchCondition(filter = "a_b")))
    }

    @Test
    fun `댓글이 있는 이슈만 보기`() {
        setUp()
        issue(1, comments = 1); issue(2)
        assertEquals(listOf(1L), numbers(IssueSearchCondition(commentedCheck = true)))
    }

    @Test
    fun `마감일 조건은 그날까지의 이슈를 찾는다`() {
        setUp()
        issue(1, dueDate = day("2030-05-01 23:59:59")); issue(2, dueDate = day("2030-05-02 12:00:00")); issue(3, dueDate = day("2030-05-03 00:00:01"))
        assertEquals(listOf(2L, 1L), numbers(IssueSearchCondition(dueDate = day("2030-05-02 00:00:00"))))
    }

    @Test
    fun `내가 댓글을 쓴 이슈`() {
        setUp()
        val other = newUser()
        val mine = issue(1); val theirs = issue(2); issue(3)
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorId = me.id, issue = mine, projectId = project.id!!))
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorId = other.id, issue = theirs, projectId = project.id!!))
        entityManager.flush()
        assertEquals(listOf(1L), numbers(IssueSearchCondition(commenterId = me.id)))
        assertEquals(emptyList(), numbers(IssueSearchCondition(commenterId = me.id), viewer = null))
    }

    @Test
    fun `공유받은 이슈와 즐겨찾는 이슈`() {
        setUp()
        val shared = issue(1); val favorite = issue(2); issue(3)
        entityManager.persist(IssueSharer(loginId = me.loginId, user = me, issue = shared))
        entityManager.persist(FavoriteIssue(user = me, issue = favorite))
        entityManager.flush()
        assertEquals(listOf(1L), numbers(IssueSearchCondition(sharerId = me.id)))
        assertEquals(listOf(2L), numbers(IssueSearchCondition(favoriteId = me.id)))
    }

    @Test
    fun `작성자나 검색어나 라벨이나 담당자 조건이 있으면 하위 이슈도 찾는다`() {
        setUp()
        val parent = issue(1, author = me)
        issue(2, author = me, parent = parent, title = "child")
        assertEquals(listOf(1L), numbers())
        assertEquals(listOf(2L, 1L), numbers(IssueSearchCondition(authorId = me.id)))
        assertEquals(listOf(2L), numbers(IssueSearchCondition(filter = "child")))
    }

    @Test
    fun `쪽 나누기와 개수`() {
        setUp()
        (1L..7L).forEach { issue(it) }
        val first = service.search(project, IssueSearchCondition(pageNum = 0), me, 3)
        assertEquals(listOf(7L, 6L, 5L), first.items.map { it.number })
        assertEquals(3, first.totalPageCount)
        assertEquals(0, first.pageIndex)
        assertEquals(listOf(1L), service.search(project, IssueSearchCondition(pageNum = 2), me, 3).items.map { it.number })
        assertEquals(emptyList(), service.search(project, IssueSearchCondition(pageNum = 3), me, 3).items)
        assertEquals(7, service.count(project, IssueSearchCondition(), me))
        assertEquals(0, service.count(project, IssueSearchCondition(state = "closed"), me))
    }

    @Test
    fun `허용하지 않는 정렬 기준은 만든 날짜이다`() {
        setUp()
        issue(1); issue(2)
        assertEquals(listOf(2L, 1L), numbers(IssueSearchCondition(orderBy = "id; drop table issue")))
    }
}
