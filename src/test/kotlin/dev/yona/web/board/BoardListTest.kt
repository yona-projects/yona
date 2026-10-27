package dev.yona.web.board

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게시판 목록(`GET /:user/:project/posts`)을 검증한다.
 *
 * 원본: `app/controllers/BoardApp.java:114-130`, `app/views/board/list.scala.html`, `app/views/board/partial_list.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class BoardListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "bl$n", name = "bl$n", email = "bl$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "bo$n", name = "bp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private var clock = 1_700_000_000_000L

    private fun post(
        project: Project, title: String, number: Long, author: User? = null, notice: Boolean = false, readme: Boolean = false, comments: Int = 0, body: String = "본문",
        postLabels: Set<IssueLabel> = emptySet()
    ): Posting {
        clock += 60_000
        project.lastPostingNumber = maxOf(project.lastPostingNumber ?: 0, number); projects.save(project)
        return postings.save(
            Posting(
                project = project, number = number, title = title, body = body, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name, notice = notice, readme = readme,
                numOfComments = comments, createdDate = Date(clock), updatedDate = Date(clock), labels = postLabels.toMutableSet()
            )
        )
    }

    private fun page(project: Project, session: MockHttpSession? = null, vararg params: Pair<String, String>) =
        mockMvc.perform(get("/${project.owner}/${project.name}/posts").apply { session?.let { session(it) } }.apply { params.forEach { (k, v) -> param(k, v) } })

    private fun doc(project: Project, session: MockHttpSession? = null, vararg params: Pair<String, String>) =
        Jsoup.parse(page(project, session, *params).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun numbers(doc: org.jsoup.nodes.Document, selector: String = "ul.post-list-wrap:not(.notice-wrap) li.post-item") =
        doc.select(selector).map { it.selectFirst("a.title")!!.text() }

    @Test
    fun `글 목록은 기본으로 작성일 내림차순이다`() {
        val project = newProject()
        post(project, "첫째", 1); post(project, "둘째", 2); post(project, "셋째", 3)
        val doc = doc(project)
        assertTrue(doc.title().contains("${project.name} - Board"), doc.title())
        assertEquals(listOf("셋째", "둘째", "첫째"), numbers(doc))
        assertEquals(listOf("3", "2", "1"), doc.select("ul.post-list-wrap:not(.notice-wrap) li .post-id").map { it.text() })
    }

    @Test
    fun `공지는 따로 위에 보이고 본문 목록에는 없다`() {
        val project = newProject()
        post(project, "일반", 1); post(project, "공지글", 2, notice = true)
        val doc = doc(project)
        assertEquals(listOf("공지글"), numbers(doc, "ul.notice-wrap li.post-item"))
        assertEquals("Notice", doc.selectFirst("ul.notice-wrap .label-notice")!!.text().trim())
        assertEquals(listOf("일반"), numbers(doc))
    }

    @Test
    fun `정렬 기준과 방향을 바꾼다`() {
        val project = newProject()
        post(project, "a", 1, comments = 5); post(project, "b", 2, comments = 1); post(project, "c", 3, comments = 3)
        assertEquals(listOf("b", "c", "a"), numbers(doc(project, null, "orderBy" to "numOfComments", "orderDir" to "asc")))
        assertEquals(listOf("a", "c", "b"), numbers(doc(project, null, "orderBy" to "numOfComments", "orderDir" to "desc")))
        assertEquals(listOf("a", "b", "c"), numbers(doc(project, null, "orderBy" to "createdDate", "orderDir" to "asc")))
    }

    @Test
    fun `제목이나 본문으로 검색한다`() {
        val project = newProject()
        post(project, "Alpha", 1); post(project, "Beta", 2, body = "has ALPHA inside"); post(project, "Gamma", 3)
        assertEquals(listOf("Beta", "Alpha"), numbers(doc(project, null, "filter" to "alpha")))
        assertEquals(emptyList(), numbers(doc(project, null, "filter" to "none")))
        assertEquals(listOf("Beta", "Alpha"), numbers(doc(project, null, "filter" to "Alp")))
    }

    @Test
    fun `검색어의 퍼센트는 와일드카드이다 (v1_6 Ebean icontains)`() {
        val project = newProject()
        post(project, "100% done", 1); post(project, "other", 2)
        // "%"만 넣으면 모든 글에 맞는다
        assertEquals(setOf("100% done", "other"), numbers(doc(project, null, "filter" to "%")).toSet())
    }

    @Test
    fun `라벨로 거른다`() {
        val project = newProject()
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        val y = labels.save(IssueLabel(project = project, category = category, name = "y", color = "#000"))
        post(project, "withX", 1, postLabels = setOf(x)); post(project, "withY", 2, postLabels = setOf(y)); post(project, "none", 3)
        assertEquals(listOf("withX"), numbers(doc(project, null, "labelIds" to x.id.toString())))
        val both = Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/posts?labelIds=${x.id}&labelIds=${y.id}")).andReturn().response.contentAsString)
        assertEquals(setOf("withX", "withY"), numbers(both).toSet())
        assertEquals(listOf("y", "x"), doc(project).select("li.post-item a.list-label").map { it.text() })
        assertEquals(1, doc(project).select(".board-labels select#labelIds").size)
    }

    @Test
    fun `쪽을 나누어 15개씩 보인다`() {
        val project = newProject()
        (1L..16L).forEach { post(project, "글 $it", it) }
        val first = doc(project)
        assertEquals(15, numbers(first).size)
        assertTrue(first.select("script").any { Regex("\"nTotalPages\"\\s*:\\s*2").containsMatchIn(it.data()) })
        assertEquals(listOf("글 1"), numbers(doc(project, null, "pageNum" to "2")))
    }

    @Test
    fun `글이 없으면 안내가 보인다`() {
        val project = newProject()
        assertEquals("No post has been added.", doc(project).selectFirst(".error-wrap p")!!.text())
        assertTrue(doc(project).select("ul.post-list-wrap li").isEmpty())
    }

    @Test
    fun `행에 작성자, 시각, 댓글 수, README 표시가 있다`() {
        val project = newProject()
        val author = join(newUser(), project)
        post(project, "[공지] 제목", 1, author = author, comments = 2)
        post(project, "README", 2, readme = true)
        val doc = doc(project)
        val rows = doc.select("ul.post-list-wrap:not(.notice-wrap) li.post-item")
        assertEquals("README", rows[0].selectFirst(".label-important")!!.text())
        assertTrue(rows[0].select(".post-id").isEmpty())
        val row = rows[1]
        assertEquals(author.name, row.selectFirst(".infos a.infos-link-item")!!.text())
        assertEquals("/${author.loginId}", row.selectFirst(".infos a.infos-link-item")!!.attr("href"))
        assertEquals("제목", row.selectFirst("a.title")!!.text())
        assertEquals("[공지]", row.selectFirst(".title-prefix")!!.text().trim())
        assertTrue(row.select(".item-count-groups").isNotEmpty())
        assertEquals("/${project.owner}/${project.name}/post/1", row.attr("href"))
    }

    @Test
    fun `작성자 행이 없으면 NullUser의 이름 링크로 보인다 (v1_6 User_findByLoginId)`() {
        val project = newProject()
        post(project, "무명", 1)
        val item = doc(project).selectFirst(".infos .infos-item")!!
        // NullUser: 이름은 user.notExists.name, 로그인 ID는 빈 문자열이라 링크는 "/"이다
        assertEquals("User exists not", item.text())
        assertEquals("/", item.attr("href"))
    }

    @Test
    fun `라벨을 여럿 고르면 정렬 링크의 labelIds는 번호를 구분 없이 이어 붙인다 (v1_6 Twirl 배열 출력)`() {
        val project = newProject()
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        val y = labels.save(IssueLabel(project = project, category = category, name = "y", color = "#fff"))
        val one = Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/posts?labelIds=${x.id}")).andReturn().response.contentAsString)
        assertTrue(one.select(".filters a.filter").all { it.attr("href").contains("labelIds=${x.id}&") })
        val both = Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/posts?labelIds=${x.id}&labelIds=${y.id}")).andReturn().response.contentAsString)
        // 배열을 그대로 출력하면 원소가 구분자 없이 붙는다: 두 라벨이 한 값이 된다
        assertTrue(both.select(".filters a.filter").all { it.attr("href").contains("labelIds=${x.id}${y.id}&") }, both.select(".filters a.filter").map { it.attr("href") }.toString())
    }

    @Test
    fun `정렬 링크는 현재 조건을 이어 받고 방향을 뒤집는다`() {
        val project = newProject()
        post(project, "x y a", 1); post(project, "x y b", 2)
        val links = doc(project, null, "filter" to "x y", "orderBy" to "createdDate", "orderDir" to "desc").select(".filters a.filter")
        assertEquals(3, links.size)
        assertTrue(links[1].hasClass("active"))
        assertEquals("/${project.owner}/${project.name}/posts?filter=x+y&labelIds=&orderBy=createdDate&orderDir=asc", links[1].attr("href"))
        assertEquals("/${project.owner}/${project.name}/posts?filter=x+y&labelIds=&orderBy=updatedDate&orderDir=desc", links[0].attr("href"))
        assertEquals("/${project.owner}/${project.name}/posts?filter=x+y&labelIds=&orderBy=numOfComments&orderDir=desc", links[2].attr("href"))
        // 글이 하나뿐이면 정렬 막대를 보이지 않는다.
        val single = newProject(); post(single, "only", 1)
        assertTrue(doc(single).select(".filters").isEmpty())
    }

    @Test
    fun `쓰기 단추와 검색 폼이 있다`() {
        val project = newProject()
        val doc = doc(project)
        assertEquals("/${project.owner}/${project.name}/postform", doc.selectFirst(".search-wrap .pull-right a.ybtn-success")!!.attr("href"))
        assertEquals("/${project.owner}/${project.name}/posts", doc.selectFirst("form#option_form")!!.attr("action"))
        assertEquals("createdDate", doc.selectFirst("form#option_form input[name=orderBy]")!!.attr("value"))
        assertEquals("desc", doc.selectFirst("form#option_form input[name=orderDir]")!!.attr("value"))
    }

    @Test
    fun `비공개 프로젝트는 403이고 없는 프로젝트는 404이다`() {
        val secret = newProject(ProjectScope.PRIVATE)
        page(secret, login(newUser())).andExpect(status().isForbidden)
        mockMvc.perform(get("/nobody/noproject/posts")).andExpect(status().isForbidden)
        page(secret, login(join(newUser(), secret))).andExpect(status().isOk)
    }

    @Test
    fun `잘못된 정렬 값은 작성일 정렬로 본다`() {
        val project = newProject()
        post(project, "a", 1); post(project, "b", 2)
        assertEquals(listOf("b", "a"), numbers(doc(project, null, "orderBy" to "password; drop")))
    }
}
