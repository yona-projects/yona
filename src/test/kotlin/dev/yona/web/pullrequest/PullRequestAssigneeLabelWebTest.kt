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
 * 풀 리퀘스트의 담당자와 라벨 화면과 요청을 검증한다(로드맵 X-14, `decisions.md` D-34): 보기 화면의 지정 영역, 지정과 라벨 변경 요청의 권한과 검사,
 * 타임라인, 목록의 아바타와 라벨 칩, 목록 필터(담당자, 라벨).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestAssigneeLabelWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
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

    private fun label(p: Project, name: String, category: IssueLabelCategory? = null, exclusive: Boolean = false): IssueLabel {
        val c = category ?: IssueLabelCategory(project = p, name = "분류${++sequence}", isExclusive = exclusive).also { em.persist(it) }
        return IssueLabel(category = c, color = "#33aa77", name = name, project = p).also { em.persist(it) }
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

    // --- 지정 요청 ---

    @Test
    fun `매니저가 담당자를 지정하면 JSON으로 담당자를 돌려주고 저장하며 비울 수 있다`() {
        val s = setup()
        mockMvc.perform(assign(s, s.member, s.manager)).andExpect(status().isOk)
            .andExpect(jsonPath("$.assignee.loginId").value(s.member.loginId)).andExpect(jsonPath("$.assignee.name").value(s.member.name))
        assertEquals(setOf(s.member.id), pullRequests.findById(s.pr.id!!).get().assignees.map { it.id }.toSet())
        mockMvc.perform(assign(s, null, s.manager)).andExpect(status().isOk).andExpect(jsonPath("$.assignee").doesNotExist())
        assertTrue(pullRequests.findById(s.pr.id!!).get().assignees.isEmpty())
    }

    @Test
    fun `후보가 아닌 사람 지정은 400이고 구성원이 아닌 사용자와 익명은 지정할 수 없다`() {
        val s = setup()
        mockMvc.perform(assign(s, s.contributor, s.manager)).andExpect(status().isBadRequest)
        mockMvc.perform(assign(s, s.member, user())).andExpect(status().isForbidden)
        mockMvc.perform(post("${s.url}/assignees").contentType("application/json").content("""{"assignees":["${s.member.loginId}"]}""")).andExpect(status().is3xxRedirection)
        assertTrue(pullRequests.findById(s.pr.id!!).get().assignees.isEmpty())
    }

    @Test
    fun `없는 사용자나 한도를 넘는 담당자 수는 400이다`() {
        val s = setup()
        mockMvc.perform(post("${s.url}/assignees").session(login(s.manager)).contentType("application/json").content("""{"assignees":["nobody-here"]}""")).andExpect(status().isBadRequest)
        mockMvc.perform(post("${s.url}/assignees").session(login(s.manager)).contentType("application/json")
            .content("""{"assignees":["${s.member.loginId}","${s.manager.loginId}"]}""")).andExpect(status().isBadRequest)
        assertTrue(pullRequests.findById(s.pr.id!!).get().assignees.isEmpty())
    }

    // --- 라벨 요청 ---

    @Test
    fun `붙일 라벨과 뗄 라벨을 보내면 그만큼 바꾸고 다른 프로젝트의 라벨은 400이며 아무것도 바꾸지 않는다`() {
        val s = setup(); val bug = label(s.origin, "bug"); val doc = label(s.origin, "doc"); val foreign = label(s.fork, "foreign")
        mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug, doc))).andExpect(status().isOk)
        assertEquals(setOf(bug.id, doc.id), pullRequests.findById(s.pr.id!!).get().labels.map { it.id }.toSet())
        mockMvc.perform(changeLabels(s, s.manager, detach = listOf(bug))).andExpect(status().isOk)
        assertEquals(setOf(doc.id), pullRequests.findById(s.pr.id!!).get().labels.map { it.id }.toSet())
        mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug, foreign))).andExpect(status().isBadRequest)
        assertEquals(setOf(doc.id), pullRequests.findById(s.pr.id!!).get().labels.map { it.id }.toSet(), "검사에 걸리면 아무것도 바꾸지 않는다")
        mockMvc.perform(post("${s.url}/labels").session(login(s.manager)).param("attachingLabelIds", "999999")).andExpect(status().isBadRequest)
        mockMvc.perform(changeLabels(s, s.manager, detach = listOf(doc))).andExpect(status().isOk)
        assertTrue(pullRequests.findById(s.pr.id!!).get().labels.isEmpty())
    }

    @Test
    fun `라벨은 구성원이 아닌 사용자가 바꿀 수 없다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        mockMvc.perform(changeLabels(s, user(), attach = listOf(bug))).andExpect(status().isForbidden)
        assertTrue(pullRequests.findById(s.pr.id!!).get().labels.isEmpty())
    }

    // --- 타임라인 조각 ---

    @Test
    fun `변경 뒤 화면이 다시 불러올 타임라인 조각을 돌려준다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val html = mockMvc.perform(get("${s.url}/timeline")).andExpect(status().isOk).andReturn().response.contentAsString
        val d = Jsoup.parse("<ul>$html</ul>")
        assertEquals(3, d.select("li.event").size, "커밋, 담당자, 라벨 이벤트")
        assertTrue(d.text().contains("assigned") && d.text().contains("bug"), d.text())
    }

    // --- 보기 화면 ---

    @Test
    fun `편집 권한이 있으면 오른쪽 열에 담당자와 라벨 선택이 있고 주소는 데이터 속성에 담긴다`() {
        val s = setup(); val bug = label(s.origin, "bug"); val doc = label(s.origin, "doc")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val d = page(s.url, login(s.manager))
        val pane = d.selectFirst(".board-body.row-fluid > .span3.span-right-pane #pr-meta")!!
        assertEquals("${s.url}/assignees", pane.attr("data-assignees-url")); assertEquals("${s.url}/labels", pane.attr("data-labels-url"))
        assertEquals("${s.url}/timeline", pane.attr("data-timeline-url"))
        assertEquals("/-_-api/v1/owners/${s.origin.owner}/projects/${s.origin.name}/assignableUsers", pane.attr("data-candidates-url"))
        val assignee = pane.selectFirst("input#assignee[name=assigneeLoginId]")!!
        assertEquals(s.member.loginId, assignee.attr("value")); assertEquals(s.member.name, assignee.attr("data-name"))
        val labelSelect = pane.selectFirst("select#labelIds[name=labelIds]")!!
        assertEquals(setOf(bug.id.toString(), doc.id.toString()), labelSelect.select("option[value]").map { it.attr("value") }.filter { it.isNotEmpty() }.toSet())
        assertEquals(listOf(bug.id.toString()), labelSelect.select("option[selected]").map { it.attr("value") })
        assertNull(d.selectFirst("form#assignee-form")); assertNull(d.selectFirst("form#labels-form"), "화면을 다시 불러오는 폼은 더 쓰지 않는다")
    }

    @Test
    fun `본문과 상태와 타임라인은 왼쪽 열에 있다`() {
        val s = setup()
        val d = page(s.url)
        val left = d.selectFirst(".board-body.row-fluid > .span9.span-left-pane")!!
        assertNotNull(left.selectFirst(".content.markdown-wrap")); assertNotNull(left.selectFirst("#state")); assertNotNull(left.selectFirst("#comments"))
    }

    @Test
    fun `라벨과 담당자가 하나도 없어도 편집 권한자의 보기 화면이 열리고 아무것도 선택되어 있지 않다`() {
        val s = setup(); label(s.origin, "bug")
        val d = page(s.url, login(s.manager))
        assertEquals("", d.selectFirst("#assignee")!!.attr("value"))
        assertTrue(d.select("#labelIds option[selected]").isEmpty())
    }

    @Test
    fun `편집 권한이 없으면 담당자와 라벨을 읽기만 한다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val d = page(s.url)
        assertNull(d.selectFirst("#pr-meta input#assignee")); assertNull(d.selectFirst("#pr-meta select#labelIds"))
        assertEquals("/${s.member.loginId}", d.selectFirst(".span-right-pane .pr-assignee a.usf-group")!!.attr("href"))
        assertEquals("bug", d.selectFirst(".span-right-pane .pr-labels a.issue-label")!!.text())
    }

    @Test
    fun `지정과 라벨 변경이 타임라인에 남는다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val text = page(s.url).select("#comments").text()
        assertTrue(text.contains(s.member.name!!) && text.contains("assigned"), text)
        assertTrue(text.contains("bug"), text)
        mockMvc.perform(assign(s, null, s.manager))
        assertTrue(page(s.url).select("#comments").text().contains("removed the assignee"))
    }

    @Test
    fun `밝은 라벨 색의 글자색을 보정하는 스크립트가 있다`() {
        val s = setup(); label(s.origin, "bug")
        val html = mockMvc.perform(get(s.url).session(login(s.manager))).andReturn().response.contentAsString
        assertTrue(html.contains("getContrastColor"), "타임라인 라벨의 글자색을 배경색에 맞춘다(이슈 화면과 같다)")
    }

    // --- 목록 ---

    @Test
    fun `목록의 줄은 라벨 칩과 담당자 아바타를 받은 사람 아바타와 따로 보여 준다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val row = page("${s.base}/pullRequests").selectFirst("li.post-item")!!
        assertEquals("bug", row.selectFirst("a.issue-label")!!.text())
        val assignee = row.selectFirst("a.avatar-wrap.assignee")!!
        assertTrue(assignee.attr("data-original-title").contains(s.member.name!!) || assignee.attr("title").contains(s.member.name!!), assignee.outerHtml())
        assertNull(row.selectFirst("a.avatar-wrap.assinee"), "열린 요청은 받은 사람이 없다")
    }

    @Test
    fun `담당자와 라벨로 목록을 거른다`() {
        val s = setup(); val bug = label(s.origin, "bug"); val doc = label(s.origin, "doc")
        mockMvc.perform(assign(s, s.member, s.manager)); mockMvc.perform(changeLabels(s, s.manager, attach = listOf(bug)))
        val list = "${s.base}/pullRequests"
        fun rows(query: String) = page("$list$query").select("li.post-item").size
        assertEquals(1, rows("?assigneeId=${s.member.id}")); assertEquals(0, rows("?assigneeId=${s.manager.id}"))
        assertEquals(1, rows("?labelIds=${bug.id}")); assertEquals(0, rows("?labelIds=${doc.id}"))
        assertEquals(0, rows("?labelIds=${bug.id}&labelIds=${doc.id}"), "고른 라벨을 모두 가진 요청만 찾는다")
        assertEquals(1, rows("?assigneeId=${s.member.id}&labelIds=${bug.id}"))
    }

    @Test
    fun `목록 검색 상자에 담당자와 라벨 필터가 있고 고른 값이 선택된다`() {
        val s = setup(); val bug = label(s.origin, "bug")
        val d = page("${s.base}/pullRequests?assigneeId=${s.member.id}&labelIds=${bug.id}")
        val assignee = d.selectFirst("#advanced-search-form select#assignees[name=assigneeId]")!!
        assertEquals(s.member.id.toString(), assignee.selectFirst("option[selected]")!!.attr("value"))
        assertEquals("select2", assignee.attr("data-toggle"), "보낸 사람 필터처럼 select2로 보여야 한다")
        assertEquals("user", assignee.attr("data-format"))
        val labels = d.selectFirst("#advanced-search-form select#labelIds[name=labelIds]")!!
        assertEquals(listOf(bug.id.toString()), labels.select("option[selected]").map { it.attr("value") })
        assertNotNull(d.selectFirst("#advanced-search-form select#contributors"), "기존 보낸 사람 필터는 그대로이다")
        assertEquals("${s.base}/issue/labels.css", d.selectFirst("link[href$=/issue/labels.css]")?.attr("href"), "라벨 색을 그리려면 프로젝트 라벨 스타일시트가 필요하다")
    }
}
