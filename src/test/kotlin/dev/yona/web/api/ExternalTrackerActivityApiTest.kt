package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.SimpleCommentThread
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date

/**
 * Magpie 이슈 계약(`contract:tracker`, 로드맵 X-23)을 채우는 서버 조회를 검증한다: 이슈 목록의 기간 조건과 `closedAt`, `authorFirstTime`, 그리고 사람과 기간별 활동 조회(`GET .../activity`).
 * 기간은 `since` 이상 `until` 미만이다(날짜만 쓰면 UTC 0시). 활동의 기준은 댓글이고 날짜는 그 사람이 창 안에서 처음 댓글을 단 시각이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-tracker-activity"])
class ExternalTrackerActivityApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0
    private fun at(iso: String) = Date.from(Instant.parse(iso))

    private fun user(): User {
        val n = ++sequence; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tka$n", name = "이름tka$n", email = "tka$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private class Fixture(val project: Project, val manager: User, val member: User) { val api get() = "/-_-api/v1/owners/${project.owner}/projects/${project.name}" }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Fixture {
        val n = ++sequence
        val project = projects.save(Project(owner = "tkao$n", name = "tkap$n", vcs = "GIT", projectScope = scope, createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        return Fixture(project, manager, member)
    }

    private fun token(user: User, vararg scopes: String): String = (tokens.create(user, "t", (scopes.toList().ifEmpty { listOf("repo") }).toSet(), null) as TokenCreation.Created).value
    private fun call(builder: MockHttpServletRequestBuilder, token: String?) = mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.also { failures.clear() })

    private fun issue(f: Fixture, title: String, author: User, created: String, state: State = State.OPEN, labels: List<IssueLabel> = emptyList(), closed: String? = null): Issue {
        val number = (issues.findAll().filter { it.project?.id == f.project.id }.maxOfOrNull { it.number ?: 0 } ?: 0) + 1
        val saved = issues.save(
            Issue(project = f.project, number = number, title = title, body = "$title 본문", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                numOfComments = 0, weight = 0, createdDate = at(created), updatedDate = at(created))
        )
        saved.labels.addAll(labels)
        closed?.let { em.persist(IssueEvent(created = at(it), senderLoginId = author.loginId, issue = saved, eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "open", newValue = "closed")) }
        em.flush()
        return saved
    }

    private fun label(f: Fixture, name: String): IssueLabel {
        val category = IssueLabelCategory(project = f.project, name = "분류$name").also { em.persist(it) }
        return IssueLabel(category = category, project = f.project, name = name, color = "#ff0000").also { em.persist(it) }
    }

    private fun comment(issue: Issue, author: User, at: String, body: String = "댓글") {
        em.persist(IssueComment(contents = body, createdDate = at(at), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = requireNotNull(issue.project?.id)))
        em.flush()
    }

    private fun pullRequest(f: Fixture, author: User, created: String): PullRequest {
        val number = (em.createQuery("select max(p.number) from PullRequest p where p.toProject.id = :id", Long::class.javaObjectType).setParameter("id", f.project.id).singleResult ?: 0L) + 1
        return PullRequest(title = "PR $number", toProject = f.project, fromProject = f.project, toBranch = "master", fromBranch = "topic$number", contributor = author, created = at(created), updated = at(created), number = number, state = State.OPEN)
            .also { em.persist(it); em.flush() }
    }

    private fun prComment(f: Fixture, pr: PullRequest, author: User, at: String) {
        val thread = SimpleCommentThread().apply { project = f.project; pullRequest = pr; authorId = author.id; authorLoginId = author.loginId; authorName = author.name; createdDate = at(at) }
        em.persist(thread)
        em.persist(ReviewComment(contents = "PR 댓글", createdDate = at(at), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, thread = thread))
        em.flush()
    }

    // --- 이슈 목록의 기간 조건과 필드 ---

    @Test
    fun `이슈 목록은 기간 조건을 받고 since 이상 until 미만이다`() {
        val f = project()
        issue(f, "1월", f.member, "2026-01-10T00:00:00Z"); issue(f, "2월 초", f.member, "2026-02-01T00:00:00Z"); issue(f, "2월 말", f.member, "2026-02-28T23:59:59Z"); issue(f, "3월", f.member, "2026-03-01T00:00:00Z")
        val t = token(user())
        call(get("${f.api}/issues").param("state", "all").param("createdSince", "2026-02-01T00:00:00Z").param("createdUntil", "2026-03-01T00:00:00Z").param("sort", "createdDate").param("order", "asc"), t)
            .andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.items[0].title").value("2월 초")).andExpect(jsonPath("$.items[1].title").value("2월 말"))
        call(get("${f.api}/issues").param("state", "all").param("createdSince", "2026-02-01").param("createdUntil", "2026-03-01"), t).andExpect(jsonPath("$.totalCount").value(2))
        call(get("${f.api}/issues").param("state", "all").param("createdSince", "2026-03-01"), t).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("3월"))
        call(get("${f.api}/issues").param("state", "all").param("createdUntil", "2026-01-11"), t).andExpect(jsonPath("$.totalCount").value(1))
        call(get("${f.api}/issues").param("createdSince", "어제"), t).andExpect(status().isBadRequest)
        call(get("${f.api}/issues").param("createdSince", "2026-03-01").param("createdUntil", "2026-02-01"), t).andExpect(status().isBadRequest)
    }

    @Test
    fun `이슈 항목은 닫은 시각과 작성자의 첫 기여 여부를 알린다`() {
        val f = project(); val newcomer = user()
        issue(f, "닫힌 이슈", f.member, "2026-01-05T00:00:00Z", state = State.CLOSED, closed = "2026-01-20T09:00:00Z")
        issue(f, "열린 이슈", f.member, "2026-01-06T00:00:00Z")
        issue(f, "신입의 첫 이슈", newcomer, "2026-02-01T00:00:00Z")
        val t = token(user())
        val body = call(get("${f.api}/issues").param("state", "all").param("sort", "number").param("order", "asc"), t).andExpect(status().isOk)
        body.andExpect(jsonPath("$.items[0].closedAt").value("2026-01-20T09:00:00Z")).andExpect(jsonPath("$.items[1].closedAt").doesNotExist()).andExpect(jsonPath("$.items[2].closedAt").doesNotExist())
        body.andExpect(jsonPath("$.items[0].authorFirstTime").value(true)).andExpect(jsonPath("$.items[1].authorFirstTime").value(false)).andExpect(jsonPath("$.items[2].authorFirstTime").value(true))
    }

    @Test
    fun `이전에 풀 리퀘스트를 낸 작성자의 첫 이슈는 첫 기여가 아니다`() {
        val f = project(); val author = user()
        pullRequest(f, author, "2026-01-01T00:00:00Z"); issue(f, "나중 이슈", author, "2026-02-01T00:00:00Z")
        call(get("${f.api}/issues").param("state", "all"), token(user())).andExpect(jsonPath("$.items[0].authorFirstTime").value(false))
    }

    // --- 활동 조회 ---

    @Test
    fun `commented는 사람이 댓글을 단 이슈와 풀 리퀘스트를 창 안의 첫 댓글 시각과 함께 돌려준다`() {
        val f = project(); val author = user(); val l = label(f, "bug")
        val a = issue(f, "A", author, "2026-01-01T00:00:00Z", labels = listOf(l)); val b = issue(f, "B", author, "2026-01-02T00:00:00Z"); val c = issue(f, "C", author, "2026-01-03T00:00:00Z")
        comment(a, f.member, "2025-12-31T10:00:00Z"); comment(a, f.member, "2026-02-03T10:00:00Z"); comment(a, f.member, "2026-02-04T10:00:00Z")
        comment(b, f.member, "2026-03-01T00:00:00Z"); comment(c, f.manager, "2026-02-10T00:00:00Z")
        val pr = pullRequest(f, author, "2026-01-04T00:00:00Z"); prComment(f, pr, f.member, "2026-02-05T00:00:00Z")
        call(get("${f.api}/activity").param("actor", f.member.loginId!!).param("since", "2026-02-01").param("until", "2026-03-01"), token(user()))
            .andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].number").value(a.number!!.toInt())).andExpect(jsonPath("$.items[0].isPullRequest").value(false)).andExpect(jsonPath("$.items[0].firstActivity").value("2026-02-03T10:00:00Z"))
            .andExpect(jsonPath("$.items[0].labels[0]").value("bug")).andExpect(jsonPath("$.items[0].url").value(org.hamcrest.Matchers.endsWith("/issue/${a.number}")))
            .andExpect(jsonPath("$.items[1].number").value(pr.number!!.toInt())).andExpect(jsonPath("$.items[1].isPullRequest").value(true)).andExpect(jsonPath("$.items[1].url").value(org.hamcrest.Matchers.endsWith("/pullRequest/${pr.number}")))
    }

    @Test
    fun `triaged는 다른 사람이 연 이슈에 댓글을 단 것만 돌려주고 풀 리퀘스트와 자기 이슈는 뺀다`() {
        val f = project(); val author = user()
        val others = issue(f, "남의 이슈", author, "2026-01-01T00:00:00Z"); val mine = issue(f, "내 이슈", f.member, "2026-01-02T00:00:00Z")
        comment(others, f.member, "2026-02-03T00:00:00Z"); comment(mine, f.member, "2026-02-04T00:00:00Z")
        val pr = pullRequest(f, author, "2026-01-04T00:00:00Z"); prComment(f, pr, f.member, "2026-02-05T00:00:00Z")
        call(get("${f.api}/activity").param("actor", f.member.loginId!!).param("kind", "triaged").param("since", "2026-02-01").param("until", "2026-03-01"), token(user()))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].number").value(others.number!!.toInt()))
    }

    @Test
    fun `밀어내기 후보는 메인테이너가 처음 남긴 문구 댓글의 주소이다`() {
        val f = project(); val author = user()
        val a = issue(f, "A", author, "2026-01-01T00:00:00Z")
        comment(a, author, "2026-02-01T00:00:00Z", "저는 이게 필요합니다"); comment(a, f.manager, "2026-02-02T00:00:00Z", "일반 답변"); comment(a, f.manager, "2026-02-03T00:00:00Z", "This is a Duplicate of #1"); comment(a, f.member, "2026-02-04T00:00:00Z", "duplicate 맞아요")
        val response = call(get("${f.api}/activity").param("actor", author.loginId!!).param("kind", "commented").param("since", "2026-02-01").param("until", "2026-03-01")
            .param("phrases", "duplicate,wontfix").param("maintainers", "${f.manager.loginId},${f.member.loginId}"), token(user())).andExpect(status().isOk)
        response.andExpect(jsonPath("$.items[0].pushbackCandidate").value(org.hamcrest.Matchers.containsString("/issue/${a.number}#comment-")))
        // 문구나 명단이 없으면 후보는 null이다
        call(get("${f.api}/activity").param("actor", author.loginId!!).param("since", "2026-02-01").param("until", "2026-03-01"), token(user())).andExpect(jsonPath("$.items[0].pushbackCandidate").doesNotExist())
        // 응답에는 댓글 본문이 들어 있지 않다
        org.junit.jupiter.api.Assertions.assertFalse(response.andReturn().response.contentAsString.contains("Duplicate of"))
    }

    @Test
    fun `활동 조회는 조건이 틀리면 400이고 프로젝트 읽기 권한과 토큰 범위를 따른다`() {
        val f = project(); val priv = project(ProjectScope.PRIVATE); val t = token(user())
        call(get("${f.api}/activity").param("since", "2026-02-01"), t).andExpect(status().isBadRequest)
        call(get("${f.api}/activity").param("actor", "nobody-here").param("since", "2026-02-01"), t).andExpect(status().isBadRequest)
        call(get("${f.api}/activity").param("actor", f.member.loginId!!), t).andExpect(status().isBadRequest)
        call(get("${f.api}/activity").param("actor", f.member.loginId!!).param("since", "2026-02-01").param("kind", "other"), t).andExpect(status().isBadRequest)
        call(get("${priv.api}/activity").param("actor", priv.member.loginId!!).param("since", "2026-02-01"), t).andExpect(status().isForbidden)
        call(get("${priv.api}/activity").param("actor", priv.member.loginId!!).param("since", "2026-02-01"), token(priv.member)).andExpect(status().isOk)
        call(get("${priv.api}/activity").param("actor", priv.member.loginId!!).param("since", "2026-02-01"), token(priv.member, "user")).andExpect(status().isForbidden)
        call(get("${f.api}/activity").param("actor", f.member.loginId!!).param("since", "2026-02-01"), null).andExpect(status().isUnauthorized)
    }
}
