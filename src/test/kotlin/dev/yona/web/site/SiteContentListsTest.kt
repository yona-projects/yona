package dev.yona.web.site

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사이트 관리의 프로젝트, 게시글, 이슈 목록과 프로젝트 삭제를 검증한다.
 *
 * 원본: `app/controllers/SiteApp.java:142-156, 214-231`, `app/views/site/projectList.scala.html`, `postList.scala.html`,
 * `issueList.scala.html`, `app/models/Project.java:151-159`, `app/models/Issue.java:559-563`.
 */
@AutoConfigureMockMvc
@Transactional
class SiteContentListsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var issues: IssueRepository

    private var sequence = 0
    private var clock = 1_700_000_000_000L
    private var number = 0L

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sc$n", name = "이름$n", email = "sc$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun admin(): MockHttpSession {
        val user = newUser(); siteAdmins.save(SiteAdmin(admin = user))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun project(owner: User, name: String, overview: String? = null, scope: ProjectScope = ProjectScope.PRIVATE): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, overview = overview, createdDate = Date(++clock)))

    private fun page(session: MockHttpSession, path: String, vararg params: Pair<String, String>): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session).apply { params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun projectNames(doc: Document) = doc.select("ul.project-list-wrap li.listitem a.project-name").map { it.text().trim() }

    // --- 프로젝트 ---

    @Test
    fun `사이트 관리자가 아니면 목록은 403이다`() {
        listOf("/sites/projectList", "/sites/postList", "/sites/issueList").forEach {
            mockMvc.perform(get(it)).andExpect(status().isForbidden)
        }
    }

    @Test
    fun `프로젝트 목록은 비공개 프로젝트까지 만든 최근 순으로 25개씩 보인다`() {
        val owner = newUser()
        (1..30).forEach { project(owner, "p$it") }
        val session = admin()
        val first = page(session, "/sites/projectList")
        assertEquals(25, projectNames(first).size)
        assertEquals("${owner.loginId}/p30", projectNames(first).first())
        assertEquals("/sites/projectList", first.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
    }

    @Test
    fun `프로젝트 목록의 pageNum은 0부터 센다`() {
        val owner = newUser()
        (1..30).forEach { project(owner, "q$it") }
        val session = admin()
        val total = projects.count().toInt()
        assertEquals(25, projectNames(page(session, "/sites/projectList", "pageNum" to "0")).size)
        assertEquals(minOf(25, total - 25), projectNames(page(session, "/sites/projectList", "pageNum" to "1")).size)
    }

    @Test
    fun `프로젝트 검색은 이름에서 대소문자 없이 부분 일치한다`() {
        val owner = newUser(); project(owner, "Alpha-Search"); project(owner, "beta", overview = "alpha 개요"); project(owner, "gamma")
        val names = projectNames(page(admin(), "/sites/projectList", "filter" to "alpha"))
        assertEquals(listOf("${owner.loginId}/Alpha-Search"), names)
        assertEquals("alpha", page(admin(), "/sites/projectList", "filter" to "alpha").selectFirst("input[name=filter]")!!.attr("value"))
    }

    @Test
    fun `프로젝트 줄에는 개요와 만든 날짜와 삭제 버튼이 있다`() {
        val owner = newUser(); val p = project(owner, "rowproj", overview = "개요 문장")
        val row = page(admin(), "/sites/projectList", "filter" to "rowproj").selectFirst("li.listitem")!!
        assertEquals("개요 문장", row.select(".listitem-col")[1].text().trim())
        assertTrue(row.select(".listitem-col")[2].text().trim().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val button = row.selectFirst("button[data-toggle=delete-project]")!!
        assertEquals("${owner.loginId}/rowproj", button.attr("data-project-name"))
        assertEquals("/sites/project/delete/${p.id}", button.attr("data-href"))
    }

    @Test
    fun `프로젝트를 삭제하면 목록으로 돌아간다`() {
        val owner = newUser(); val p = project(owner, "todelete")
        mockMvc.perform(delete("/sites/project/delete/${p.id}").session(admin())).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/projectList"))
        assertFalse(projects.existsById(p.id!!))
    }

    @Test
    fun `프로젝트 삭제는 사이트 관리자만 하고 없는 프로젝트는 404이다`() {
        val owner = newUser(); val p = project(owner, "keepme")
        mockMvc.perform(delete("/sites/project/delete/${p.id}")).andExpect(status().isForbidden)
        assertTrue(projects.existsById(p.id!!))
        mockMvc.perform(delete("/sites/project/delete/9999999").session(admin())).andExpect(status().isNotFound)
    }

    // --- 게시글 ---

    @Test
    fun `게시글 목록은 모든 프로젝트의 글을 최근 순으로 30개씩 보인다`() {
        val owner = newUser(); val p = project(owner, "board")
        (1..32).forEach { postings.save(Posting(project = p, number = ++number, title = "글$it", body = "b", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, numOfComments = it, createdDate = Date(++clock), updatedDate = Date(clock))) }
        val doc = page(admin(), "/sites/postList")
        val titles = doc.select("ul.post-list-wrap li.listitem a.post-title").map { it.text().trim() }
        assertEquals(30, titles.size); assertEquals("글32", titles.first())
        assertEquals(2, page(admin(), "/sites/postList", "pageNum" to "2").select("li.listitem").size)
    }

    @Test
    fun `게시글 줄에는 프로젝트와 제목과 작성자와 댓글 수가 있다`() {
        val owner = newUser(); val p = project(owner, "board2")
        val post = postings.save(Posting(project = p, number = ++number, title = "제목", body = "b", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, numOfComments = 7, createdDate = Date(), updatedDate = Date()))
        val row = page(admin(), "/sites/postList").select("li.listitem").first { it.text().contains("제목") }
        assertEquals("${owner.loginId}/board2", row.selectFirst("a.post-project")!!.text().trim())
        assertEquals("/${owner.loginId}/board2/post/${post.number}", row.selectFirst("a.post-title")!!.attr("href"))
        assertEquals("/${owner.loginId}", row.select(".post-meta-wrap a.post-meta-item").first()!!.attr("href"))
        assertEquals("7", row.selectFirst(".post-comments")!!.text().trim())
        assertEquals("/${owner.loginId}/board2/post/${post.number}#comments", row.selectFirst(".post-comments a")!!.attr("href"))
    }

    // --- 이슈 ---

    private fun issue(project: Project, title: String, author: User, state: State = State.OPEN) = issues.save(Issue(
        project = project, number = ++number, title = title, body = "b", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
        numOfComments = 3, weight = 0, createdDate = Date(++clock), updatedDate = Date(clock)
    ))

    @Test
    fun `이슈 목록은 열린 이슈가 기본이고 state로 닫힌 이슈를 본다`() {
        val owner = newUser(); val p = project(owner, "iss")
        issue(p, "열림", owner); issue(p, "닫힘", owner, State.CLOSED)
        val session = admin()
        fun titles(doc: Document) = doc.select("ul.post-list-wrap li.listitem a.post-title").map { it.text().trim() }
        assertEquals(listOf("열림"), titles(page(session, "/sites/issueList")))
        assertEquals(listOf("닫힘"), titles(page(session, "/sites/issueList", "state" to "closed")))
        assertEquals(listOf("open", "closed"), page(session, "/sites/issueList").select(".span10 > ul.nav-tabs > li a").map { it.attr("href").substringAfter("state=") })
        assertEquals("open", page(session, "/sites/issueList").selectFirst(".span10 > ul.nav-tabs > li.active a")!!.attr("href").substringAfter("state="))
    }

    @Test
    fun `이슈 목록의 state는 대문자로 바꿔 읽고 all이면 비며 모르는 값은 400이다`() {
        val owner = newUser(); val p = project(owner, "iss2"); issue(p, "열림", owner)
        val session = admin()
        assertTrue(page(session, "/sites/issueList", "state" to "OPEN").select("li.listitem a.post-title").isNotEmpty())
        assertTrue(page(session, "/sites/issueList", "state" to "all").select("li.listitem").isEmpty())
        mockMvc.perform(get("/sites/issueList").session(session).param("state", "nope")).andExpect(status().isBadRequest)
    }

    @Test
    fun `이슈 줄에는 프로젝트와 제목과 작성자와 댓글 수가 있다`() {
        val owner = newUser(); val p = project(owner, "iss3"); val one = issue(p, "줄 확인", owner)
        val row = page(admin(), "/sites/issueList").select("li.listitem").first { it.text().contains("줄 확인") }
        assertEquals("/${owner.loginId}/iss3/issue/${one.number}", row.selectFirst("a.post-title")!!.attr("href"))
        assertEquals("3", row.selectFirst(".post-comments")!!.text().trim())
        assertEquals("/${owner.loginId}/iss3", row.selectFirst("a.post-project")!!.attr("href"))
    }

    @Test
    fun `기본 아바타는 alt와 크기 없이 그림만 낸다`() {
        val owner = newUser(); val p = project(owner, "avt"); issue(p, "아바타 확인", owner)
        val img = page(admin(), "/sites/issueList").select("li.listitem").first { it.text().contains("아바타 확인") }.selectFirst(".post-meta-wrap a.avatar-wrap img")!!
        // v1.6 issueList.scala.html: user.avatarUrl == DEFAULT_AVATAR_URL이면 src만 있고, 아니면 alt, width, height가 붙는다.
        if (img.attr("src") == "/assets/images/default-avatar-128.png") {
            assertTrue(!img.hasAttr("alt") && !img.hasAttr("width") && !img.hasAttr("height"), img.outerHtml())
        } else {
            assertEquals("16", img.attr("width")); assertTrue(img.hasAttr("alt"))
        }
    }
}
