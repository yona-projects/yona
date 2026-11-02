package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.SimpleCommentThread
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
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
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestReview
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
 * Magpie 변경 요청 계약(`contract:change-request`, 로드맵 X-23)을 채우는 PR 조회를 검증한다: 목록의 상태(`merged`, `all`)와 기간과 정렬 조건과 `authorFirstTime`,
 * 토론 조회(`GET .../pullrequests/{n}/discussion`)와 리뷰 활동 조회(`GET .../activity/reviews`). 기간은 `since` 이상 `until` 미만이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-pr-magpie"])
class ExternalPullRequestMagpieApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0
    private fun at(iso: String) = Date.from(Instant.parse(iso))

    private fun user(): User {
        val n = ++sequence; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pmg$n", name = "이름pmg$n", email = "pmg$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private class Fixture(val project: Project, val manager: User, val member: User) {
        val api get() = "/-_-api/v1/owners/${project.owner}/projects/${project.name}"
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Fixture {
        val n = ++sequence
        val project = projects.save(Project(owner = "pmgo$n", name = "pmgp$n", vcs = "GIT", projectScope = scope, createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        return Fixture(project, manager, member)
    }

    private fun token(user: User, vararg scopes: String): String = (tokens.create(user, "t", (scopes.toList().ifEmpty { listOf("repo") }).toSet(), null) as TokenCreation.Created).value
    private fun call(builder: MockHttpServletRequestBuilder, token: String?) = mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.also { failures.clear() })

    private fun pull(f: Fixture, author: User, created: String, state: State = State.OPEN, updated: String = created, merged: String? = null): PullRequest {
        val number = (em.createQuery("select max(p.number) from PullRequest p where p.toProject.id = :id", Long::class.javaObjectType).setParameter("id", f.project.id).singleResult ?: 0L) + 1
        return PullRequest(
            title = "PR $number", toProject = f.project, fromProject = f.project, toBranch = "master", fromBranch = "topic$number", contributor = author, created = at(created), updated = at(updated),
            received = (merged ?: if (state == State.CLOSED) updated else null)?.let { at(it) }, number = number, state = state
        ).also { em.persist(it); em.flush() }
    }

    // --- 목록 조건 ---

    @Test
    fun `목록은 merged와 all 상태를 받는다`() {
        val f = project(); val a = user()
        pull(f, a, "2026-01-01T00:00:00Z"); pull(f, a, "2026-01-02T00:00:00Z", State.MERGED, merged = "2026-01-05T00:00:00Z"); pull(f, a, "2026-01-03T00:00:00Z", State.CLOSED)
        val t = token(user())
        call(get("${f.api}/pullrequests").param("state", "merged"), t).andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].state").value("merged"))
        call(get("${f.api}/pullrequests").param("state", "all"), t).andExpect(jsonPath("$.totalCount").value(3))
        call(get("${f.api}/pullrequests").param("state", "nope"), t).andExpect(status().isBadRequest)
    }

    @Test
    fun `목록은 만든 기간과 오래됨과 병합 기간으로 거르고 갱신 순으로 정렬한다`() {
        val f = project(); val a = user()
        val p1 = pull(f, a, "2026-01-10T00:00:00Z", updated = "2026-01-12T00:00:00Z"); val p2 = pull(f, a, "2026-02-10T00:00:00Z", updated = "2026-02-11T00:00:00Z")
        val p3 = pull(f, a, "2026-03-10T00:00:00Z", State.MERGED, updated = "2026-03-15T00:00:00Z", merged = "2026-03-15T00:00:00Z")
        val t = token(user())
        call(get("${f.api}/pullrequests").param("state", "all").param("createdSince", "2026-02-01").param("createdUntil", "2026-03-01"), t).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].number").value(p2.number!!.toInt()))
        call(get("${f.api}/pullrequests").param("updatedBefore", "2026-02-01"), t).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].number").value(p1.number!!.toInt()))
        call(get("${f.api}/pullrequests").param("state", "merged").param("mergedSince", "2026-03-01").param("mergedUntil", "2026-04-01"), t).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].number").value(p3.number!!.toInt()))
        call(get("${f.api}/pullrequests").param("state", "merged").param("mergedUntil", "2026-03-01"), t).andExpect(jsonPath("$.totalCount").value(0))
        call(get("${f.api}/pullrequests").param("state", "all").param("sort", "updated"), t).andExpect(jsonPath("$.items[0].number").value(p3.number!!.toInt())).andExpect(jsonPath("$.items[2].number").value(p1.number!!.toInt()))
        call(get("${f.api}/pullrequests").param("createdSince", "어제"), t).andExpect(status().isBadRequest)
        call(get("${f.api}/pullrequests").param("createdSince", "2026-03-01").param("createdUntil", "2026-02-01"), t).andExpect(status().isBadRequest)
        call(get("${f.api}/pullrequests").param("sort", "password"), t).andExpect(status().isBadRequest)
    }

    @Test
    fun `목록 항목은 작성자의 첫 기여 여부를 알린다`() {
        val f = project(); val newcomer = user(); val veteran = user()
        pull(f, veteran, "2026-01-01T00:00:00Z"); pull(f, veteran, "2026-01-05T00:00:00Z"); pull(f, newcomer, "2026-02-01T00:00:00Z")
        call(get("${f.api}/pullrequests").param("state", "all").param("sort", "created"), token(user()))
            .andExpect(jsonPath("$.items[?(@.author.loginId=='${newcomer.loginId}')].authorFirstTime").value(true))
            .andExpect(jsonPath("$.items[?(@.createdAt =~ /^2026-01-05.*/)].authorFirstTime").value(false))
    }

    // --- 토론 ---

    @Test
    fun `토론은 댓글과 리뷰를 종류와 함께 시각 순으로 돌려주고 참여자와 풀리지 않은 스레드 수를 알린다`() {
        val f = project(); val author = user(); val pr = pull(f, author, "2026-01-01T00:00:00Z")
        fun thread(state: ThreadState, by: User, at: String, body: String): SimpleCommentThread {
            val t = SimpleCommentThread().apply { project = f.project; pullRequest = pr; authorId = by.id; authorLoginId = by.loginId; authorName = by.name; createdDate = at(at); this.state = state }
            em.persist(t); val c = ReviewComment(contents = body, createdDate = at(at), authorId = by.id, authorLoginId = by.loginId, authorName = by.name, thread = t)
            em.persist(c); t.reviewComments.add(c); return t
        }
        thread(ThreadState.OPEN, f.member, "2026-01-02T00:00:00Z", "열린 스레드"); thread(ThreadState.CLOSED, author, "2026-01-03T00:00:00Z", "닫힌 스레드")
        em.persist(PullRequestReview(pullRequest = pr, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.APPROVED, contents = "좋습니다", createdDate = at("2026-01-04T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = pr, authorId = f.member.id, authorLoginId = f.member.loginId, verdict = ReviewVerdict.CHANGES_REQUESTED, contents = "고쳐 주세요", createdDate = at("2026-01-05T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = pr, authorId = author.id, authorLoginId = author.loginId, verdict = ReviewVerdict.COMMENTED, contents = "의견", createdDate = at("2026-01-06T00:00:00Z")))
        em.flush()
        call(get("${f.api}/pullrequests/${pr.number}/discussion"), token(user())).andExpect(status().isOk)
            .andExpect(jsonPath("$.comments.length()").value(5))
            .andExpect(jsonPath("$.comments[0].kind").value("comment")).andExpect(jsonPath("$.comments[0].author").value(f.member.loginId)).andExpect(jsonPath("$.comments[0].body").value("열린 스레드")).andExpect(jsonPath("$.comments[0].date").value("2026-01-02T00:00:00Z"))
            .andExpect(jsonPath("$.comments[2].kind").value("approval")).andExpect(jsonPath("$.comments[3].kind").value("change-request")).andExpect(jsonPath("$.comments[4].kind").value("review"))
            .andExpect(jsonPath("$.unresolvedCount").value(1))
            .andExpect(jsonPath("$.participants.length()").value(3))
    }

    @Test
    fun `활동이 없는 풀 리퀘스트의 토론은 빈 목록이고 해제한 리뷰는 담지 않는다`() {
        val f = project(); val author = user(); val pr = pull(f, author, "2026-01-01T00:00:00Z")
        em.persist(PullRequestReview(pullRequest = pr, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.APPROVED, contents = "승인", createdDate = at("2026-01-04T00:00:00Z"), dismissedDate = at("2026-01-05T00:00:00Z"), dismissedByLoginId = f.manager.loginId))
        em.flush()
        call(get("${f.api}/pullrequests/${pr.number}/discussion"), token(user())).andExpect(jsonPath("$.comments.length()").value(0)).andExpect(jsonPath("$.unresolvedCount").value(0)).andExpect(jsonPath("$.participants.length()").value(0))
        call(get("${f.api}/pullrequests/999/discussion"), token(user())).andExpect(status().isNotFound)
    }

    // --- 리뷰 활동 ---

    @Test
    fun `리뷰 활동은 사람이 창 안에서 처음 리뷰한 풀 리퀘스트와 댓글 수를 돌려준다`() {
        val f = project(); val author = user(); val a = pull(f, author, "2026-01-01T00:00:00Z"); val b = pull(f, author, "2026-01-02T00:00:00Z"); val c = pull(f, author, "2026-01-03T00:00:00Z")
        em.persist(PullRequestReview(pullRequest = a, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.APPROVED, contents = "좋아요", createdDate = at("2026-02-02T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = a, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.COMMENTED, contents = "추가", createdDate = at("2026-02-03T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = b, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.APPROVED, contents = "창 밖", createdDate = at("2026-03-02T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = c, authorId = f.member.id, authorLoginId = f.member.loginId, verdict = ReviewVerdict.APPROVED, contents = "남의 리뷰", createdDate = at("2026-02-05T00:00:00Z")))
        val thread = SimpleCommentThread().apply { project = f.project; pullRequest = a; authorId = f.manager.id; authorLoginId = f.manager.loginId; authorName = f.manager.name; createdDate = at("2026-02-02T01:00:00Z") }
        em.persist(thread); em.persist(ReviewComment(contents = "인라인", createdDate = at("2026-02-02T01:00:00Z"), authorId = f.manager.id, authorLoginId = f.manager.loginId, authorName = f.manager.name, thread = thread)); em.flush()
        call(get("${f.api}/activity/reviews").param("reviewer", f.manager.loginId!!).param("since", "2026-02-01").param("until", "2026-03-01"), token(user())).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].pullRequestNumber").value(a.number!!.toInt())).andExpect(jsonPath("$.items[0].firstReviewAt").value("2026-02-02T00:00:00Z"))
            .andExpect(jsonPath("$.items[0].reviewer").value(f.manager.loginId)).andExpect(jsonPath("$.items[0].bodyLength").value(5)).andExpect(jsonPath("$.items[0].inlineCommentCount").value(1))
            .andExpect(jsonPath("$.items[0].url").value(org.hamcrest.Matchers.endsWith("/pullRequest/${a.number}")))
        call(get("${f.api}/activity/reviews").param("reviewer", "nobody-here").param("since", "2026-02-01"), token(user())).andExpect(status().isBadRequest)
    }

    @Test
    fun `리뷰 활동은 사람을 생략하면 프로젝트 전체의 리뷰를 돌려주고 membersOnly이면 구성원만이다`() {
        val f = project(); val author = user(); val outsider = user(); val pr = pull(f, author, "2026-01-01T00:00:00Z")
        em.persist(PullRequestReview(pullRequest = pr, authorId = f.manager.id, authorLoginId = f.manager.loginId, verdict = ReviewVerdict.APPROVED, contents = "구성원 리뷰", createdDate = at("2026-02-02T00:00:00Z")))
        em.persist(PullRequestReview(pullRequest = pr, authorId = outsider.id, authorLoginId = outsider.loginId, verdict = ReviewVerdict.COMMENTED, contents = "외부 리뷰", createdDate = at("2026-02-03T00:00:00Z")))
        em.flush()
        val t = token(user())
        call(get("${f.api}/activity/reviews").param("since", "2026-02-01").param("until", "2026-03-01"), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].reviewer").value(f.manager.loginId)).andExpect(jsonPath("$.items[1].reviewer").value(outsider.loginId))
        call(get("${f.api}/activity/reviews").param("since", "2026-02-01").param("until", "2026-03-01").param("membersOnly", "true"), t)
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].reviewer").value(f.manager.loginId))
    }
}
