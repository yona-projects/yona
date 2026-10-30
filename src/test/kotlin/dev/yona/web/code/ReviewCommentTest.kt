package dev.yona.web.code

import dev.yona.support.selectFirst
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
import dev.yona.domain.notification.NotificationMessages
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.Attachment
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코드 리뷰 댓글(커밋과 풀 리퀘스트), 스레드 열기와 닫기, 댓글 삭제를 검증한다.
 *
 * 원본: `app/controllers/CodeHistoryApp.java`(`newComment`), `PullRequestApp.java`(`newComment`), `CommentThreadApp.java`, `CommentApp.java`,
 * `app/models/ReviewComment.java`, `NotificationEvent.java`(`forNewCommitComment`, `forNewComment`, `afterStateChanged`).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class ReviewCommentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var attachmentRepository: dev.yona.domain.misc.AttachmentRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository
    @Autowired private lateinit var notificationMessages: NotificationMessages

    private var sequence = 0

    private fun user(email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "rc$n", name = "이름rc$n", email = email ?: "rc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private class Setup(val project: Project, val commitId: String, val committer: User, val manager: User, val managerSession: MockHttpSession)

    /** 두 파일을 가진 커밋 하나가 있는 프로젝트이다. 커밋 작성자는 가입한 사용자이다. */
    private fun commitSetup(): Setup {
        val n = ++sequence
        val project = projects.save(Project(owner = "rco$n", name = "rcp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        val committer = user()
        GitTestSupport.pushCommits(
            repositories, project, TestCommit("커밋", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray()), authorName = "작성자", authorEmail = committer.email!!)
        )
        val commitId = repositories.openRepository(project).use { it.resolve("refs/heads/master")!!.name }
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        return Setup(project, commitId, committer, manager, login(manager))
    }

    private fun commentUrl(s: Setup) = "/${s.project.owner}/${s.project.name}/commit/${s.commitId}/comments"

    private fun range(path: String = "a.txt") = mapOf(
        "path" to path, "startSide" to "B", "startLine" to "2", "startColumn" to "0", "endSide" to "B", "endLine" to "2", "endColumn" to "5"
    )

    private fun send(url: String, session: MockHttpSession, contents: String, extra: Map<String, String> = emptyMap()) =
        post(url).session(session).param("contents", contents).apply { extra.forEach { (k, v) -> param(k, v) } }

    @Test
    fun `커밋에 범위 없이 댓글을 달면 파일 단위 스레드가 열리고 커밋 보기에 나온다`() {
        val s = commitSetup()
        val mentioned = user()
        val result = mockMvc.perform(send(commentUrl(s), s.managerSession, "전체 의견 @${mentioned.loginId}")).andExpect(status().isSeeOther).andReturn()
        val thread = threads.findAll().single() as NonRangedCodeCommentThread
        assertEquals(ThreadState.OPEN, thread.state); assertEquals(s.commitId, thread.commitId); assertNull(thread.prevCommitId)
        assertEquals(s.manager.id, thread.authorId); assertEquals(s.project.id, thread.project?.id)
        val comment = reviewComments.findAll().single()
        assertEquals("/${s.project.owner}/${s.project.name}/commit/${s.commitId}#comment-${comment.id}", result.response.getHeader("Location"))
        val doc: Document = Jsoup.parse(mockMvc.perform(get("/${s.project.owner}/${s.project.name}/commit/${s.commitId}").session(s.managerSession)).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst(".non-ranged-threads-wrap #thread-${thread.id}"))
        assertTrue(doc.selectFirst("#thread-${thread.id} .comment-body")!!.text().contains("전체 의견"))
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_REVIEW_COMMENT }
        assertTrue(note.receivers.any { it.id == mentioned.id }); assertTrue(note.receivers.none { it.id == s.manager.id })
        assertEquals("Re: [${s.project.name}] 커밋 (${s.commitId.take(7)})", note.title)
    }

    @Test
    fun `범위가 있으면 코드 범위 스레드가 열리고 커밋 작성자가 코드 작성자로 들어가며 줄 사이에 나온다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.managerSession, "이 줄이 이상해요", range())).andExpect(status().isSeeOther)
        val thread = threads.findAll().single() as CodeCommentThread
        assertEquals(listOf("a.txt", Side.B, 2, 0, Side.B, 2, 5), listOf(thread.path, thread.startSide, thread.startLine, thread.startColumn, thread.endSide, thread.endLine, thread.endColumn))
        assertEquals(setOf(s.committer.id), thread.codeAuthors.map { it.id }.toSet())
        val doc = Jsoup.parse(mockMvc.perform(get("/${s.project.owner}/${s.project.name}/commit/${s.commitId}").session(s.managerSession)).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst(".diff-body #thread-${thread.id}[data-range-startline=2]"))
    }

    @Test
    fun `범위의 숫자가 하나라도 없으면 파일 단위 스레드이다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.managerSession, "의견", range() - "endColumn")).andExpect(status().isSeeOther)
        assertTrue(threads.findAll().single() is NonRangedCodeCommentThread)
    }

    @Test
    fun `thread_id를 주면 그 스레드에 이어 단다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.managerSession, "첫 댓글", range())).andExpect(status().isSeeOther)
        val thread = threads.findAll().single()
        mockMvc.perform(send(commentUrl(s), login(user()), "답글", mapOf("thread.id" to thread.id.toString()))).andExpect(status().isSeeOther)
        assertEquals(1, threads.count()); assertEquals(listOf("첫 댓글", "답글"), reviewComments.findAll().sortedBy { it.id }.map { it.contents })
    }

    @Test
    fun `내용이 비었거나 커밋이 없거나 로그인하지 않았으면 만들지 않는다`() {
        val s = commitSetup()
        mockMvc.perform(send(commentUrl(s), s.managerSession, "")).andExpect(status().isBadRequest)
        mockMvc.perform(send("/${s.project.owner}/${s.project.name}/commit/${"0".repeat(40)}/comments", s.managerSession, "의견")).andExpect(status().isNotFound)
        mockMvc.perform(post(commentUrl(s)).param("contents", "의견")).andExpect(status().isForbidden)
        assertEquals(0, threads.count())
    }

    @Test
    fun `스레드를 닫고 다시 열 수 있고 알림이 남으며 권한이 없으면 403이다`() {
        val s = commitSetup()
        val watcher = user()
        mockMvc.perform(send(commentUrl(s), login(watcher), "의견")).andExpect(status().isSeeOther)
        val thread = threads.findAll().single()
        mockMvc.perform(post("/threads/${thread.id}/close").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/threads/${thread.id}/close").session(s.managerSession)).andExpect(status().isOk)
        assertEquals(ThreadState.CLOSED, threads.findById(thread.id!!).get().state)
        val note = notificationEvents.findAll().single { it.eventType == EventType.REVIEW_THREAD_STATE_CHANGED }
        assertEquals("OPEN", note.oldValue); assertEquals("CLOSED", note.newValue); assertTrue(note.receivers.any { it.id == watcher.id })
        assertEquals(messageOf("notification.reviewthread.closed"), notificationMessages.message(note, LANG))
        mockMvc.perform(post("/threads/${thread.id}/open").session(s.managerSession)).andExpect(status().isOk)
        assertEquals(ThreadState.OPEN, threads.findById(thread.id!!).get().state)
        mockMvc.perform(post("/threads/99999/open").session(s.managerSession)).andExpect(status().isNotFound)
    }

    @Test
    fun `댓글을 지우고 마지막 댓글이면 스레드도 지워진다`() {
        val s = commitSetup()
        val author = user()
        mockMvc.perform(send(commentUrl(s), login(author), "첫 댓글", range())).andExpect(status().isSeeOther)
        val thread = threads.findAll().single()
        mockMvc.perform(send(commentUrl(s), s.managerSession, "답글", mapOf("thread.id" to thread.id.toString()))).andExpect(status().isSeeOther)
        val (first, second) = reviewComments.findAll().sortedBy { it.id }
        mockMvc.perform(delete("/comments/review_comment/${first.id}").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/comments/review_comment/${first.id}").session(login(author))).andExpect(status().isOk)
        assertEquals(1, threads.count())
        mockMvc.perform(delete("/comments/review_comment/${second.id}").session(s.managerSession)).andExpect(status().isOk)
        assertEquals(0, threads.count()); assertEquals(0, reviewComments.count())
        mockMvc.perform(delete("/comments/review_comment/99999").session(s.managerSession)).andExpect(status().isBadRequest)
        mockMvc.perform(delete("/comments/issue_post/1").session(s.managerSession)).andExpect(status().isBadRequest)
    }

    @Test
    fun `첫 댓글 알림 문구에는 댓글이 달린 줄까지의 차이가 함께 나온다`() {
        val s = commitSetup()
        GitTestSupport.appendCommits(repositories, s.project, TestCommit("수정", mapOf("a.txt" to "line1\nCHANGED\nline3\n".toByteArray()), authorEmail = s.committer.email!!))
        val second = repositories.openRepository(s.project).use { it.resolve("refs/heads/master")!!.name }
        mockMvc.perform(send("/${s.project.owner}/${s.project.name}/commit/$second/comments", s.managerSession, "여기 확인", range() + ("endLine" to "1"))).andExpect(status().isSeeOther)
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_REVIEW_COMMENT }
        val message = notificationMessages.message(note, LANG)!!
        assertTrue(message.startsWith(messageOf("notification.reviewthread.inTheFile", "a.txt") + "\n```diff\n> @@ -1, 3 +1, 3 @@\n>  line1\n> -line2\n> +CHANGED\n```\n\n여기 확인\n\n```diff\n>  line3\n```\n"), message)
    }

    @Test
    fun `풀 리퀘스트 댓글은 폼에 적힌 임시 첨부만 옮기고 나머지는 남긴다 (v1_6 moveOnlySelected)`() {
        val n = ++sequence
        val origin = projects.save(Project(owner = "rao$n", name = "rap$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\n".toByteArray())))
        val manager = user(); projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "raf$n", ProjectScope.PUBLIC, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청").param("body", "본문")
        ).andExpect(status().isSeeOther)
        val pr: PullRequest = pullRequests.findAll().single()
        fun temp(hash: String) = attachmentRepository.save(Attachment(name = "$hash.txt", hash = "$hash-$n", containerType = ResourceType.USER, containerId = manager.id!!, mimeType = "text/plain", size = 1L, createdDate = java.util.Date(), ownerLoginId = manager.loginId))
        val selected = temp("sel"); val untouched = temp("untouched")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/${origin.owner}/${origin.name}/pullRequest/${pr.id}/comments").session(login(manager))
                .param("contents", "첨부 확인").param("temporaryUploadFiles", selected.id.toString())
        ).andExpect(status().isSeeOther)
        val comment = reviewComments.findAll().single()
        assertEquals(ResourceType.REVIEW_COMMENT, attachmentRepository.findById(selected.id!!).get().containerType)
        assertEquals(comment.id, attachmentRepository.findById(selected.id!!).get().containerId)
        assertEquals(ResourceType.USER, attachmentRepository.findById(untouched.id!!).get().containerType)
    }

    @Test
    fun `풀 리퀘스트에 댓글을 달면 풀 리퀘스트에 스레드가 붙고 변경 화면에 나온다`() {
        val n = ++sequence
        val origin = projects.save(Project(owner = "rpo$n", name = "rpp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user(); projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "rpf$n", ProjectScope.PUBLIC, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청").param("body", "본문")
        ).andExpect(status().isSeeOther)
        val pr: PullRequest = pullRequests.findAll().single()
        val session = login(manager)

        val result = mockMvc.perform(send("/${origin.owner}/${origin.name}/pullRequest/${pr.id}/comments", session, "전체 변경 의견")).andExpect(status().isSeeOther).andReturn()
        val thread = threads.findAll().single() as NonRangedCodeCommentThread
        assertEquals(pr.id, thread.pullRequest?.id); assertEquals(pr.mergedCommitIdTo, thread.commitId); assertEquals(pr.mergedCommitIdFrom, thread.prevCommitId)
        assertEquals("/${origin.owner}/${origin.name}/pullRequest/${pr.number}/changes#comment-${reviewComments.findAll().single().id}", result.response.getHeader("Location"))
        val doc = Jsoup.parse(mockMvc.perform(get("/${origin.owner}/${origin.name}/pullRequest/${pr.number}/changes").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst("#thread-${thread.id}"))
        // 스레드 하나는 한 번만 그린다 (코드 스레드용 조각과 파일 단위 스레드용 조각을 둘 다 그리지 않는다)
        assertEquals(1, doc.select("#thread-${thread.id}").size)
        // v1.6 풀 리퀘스트 변경 화면은 파일 단위 스레드도 상태 배지와 답글 입력란이 있는 전체 조각으로 그린다 (커밋 화면은 가벼운 조각)
        assertEquals("open", doc.selectFirst("#thread-${thread.id}")!!.attr("data-state"))
        assertNotNull(doc.selectFirst("#thread-${thread.id} .thread-header"))
        assertEquals("/${origin.owner}/${origin.name}/pullRequest/${pr.id}/comments?commitId=${pr.mergedCommitIdTo}", doc.selectFirst("#thread-${thread.id} form")!!.attr("action"))
        assertEquals(1, doc.select("#reviewcards-open a.review-card").size)
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_REVIEW_COMMENT }
        assertTrue(note.receivers.any { it.id == contributor.id }); assertTrue(note.receivers.none { it.id == manager.id })
    }

    /** 포크에서 `b.txt`를 추가해 보낸 풀 리퀘스트와 그 원본의 관리자이다. */
    private class PullSetup(val origin: Project, val pr: PullRequest, val session: MockHttpSession)

    private fun pullSetup(): PullSetup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "rpo$n", name = "rpp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user(); projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "rpf$n", ProjectScope.PUBLIC, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b1\nb2\n".toByteArray())))
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청").param("body", "본문")
        ).andExpect(status().isSeeOther)
        return PullSetup(origin, pullRequests.findAll().single(), login(manager))
    }

    @Test
    fun `풀 리퀘스트 변경 화면에서 줄에 단 댓글은 어긋남이 아니고 줄 사이에 나온다`() {
        val s = pullSetup(); val pr = s.pr
        val ranged = range("b.txt") + mapOf("startLine" to "1", "endLine" to "1", "prevCommitId" to pr.mergedCommitIdFrom!!, "commitId" to pr.mergedCommitIdTo!!)
        mockMvc.perform(send("/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.id}/comments", s.session, "이 줄을 확인해 주세요", ranged)).andExpect(status().isSeeOther)
        val thread = threads.findAll().single() as CodeCommentThread
        assertEquals(pr.id, thread.pullRequest?.id); assertEquals("b.txt", thread.path)
        val doc = Jsoup.parse(mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}/changes").session(s.session)).andExpect(status().isOk).andReturn().response.contentAsString)
        val card = doc.selectFirst("#reviewcards-open a.review-card")!!
        assertFalse(card.hasClass("outdated"), "병합 기준 커밋에 단 댓글이 어긋남으로 나온다: ${card.className()}")
        assertNotNull(doc.selectFirst("#thread-${thread.id}"), "줄 사이 스레드가 나오지 않는다")
    }

    @Test
    fun `쿼리의 commitId가 있으면 커밋 하나에 단 댓글이고 본문의 commitId는 쓰지 않는다`() {
        val s = pullSetup(); val pr = s.pr
        val base = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.id}/comments"
        val byQueryResult = mockMvc.perform(send("$base?commitId=${pr.mergedCommitIdTo}", s.session, "커밋 의견")).andExpect(status().isSeeOther).andReturn()
        // v1.6 newComment는 쿼리의 commitId가 있을 때만 커밋 하나의 변경 화면(specificChange)으로 돌려보낸다
        assertTrue(byQueryResult.response.getHeader("Location")!!.contains("/pullRequest/${pr.number}/changes/${pr.mergedCommitIdTo}#comment-"), byQueryResult.response.getHeader("Location"))
        val byQuery = threads.findAll().single() as NonRangedCodeCommentThread
        assertEquals(pr.mergedCommitIdTo, byQuery.commitId); assertEquals("", byQuery.prevCommitId)

        mockMvc.perform(send(base, s.session, "본문 값은 무시", mapOf("commitId" to "0000000000000000000000000000000000000000"))).andExpect(status().isSeeOther)
        val byBody = threads.findAll().single { it.id != byQuery.id } as NonRangedCodeCommentThread
        assertEquals(pr.mergedCommitIdTo, byBody.commitId); assertEquals(pr.mergedCommitIdFrom, byBody.prevCommitId)
    }

    @Test
    fun `풀 리퀘스트 댓글 내용이 비면 400이고 프로젝트 홈 메뉴가 활성이다 (v1_6 render(키, project))`() {
        val s = pullSetup()
        val html = mockMvc.perform(send("/${s.origin.owner}/${s.origin.name}/pullRequest/${s.pr.id}/comments", s.session, "")).andExpect(status().isBadRequest)
            .andReturn().response.contentAsString
        val active = org.jsoup.Jsoup.parse(html).select("ul.project-menu-nav > li.active > a").attr("href")
        assertEquals("/${s.origin.owner}/${s.origin.name}", active)
    }

    private fun messageOf(key: String, vararg args: Any) = messages.getMessage(key, args, java.util.Locale.forLanguageTag(LANG))

    private companion object {
        const val LANG = "ko-KR"
    }

    @Autowired private lateinit var messages: org.springframework.context.MessageSource
}
