package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.ForkService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import dev.yona.support.selectFirst
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.jsoup.Jsoup
import java.util.Date
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 리뷰 요청의 화면과 요청을 검증한다(로드맵 X-27, `decisions.md` D-37): 보기 화면 오른쪽 열의 리뷰어 칸(대기 중, 최신 판정, 요청과 다시 요청과 취소),
 * 요청과 취소와 후보 검색 요청의 권한과 검사, 타임라인, 목록의 "리뷰 요청" 필터.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-review-request"])
class PullRequestReviewRequestWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pw$n", name = "이름pw$n", email = "pw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 원본(매니저 한 명, 일반 구성원 한 명)과 포크를 만들고 포크에서 원본으로 보낸 열린 풀 리퀘스트 하나가 있다. */
    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val member: User, val pr: PullRequest) {
        val base get() = "/${origin.owner}/${origin.name}"
        val url get() = "$base/pullRequest/${pr.number}"
    }

    private fun setup(): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "pwo$n", name = "pwp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MEMBER.id).get())) }
        val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "pwf$n", ProjectScope.PUBLIC, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청").param("body", "본문")
        ).andExpect(status().isSeeOther)
        return Setup(origin, fork, contributor, manager, member, pullRequests.findAll().maxByOrNull { it.id!! }!!)
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    /** 담당자 지정 요청이다(이슈 화면과 같은 JSON: `{"assignees":["로그인ID"]}`). 비우면 해제한다. */
    private fun assign(s: Setup, who: User?, by: User) =
        post("${s.url}/assignees").session(login(by)).contentType("application/json").content("""{"assignees":[${who?.let { "\"${it.loginId}\"" } ?: ""}]}""")

    /** 라벨 요청이다(이슈 화면의 일괄 변경과 같은 `attachingLabelIds`, `detachingLabelIds`). */
    private fun changeLabels(s: Setup, by: User, attach: List<IssueLabel> = emptyList(), detach: List<IssueLabel> = emptyList()) =
        post("${s.url}/labels").session(login(by)).apply {
            attach.forEach { param("attachingLabelIds", it.id.toString()) }
            detach.forEach { param("detachingLabelIds", it.id.toString()) }
        }


    /** 리뷰 요청이다. 본문은 `{"reviewer":"로그인ID"}`이다. */
    private fun requestReview(s: Setup, who: User, by: User) =
        post("${s.url}/reviewRequests").session(login(by)).contentType("application/json").content("""{"reviewer":"${who.loginId}"}""")

    private fun cancelRequest(s: Setup, who: User, by: User) = post("${s.url}/reviewRequests/${who.loginId}/delete").session(login(by))

    private fun submitReview(s: Setup, by: User, verdict: String, contents: String? = null) =
        post("${s.url}/reviews").session(login(by)).param("verdict", verdict).apply { contents?.let { param("contents", it) } }

    private fun pending(s: Setup) = pullRequests.findById(s.pr.id!!).get().reviewRequests.mapNotNull { it.loginId }.toSet()

    // --- 요청과 취소 ---

    @Test
    fun `매니저가 리뷰를 요청하면 JSON으로 대기 목록을 돌려주고 저장한다`() {
        val s = setup()
        mockMvc.perform(requestReview(s, s.member, s.manager)).andExpect(status().isOk)
            .andExpect(jsonPath("$.pending[0].loginId").value(s.member.loginId)).andExpect(jsonPath("$.pending[0].name").value(s.member.name))
        assertEquals(setOf(s.member.loginId), pending(s))
    }

    @Test
    fun `요청 취소도 JSON으로 대기 목록을 돌려준다`() {
        val s = setup()
        mockMvc.perform(requestReview(s, s.member, s.manager))
        mockMvc.perform(cancelRequest(s, s.member, s.manager)).andExpect(status().isOk).andExpect(jsonPath("$.pending").isEmpty)
        assertTrue(pending(s).isEmpty())
    }

    @Test
    fun `수정 권한이 없거나 로그인하지 않으면 요청하지 못한다`() {
        val s = setup(); val stranger = user()
        mockMvc.perform(requestReview(s, s.member, stranger)).andExpect(status().isForbidden)
        mockMvc.perform(post("${s.url}/reviewRequests").contentType("application/json").content("""{"reviewer":"${s.member.loginId}"}""")).andExpect(status().is3xxRedirection)
        assertTrue(pending(s).isEmpty())
    }

    @Test
    fun `없는 사용자와 작성자와 본문 없는 요청은 400이다`() {
        val s = setup()
        mockMvc.perform(post("${s.url}/reviewRequests").session(login(s.manager)).contentType("application/json").content("""{"reviewer":"nobody"}""")).andExpect(status().isBadRequest)
        mockMvc.perform(requestReview(s, s.contributor, s.manager)).andExpect(status().isBadRequest)
        mockMvc.perform(post("${s.url}/reviewRequests").session(login(s.manager)).contentType("application/json").content("{}")).andExpect(status().isBadRequest)
        assertTrue(pending(s).isEmpty())
    }

    @Test
    fun `후보 검색은 이름이나 로그인 ID로 찾고 작성자를 빼며 수정 권한이 필요하다`() {
        val s = setup()
        val found = mockMvc.perform(get("${s.url}/reviewRequestCandidates").session(login(s.manager)).param("query", "pw")).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(found.contains(s.member.loginId!!) && !found.contains("\"${s.contributor.loginId}\""), found)
        assertTrue(mockMvc.perform(get("${s.url}/reviewRequestCandidates").session(login(s.manager)).param("query", "")).andReturn().response.contentAsString.trim() == "[]")
        mockMvc.perform(get("${s.url}/reviewRequestCandidates").session(login(user())).param("query", "pw")).andExpect(status().isForbidden)
    }

    // --- 보기 화면 ---

    @Test
    fun `대기 중인 사람은 리뷰 칸에 대기 중으로 보이고 편집 권한자에게만 취소 단추가 있다`() {
        val s = setup()
        mockMvc.perform(requestReview(s, s.member, s.manager))
        val editor = page(s.url, login(s.manager)).selectFirst("#review-area .reviewer-row.pending[data-login-id=${s.member.loginId}]")!!
        assertEquals("Awaiting review", editor.selectFirst(".verdict-pending")!!.text())
        assertEquals("${s.url}/reviewRequests/${s.member.loginId}/delete", editor.selectFirst("button.review-cancel")!!.attr("data-url"))
        val visitor = page(s.url).selectFirst("#review-area .reviewer-row.pending[data-login-id=${s.member.loginId}]")!!
        assertNull(visitor.selectFirst("button.review-cancel"), "읽기만 하는 방문자에게는 취소 단추가 없다")
    }

    @Test
    fun `편집 권한자에게는 리뷰 요청 선택 상자가 있고 주소는 데이터 속성에 담긴다`() {
        val s = setup()
        val box = page(s.url, login(s.manager)).selectFirst("#review-area input#review-request")!!
        assertEquals("${s.url}/reviewRequests", box.attr("data-request-url"))
        assertEquals("${s.url}/reviewRequestCandidates", box.attr("data-candidates-url"))
        assertNull(page(s.url).selectFirst("#review-request"), "읽기만 하는 방문자에게는 선택 상자가 없다")
        assertNull(page(s.url, login(user())).selectFirst("#review-request"), "수정 권한이 없는 로그인 사용자에게도 없다")
    }

    @Test
    fun `리뷰한 사람은 최신 판정으로 보이고 편집 권한자에게는 다시 요청 단추가 있다`() {
        val s = setup()
        mockMvc.perform(submitReview(s, s.member, "CHANGES_REQUESTED", "고쳐 주세요")).andExpect(status().is3xxRedirection)
        val row = page(s.url, login(s.manager)).selectFirst("#review-area .reviewer-row[data-login-id=${s.member.loginId}]")!!
        assertEquals("CHANGES_REQUESTED", row.attr("data-verdict"))
        assertEquals("${s.url}/reviewRequests", row.selectFirst("button.review-rerequest")!!.attr("data-url"))
        assertEquals(s.member.loginId, row.selectFirst("button.review-rerequest")!!.attr("data-reviewer"))
        assertNull(page(s.url).selectFirst("#review-area .reviewer-row button.review-rerequest"))
    }

    @Test
    fun `다시 요청하면 최신 판정 줄 대신 대기 중 줄로 바뀌고 리뷰를 제출하면 다시 판정으로 돌아간다`() {
        val s = setup()
        mockMvc.perform(submitReview(s, s.member, "APPROVED"))
        mockMvc.perform(requestReview(s, s.member, s.manager))
        val d = page(s.url, login(s.manager))
        assertEquals(1, d.select("#review-area .reviewer-row[data-login-id=${s.member.loginId}]").size, "한 사람은 한 줄만 보인다")
        assertNotNull(d.selectFirst("#review-area .reviewer-row.pending[data-login-id=${s.member.loginId}]"))
        mockMvc.perform(submitReview(s, s.member, "COMMENTED", "다시 봤습니다"))
        val after = page(s.url, login(s.manager))
        assertNull(after.selectFirst("#review-area .reviewer-row.pending"))
        assertEquals("COMMENTED", after.selectFirst("#review-area .reviewer-row[data-login-id=${s.member.loginId}]")!!.attr("data-verdict"))
    }

    @Test
    fun `닫힌 풀 리퀘스트에서는 요청 선택 상자와 단추가 없다`() {
        val s = setup()
        mockMvc.perform(requestReview(s, s.member, s.manager))
        s.pr.state = dev.yona.domain.enumeration.State.CLOSED; pullRequests.save(s.pr); em.flush()
        val d = page(s.url, login(s.manager))
        assertNull(d.selectFirst("#review-request")); assertNull(d.selectFirst("button.review-cancel")); assertNull(d.selectFirst("button.review-rerequest"))
        mockMvc.perform(requestReview(s, s.manager, s.manager)).andExpect(status().isBadRequest)
    }

    // --- 타임라인 ---

    @Test
    fun `요청과 취소가 타임라인에 남는다`() {
        val s = setup()
        mockMvc.perform(requestReview(s, s.member, s.manager)); mockMvc.perform(cancelRequest(s, s.member, s.manager))
        val html = mockMvc.perform(get("${s.url}/timeline")).andExpect(status().isOk).andReturn().response.contentAsString
        val text = Jsoup.parse("<ul>$html</ul>").text()
        assertTrue(text.contains("requested a review from") && text.contains(s.member.name!!), text)
        assertTrue(text.contains("removed the review request for"), text)
    }

    // --- 목록 필터 ---

    @Test
    fun `목록의 리뷰 요청 필터는 나에게 요청된 풀 리퀘스트만 거른다`() {
        val s = setup()
        val other = pullRequests.save(PullRequest(title = "요청되지 않은 요청", body = "본문", toProject = s.origin, fromProject = s.fork, toBranch = "refs/heads/master", fromBranch = "refs/heads/other", contributor = s.contributor, state = dev.yona.domain.enumeration.State.OPEN, number = 9))
        mockMvc.perform(requestReview(s, s.member, s.manager))
        val list = "${s.base}/pullRequests"
        fun titles(query: String, by: User) = page("$list$query", login(by)).select("li.post-item .title-wrap a.title").map { it.text() }
        assertEquals(listOf("요청"), titles("?reviewRequestedId=${s.member.id}", s.member))
        assertEquals(emptyList<String>(), titles("?reviewRequestedId=${s.manager.id}", s.manager))
        assertEquals(2, titles("", s.member).size)
        val d = page("$list?reviewRequestedId=${s.member.id}", login(s.member))
        val options = d.select("#reviewRequested option").associate { it.attr("value") to it.text() }
        assertEquals("Review requested of me", options[s.member.id.toString()])
        assertEquals(s.member.id.toString(), d.selectFirst("#reviewRequested option[selected]")!!.attr("value"))
        assertNull(page(list).selectFirst("#reviewRequested option[value]:not([value=''])"), "로그인하지 않으면 '나에게' 선택지가 없다")
        assertNotNull(other.id)
    }
}
