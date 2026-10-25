package dev.yona.web.issue

import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.misc.FavoriteIssue
import dev.yona.domain.misc.FavoriteIssueRepository
import dev.yona.domain.notification.Mention
import dev.yona.domain.notification.MentionRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 내 이슈(`GET /user/issues`)를 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:68-111, 180-188, 201-209, 211-247`, `app/models/support/SearchCondition.java:229-253`,
 * `app/views/issue/my_list.scala.html`, `my_partial_search.scala.html`, `my_partial_list.scala.html`, `my_partial_list_quicksearch.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class MyIssuesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var sharers: IssueSharerRepository
    @Autowired private lateinit var favorites: FavoriteIssueRepository
    @Autowired private lateinit var mentions: MentionRepository
    @Autowired private lateinit var labelCategories: IssueLabelCategoryRepository
    @Autowired private lateinit var issueLabels: IssueLabelRepository

    private var sequence = 0
    private var number = 0L
    private var clock = 1_700_000_000_000L

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mi$n", name = "mi$n", email = "mi$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, name: String, scope: ProjectScope = ProjectScope.PUBLIC): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, createdDate = Date()))

    private fun issue(
        project: Project, title: String, author: User, assignee: User? = null, state: State = State.OPEN, draft: Boolean = false,
        parent: Issue? = null, body: String = "본문", updated: Long = ++clock
    ): Issue = issues.save(Issue(
        project = project, number = ++number, title = title, body = body, state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
        assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0, isDraft = draft, parent = parent,
        createdDate = Date(updated), updatedDate = Date(updated)
    ))

    private fun page(session: MockHttpSession, vararg params: Pair<String, String>): Document =
        Jsoup.parse(mockMvc.perform(get("/user/issues").session(session).apply { params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun titles(doc: Document) = doc.select("ul.post-list-wrap.my-issues li.post-item a.title:not(.project)").map { it.text().trim() }.filter { it.isNotEmpty() }

    @Test
    fun `로그인하지 않으면 로그인 폼으로 보낸다`() {
        mockMvc.perform(get("/user/issues")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/loginform?redirectUrl=/user/issues"))
    }

    @Test
    fun `조건이 없으면 내게 할당된 열린 이슈를 모든 프로젝트에서 갱신 최근 순으로 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a"); val b = project(other, "b", ProjectScope.PRIVATE)
        issue(a, "첫째", other, assignee = me); issue(b, "비공개 프로젝트의 둘째", other, assignee = me); issue(a, "남의 것", other, assignee = other)
        issue(a, "닫힌 것", other, assignee = me, state = State.CLOSED)
        assertEquals(listOf("비공개 프로젝트의 둘째", "첫째"), titles(page(login(me))))
    }

    @Test
    fun `내 이슈 탭이 활성이고 열림과 닫힘 개수가 있다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "열림1", other, assignee = me); issue(a, "열림2", other, assignee = me); issue(a, "닫힘", other, assignee = me, state = State.CLOSED)
        val doc = page(login(me))
        assertEquals("/user/issues", doc.selectFirst(".page-wrap > ul.nav-tabs li.active a")!!.attr("href"))
        assertEquals(listOf("2", "1"), doc.select("#span10 > ul.nav-tabs li .num-badge").map { it.text() })
        assertEquals("open", doc.selectFirst("#span10 > ul.nav-tabs li.active a")!!.attr("state"))
    }

    @Test
    fun `state가 closed이면 닫힌 이슈를 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "열림", other, assignee = me); issue(a, "닫힘", other, assignee = me, state = State.CLOSED)
        val doc = page(login(me), "state" to "closed")
        assertEquals(listOf("닫힘"), titles(doc))
        assertEquals("closed", doc.selectFirst("#span10 > ul.nav-tabs li.active a")!!.attr("state"))
    }

    @Test
    fun `이슈가 없으면 안내 문구가 보인다`() {
        val doc = page(login(newUser()))
        assertEquals("No issue found", doc.selectFirst("#span10 .error-wrap p")!!.text())
        assertTrue(doc.select("ul.post-list-wrap").isEmpty())
    }

    @Test
    fun `authorId를 주면 번호와 관계없이 내가 작성한 이슈를 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "내가 쓴 것", me); issue(a, "남이 쓴 것", other, assignee = me)
        assertEquals(listOf("내가 쓴 것"), titles(page(login(me), "authorId" to "${other.id}")))
    }

    @Test
    fun `commenterId를 주면 내가 댓글을 쓴 이슈를 모든 프로젝트에서 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a"); val b = project(other, "b")
        val one = issue(a, "댓글 단 이슈1", other); val two = issue(b, "댓글 단 이슈2", other); issue(a, "댓글 없는 이슈", other)
        listOf(one, two).forEach { comments.save(IssueComment(contents = "c", authorId = me.id, authorLoginId = me.loginId, issue = it, projectId = it.project!!.id!!, createdDate = Date())) }
        assertEquals(setOf("댓글 단 이슈1", "댓글 단 이슈2"), titles(page(login(me), "commenterId" to "1")).toSet())
    }

    @Test
    fun `mentionId를 주면 내가 멘션된 이슈와 멘션된 댓글의 이슈를 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        val post = issue(a, "본문에서 멘션", other); val viaComment = issue(a, "댓글에서 멘션", other); issue(a, "멘션 없음", other)
        val comment = comments.save(IssueComment(contents = "@${me.loginId}", authorId = other.id, issue = viaComment, projectId = a.id!!, createdDate = Date()))
        mentions.save(Mention(resourceType = ResourceType.ISSUE_POST, resourceId = post.id.toString(), user = me))
        mentions.save(Mention(resourceType = ResourceType.ISSUE_COMMENT, resourceId = comment.id.toString(), user = me))
        assertEquals(setOf("본문에서 멘션", "댓글에서 멘션"), titles(page(login(me), "mentionId" to "1")).toSet())
    }

    @Test
    fun `sharerId와 favoriteId를 주면 공유받은 이슈와 즐겨찾는 이슈를 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        val shared = issue(a, "공유받음", other); val favorite = issue(a, "즐겨찾기", other); issue(a, "무관", other)
        sharers.save(IssueSharer(user = me, issue = shared, loginId = me.loginId, created = LocalDate.now()))
        favorites.save(FavoriteIssue(user = me, issue = favorite))
        val session = login(me)
        assertEquals(listOf("공유받음"), titles(page(session, "sharerId" to "1")))
        assertEquals(listOf("즐겨찾기"), titles(page(session, "favoriteId" to "1")))
    }

    @Test
    fun `빠른 검색에는 여섯 항목이 있고 선택한 것이 활성이며 개수가 붙는다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        val shared = issue(a, "공유받음", other); val favorite = issue(a, "즐겨찾기", other); val mentioned = issue(a, "멘션", other)
        sharers.save(IssueSharer(user = me, issue = shared, loginId = me.loginId, created = LocalDate.now()))
        favorites.save(FavoriteIssue(user = me, issue = favorite))
        mentions.save(Mention(resourceType = ResourceType.ISSUE_POST, resourceId = mentioned.id.toString(), user = me))
        val doc = page(login(me), "authorId" to me.id.toString())
        val items = doc.select(".left-menu ul.lst-stacked li")
        assertEquals(listOf("assigned-to-me", "authored-by-me", "commented-by-me", "mentioned-of-me", "shared-with-me", "favorite-issue"), items.map { it.selectFirst("span")!!.className() })
        assertEquals(listOf("authored-by-me"), items.filter { it.hasClass("active") }.map { it.selectFirst("span")!!.className() })
        assertEquals("(1)", items[3].selectFirst("a")!!.ownText().trim())
        assertEquals("(1)", items[4].selectFirst("a")!!.ownText().trim())
        assertEquals("(1)", items[5].selectFirst("a")!!.ownText().trim())
        assertEquals(me.id.toString(), items[0].selectFirst("a")!!.attr("data-assignee-id"))
    }

    @Test
    fun `검색어는 제목과 본문과 댓글에서 찾는다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "열쇠 제목", other, assignee = me); issue(a, "본문 일치", other, assignee = me, body = "여기 열쇠가 있다")
        val viaComment = issue(a, "댓글 일치", other, assignee = me); issue(a, "무관", other, assignee = me)
        comments.save(IssueComment(contents = "열쇠 발견", authorId = other.id, issue = viaComment, projectId = a.id!!, createdDate = Date()))
        assertEquals(setOf("열쇠 제목", "본문 일치", "댓글 일치"), titles(page(login(me), "filter" to "열쇠")).toSet())
    }

    @Test
    fun `초안은 보이지 않고 하위 이슈는 보인다`() {
        val me = newUser(); val a = project(me, "a")
        val parent = issue(a, "부모", me, assignee = me); issue(a, "하위", me, assignee = me, parent = parent); issue(a, "초안", me, assignee = me, draft = true)
        assertEquals(setOf("부모", "하위"), titles(page(login(me))).toSet())
    }

    @Test
    fun `라벨과 마일스톤 조건은 쓰지 않는다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "이슈", other, assignee = me)
        assertEquals(listOf("이슈"), titles(page(login(me), "labelIds" to "999", "milestoneId" to "999")))
    }

    @Test
    fun `기본 정렬은 갱신 시각 내림차순이고 orderBy로 바꾼다`() {
        val me = newUser(); val a = project(me, "a")
        issue(a, "가", me, assignee = me, updated = 1_700_000_300_000); issue(a, "나", me, assignee = me, updated = 1_700_000_100_000)
        issues.findAll().first { it.title == "나" }.also { it.createdDate = Date(1_700_000_500_000); issues.save(it) }
        val session = login(me)
        assertEquals(listOf("가", "나"), titles(page(session)))
        assertEquals(listOf("나", "가"), titles(page(session, "orderBy" to "createdDate")))
        assertEquals("updatedDate", page(session).selectFirst("a.filter.active")!!.attr("orderBy"))
        assertEquals("asc", page(session).selectFirst("a.filter.active")!!.attr("orderDir"))
    }

    @Test
    fun `한 쪽에 15개이고 itemsPerPage는 45개까지이다`() {
        val me = newUser(); val a = project(me, "a")
        (1..50).forEach { issue(a, "이슈$it", me, assignee = me) }
        val session = login(me)
        val first = page(session)
        assertEquals(15, titles(first).size)
        assertEquals("4", first.selectFirst("#pagination")!!.attr("data-total"))
        assertEquals(5, titles(page(session, "pageNum" to "4")).size)
        assertEquals(45, titles(page(session, "itemsPerPage" to "100")).size)
        assertEquals(10, titles(page(session, "itemsPerPage" to "10")).size)
    }

    @Test
    fun `Ajax 요청은 JSON이고 PJAX 요청은 조각이다`() {
        val me = newUser(); val a = project(me, "a")
        val one = issue(a, "이슈", me, assignee = me)
        val session = login(me)
        mockMvc.perform(get("/user/issues").session(session).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk).andExpect(jsonPath("$.${one.id}.title").value("이슈")).andExpect(jsonPath("$.${one.id}.state").value("OPEN"))
            .andExpect(jsonPath("$.${one.id}.link").doesNotExist())
        val fragment = mockMvc.perform(get("/user/issues").session(session).header("X-Requested-With", "XMLHttpRequest").header("X-PJAX", "true"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store")).andReturn().response.contentAsString
        assertFalse(fragment.contains("<html"))
        assertTrue(fragment.contains("issue-list-wrap"))
    }

    @Test
    fun `내 이슈 줄에는 프로젝트 이름과 번호와 제목 링크가 있다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "alpha")
        val one = issue(a, "이슈", other, assignee = me)
        val row = page(login(me)).selectFirst("ul.post-list-wrap.my-issues li.post-item")!!
        assertEquals("alpha", row.selectFirst("a.project")!!.text().trim())
        assertEquals("/${other.loginId}/alpha", row.selectFirst("a.project")!!.attr("href"))
        assertEquals("#${one.number}", row.selectFirst(".post-id")!!.text())
        assertEquals("/${other.loginId}/alpha/issue/${one.number}", row.selectFirst("a.title:not(.project)")!!.attr("href"))
    }

    @Test
    fun `작성자 탭에서는 작성자 열을 숨기고 담당 탭이 아니면 담당자 아바타를 보인다`() {
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        issue(a, "내가 쓴 것", me, assignee = other)
        val doc = page(login(me), "authorId" to me.id.toString())
        assertTrue(doc.select(".author a.author-cell").isEmpty())
        assertEquals("/${other.loginId}", doc.selectFirst("a.avatar-wrap.assinee")!!.attr("href"))
        val assigned = page(login(me), "assigneeId" to me.id.toString())
        assertTrue(assigned.select("a.avatar-wrap.assinee").isEmpty())
    }

    @Test
    fun `기본 페이지 설정 스크립트는 주소를 변수로 받아 요청한다`() {
        val html = mockMvc.perform(get("/user/issues").session(login(newUser()))).andReturn().response.contentAsString
        assertTrue(html.contains("var defaultLoginPageUrl = \"\\/-_-api\\/v1\\/user\\/defultLoginPage\";"))
        assertTrue(html.contains("\$.post(defaultLoginPageUrl + \"?path=\" + \$setDefaultLoginPage.data(\"url\"))"))
    }

    @Test
    fun `라벨 링크는 프로젝트 이슈 목록의 열린 이슈로 가는 주소이다`() {
        // v1.6은 `routes.IssueApp.issues(소유자, 이름, "open", "html", 1)`에 `&labelIds=`를 붙인다. state의 기본값이 빈 문자열이라 `?state=open`이 남는다.
        val me = newUser(); val other = newUser(); val a = project(other, "a")
        val category = labelCategories.save(IssueLabelCategory(project = a, name = "종류", isExclusive = false))
        val label = issueLabels.save(IssueLabel(project = a, category = category, name = "버그", color = "#f00"))
        issue(a, "라벨 있음", other, assignee = me).also { it.labels.add(label); issues.save(it) }
        assertEquals("/${a.owner}/a/issues?state=open&labelIds=${label.id}", page(login(me)).selectFirst("a.issue-label.list-label")!!.attr("href"))
    }
}
