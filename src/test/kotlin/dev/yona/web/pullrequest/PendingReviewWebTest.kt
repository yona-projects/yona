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
 * 임시 저장 리뷰의 화면과 요청을 검증한다(로드맵 X-26, `decisions.md` D-38): 줄 댓글을 임시로 달기, 변경 화면에서 본인에게만 보이는 것, 개요의 임시 리뷰 칸,
 * 임시 댓글 지우기와 리뷰 버리기, 리뷰 제출로 공개되는 것.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-pending-web"])
class PendingReviewWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var reader: dev.yona.domain.comment.PendingReviewReader
    @Autowired private lateinit var threadRepository: dev.yona.domain.comment.CommentThreadRepository
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



    /** 줄 댓글 요청이다. [pending]이면 "리뷰 시작/리뷰에 추가" 단추를 누른 것이다. */
    private fun lineComment(s: Setup, by: User, text: String, pending: Boolean, threadId: Long? = null) =
        post("${s.base}/pullRequest/${s.pr.id}/comments").session(login(by)).param("contents", text).apply {
            if (threadId == null) { param("path", "b.txt"); param("startSide", "B"); param("startLine", "1"); param("startColumn", "0"); param("endSide", "B"); param("endLine", "1"); param("endColumn", "1") }
            threadId?.let { param("thread.id", it.toString()) }
            if (pending) param("pending", "true")
        }

    private fun count(s: Setup, by: User) = reader.count(pullRequests.findById(s.pr.id!!).get(), by)

    private fun changes(s: Setup, by: User? = null) = page("${s.url}/changes", by?.let { login(it) })

    // --- 임시로 달기 ---

    @Test
    fun `임시로 단 줄 댓글은 알림 없이 저장되고 공개 목록에는 없다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "임시 내용", pending = true)).andExpect(status().is3xxRedirection)
        assertEquals(1L, count(s, s.member))
        assertEquals(0L, count(s, s.manager))
        assertTrue(threadRepository.findByPullRequestId(s.pr.id!!).isEmpty())
    }

    @Test
    fun `임시 줄 댓글은 변경 화면에서 작성자에게만 대기 중 표시와 함께 보인다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "내 임시 댓글", pending = true))
        val mine = changes(s, s.member)
        assertTrue(mine.html().contains("내 임시 댓글"))
        assertNotNull(mine.selectFirst(".thread-pending .pending-badge"), mine.select(".thread-pending").outerHtml())
        assertTrue(!changes(s, s.manager).html().contains("내 임시 댓글"), "다른 구성원에게는 보이지 않는다")
        assertTrue(!changes(s).html().contains("내 임시 댓글"), "로그인하지 않은 방문자에게도 보이지 않는다")
        assertTrue(!page(s.url, login(s.manager)).html().contains("내 임시 댓글"))
    }

    @Test
    fun `공개 스레드에 임시 답글을 달면 작성자에게만 보인다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.manager, "공개 댓글", pending = false))
        val thread = threadRepository.findByPullRequestId(s.pr.id!!).single()
        mockMvc.perform(lineComment(s, s.member, "임시 답글", pending = true, threadId = thread.id))
        em.flush(); em.clear() // 시험은 한 트랜잭션이라 요청이 영속성 컨텍스트를 나눠 쓴다. 실제 요청은 각각 따로이다
        assertTrue(changes(s, s.member).html().contains("임시 답글"))
        assertTrue(changes(s, s.member).html().contains("공개 댓글"))
        em.flush(); em.clear()
        assertTrue(!changes(s, s.manager).html().contains("임시 답글"))
        assertEquals(1, threadRepository.findByPullRequestId(s.pr.id!!).size)
    }

    @Test
    fun `임시 스레드에 이어 다는 답글도 임시이고 같은 스레드에 붙는다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "임시 하나", pending = true))
        val pendingThread = reader.threads(pullRequests.findById(s.pr.id!!).get(), s.member).single()
        mockMvc.perform(lineComment(s, s.member, "임시 둘", pending = false, threadId = pendingThread.id)).andExpect(status().is3xxRedirection)
        assertEquals(2L, count(s, s.member))
        assertEquals(1, reader.threads(pullRequests.findById(s.pr.id!!).get(), s.member).size)
    }

    @Test
    fun `내 임시 스레드만 있어도 리뷰 카드 영역이 있는 화면으로 그린다 (화면 스크립트가 없는 탭을 찾지 않게)`() {
        val s = setup()
        assertNull(changes(s, s.member).selectFirst(".review-wrap"))
        mockMvc.perform(lineComment(s, s.member, "임시 하나", pending = true))
        em.flush(); em.clear()
        assertNotNull(changes(s, s.member).selectFirst(".review-wrap #reviewcards-open"))
        assertNull(changes(s, s.manager).selectFirst(".review-wrap"), "남에게는 스레드가 없는 화면이다")
    }

    // --- 단추와 안내 ---

    @Test
    fun `변경 화면의 줄 댓글 입력 상자에 리뷰 시작 단추가 있고 임시 댓글이 있으면 리뷰에 추가로 바뀐다`() {
        val s = setup()
        val before = changes(s, s.member)
        assertEquals("Start a review", before.selectFirst("#review-form button[name=pending]")!!.text())
        assertNotNull(before.selectFirst("#review-form button[type=submit]:not([name=pending])"), "단일 댓글 단추는 그대로 있다")
        assertNull(before.selectFirst("#pending-review-bar"))
        mockMvc.perform(lineComment(s, s.member, "하나", pending = true))
        val after = changes(s, s.member)
        assertEquals("Add review comment", after.selectFirst("#review-form button[name=pending]")!!.text())
        assertEquals("1", after.selectFirst("#pending-review-bar")!!.attr("data-count"))
    }

    @Test
    fun `임시 리뷰가 있으면 개요에 개수와 버리기가 보이고 다른 사람에게는 보이지 않는다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "하나", pending = true)); mockMvc.perform(lineComment(s, s.member, "둘", pending = true))
        val mine = page(s.url, login(s.member)).selectFirst("#pending-review")!!
        assertEquals("2", mine.attr("data-count"))
        assertEquals("${s.url}/pendingReview/discard", mine.selectFirst("form#pending-discard")!!.attr("action"))
        assertNull(page(s.url, login(s.manager)).selectFirst("#pending-review"))
    }

    // --- 지우기와 버리기 ---

    @Test
    fun `임시 댓글을 지우고 리뷰를 버릴 수 있고 남의 임시 댓글은 지우지 못한다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "하나", pending = true)); mockMvc.perform(lineComment(s, s.member, "둘", pending = true))
        val ids = reader.threads(pullRequests.findById(s.pr.id!!).get(), s.member).flatMap { reader.threadComments(it) }.mapNotNull { it.id }
        mockMvc.perform(post("${s.url}/pendingReview/comments/${ids.first()}/delete").session(login(s.manager))).andExpect(status().isNotFound)
        assertEquals(2L, count(s, s.member))
        mockMvc.perform(post("${s.url}/pendingReview/comments/${ids.first()}/delete").session(login(s.member))).andExpect(status().is3xxRedirection)
        assertEquals(1L, count(s, s.member))
        mockMvc.perform(post("${s.url}/pendingReview/discard").session(login(s.member))).andExpect(status().is3xxRedirection)
        assertEquals(0L, count(s, s.member))
        mockMvc.perform(post("${s.url}/pendingReview/discard")).andExpect(status().is3xxRedirection)
    }

    // --- 제출 ---

    @Test
    fun `본문 없이 리뷰를 제출하면 임시 댓글이 모두에게 공개된다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "공개될 임시 댓글", pending = true))
        assertTrue(!changes(s, s.manager).html().contains("공개될 임시 댓글"))
        mockMvc.perform(post("${s.url}/reviews").session(login(s.member)).param("verdict", "COMMENTED")).andExpect(status().is3xxRedirection)
        assertEquals(0L, count(s, s.member))
        assertTrue(changes(s, s.manager).html().contains("공개될 임시 댓글"), "제출하면 다른 구성원에게도 보인다")
        assertNull(page(s.url, login(s.member)).selectFirst("#pending-review"))
    }

    @Test
    fun `임시 댓글도 본문도 없으면 제출할 수 없다`() {
        val s = setup()
        mockMvc.perform(post("${s.url}/reviews").session(login(s.member)).param("verdict", "COMMENTED")).andExpect(status().isBadRequest)
    }

    // --- 리뷰 댓글 고치기 (GitHub 기준: 작성자가 제출 전후 모두 고치고 "수정됨"으로 표시) ---

    private fun editComment(id: Long, by: User, text: String?) =
        post("/comments/review_comment/$id/edit").session(login(by)).apply { text?.let { param("contents", it) } }

    private fun publicComment(s: Setup, by: User, text: String): Long {
        mockMvc.perform(lineComment(s, by, text, pending = false)).andExpect(status().is3xxRedirection)
        em.flush(); em.clear()
        return threadRepository.findByPullRequestId(s.pr.id!!).flatMap { it.reviewComments }.first { it.contents == text }.id!!
    }

    @Test
    fun `작성자가 공개 댓글을 고치면 JSON으로 새 본문을 돌려주고 수정됨으로 표시된다`() {
        val s = setup()
        val id = publicComment(s, s.member, "처음 내용")
        mockMvc.perform(editComment(id, s.member, "고친 **내용**")).andExpect(status().isOk)
            .andExpect(jsonPath("$.edited").value(true)).andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("<strong>내용</strong>")))
        em.flush(); em.clear()
        val doc = changes(s, s.manager)
        assertTrue(doc.html().contains("고친"))
        assertNotNull(doc.selectFirst("#comment-$id .comment-edited"), "다른 사용자에게도 수정됨이 보인다")
    }

    @Test
    fun `지울 수 있는 사람(프로젝트 구성원)은 남의 댓글도 고칠 수 있고 구성원이 아닌 사람은 고칠 수 없다`() {
        val s = setup()
        val id = publicComment(s, s.manager, "매니저의 댓글")
        mockMvc.perform(editComment(id, s.member, "구성원이 고침")).andExpect(status().isOk)
        mockMvc.perform(editComment(id, user(), "외부인")).andExpect(status().isForbidden)
        mockMvc.perform(post("/comments/review_comment/$id/edit").param("contents", "x")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `빈 내용과 없는 댓글은 거절한다`() {
        val s = setup()
        val id = publicComment(s, s.member, "내용")
        mockMvc.perform(editComment(id, s.member, " ")).andExpect(status().isBadRequest)
        mockMvc.perform(editComment(id, s.member, null)).andExpect(status().isBadRequest)
        mockMvc.perform(editComment(9_999_999L, s.member, "x")).andExpect(status().isNotFound)
    }

    @Test
    fun `임시 댓글은 작성자만 고치고 고쳐도 임시이며 남에게는 찾을 수 없다`() {
        val s = setup()
        mockMvc.perform(lineComment(s, s.member, "임시 원문", pending = true))
        val id = reader.threads(pullRequests.findById(s.pr.id!!).get(), s.member).flatMap { reader.threadComments(it) }.single().id!!
        em.flush(); em.clear()
        mockMvc.perform(editComment(id, s.manager, "남의 임시")).andExpect(status().isNotFound)
        mockMvc.perform(editComment(id, s.member, "고친 임시")).andExpect(status().isOk)
        em.flush(); em.clear()
        assertEquals(1L, count(s, s.member))
        assertTrue(changes(s, s.member).html().contains("고친 임시"))
        em.flush(); em.clear()
        assertTrue(!changes(s, s.manager).html().contains("고친 임시"))
    }

    @Test
    fun `고치기 단추는 고칠 수 있는 사람에게만 있고 원문을 담고 있다`() {
        val s = setup()
        val mine = publicComment(s, s.member, "내 댓글 원문")
        val theirs = publicComment(s, s.manager, "매니저 댓글")
        em.flush(); em.clear()
        val doc = changes(s, s.member)
        assertEquals("/comments/review_comment/$mine/edit", doc.selectFirst("#comment-$mine button.comment-edit")!!.attr("data-url"))
        assertEquals("내 댓글 원문", doc.selectFirst("#comment-$mine textarea.comment-raw")!!.text())
        assertTrue(doc.selectFirst("#comment-$mine textarea.comment-raw")!!.attr("style").contains("display:none"), "원문 입력란은 화면에 보이면 안 된다(hidden 속성은 CSS가 덮어쓴다)")
        assertNotNull(doc.selectFirst("#comment-$theirs button.comment-edit"), "프로젝트 구성원은 지울 수 있는 댓글을 고칠 수도 있다")
        assertNull(changes(s, user()).selectFirst("button.comment-edit"), "구성원이 아닌 로그인 사용자에게는 없다")
        assertNull(changes(s).selectFirst("button.comment-edit"), "로그인하지 않으면 없다")
    }
}
