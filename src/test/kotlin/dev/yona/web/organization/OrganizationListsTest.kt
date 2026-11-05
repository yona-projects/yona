package dev.yona.web.organization

import dev.yona.support.selectFirst
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 조직 이슈 목록(`GET /organizations/:name/issues`)과 조직 풀 리퀘스트 목록(`pullrequests`, `closedPullrequests`)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:50-70`, `app/controllers/OrganizationApp.java:53-79`, `app/models/support/SearchCondition.java:100-230`,
 * `app/models/PullRequest.java:752-818`, `app/views/organization/group_issue_*.scala.html`, `group_pullrequest_list*.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class OrganizationListsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var labelCategories: IssueLabelCategoryRepository
    @Autowired private lateinit var issueLabels: IssueLabelRepository

    private var sequence = 0
    private var number = 0L
    private var clock = 1_700_000_000_000L

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ol$n", name = "ol$n", email = "ol$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrg(): Organization = organizations.save(Organization(name = "lorg${++sequence}", descr = "설명", created = Date()))

    private fun orgProject(org: Organization, scope: ProjectScope = ProjectScope.PUBLIC, name: String = "lp${++sequence}"): Project {
        val project = projects.save(Project(owner = org.name, name = name, vcs = "GIT", projectScope = scope, organization = org, overview = "개요", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun member(project: Project, user: User = newUser()): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get())); return user
    }

    private fun issue(
        project: Project, title: String, author: User = newUser(), assignee: User? = null, state: State = State.OPEN, draft: Boolean = false,
        parent: Issue? = null, body: String = "본문", comments: Int = 0
    ): Issue {
        clock += 60_000
        return issues.save(Issue(
            project = project, number = ++number, title = title, body = body, state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = comments, weight = 0, isDraft = draft, parent = parent,
            createdDate = Date(clock), updatedDate = Date(clock)
        ))
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun issueTitles(doc: Document) = doc.select("ul.post-list-wrap li.post-item div.title-wrap a.title").map { it.text().trim() }

    // --- 이슈 목록 ---

    @Test
    fun `없는 조직은 404이다`() {
        mockMvc.perform(get("/organizations/nosuchorg/issues")).andExpect(status().isNotFound)
        mockMvc.perform(get("/organizations/nosuchorg/pullrequests")).andExpect(status().isNotFound)
        mockMvc.perform(get("/organizations/nosuchorg/closedPullrequests")).andExpect(status().isNotFound)
    }

    @Test
    fun `로그인하지 않아도 공개 프로젝트의 열린 이슈를 만든 시각 내림차순으로 본다`() {
        val org = newOrg(); val a = orgProject(org); val b = orgProject(org)
        issue(a, "첫째"); issue(b, "둘째"); issue(a, "닫힘", state = State.CLOSED)
        assertEquals(listOf("둘째", "첫째"), issueTitles(page("/organizations/${org.name}/issues")))
    }

    @Test
    fun `다른 조직의 프로젝트 이슈와 초안은 나오지 않고 하위 이슈는 나온다`() {
        val org = newOrg(); val other = newOrg(); val a = orgProject(org); val o = orgProject(other)
        val parent = issue(a, "부모"); issue(a, "자식", parent = parent); issue(a, "초안", draft = true); issue(o, "다른 조직")
        assertEquals(listOf("자식", "부모"), issueTitles(page("/organizations/${org.name}/issues")))
    }

    @Test
    fun `비공개 프로젝트의 이슈는 구성원에게만 보인다`() {
        val org = newOrg(); val open = orgProject(org); val secret = orgProject(org, ProjectScope.PRIVATE)
        issue(open, "공개"); issue(secret, "비공개")
        val insider = member(secret)
        assertEquals(listOf("공개"), issueTitles(page("/organizations/${org.name}/issues")))
        assertEquals(listOf("공개"), issueTitles(page("/organizations/${org.name}/issues", login(newUser()))))
        assertEquals(listOf("비공개", "공개"), issueTitles(page("/organizations/${org.name}/issues", login(insider))))
    }

    @Test
    fun `state가 closed이면 닫힌 이슈를 보이고 탭에 열림과 닫힘 개수가 나온다`() {
        val org = newOrg(); val a = orgProject(org)
        issue(a, "열림1"); issue(a, "열림2"); issue(a, "닫힘", state = State.CLOSED)
        val doc = page("/organizations/${org.name}/issues?state=closed")
        assertEquals(listOf("닫힘"), issueTitles(doc))
        assertEquals(listOf("2", "1"), doc.select("#span10 > ul.nav-tabs li .num-badge").map { it.text() })
        assertEquals("closed", doc.selectFirst("#span10 > ul.nav-tabs a.nav-link.active")!!.attr("state"))
    }

    @Test
    fun `projectNames로 프로젝트를 고르면 보이는 프로젝트만 대소문자 없이 걸러진다`() {
        val org = newOrg(); val a = orgProject(org, name = "Alpha"); val b = orgProject(org, name = "beta"); val secret = orgProject(org, ProjectScope.PRIVATE, "gamma")
        issue(a, "에이"); issue(b, "비"); issue(secret, "감마")
        assertEquals(listOf("에이"), issueTitles(page("/organizations/${org.name}/issues?projectNames[]=alpha")))
        assertEquals(emptyList(), issueTitles(page("/organizations/${org.name}/issues?projectNames[]=gamma")))
        assertEquals(emptyList(), issueTitles(page("/organizations/${org.name}/issues?projectNames[]=nothing")))
    }

    @Test
    fun `프로젝트 선택기에는 보이는 프로젝트만 나오고 고른 것이 선택돼 있다`() {
        val org = newOrg(); orgProject(org, name = "b-proj"); orgProject(org, name = "a-proj"); orgProject(org, ProjectScope.PRIVATE, "hidden")
        val doc = page("/organizations/${org.name}/issues?projectNames[]=b-proj")
        assertEquals(listOf("a-proj", "b-proj"), doc.select("select#projects option").map { it.attr("value") })
        assertEquals(listOf("b-proj"), doc.select("select#projects option[selected]").map { it.attr("value") })
        assertEquals("projectNames[]", doc.selectFirst("select#projects")!!.attr("name"))
    }

    @Test
    fun `assigneeId와 authorId는 요청한 사용자 값으로 거른다 (C047)`() {
        val org = newOrg(); val a = orgProject(org); val me = newUser(); val other = newUser()
        issue(a, "내가 맡은 것", author = other, assignee = me); issue(a, "내가 쓴 것", author = me); issue(a, "남의 것", author = other, assignee = other)
        val s = login(me)
        assertEquals(listOf("남의 것"), issueTitles(page("/organizations/${org.name}/issues?assigneeId=${other.id}", s)))
        assertEquals(listOf("남의 것", "내가 맡은 것"), issueTitles(page("/organizations/${org.name}/issues?authorId=${other.id}", s)))
        assertEquals(listOf("내가 맡은 것"), issueTitles(page("/organizations/${org.name}/issues?assigneeId=${me.id}", s)))
        assertEquals(listOf("내가 쓴 것"), issueTitles(page("/organizations/${org.name}/issues?authorId=${me.id}", s)))
    }

    @Test
    fun `담당자 조건이 -1이면 담당자가 없는 이슈이다`() {
        val org = newOrg(); val a = orgProject(org); val me = newUser()
        issue(a, "담당 있음", assignee = me); issue(a, "담당 없음")
        assertEquals(listOf("담당 없음"), issueTitles(page("/organizations/${org.name}/issues?assigneeId=-1", login(me))))
    }

    @Test
    fun `조건이 없어도 담당자 조건을 자동으로 걸지 않는다`() {
        val org = newOrg(); val a = orgProject(org); val me = newUser()
        issue(a, "내 것", assignee = me); issue(a, "남의 것")
        assertEquals(listOf("남의 것", "내 것"), issueTitles(page("/organizations/${org.name}/issues", login(me))))
    }

    @Test
    fun `검색어는 제목 본문 댓글에서 대소문자 없이 찾는다`() {
        val org = newOrg(); val a = orgProject(org)
        issue(a, "Needle 제목"); issue(a, "본문", body = "속에 NEEDLE이 있다"); issue(a, "댓글") .also { comments.save(IssueComment(issue = it, projectId = requireNotNull(a.id), contents = "needle 댓글", authorId = 1L, authorLoginId = "x", createdDate = Date(), authorName = "x")) }
        issue(a, "상관없음")
        assertEquals(setOf("Needle 제목", "본문", "댓글"), issueTitles(page("/organizations/${org.name}/issues?filter=needle")).toSet())
    }

    @Test
    fun `orderBy와 orderDir로 정렬하고 정렬 링크는 두 건 이상일 때 나온다`() {
        val org = newOrg(); val a = orgProject(org)
        issue(a, "댓글 적음", comments = 1); issue(a, "댓글 많음", comments = 5)
        val doc = page("/organizations/${org.name}/issues?orderBy=numOfComments&orderDir=asc")
        assertEquals(listOf("댓글 적음", "댓글 많음"), issueTitles(doc))
        assertEquals(4, doc.select(".filters a.filter").size)
        assertEquals("numOfComments", doc.selectFirst(".filters a.filter.active")!!.attr("orderBy"))
        assertTrue(page("/organizations/${org.name}/issues?filter=적음").select(".filters a.filter").isEmpty())
    }

    @Test
    fun `itemsPerPage와 pageNum으로 쪽을 나눈다`() {
        val org = newOrg(); val a = orgProject(org)
        issue(a, "일"); issue(a, "이"); issue(a, "삼")
        val first = page("/organizations/${org.name}/issues?itemsPerPage=2")
        assertEquals(listOf("삼", "이"), issueTitles(first))
        assertEquals("2", first.selectFirst("#pagination")!!.attr("data-total"))
        assertEquals(listOf("일"), issueTitles(page("/organizations/${org.name}/issues?itemsPerPage=2&pageNum=2")))
    }

    @Test
    fun `이슈가 없으면 안내 문구가 보인다`() {
        val org = newOrg(); orgProject(org)
        val doc = page("/organizations/${org.name}/issues")
        assertEquals("No issue found", doc.selectFirst("#span10 .error-wrap p")!!.text())
        assertTrue(doc.select("ul.post-list-wrap").isEmpty())
    }

    @Test
    fun `한 줄에는 제목 링크와 프로젝트 이름과 번호와 작성자와 담당자가 나온다`() {
        val org = newOrg(); val a = orgProject(org, name = "linep"); val author = newUser(); val worker = newUser()
        val issue = issue(a, "표시 확인", author = author, assignee = worker)
        val row = page("/organizations/${org.name}/issues").selectFirst("li.post-item")!!
        assertEquals("/${org.name}/linep/issue/${issue.number}", row.attr("href"))
        assertEquals("/${org.name}/linep/issue/${issue.number}", row.selectFirst("div.title-wrap a.title")!!.attr("href"))
        assertEquals("linep", row.selectFirst("a.group-project-name")!!.text())
        assertEquals("#${issue.number}", row.selectFirst("span.post-id")!!.text())
        assertEquals(author.loginId, row.selectFirst("a.avatar-wrap.mlarge")!!.attr("title"))
        assertEquals("/${worker.loginId}", row.selectFirst("a.avatar-wrap.assinee")!!.attr("href"))
    }

    @Test
    fun `라벨 링크는 프로젝트 이슈 목록의 열린 이슈로 가는 주소이다`() {
        val org = newOrg(); val a = orgProject(org, name = "labp")
        val category = labelCategories.save(IssueLabelCategory(project = a, name = "종류", isExclusive = false))
        val label = issueLabels.save(IssueLabel(project = a, category = category, name = "버그", color = "#f00"))
        issue(a, "라벨 있음").also { it.labels.add(label); issues.save(it) }
        val link = page("/organizations/${org.name}/issues").selectFirst("a.issue-label.list-label")!!
        assertEquals("/${org.name}/labp/issues?state=open&labelIds=${label.id}", link.attr("href"))
        assertEquals("버그", link.text())
    }

    @Test
    fun `닫힌 이슈의 라벨 링크는 닫힘 목록으로 간다 (C020, v1_6은 항상 열림 목록이었다)`() {
        val org = newOrg(); val a = orgProject(org, name = "labc")
        val category = labelCategories.save(IssueLabelCategory(project = a, name = "종류", isExclusive = false))
        val label = issueLabels.save(IssueLabel(project = a, category = category, name = "버그", color = "#f00"))
        issue(a, "닫힌 이슈", state = State.CLOSED).also { it.labels.add(label); issues.save(it) }
        val link = page("/organizations/${org.name}/issues?state=closed").selectFirst("a.issue-label.list-label")!!
        assertEquals("/${org.name}/labc/issues?state=closed&labelIds=${label.id}", link.attr("href"))
    }

    @Test
    fun `조직 메뉴의 Issue 탭이 활성이고 빠른 검색은 로그인했을 때만 세 항목이 더 나온다`() {
        val org = newOrg(); orgProject(org)
        val anonymous = page("/organizations/${org.name}/issues")
        assertEquals("/organizations/${org.name}/issues", anonymous.selectFirst(".project-menu-nav li.active a")!!.attr("href"))
        assertEquals(1, anonymous.select(".left-menu ul.lst-stacked li").size)
        assertEquals(4, page("/organizations/${org.name}/issues", login(newUser())).select(".left-menu ul.lst-stacked li").size)
    }

    // --- 풀 리퀘스트 목록 ---

    private fun pr(
        to: Project, from: Project = to, number: Long, title: String = "요청 $number", state: State = State.OPEN, contributor: User = newUser(),
        received: Date? = null, conflict: Boolean = false
    ): PullRequest {
        val created = Date(1_700_000_000_000L + number * 1000)
        return pullRequests.save(PullRequest(
            title = title, body = "본문", toProject = to, fromProject = from, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = contributor,
            created = created, updated = created, received = received, state = state, isConflict = conflict, number = number
        ))
    }

    private fun prTitles(doc: Document) = doc.select("ul.post-list-wrap li.post-item div.title-wrap a.title").map { it.text().trim() }

    @Test
    fun `열림 목록은 보이는 프로젝트 사이의 열린 요청을 번호 내림차순으로 보인다`() {
        val org = newOrg(); val a = orgProject(org); val b = orgProject(org)
        pr(a, number = 1, title = "일"); pr(b, number = 2, title = "이"); pr(a, number = 3, title = "닫힘", state = State.CLOSED)
        val doc = page("/organizations/${org.name}/pullrequests")
        assertEquals(listOf("이", "일"), prTitles(doc))
        assertEquals(listOf("2", "1"), doc.select(".pullrequeset-tab-menu .num-badge").map { it.text() })
        assertEquals(
            listOf("/organizations/${org.name}/pullrequests", "/organizations/${org.name}/closedPullrequests"),
            doc.select(".pullrequeset-tab-menu a[data-type=state]").map { it.attr("data-url") }
        )
        assertEquals("/organizations/${org.name}/pullrequests", doc.selectFirst("#search")!!.attr("action"))
    }

    @Test
    fun `PJAX 요청은 전체 화면이 아니라 목록 조각만 받는다 (R-64-4, v1_6은 항상 전체 화면이었다)`() {
        val org = newOrg(); val a = orgProject(org)
        issue(a, "조각 이슈")
        val full = mockMvc.perform(get("/organizations/${org.name}/issues")).andExpect(status().isOk).andReturn().response.contentAsString
        val pjax = mockMvc.perform(get("/organizations/${org.name}/issues").header("X-Requested-With", "XMLHttpRequest").header("X-PJAX", "true")).andExpect(status().isOk).andReturn().response.contentAsString
        val doc = Jsoup.parse(pjax)
        assertEquals(listOf("조각 이슈"), issueTitles(doc))
        assertEquals(1, doc.select("div[pjax-container]").size)
        assertTrue(doc.select(".project-header-outer").isEmpty() && !pjax.contains("<head"), "머리말과 메뉴 없이 조각만 와야 한다")
        assertTrue(pjax.length < full.length, "조각은 전체 화면보다 작다")
        assertTrue(Jsoup.parse(full).select("div[pjax-container]").size == 1, "전체 화면에도 같은 컨테이너가 한 번 들어 있다")
    }

    @Test
    fun `닫힘 목록은 closedPullrequests와 category가 closed인 요청이 같다`() {
        val org = newOrg(); val a = orgProject(org)
        pr(a, number = 1, title = "닫음", state = State.CLOSED, received = Date(1_700_000_100_000L)); pr(a, number = 2, title = "병합", state = State.MERGED, received = Date(1_700_000_200_000L))
        pr(a, number = 3, title = "열림")
        assertEquals(listOf("병합", "닫음"), prTitles(page("/organizations/${org.name}/closedPullrequests")))
        assertEquals(listOf("병합", "닫음"), prTitles(page("/organizations/${org.name}/pullrequests?category=closed")))
        val closed = page("/organizations/${org.name}/closedPullrequests")
        assertEquals("/organizations/${org.name}/closedPullrequests", closed.selectFirst(".pullrequeset-tab-menu a.nav-link.active")!!.attr("data-url"))
        assertEquals("/organizations/${org.name}/closedPullrequests", closed.selectFirst("#search")!!.attr("action"))
    }

    @Test
    fun `받는 프로젝트가 보이면 보내는 프로젝트가 가려져 있어도 목록에 나온다 (R-64-3, v1_6은 둘 다 보여야 했다)`() {
        val org = newOrg(); val open = orgProject(org); val secret = orgProject(org, ProjectScope.PRIVATE)
        pr(open, number = 1, title = "공개끼리"); pr(open, from = secret, number = 2, title = "비공개에서 옴"); pr(secret, number = 3, title = "비공개로 감")
        assertEquals(listOf("비공개에서 옴", "공개끼리"), prTitles(page("/organizations/${org.name}/pullrequests")), "받는 프로젝트(공개)만 보이면 된다. 받는 프로젝트가 가려진 요청은 빠진다")
        assertEquals(listOf("비공개로 감", "비공개에서 옴", "공개끼리"), prTitles(page("/organizations/${org.name}/pullrequests", login(member(secret)))))
    }

    @Test
    fun `조직 밖의 포크에서 온 요청도 목록에 나온다 (R-64-3)`() {
        val org = newOrg(); val target = orgProject(org)
        val fork = projects.save(Project(owner = "someone-${++sequence}", name = "fork${sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        pr(target, from = fork, number = 5, title = "포크에서 온 요청")
        assertEquals(listOf("포크에서 온 요청"), prTitles(page("/organizations/${org.name}/pullrequests")))
    }

    @Test
    fun `검색어와 보낸 사람으로 거른다`() {
        val org = newOrg(); val a = orgProject(org); val writer = newUser()
        pr(a, number = 1, title = "고칠 버그", contributor = writer); pr(a, number = 2, title = "기능 추가")
        assertEquals(listOf("고칠 버그"), prTitles(page("/organizations/${org.name}/pullrequests?filter=버그")))
        assertEquals(listOf("고칠 버그"), prTitles(page("/organizations/${org.name}/pullrequests?contributorId=${writer.id}")))
    }

    @Test
    fun `충돌한 요청은 conflict 표시가 붙는다`() {
        val org = newOrg(); val a = orgProject(org, name = "pj")
        pr(a, number = 7, title = "충돌 요청", conflict = true)
        val row = page("/organizations/${org.name}/pullrequests").selectFirst("li.post-item")!!
        assertTrue(row.selectFirst("a.title")!!.hasClass("conflict"))
        assertEquals("/${org.name}/pj/pullRequest/7", row.attr("href"))
        assertEquals("pj", row.selectFirst("a.group-project-name")!!.text())
        assertEquals("Conflict", row.selectFirst("div.state")!!.text())
    }

    @Test
    fun `요청이 없으면 안내 문구가 보이고 Pull Request 탭이 활성이다`() {
        val org = newOrg(); orgProject(org)
        val doc = page("/organizations/${org.name}/pullrequests")
        assertEquals("No pull requests have been received", doc.selectFirst(".error-wrap p")!!.text())
        assertEquals("/organizations/${org.name}/pullrequests", doc.selectFirst(".project-menu-nav li.active a")!!.attr("href"))
    }
}
