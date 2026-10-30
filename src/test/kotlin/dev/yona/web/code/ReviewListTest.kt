package dev.yona.web.code

import dev.yona.support.selectFirst
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jxl.Workbook
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 프로젝트 리뷰 목록(`GET /:user/:project/reviews`)과 엑셀 내려받기를 검증한다.
 *
 * 원본: `app/controllers/ReviewThreadApp.java:36-190`, `app/models/support/ReviewSearchCondition.java`, `app/models/CommentThread.java:153-177`,
 * `app/views/reviewthread/list.scala.html`, `partial_list.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class ReviewListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository

    private var sequence = 0
    private var clock = 1_700_000_000_000L

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "rv$n", name = "리뷰어$n", email = "rv$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User) = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        return projects.save(Project(owner = "rvo$n", name = "rvp$n", vcs = "GIT", projectScope = scope)).also {
            menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        }
    }

    /** 커밋에 달린 코드 범위 스레드를 만든다. 첫 댓글이 [first], 이어지는 댓글은 [replies]이다. */
    private fun thread(
        project: Project, first: String, author: User = user(), state: ThreadState = ThreadState.OPEN, commitId: String = "abcdef1234567890",
        path: String = "src/App.kt", replies: List<Pair<User, String>> = emptyList()
    ): CommentThread {
        clock += 60_000
        val thread = threads.save(CodeCommentThread(commitId = commitId, path = path).also {
            it.project = project; it.state = state; it.createdDate = Date(clock); it.authorId = author.id; it.authorLoginId = author.loginId; it.authorName = author.name
        })
        var offset = 0L
        (listOf(author to first) + replies).forEach { (who, text) ->
            thread.reviewComments.add(
                reviewComments.save(ReviewComment(contents = text, createdDate = Date(clock + offset++), authorId = who.id, authorLoginId = who.loginId, authorName = who.name, thread = thread))
            )
        }
        return thread
    }

    private fun page(path: String, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun contents(doc: Document) = doc.select("ul.post-list-wrap li.post-item div.title-wrap a.title").map { it.text().trim() }

    private fun url(p: Project, query: String = "") = "/${p.owner}/${p.name}/reviews$query"

    @Test
    fun `로그인하지 않으면 로그인 화면으로 보낸다`() {
        val p = project()
        mockMvc.perform(get(url(p))).andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/loginform?redirectUrl=${url(p)}"))
    }

    @Test
    fun `프로젝트를 읽을 수 없으면 403이다`() {
        val p = project(ProjectScope.PRIVATE)
        mockMvc.perform(get(url(p)).session(login(user()))).andExpect(status().isForbidden)
    }

    @Test
    fun `기본은 열린 리뷰를 만든 시각 내림차순으로 보이고 한 줄에 번호와 내용과 링크가 있다`() {
        val p = project(); val me = user()
        val first = thread(p, "첫 리뷰"); val second = thread(p, "둘째 리뷰"); thread(p, "닫힌 리뷰", state = ThreadState.CLOSED)
        val doc = page(url(p), login(me))
        assertEquals(listOf("둘째 리뷰", "첫 리뷰"), contents(doc))
        val row = doc.selectFirst("li.post-item")!!
        assertEquals("${second.id}", row.selectFirst("span.post-id")!!.text())
        assertEquals("/${p.owner}/${p.name}/commit/abcdef1234567890#thread-${second.id}", row.selectFirst("a.title")!!.attr("href"))
        assertEquals(first.id.toString(), doc.select("li.post-item span.post-id").last()!!.text())
    }

    @Test
    fun `댓글 수는 첫 댓글을 뺀 개수이고 없으면 표시하지 않는다`() {
        val p = project(); val me = user(); val other = user()
        thread(p, "답 있음", replies = listOf(other to "답1", me to "답2")); thread(p, "답 없음")
        val rows = page(url(p), login(me)).select("li.post-item")
        assertEquals("2", rows.first { it.text().contains("답 있음") }.selectFirst(".item-count-groups .item-count")!!.text())
        assertTrue(rows.first { it.text().contains("답 없음") }.select(".item-count-groups").isEmpty())
    }

    @Test
    fun `첫 댓글 작성자를 한 줄에 보인다`() {
        val p = project(); val me = user(); val writer = user()
        thread(p, "작성자 확인", author = writer)
        val row = page(url(p), login(me)).selectFirst("li.post-item")!!
        assertEquals("/${writer.loginId}", row.selectFirst("a.avatar-wrap")!!.attr("href"))
        assertEquals(writer.loginId, row.selectFirst("a.avatar-wrap")!!.attr("title"))
        assertEquals("/${writer.loginId}", row.selectFirst(".infos a.infos-link-item")!!.attr("href"))
    }

    @Test
    fun `state가 CLOSED이면 닫힌 리뷰를 보이고 탭에 개수가 나온다`() {
        val p = project(); val me = user()
        thread(p, "열림1"); thread(p, "열림2"); thread(p, "닫힘", state = ThreadState.CLOSED)
        val doc = page(url(p, "?state=CLOSED"), login(me))
        assertEquals(listOf("닫힘"), contents(doc))
        assertEquals(listOf("2", "1"), doc.select(".col-lg-10 > ul.nav-tabs li .num-badge").map { it.text() })
        assertEquals("CLOSED", doc.selectFirst(".col-lg-10 > ul.nav-tabs a.nav-link.active")!!.attr("data-value"))
    }

    @Test
    fun `authorId와 participantId는 받은 값으로 거른다`() {
        val p = project(); val me = user(); val a = user(); val b = user()
        thread(p, "A가 만듦", author = a); thread(p, "B가 만들고 A가 답", author = b, replies = listOf(a to "답")); thread(p, "B만", author = b)
        assertEquals(listOf("A가 만듦"), contents(page(url(p, "?authorId=${a.id}"), login(me))))
        assertEquals(listOf("B가 만들고 A가 답", "A가 만듦"), contents(page(url(p, "?participantId=${a.id}"), login(me))))
    }

    @Test
    fun `빠른 검색 개수는 조건에서 작성자와 참여자만 바꿔 센다`() {
        val p = project(); val me = user(); val other = user()
        thread(p, "내가 만듦", author = me); thread(p, "남이 만들고 내가 답", author = other, replies = listOf(me to "답")); thread(p, "남의 것", author = other)
        val doc = page(url(p), login(me))
        assertEquals(listOf("3", "2", "1"), doc.select(".search-wrap ul.lst-stacked li .num-badge").map { it.text() })
        assertEquals(1, doc.select("ul.lst-stacked li.active").size)
        val p0 = p
        assertEquals("${p0.name} - Review - ${p0.owner}/${p0.name}", doc.selectFirst("title")!!.text())
        val mine = page(url(p, "?authorId=${me.id}"), login(me))
        assertEquals("${me.id}", mine.selectFirst("ul.lst-stacked li.active a")!!.attr("data-value"))
        assertEquals("authorId", mine.selectFirst("ul.lst-stacked li.active a")!!.attr("data-type"))
    }

    @Test
    fun `검색어는 댓글 내용과 커밋 번호와 파일 경로에서 대소문자 없이 찾는다`() {
        val p = project(); val me = user(); val s = login(me)
        thread(p, "Needle 내용"); thread(p, "경로", path = "lib/NEEDLE.kt"); thread(p, "커밋", commitId = "needle9999"); thread(p, "답에", replies = listOf(me to "needle 답")); thread(p, "상관없음")
        assertEquals(setOf("Needle 내용", "경로", "커밋", "답에"), contents(page(url(p, "?filter=needle"), s)).toSet())
    }

    @Test
    fun `orderDir로 정렬 방향을 바꾼다`() {
        val p = project(); val me = user()
        thread(p, "먼저"); thread(p, "나중")
        assertEquals(listOf("먼저", "나중"), contents(page(url(p, "?orderDir=asc"), login(me))))
        val sort = page(url(p, "?orderDir=asc"), login(me)).selectFirst("a[data-toggle=order]")!!
        assertEquals("createdDate", sort.attr("data-field"))
        assertEquals("desc", sort.attr("data-value"))
    }

    @Test
    fun `잘못된 상태나 방향은 400이고 허용하지 않는 정렬 기준은 만든 시각으로 본다`() {
        val p = project(); val s = login(user())
        thread(p, "하나")
        mockMvc.perform(get(url(p, "?state=nothing")).session(s)).andExpect(status().isBadRequest)
        mockMvc.perform(get(url(p, "?orderDir=sideways")).session(s)).andExpect(status().isBadRequest)
        assertEquals(listOf("하나"), contents(page(url(p, "?orderBy=password"), s)))
    }

    @Test
    fun `한 쪽은 15개이다`() {
        val p = project(); val s = login(user())
        (1..16).forEach { thread(p, "리뷰$it") }
        val first = page(url(p), s)
        assertEquals(15, contents(first).size)
        assertTrue(Regex("\"nTotalPages\"\\s*:\\s*2").containsMatchIn(first.html()), first.select("script").last()!!.data())
        assertEquals(listOf("리뷰1"), contents(page(url(p, "?pageNum=2"), s)))
    }

    @Test
    fun `리뷰가 없으면 안내 문구가 보이고 프로젝트 메뉴의 Review 탭이 활성이다`() {
        val p = project()
        val doc = page(url(p), login(user()))
        assertEquals("No review has been added.", doc.selectFirst(".error-wrap p")!!.text())
        assertEquals("/${p.owner}/${p.name}/reviews", doc.selectFirst(".project-menu-nav li.active a")!!.attr("href"))
    }

    @Test
    fun `엑셀 링크는 쪽 번호를 빼고 format=xls를 붙인다`() {
        val p = project()
        val href = page(url(p, "?pageNum=2&state=OPEN"), login(user())).selectFirst("a.ybtn.small")!!.attr("href")
        assertEquals("${url(p)}?state=OPEN&format=xls", href)
    }

    @Test
    fun `엑셀은 조건에 맞는 모든 스레드를 댓글마다 한 줄로 내려준다`() {
        val p = project(); val me = user(); val other = user()
        thread(p, "제목 글", author = me, commitId = "1234567890abcdef", replies = listOf(other to "응답 글", me to "제목 글"))
        thread(p, "닫힌 것", state = ThreadState.CLOSED)
        val result = mockMvc.perform(get(url(p, "?format=xls")).session(login(me))).andExpect(status().isOk).andReturn().response
        assertEquals("application/vnd.ms-excel", result.contentType)
        assertTrue(result.getHeader("Content-Disposition")!!.startsWith("attachment; filename*=UTF-8''${p.name}_reviews_"))
        assertTrue(result.getHeader("Content-Disposition")!!.endsWith(".xls"))
        val sheet = Workbook.getWorkbook(ByteArrayInputStream(result.contentAsByteArray)).getSheet(0)
        assertEquals(listOf("No", "COMMIT ID", "REVIEW ID", "REVIEW TITLE", "Thread Author", "Response Text", "Response", "REVIEW STATE", "is PullRequest?", "Date"), (0..9).map { sheet.getCell(it, 0).contents })
        assertEquals(4, sheet.rows)
        assertEquals(listOf("1", "1234567", "", "제목 글", me.name, "", "", "OPEN", "false"), (0..8).map { sheet.getCell(it, 1).contents }.mapIndexed { i, v -> if (i == 2) "" else v })
        assertEquals(listOf("", "", "응답 글", other.name), listOf(3, 4, 5, 6).map { sheet.getCell(it, 2).contents })
        assertEquals(listOf("제목 글", me.name, "", ""), listOf(3, 4, 5, 6).map { sheet.getCell(it, 3).contents })
    }
}
