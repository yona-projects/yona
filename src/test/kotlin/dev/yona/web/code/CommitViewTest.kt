package dev.yona.web.code

import dev.yona.support.selectFirst
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertNull
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.mock.web.MockHttpSession
import dev.yona.domain.enumeration.ResourceType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 커밋 보기와 두 커밋 비교 화면을 검증한다.
 *
 * 원본: `app/controllers/CodeHistoryApp.java`(`show`), `CompareApp.java`, `app/views/code/diff.scala.html`, `compare.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CommitViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var originalEmails: dev.yona.domain.notification.OriginalEmailRepository

    private var sequence = 0

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "cvo$n", name = "cvp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun user(loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = "$loginId@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path))).andExpect(status().isOk).andReturn().response.contentAsString)

    /** 커밋 두 개를 밀어 넣고 그 해시를 오래된 순서로 돌려준다. */
    private fun twoCommits(p: Project): List<String> {
        GitTestSupport.pushCommits(
            repositories, p,
            TestCommit("첫 커밋\n\n상세 설명", mapOf("a.txt" to "one\n".toByteArray())),
            TestCommit("둘째 커밋", mapOf("a.txt" to "one\ntwo\n".toByteArray()))
        )
        return page("/${p.owner}/${p.name}/commits").select(".btn-copy-commitId").map { it.attr("data-commitId") }.reversed()
    }

    private fun thread(p: Project, author: User, commitId: String, state: ThreadState, ranged: Boolean): Long {
        val t = if (ranged) CodeCommentThread(commitId = commitId, path = "a.txt", startSide = Side.B, startLine = 2, startColumn = 0, endSide = Side.B, endLine = 2, endColumn = 3)
        else NonRangedCodeCommentThread(commitId = commitId)
        t.project = p
        t.state = state
        t.authorId = author.id
        t.authorLoginId = author.loginId
        t.authorName = author.name
        t.createdDate = Date()
        threads.save(t)
        reviewComments.save(ReviewComment(contents = "리뷰 내용 ${t.id}", createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, thread = t))
        t.reviewComments.add(reviewComments.findAll().last())
        return requireNotNull(t.id)
    }

    @Test
    fun `커밋 보기에는 작성자와 메시지, 커밋 번호, 파일별 차이가 나온다`() {
        val p = project()
        val (first, second) = twoCommits(p)
        val doc = page("/${p.owner}/${p.name}/commit/$second")
        assertEquals("@$second", doc.selectFirst(".commitId")!!.text())
        assertEquals("둘째 커밋", doc.selectFirst(".commitMsg.short")!!.text())
        assertTrue(doc.select(".diff-body").first()!!.text().contains("two"))
        assertNotNull(doc.selectFirst("#watch-button"))
        assertTrue(page("/${p.owner}/${p.name}/commit/$first").selectFirst("pre.commitMsg.desc")!!.text().contains("상세 설명"))
        assertNull(doc.selectFirst(".code-browse-wrap > ul.nav-tabs"), "커밋 상세에는 탭 줄이 없다(A-516)")
        assertTrue(doc.selectFirst(".code-browse-wrap a.code-back-link")!!.attr("href").startsWith("/${p.owner}/${p.name}/commits"), "커밋 이력으로 되돌아가는 링크")
    }

    @Test
    fun `이메일이 빈 Git 커밋도 아바타를 그린다 (v1_6 email != null 분기)`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("메일 없음", mapOf("a.txt" to "x\n".toByteArray()), authorName = "no-mail", authorEmail = ""))
        val id = page("/${p.owner}/${p.name}/commits").select(".btn-copy-commitId").map { it.attr("data-commitId") }.single()
        val author = page("/${p.owner}/${p.name}/commit/$id").selectFirst(".commitAuthor")!!
        assertNotNull(author.selectFirst("span.avatar-wrap img"), author.html())
        assertEquals("no-mail", author.selectFirst("strong")!!.text())
    }

    @Test
    fun `리뷰 댓글의 첨부 JSON은 attachments 키로 감싼다 (v1_6 AttachmentApp_getFileList)`() {
        val p = project()
        val (_, second) = twoCommits(p)
        thread(p, user("reviewer2"), second, ThreadState.OPEN, true)
        val json = page("/${p.owner}/${p.name}/commit/$second").selectFirst(".comment-thread-wrap .attachments")!!.attr("data-attachments")
        // yona.Attachments._updateAttachments는 oRes.attachments를 읽는다
        assertEquals("""{"attachments":[]}""", json)
    }

    @Test
    fun `없는 커밋은 404이다`() {
        val p = project()
        twoCommits(p)
        mockMvc.perform(get("/${p.owner}/${p.name}/commit/${"0".repeat(40)}")).andExpect(status().isNotFound)
    }

    @Test
    fun `스레드는 줄 사이와 파일 단위 영역에, 리뷰 카드는 상태별 탭에 나온다`() {
        val p = project()
        val (_, second) = twoCommits(p)
        val author = user("reviewer")
        val ranged = thread(p, author, second, ThreadState.OPEN, true)
        val closed = thread(p, author, second, ThreadState.CLOSED, true)
        val flat = thread(p, author, second, ThreadState.OPEN, false)
        val doc = page("/${p.owner}/${p.name}/commit/$second")
        assertNotNull(doc.selectFirst(".diff-body #thread-$ranged"))
        assertTrue(doc.selectFirst("#thread-$closed")!!.hasClass("fold"))
        assertNotNull(doc.selectFirst(".non-ranged-threads-wrap #thread-$flat"))
        assertEquals(setOf("#thread-$ranged", "#thread-$flat"), doc.select("#reviewcards-open a.review-card").map { it.attr("href") }.toSet())
        assertEquals(listOf("#thread-$closed"), doc.select("#reviewcards-closed a.review-card").map { it.attr("href") })
    }

    @Test
    fun `파일 단위 스레드에도 로그인한 사용자에게는 답글 입력란이 나온다`() {
        val p = project()
        val (_, second) = twoCommits(p)
        val author = user("flatreviewer")
        val flat = thread(p, author, second, ThreadState.OPEN, false)
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", author.loginId!!).param("password", "1234")) }
        val html = mockMvc.perform(get("/${p.owner}/${p.name}/commit/$second").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        val form = Jsoup.parse(html).selectFirst(".non-ranged-threads-wrap #thread-$flat form.review-form")
        // v1.6 partial_nonrange_codecomment_thread는 partial_comment_form_on_thread를 그려 파일 단위 스레드에도 답글을 달 수 있다
        assertNotNull(form); assertEquals("thread.id", form.selectFirst("input[type=hidden]")!!.attr("name"))
        assertNull(page("/${p.owner}/${p.name}/commit/$second").selectFirst("#thread-$flat form.review-form"))
    }

    @Test
    fun `메일 답장으로 단 리뷰 댓글은 인용 원문을 접도록 표시한다`() {
        val p = project()
        val (_, second) = twoCommits(p)
        val author = user("mailreviewer")
        val viaMail = thread(p, author, second, ThreadState.OPEN, true)
        val normal = thread(p, author, second, ThreadState.OPEN, false)
        val mailComment = reviewComments.findAll().single { it.thread?.id == viaMail }
        originalEmails.save(dev.yona.domain.notification.OriginalEmail(messageId = "<m1@x>", resourceType = ResourceType.REVIEW_COMMENT, resourceId = mailComment.id.toString(), handledDate = Date()))
        val doc = page("/${p.owner}/${p.name}/commit/$second")
        // v1.6: data-via-email="@OriginalEmail.exists(comment.asResource)"
        assertEquals("true", doc.selectFirst("#thread-$viaMail .comment-body")!!.attr("data-via-email"))
        assertEquals("false", doc.selectFirst("#thread-$normal .comment-body")!!.attr("data-via-email"))
    }

    @Test
    fun `파일 보기의 댓글 수는 코드 범위 스레드도 센다 (R-41, v1_6은 경로에 슬래시를 붙여 파일 단위 스레드만 셌다)`() {
        val p = project()
        val (_, second) = twoCommits(p)
        val author = user("filecounter")
        thread(p, author, second, ThreadState.OPEN, true)
        thread(p, author, second, ThreadState.OPEN, false)
        assertEquals("2", page("/${p.owner}/${p.name}/code/master/a.txt").selectFirst(".number-of-comments")!!.text().trim())
    }

    @Test
    fun `커밋 이력에는 댓글 수가 나온다`() {
        val p = project()
        val (_, second) = twoCommits(p)
        val author = user("counter")
        thread(p, author, second, ThreadState.OPEN, true)
        thread(p, author, second, ThreadState.OPEN, false)
        assertEquals("2", page("/${p.owner}/${p.name}/commits").selectFirst("tbody tr .number-of-comments")!!.text().trim())
    }

    @Test
    fun `비교 화면은 코드로 돌아가는 링크와 변경 요약(파일 수, 추가와 삭제 줄)을 보인다(A-572)`() {
        val p = project()
        val (first, second) = twoCommits(p)
        val doc = page("/${p.owner}/${p.name}/compare/$first..$second")
        assertEquals("/${p.owner}/${p.name}/code", doc.selectFirst(".compare-back a.code-back-link")!!.attr("href"))
        assertEquals("1 changed files", doc.selectFirst(".compare-summary .summary-files")!!.text())
        assertEquals("+1", doc.selectFirst(".compare-summary .summary-added")!!.text())
        assertEquals("−0", doc.selectFirst(".compare-summary .summary-deleted")!!.text())
        // 변경이 없는 비교에는 요약이 없다
        assertTrue(page("/${p.owner}/${p.name}/compare/$second..$second").select(".compare-summary").isEmpty())
    }

    @Test
    fun `비교와 커밋 화면은 변경 파일 트리 모듈을 diff 모듈 뒤에 부른다(A-571)`() {
        val p = project()
        val (first, second) = twoCommits(p)
        for (path in listOf("/${p.owner}/${p.name}/compare/$first..$second", "/${p.owner}/${p.name}/commit/$second")) {
            val doc = page(path)
            val script = doc.select("script").joinToString("\n") { it.data() }
            assertTrue(script.contains("\"code.Diff\"") && script.contains("loadModule(\"code.DiffTree\""), path)
            assertTrue(script.indexOf("\"code.Diff\"") < script.indexOf("code.DiffTree"), "트리는 diff를 그리는 모듈 뒤에 부른다: $path")
            assertTrue(doc.select("script[type=module]").any { it.attr("src").endsWith("code.DiffTree.js") }, path)
        }
    }

    @Test
    fun `두 커밋을 비교하면 차이가 나오고 같으면 변경 없음 안내가 나온다`() {
        val p = project()
        val (first, second) = twoCommits(p)
        val doc = page("/${p.owner}/${p.name}/compare/$first..$second")
        // A-570: 긴 해시 두 개를 이어 붙인 한 줄이 아니라 비교하는 두 쪽을 이름과 짧은 번호로 나눠 보인다
        assertEquals(first.take(7), doc.selectFirst(".compare-range .compare-from .compare-name")!!.text())
        assertEquals(second.take(7), doc.selectFirst(".compare-range .compare-to .compare-name")!!.text())
        assertTrue(doc.select(".compare-range .compare-from .compare-id").isEmpty(), "이름이 번호 자체이면 번호를 되풀이하지 않는다")
        assertTrue(doc.selectFirst(".diff-body")!!.text().contains("two"))
        val named = page("/${p.owner}/${p.name}/compare/$first..master")
        assertEquals("master", named.selectFirst(".compare-range .compare-to .compare-name")!!.text())
        assertEquals(second.take(7), named.selectFirst(".compare-range .compare-to .compare-id")!!.text(), "브랜치나 태그 이름이면 가리키는 커밋 번호를 곁들인다")
        assertNotNull(page("/${p.owner}/${p.name}/compare/$second..$second").selectFirst(".alert"))
        mockMvc.perform(get("/${p.owner}/${p.name}/compare/${"0".repeat(40)}..$second")).andExpect(status().isNotFound)
    }

    @Test
    fun `커밋 diff 멘션 후보에는 커밋 작성자와 프로젝트 전체 항목이 나온다`() {
        val p = project()
        val (_, second) = twoCommits(p)
        user("tester")
        val body = mockMvc.perform(get("/${p.owner}/${p.name}/mentionListAtCommitDiff?commitId=$second&mentionType=user")).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("\"loginid\":\"tester\""), body)
        assertTrue(body.contains("${p.owner}/${p.name}"), body)
    }

    @Test
    fun `없는 커밋이어도 멘션 종류가 사용자가 아니면 빈 JSON을 돌려준다 (공식 v1_16_0 실행 결과)`() {
        val p = project()
        twoCommits(p)
        mockMvc.perform(get("/${p.owner}/${p.name}/mentionListAtCommitDiff?commitId=zzz")).andExpect(status().isOk).andExpect(content().json("{}", true))
        mockMvc.perform(get("/${p.owner}/${p.name}/mentionListAtCommitDiff?commitId=zzz&mentionType=issue")).andExpect(status().isOk)
        // 사용자 후보는 커밋 작성자가 필요하다. v1.6은 널 참조로 500이었고 v2는 404이다(A-72)
        mockMvc.perform(get("/${p.owner}/${p.name}/mentionListAtCommitDiff?commitId=zzz&mentionType=user")).andExpect(status().isNotFound)
    }
}
