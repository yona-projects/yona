package dev.yona.web.code

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.NotificationEventRepository
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
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 커밋 댓글과 풀 리퀘스트 줄 댓글(X-7 9단계 보완, `decisions.md` A-388)을 [ReviewCommentTest]의 Git 흐름과 같은 모양으로 확인한다:
 * 커밋에 단 파일 단위 댓글과 줄 범위 댓글, 커밋 작성자가 코드 작성자로 들어가는 것, 알림, 풀 리퀘스트 변경 화면의 줄 사이 스레드이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-review-comment"])
class HgReviewCommentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hrc$n-$stamp", name = "이름hrc$n", email = email ?: "hrc$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun dir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    private fun hgProject(label: String): Project {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val p = projects.save(Project(owner = "hro$n-$stamp", name = "$label$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun manager(p: Project): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun range(path: String = "a.txt") = mapOf("path" to path, "startSide" to "B", "startLine" to "2", "startColumn" to "0", "endSide" to "B", "endLine" to "2", "endColumn" to "5")

    private fun send(url: String, session: MockHttpSession, contents: String, extra: Map<String, String> = emptyMap()) =
        post(url).session(session).param("contents", contents).apply { extra.forEach { (k, v) -> param(k, v) } }

    private class Setup(val project: Project, val commitId: String, val committer: User, val manager: User, val session: MockHttpSession)

    /** 파일 하나를 가진 커밋 하나가 있는 프로젝트이다. 커밋 작성자는 가입한 사용자의 이메일이다. */
    private fun commitSetup(): Setup {
        val project = hgProject("hrp")
        val committer = user()
        val commitId = HgTestSupport.commit(dir(project), "커밋", mapOf("a.txt" to "line1\nline2\nline3\n"), "2026-11-02 10:00 +0900", "작성자 <${committer.email}>")
        HgTestSupport.bookmark(dir(project), "main")
        val manager = manager(project)
        return Setup(project, commitId, committer, manager, login(manager))
    }

    private fun commentUrl(s: Setup) = "/${s.project.owner}/${s.project.name}/commit/${s.commitId}/comments"

    @Test
    fun `커밋에 범위 없이 댓글을 달면 파일 단위 스레드가 열리고 커밋 보기에 나오며 멘션이 알림을 받는다`() {
        val s = commitSetup(); val mentioned = user()
        val result = mockMvc.perform(send(commentUrl(s), s.session, "전체 의견 @${mentioned.loginId}")).andExpect(status().isSeeOther).andReturn()
        val thread = threads.findAll().single() as NonRangedCodeCommentThread
        assertEquals(ThreadState.OPEN, thread.state); assertEquals(s.commitId, thread.commitId)
        val comment = reviewComments.findAll().single()
        assertEquals("/${s.project.owner}/${s.project.name}/commit/${s.commitId}#comment-${comment.id}", result.response.getHeader("Location"))
        val doc = Jsoup.parse(mockMvc.perform(get("/${s.project.owner}/${s.project.name}/commit/${s.commitId}").session(s.session)).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst(".non-ranged-threads-wrap #thread-${thread.id}"))
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_REVIEW_COMMENT }
        assertTrue(note.receivers.any { it.id == mentioned.id }); assertTrue(note.receivers.none { it.id == s.manager.id })
        // 알림 제목의 짧은 번호는 Mercurial의 12자이다(A-378)
        assertEquals("Re: [${s.project.name}] 커밋 (${s.commitId.take(12)})", note.title)
    }

    @Test
    fun `범위가 있으면 코드 범위 스레드가 열리고 커밋 작성자가 코드 작성자로 들어가며 줄 사이에 나온다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.session, "이 줄이 이상해요", range())).andExpect(status().isSeeOther)
        val thread = threads.findAll().single() as CodeCommentThread
        assertEquals(listOf("a.txt", Side.B, 2, 0, Side.B, 2, 5), listOf(thread.path, thread.startSide, thread.startLine, thread.startColumn, thread.endSide, thread.endLine, thread.endColumn))
        assertEquals(setOf(s.committer.id), thread.codeAuthors.map { it.id }.toSet())
        val doc = Jsoup.parse(mockMvc.perform(get("/${s.project.owner}/${s.project.name}/commit/${s.commitId}").session(s.session)).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst(".diff-body #thread-${thread.id}[data-range-startline=2]"))
        // diff 머리글의 짧은 커밋 번호는 Mercurial의 12자이다(A-378)
        assertEquals(12, doc.select(".diff-partial-commit-id a").first { it.text().isNotBlank() }.text().trim().length)
    }

    @Test
    fun `없는 커밋이나 빈 내용이나 로그인하지 않은 요청은 댓글을 만들지 않는다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.session, "")).andExpect(status().isBadRequest)
        mockMvc.perform(send("/${s.project.owner}/${s.project.name}/commit/${"f".repeat(40)}/comments", s.session, "내용")).andExpect(status().isNotFound)
        mockMvc.perform(post(commentUrl(s)).param("contents", "익명")).andExpect(status().isForbidden)
        assertEquals(0, threads.findAll().size)
    }

    @Test
    fun `풀 리퀘스트 변경 화면에서 줄에 단 댓글은 어긋남이 아니고 줄 사이에 나온다`() {
        val origin = hgProject("hro")
        HgTestSupport.commit(dir(origin), "기반", mapOf("a.txt" to "line1\nline2\nline3\n")); HgTestSupport.bookmark(dir(origin), "main")
        val managerUser = manager(origin); val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "hrf${++sequence}", ProjectScope.PUBLIC, contributor)
        HgTestSupport.hg(dir(fork), "bookmark", "-r", "main", "feature"); HgTestSupport.hg(dir(fork), "update", "-C", "feature")
        HgTestSupport.commit(dir(fork), "새 파일", mapOf("b.txt" to "b1\nb2\n"), "2026-11-02 10:00 +0900")
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/feature").param("toBranch", "refs/heads/main").param("title", "요청").param("body", "본문")
        ).andExpect(status().isSeeOther)
        val pr: PullRequest = pullRequests.findAll().single(); val session = login(managerUser)
        val ranged = range("b.txt") + mapOf("startLine" to "1", "endLine" to "1", "prevCommitId" to pr.mergedCommitIdFrom!!, "commitId" to pr.mergedCommitIdTo!!)
        mockMvc.perform(send("/${origin.owner}/${origin.name}/pullRequest/${pr.id}/comments", session, "이 줄을 확인해 주세요", ranged)).andExpect(status().isSeeOther)
        val thread = threads.findAll().single() as CodeCommentThread
        assertEquals(pr.id, thread.pullRequest?.id); assertEquals("b.txt", thread.path)
        val doc = Jsoup.parse(mockMvc.perform(get("/${origin.owner}/${origin.name}/pullRequest/${pr.number}/changes").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        val card = doc.selectFirst("#reviewcards-open a.review-card")!!
        assertFalse(card.hasClass("outdated"), "병합 기준 커밋에 단 댓글이 어긋남으로 나온다: ${card.className()}")
        assertNotNull(doc.selectFirst("#thread-${thread.id}"), "줄 사이 스레드가 나오지 않는다")
    }
}
