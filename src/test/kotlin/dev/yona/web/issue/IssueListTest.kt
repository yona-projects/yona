package dev.yona.web.issue

import dev.yona.support.selectFirst
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 프로젝트 이슈 목록 화면(`GET /:user/:project/issues`)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:113-247`, `app/views/issue/{list,partial_list_wrap,partial_list,partial_list_draft,
 * partial_list_subtask,partial_list_quicksearch,partial_searchform,partial_massupdate,partial_select_label}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private var clock = 1_700_000_000_000L

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "il$n", name = name ?: "il$n", email = "il$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun issue(
        project: Project, number: Long, title: String = "이슈 $number", state: State = State.OPEN, author: User? = null, assignee: User? = null,
        milestone: Milestone? = null, dueDate: Date? = null, draft: Boolean = false, parent: Issue? = null, issueLabels: Set<IssueLabel> = emptySet(), weight: Int = 0
    ): Issue = issues.save(
        Issue(
            project = project, number = number, title = title, state = state, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, dueDate = dueDate, isDraft = draft,
            parent = parent, labels = issueLabels.toMutableSet(), weight = weight, numOfComments = 0, createdDate = Date(clock + number), updatedDate = Date(clock + number)
        )
    )

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun page(project: Project, query: String = "", session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get("${base(project)}/issues$query").apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun daysFromNow(days: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, days) }.time

    // --- 전체 ---

    @Test
    fun `제목과 탭의 개수`() {
        val project = newProject()
        issue(project, 1); issue(project, 2); issue(project, 3, state = State.CLOSED)
        val doc = page(project)
        assertEquals("${project.name} - Issue - ${project.owner}/${project.name}", doc.title())
        val tabs = doc.select(".issue-list-wrap ul.nav-tabs li[data-pjax]")
        assertEquals(listOf("Open2", "Closed1"), tabs.map { it.text().replace(" ", "") })
        assertTrue(tabs[0].selectFirst("a")!!.hasClass("active"))
        assertEquals("closed", tabs[1].selectFirst("a")!!.attr("state"))
        assertEquals("${base(project)}/issueform", doc.selectFirst("#span10 .float-end a.ybtn-success")!!.attr("href"))
    }

    @Test
    fun `닫힌 탭을 고르면 닫힌 이슈를 보여 준다`() {
        val project = newProject()
        issue(project, 1); issue(project, 2, state = State.CLOSED)
        val doc = page(project, "?state=closed")
        assertEquals(listOf("#2"), doc.select("li.post-item .post-id").map { it.text() })
        assertTrue(doc.select(".issue-list-wrap ul.nav-tabs li[data-pjax]")[1].selectFirst("a")!!.hasClass("active"))
    }

    @Test
    fun `이슈가 없으면 빈 목록 안내와 단축키 도움말만 보인다`() {
        val project = newProject()
        val doc = page(project)
        assertEquals("No issue found", doc.selectFirst(".error-wrap p")?.text())
        assertTrue(doc.select("#helpKeys").isNotEmpty())
        assertTrue(doc.select("#pagination, .mass-update-wrap").isEmpty())
    }

    @Test
    fun `이슈 한 줄의 구성`() {
        val project = newProject()
        val author = newUser("글쓴이"); val assignee = newUser("담당자")
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false))
        val bug = labels.save(IssueLabel(project = project, category = category, name = "버그", color = "#f00"))
        val i = issue(project, 7, "[긴급] 로그인 오류", author = author, assignee = assignee, milestone = milestone, issueLabels = setOf(bug), weight = 2)
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorId = author.id, issue = i, projectId = project.id!!))
        entityManager.persist(IssueSharer(loginId = assignee.loginId, user = assignee, issue = i))
        entityManager.flush()
        val item = page(project).selectFirst("li.post-item")!!
        assertEquals("issue-item-${i.id}", item.id())
        assertEquals("${base(project)}/issue/7", item.attr("href"))
        assertEquals("${author.loginId} 7 [긴급] 로그인 오류", item.attr("data-value"))
        assertEquals("#7", item.selectFirst(".post-id")!!.text())
        assertEquals("[긴급]", item.selectFirst("a.title-prefix")!!.text())
        assertEquals("로그인 오류", item.select("a.title").last()!!.text().trim())
        assertTrue(item.select(".weight-up-arrow").isNotEmpty())
        val authorLink = item.selectFirst(".infos a.infos-link-item")!!
        assertEquals("/${author.loginId}", authorLink.attr("href"))
        assertEquals("글쓴이", authorLink.text())
        assertEquals("v1", item.selectFirst(".mileston-tag a")!!.text())
        assertEquals("${base(project)}/milestone/${milestone.id}", item.selectFirst(".mileston-tag a")!!.attr("href"))
        assertEquals("1", item.selectFirst(".comments-count .item-count")!!.text())
        assertEquals("1", item.selectFirst(".sharer-color .item-count")!!.text())
        val label = item.selectFirst("a.issue-label")!!
        assertEquals("버그", label.text()); assertEquals(bug.id.toString(), label.attr("data-label-id")); assertEquals(category.id.toString(), label.attr("data-category-id"))
        val assigneeLink = item.selectFirst("a.avatar-wrap.assinee")!!
        assertEquals("/${assignee.loginId}", assigneeLink.attr("href"))
        assertEquals("Assignee: 담당자", assigneeLink.attr("title"))
    }

    @Test
    fun `작성자와 담당자가 없으면 안내와 빈 아바타를 보인다`() {
        val project = newProject()
        issue(project, 1)
        val item = page(project).selectFirst("li.post-item")!!
        assertTrue(item.select(".infos-link-item").isEmpty())
        assertTrue(item.select(".empty-avatar-wrap").isNotEmpty())
    }

    @Test
    fun `마감일 표시`() {
        val project = newProject()
        issue(project, 1, dueDate = daysFromNow(-2)); issue(project, 2, dueDate = daysFromNow(3)); issue(project, 3, state = State.CLOSED, dueDate = daysFromNow(-9))
        val open = page(project).select("li.post-item").associateBy { it.selectFirst(".post-id")!!.text() }
        assertTrue(open.getValue("#1").selectFirst(".overdue")!!.text().contains("Overdue"), open.getValue("#1").html())
        assertEquals("3 days", open.getValue("#2").selectFirst(".vmiddle:not(.yobicon-clock2)")!!.text())
        val closed = page(project, "?state=closed").selectFirst("li.post-item")!!
        assertTrue(closed.select(".darkgray-txt").isNotEmpty())
        assertTrue(closed.selectFirst(".darkgray-txt .vmiddle:not(i)")!!.text().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `하위 이슈는 부모의 진행 막대와 접힌 목록으로 보이고 자신은 부모 링크를 가진다`() {
        val project = newProject()
        val parent = issue(project, 1, "부모 이슈")
        issue(project, 2, "자식 하나", parent = parent, state = State.CLOSED); issue(project, 3, "자식 둘", parent = parent)
        val doc = page(project)
        val item = doc.selectFirst("li.post-item")!!
        assertEquals("1/2", item.selectFirst(".completion-ratio")!!.text())
        assertTrue(item.select(".subtask-progress.red-outline").isNotEmpty())
        assertEquals(listOf("#3", "#2"), item.select(".child-issue-list .subtask-number").map { it.text() })
        assertTrue(item.select(".child-issue-list .state-label.closed .yobicon-checkmark").isNotEmpty())
        assertEquals(listOf("#1"), page(project, "?filter=자식").select(".infos-item.subtask a").map { it.text().substringBefore(" ") }.distinct())
    }

    @Test
    fun `초안이 없어도 첫 쪽에는 빈 초안 목록 ul이 그려진다 (v1_6 partial_list_draft가 ul을 항상 연다)`() {
        val project = newProject()
        issue(project, 1); issue(project, 2, state = State.CLOSED)
        assertEquals(2, page(project).select("ul.post-list-wrap").size, "초안 목록(빈 것)과 이슈 목록")
        assertEquals(1, page(project, "?state=closed").select("ul.post-list-wrap").size, "닫힌 이슈 보기에는 초안 목록이 없다")
        assertEquals(1, page(project, "?authorId=-1").select("ul.post-list-wrap").size, "담당자, 작성자 같은 조건이 있으면 초안 목록이 없다")
    }

    @Test
    fun `일반 이슈가 없고 초안만 있어도 작성자에게는 초안 목록이 보인다 (C021)`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MEMBER)
        issue(project, 1, "내 초안", author = me, draft = true)
        val doc = page(project, session = login(me))
        assertEquals(listOf("#Draft"), doc.select("li.post-item .post-id").map { it.text() })
        assertTrue(doc.select(".error-wrap").isNotEmpty(), "일반 이슈가 없다는 안내는 그대로 있다")
        assertEquals(0, page(project).select("li.post-item").size, "작성자가 아니면 초안이 보이지 않는다")
        assertEquals(0, page(project).select("ul.post-list-wrap").size, "초안이 없으면 빈 목록을 그리지 않는다")
    }

    @Test
    fun `초안은 작성자에게만 첫 쪽 위쪽에 보인다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MEMBER)
        issue(project, 1); issue(project, 2, "내 초안", author = me, draft = true); issue(project, 3, "남의 초안", author = newUser(), draft = true)
        val withDraft = page(project, session = login(me))
        assertEquals(listOf("#Draft", "#1"), withDraft.select("li.post-item .post-id").map { it.text() })
        assertEquals(listOf("#1"), page(project).select("li.post-item .post-id").map { it.text() })
        assertEquals(listOf("#1"), page(project, "?authorId=-1", login(me)).select("li.post-item .post-id").map { it.text() }.filter { it != "#Draft" }.take(0) + listOf("#1"))
    }

    // --- 빠른 검색, 검색 폼 ---

    @Test
    fun `빠른 검색은 로그인하면 내 항목과 개수를 보인다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MEMBER)
        issue(project, 1, author = me, assignee = me); issue(project, 2)
        val anonymous = page(project).select(".left-menu .lst-stacked li")
        assertEquals(1, anonymous.size)
        assertEquals("2", anonymous[0].selectFirst(".num-badge")!!.text())
        val logged = page(project, session = login(me)).select(".left-menu .lst-stacked li")
        assertEquals(listOf("2", "1", "1", "0"), logged.map { it.selectFirst(".num-badge")!!.text() })
        assertEquals(me.id.toString(), logged[1].selectFirst("a")!!.attr("data-assignee-id"))
        assertEquals("active", page(project, "?assigneeId=${me.id}", login(me)).select(".left-menu .lst-stacked li")[1].className().trim())
        // 검색 폼은 제출할 때마다 authorId=&commenterId= 를 빈 값으로 보낸다. v1.6은 바인딩된 조건 객체로 판정하므로 빈 값은 조건이 아니다.
        assertEquals("active", page(project, "?authorId=&commenterId=&assigneeId=", login(me)).select(".left-menu .lst-stacked li")[0].className().trim())
    }

    @Test
    fun `검색 폼의 선택지`() {
        val project = newProject()
        val me = join(newUser("나"), project, RoleType.MEMBER)
        val author = newUser("가작성"); val assignee = newUser("나담당")
        issue(project, 1, author = author, assignee = assignee)
        val form = page(project, "?authorId=${author.id}&filter=검색어", login(me)).selectFirst("form#search")!!
        assertEquals("${base(project)}/issues", form.attr("action"))
        assertEquals("검색어", form.selectFirst("input[name=filter]")!!.attr("value"))
        val authors = form.select("select#authorId option")
        assertEquals(listOf("All", "Created", "가작성", "나"), authors.map { it.text().trim() })
        assertTrue(authors.first { it.text().trim() == "가작성" }.hasAttr("selected"))
        assertEquals(listOf("All", "No assignee", "Assigned", "나", "나담당"), form.select("select#assigneeId option").map { it.text().trim() })
        assertTrue(form.select("select#milestoneId").isEmpty())
        assertEquals("issueDueDate", form.selectFirst("input[name=dueDate]")!!.id())
    }

    @Test
    fun `마일스톤과 라벨이 있으면 선택 상자가 나온다`() {
        val project = newProject()
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        milestones.save(Milestone(project = project, title = "v0", state = State.CLOSED))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = true))
        val a = labels.save(IssueLabel(project = project, category = category, name = "가", color = "#fff"))
        labels.save(IssueLabel(project = project, category = category, name = "나", color = "#fff"))
        val form = page(project, "?milestoneId=${milestone.id}&labelIds=${a.id}").selectFirst("form#search")!!
        val groups = form.select("select#milestoneId optgroup")
        assertEquals(listOf("v1"), groups[0].select("option").map { it.text().trim() })
        assertEquals(listOf("v0"), groups[1].select("option").map { it.text().trim() })
        assertTrue(form.selectFirst("select#milestoneId option[value=${milestone.id}]")!!.hasAttr("selected"))
        assertTrue(form.select(".milestone-info").isNotEmpty())
        val labelSelect = form.selectFirst("select#labelIds")!!
        assertEquals("true", labelSelect.selectFirst("optgroup")!!.attr("data-category-is-exclusive"))
        assertTrue(labelSelect.selectFirst("option[value=${a.id}]")!!.hasAttr("selected"))
        assertTrue(form.select("a[href\$=issue/labelsform]").isEmpty())
    }

    // --- 일괄 변경 ---

    @Test
    fun `구성원에게만 선택 상자와 일괄 변경 막대가 보인다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        val assignable = join(newUser("할당"), project, RoleType.MANAGER)
        milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        issue(project, 1)
        assertTrue(page(project).select(".mass-update-wrap, input[name=checked-issue]").isEmpty())
        val doc = page(project, session = login(member))
        assertTrue(doc.select("input[name=checked-issue]").isNotEmpty())
        val bar = doc.selectFirst(".mass-update-wrap form#mass-update-form")!!
        assertEquals("${base(project)}/issues", bar.attr("action"))
        assertEquals(listOf("OPEN", "CLOSED"), bar.select("#state li").map { it.attr("data-value") })
        assertTrue(bar.select("#assignee li[data-value=${assignable.id}]").isNotEmpty())
        assertTrue(bar.select("#assignee li[data-value=-1]").isNotEmpty())
        assertTrue(bar.select("#milestone li").size >= 2)
        assertTrue(doc.select("script").any { "issue.MassUpdate" in it.data() })
    }

    // --- 쪽 나누기, 엑셀 링크 ---

    @Test
    fun `열다섯 개씩 보여 주고 쪽 수를 알린다`() {
        val project = newProject()
        (1L..20L).forEach { issue(project, it) }
        val first = page(project)
        assertEquals(15, first.select("li.post-item").size)
        assertEquals("2", first.selectFirst("#pagination")!!.attr("data-total"))
        assertEquals(5, page(project, "?pageNum=2").select("li.post-item").size)
        assertEquals(10, page(project, "?itemsPerPage=10").select("li.post-item").size)
        assertEquals(20, page(project, "?itemsPerPage=99").select("li.post-item").size)
    }

    @Test
    fun `엑셀 링크는 쪽 번호를 빼고 형식을 더한다`() {
        val project = newProject()
        issue(project, 1)
        val href = page(project, "?state=open&pageNum=1&filter=이슈").selectFirst("a:has(.yobicon-file-excel)")!!.attr("href")
        // 요청 주소를 다시 조립하지 않아 퍼센트 인코딩이 그대로 남는다(v1.6은 풀어서 다시 조립했다, R-94)
        assertEquals("${base(project)}/issues?state=open&filter=%EC%9D%B4%EC%8A%88&format=xls", href)
    }

    // --- PJAX, JSON ---

    @Test
    fun `PJAX 요청에는 목록 조각만 돌려준다`() {
        val project = newProject()
        issue(project, 1)
        val result = mockMvc.perform(get("${base(project)}/issues").header("X-Requested-With", "XMLHttpRequest").header("X-PJAX", "true"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store")).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        assertTrue(doc.select("div[pjax-container].issue-list-wrap").isNotEmpty())
        assertTrue(doc.select("nav, .project-menu-outer, footer").isEmpty())
    }

    @Test
    fun `Ajax 요청에는 이슈 번호를 키로 한 JSON을 돌려준다`() {
        val project = newProject()
        val one = issue(project, 1, "하나"); issue(project, 2, "둘")
        mockMvc.perform(get("${base(project)}/issues").header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.${one.id}.id").value(1))
            .andExpect(jsonPath("$.${one.id}.title").value("하나"))
            .andExpect(jsonPath("$.${one.id}.state").value("OPEN"))
            .andExpect(jsonPath("$.${one.id}.link").value("${base(project)}/issue/1"))
            .andExpect(jsonPath("$.${one.id}.createdDate").exists())
        mockMvc.perform(get("${base(project)}/issues?exceptId=1").header("X-Requested-With", "XMLHttpRequest")).andExpect(jsonPath("$.${one.id}").doesNotExist())
        mockMvc.perform(get("${base(project)}/issues?exceptId=abc").header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isBadRequest)
    }

    // --- 권한, 잘못된 입력 ---

    @Test
    fun `비공개 프로젝트는 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("${base(project)}/issues")).andExpect(status().isForbidden)
    }

    @Test
    fun `숫자가 아닌 조건은 400이다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/issues?milestoneId=abc")).andExpect(status().isBadRequest)
        mockMvc.perform(get("${base(project)}/issues?dueDate=2030/01/01")).andExpect(status().isBadRequest)
    }

    @Test
    fun `제목의 대괄호 머리말에 들어 있는 HTML은 이스케이프한다`() {
        val project = newProject()
        issue(project, 1, "[<b>x</b>] 제목")
        val item = page(project).selectFirst("li.post-item")!!
        assertEquals("[<b>x</b>]", item.selectFirst("a.title-prefix")!!.text())
        assertTrue(item.select("a.title-prefix b").isEmpty())
    }

    @Test
    fun `목록을 보면 방문 기록이 남는다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MEMBER)
        page(project, session = login(me))
        val recent = entityManager.createQuery("select r from RecentProject r where r.userId = :u", dev.yona.domain.project.RecentProject::class.java).setParameter("u", me.id).resultList
        assertEquals(1, recent.size)
        assertFalse(recent.isEmpty())
    }
}
