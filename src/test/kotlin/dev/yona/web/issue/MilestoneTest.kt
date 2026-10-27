package dev.yona.web.issue

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 마일스톤을 검증한다. 목록, 만들기, 수정, 삭제, 열기/닫기, 보기(이슈 목록 제외).
 *
 * 원본: `app/controllers/MilestoneApp.java`, `app/models/Milestone.java`,
 * `app/views/milestone/{list,create,edit,view}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class MilestoneTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ms$n", name = "ms$n", email = "ms$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "mo$n", name = "mp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun manager(project: Project) = join(newUser(), project, RoleType.MANAGER)

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun daysFromNow(days: Int): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, days); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 0) }.time

    private fun newMilestone(project: Project, title: String, state: State = State.OPEN, dueDate: Date? = null, contents: String? = null) =
        milestones.save(Milestone(project = project, title = title, state = state, dueDate = dueDate, contents = contents))

    private fun newIssue(project: Project, milestone: Milestone?, state: State, number: Long) = issues.save(
        Issue(project = project, number = number, title = "이슈 $number", state = state, milestone = milestone, createdDate = Date(), updatedDate = Date())
    )

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    // --- 목록 ---

    @Test
    fun `목록의 제목과 탭`() {
        val project = newProject()
        val doc = page("${base(project)}/milestones")
        assertEquals("${project.name} - milestone - ${project.owner}/${project.name}", doc.title())
        assertEquals(listOf("Open", "Closed", "All"), doc.select("ul.nav-tabs li a").filter { "state=" in it.attr("href") }.map { it.text() })
        assertEquals("active", doc.selectFirst(".tab-wrap ul.nav-tabs li.active")?.className()?.trim())
        assertEquals("No milestone entered.", doc.selectFirst(".error-wrap p")?.text())
    }

    @Test
    fun `기본은 열린 마일스톤이고 마감일이 빠른 순이다`() {
        val project = newProject()
        newMilestone(project, "늦음", dueDate = daysFromNow(10))
        newMilestone(project, "이름", dueDate = daysFromNow(2))
        newMilestone(project, "닫힘", State.CLOSED, daysFromNow(1))
        val doc = page("${base(project)}/milestones")
        assertEquals(listOf("이름", "늦음"), doc.select("a.milestone-name").map { it.text() })
        assertEquals(listOf("닫힘"), page("${base(project)}/milestones?state=closed").select("a.milestone-name").map { it.text() })
        assertEquals(3, page("${base(project)}/milestones?state=all").select("a.milestone-name").size)
    }

    @Test
    fun `마감일이 없는 마일스톤은 먼저 나오고 내림차순이면 나중에 나온다`() {
        val project = newProject()
        newMilestone(project, "기한없음")
        newMilestone(project, "기한있음", dueDate = daysFromNow(3))
        assertEquals(listOf("기한없음", "기한있음"), page("${base(project)}/milestones").select("a.milestone-name").map { it.text() })
        assertEquals(listOf("기한있음", "기한없음"), page("${base(project)}/milestones?orderBy=dueDate&orderDir=desc").select("a.milestone-name").map { it.text() })
    }

    @Test
    fun `완료율 순으로 정렬한다`() {
        val project = newProject()
        val half = newMilestone(project, "절반")
        val full = newMilestone(project, "전부")
        newIssue(project, half, State.CLOSED, 1); newIssue(project, half, State.OPEN, 2)
        newIssue(project, full, State.CLOSED, 3)
        entityManager.flush()
        assertEquals(listOf("절반", "전부"), page("${base(project)}/milestones?orderBy=completionRate&orderDir=asc").select("a.milestone-name").map { it.text() })
        assertEquals(listOf("전부", "절반"), page("${base(project)}/milestones?orderBy=completionRate&orderDir=desc").select("a.milestone-name").map { it.text() })
    }

    @Test
    fun `각 항목은 이슈 수와 완료율과 마감일을 보여 준다`() {
        val project = newProject()
        val m = newMilestone(project, "v1", dueDate = daysFromNow(5))
        newIssue(project, m, State.CLOSED, 1); newIssue(project, m, State.OPEN, 2); newIssue(project, m, State.OPEN, 3)
        entityManager.flush()
        val item = page("${base(project)}/milestones").selectFirst("li.milestone")!!
        assertEquals("1 / 3", item.selectFirst(".issue-item")!!.text())
        assertEquals("33 %", item.selectFirst(".completion-rate")!!.text().replace(Regex("\\s+"), " ").trim())
        assertTrue(item.selectFirst(".bar")!!.attr("style").contains("width: 33%"))
        assertEquals(SimpleDateFormat("yyyy-MM-dd").format(m.dueDate!!), item.selectFirst(".due-date strong")!!.text())
        assertTrue(item.selectFirst(".due-date .date")!!.text().contains("days left"))
        assertEquals(listOf("#2", "#3"), item.select(".state-label.open ~ .item-name .number").map { it.text() }.sorted())
        assertEquals("/${project.owner}/${project.name}/milestone/${m.id}", item.selectFirst("a.milestone-name")!!.attr("href"))
    }

    @Test
    fun `이슈가 없으면 완료율을 보이지 않고 지난 마감일은 over이다`() {
        val project = newProject()
        newMilestone(project, "지남", dueDate = daysFromNow(-3))
        val item = page("${base(project)}/milestones").selectFirst("li.milestone")!!
        assertEquals("", item.selectFirst(".completion-rate")!!.text().trim())
        assertTrue(item.selectFirst(".due-date")!!.hasClass("over"))
        assertTrue(item.selectFirst(".due-date .date")!!.text().contains("days past"))
    }

    @Test
    fun `새 마일스톤 버튼은 만들 수 있는 사용자에게만 보인다`() {
        val project = newProject()
        assertTrue(page("${base(project)}/milestones").select("a[href\$=newMilestoneForm]").isEmpty())
        assertEquals("New milestone", page("${base(project)}/milestones", login(manager(project))).selectFirst("a[href\$=newMilestoneForm]")?.text())
    }

    @Test
    fun `비공개 프로젝트의 목록은 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("${base(project)}/milestones")).andExpect(status().isForbidden)
    }

    // --- 만들기 ---

    @Test
    fun `만들기 폼은 로그인한 구성원에게만 열린다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/newMilestoneForm")).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("${base(project)}/newMilestoneForm").session(login(newUser()))).andExpect(status().isForbidden)
        val doc = page("${base(project)}/newMilestoneForm", login(manager(project)))
        assertEquals("New milestone - ${project.owner}/${project.name}", doc.title())
        val form = doc.selectFirst("form#milestone-form")!!
        assertEquals("${base(project)}/milestones", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        assertEquals("250", form.selectFirst("input#title[name=title]")!!.attr("maxlength"))
        val editor = form.selectFirst("textarea[name=contents]")!!
        assertEquals("2", editor.attr("tabindex"))
        assertEquals("content-body", editor.attr("data-editor-mode"))
        assertTrue(editor.id().startsWith("editor-contents-"))
        assertTrue(form.select("[data-toggle=markdown-editor] .markdown-help .help-nav").size == 10)
        assertEquals(listOf("OPEN", "CLOSED"), form.select("input[name=state]").map { it.attr("value") })
        assertTrue(form.selectFirst("input#milestone-open")!!.hasAttr("checked"))
        assertTrue(form.select("input#dueDate[name=dueDate]").isNotEmpty())
        assertTrue(doc.select("script").any { "milestone.Write" in it.data() })
        assertTrue(doc.select("#upload[data-resource-type=MILESTONE]").isNotEmpty())
    }

    @Test
    fun `마일스톤을 만들면 마감일은 그날의 마지막 초가 되고 보기 화면으로 간다`() {
        val project = newProject()
        val result = mockMvc.perform(
            post("${base(project)}/milestones").session(login(manager(project)))
                .param("title", "v1.0").param("contents", "설명").param("state", "OPEN").param("dueDate", "2030-05-06")
        ).andExpect(status().isSeeOther).andReturn()
        val created = milestones.findAll().single { it.project?.id == project.id }
        assertEquals("v1.0", created.title)
        assertEquals("설명", created.contents)
        assertEquals(State.OPEN, created.state)
        assertEquals("2030-05-06 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(created.dueDate!!))
        assertEquals("${base(project)}/milestone/${created.id}", result.response.getHeader("Location"))
    }

    @Test
    fun `마감일 없이도 만들 수 있다`() {
        val project = newProject()
        mockMvc.perform(post("${base(project)}/milestones").session(login(manager(project))).param("title", "v2").param("state", "OPEN")).andExpect(status().isSeeOther)
        assertNull(milestones.findAll().single { it.project?.id == project.id }.dueDate)
    }

    @Test
    fun `제목이 비어 있으면 오류와 함께 폼을 다시 보여 준다`() {
        val project = newProject()
        val doc = Jsoup.parse(
            mockMvc.perform(post("${base(project)}/milestones").session(login(manager(project))).param("title", "").param("contents", "남아야 함").param("state", "OPEN"))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals("This field is required", doc.selectFirst("input#title.error ~ .message div")?.text())
        assertEquals("남아야 함", doc.selectFirst("textarea[name=contents]")!!.text())
        assertTrue(milestones.findAll().none { it.project?.id == project.id })
    }

    @Test
    fun `같은 제목이 있으면 안내와 함께 폼을 다시 보여 준다`() {
        val project = newProject()
        newMilestone(project, "v1")
        val doc = Jsoup.parse(
            mockMvc.perform(post("${base(project)}/milestones").session(login(manager(project))).param("title", "v1").param("state", "OPEN"))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals("This milestone title already exists. Please enter a different title.", doc.selectFirst("input#title ~ .message div")?.text())
        assertTrue(doc.html().contains("This milestone title already exists. Please enter a different title."))
        assertEquals(1, milestones.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `제목이 겹치고 마감일 형식도 틀리면 경고는 마감일 문구가 남는다 (v1_6은 validateDueDate가 나중에 덮는다)`() {
        val project = newProject()
        newMilestone(project, "v1")
        val html = mockMvc.perform(post("${base(project)}/milestones").session(login(manager(project))).param("title", "v1").param("dueDate", "2026-13-45").param("state", "OPEN"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val duplicated = "This milestone title already exists. Please enter a different title."
        // 제목 입력란의 필드 오류로만 한 번 나오고, 경고 문구는 마감일 형식이다
        assertEquals(1, Regex(Regex.escape(duplicated)).findAll(html).count())
        assertTrue(html.contains("Invalid format. Enter the due date in YYYY-MM-DD format."))
    }

    @Test
    fun `마감일 형식이 틀리면 안내한다`() {
        val project = newProject()
        val doc = Jsoup.parse(
            mockMvc.perform(post("${base(project)}/milestones").session(login(manager(project))).param("title", "v1").param("dueDate", "2030/01/01").param("state", "OPEN"))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertTrue(doc.html().contains("Invalid format. Enter the due date in YYYY-MM-DD format."))
        assertEquals("2030/01/01", doc.selectFirst("input#dueDate")!!.attr("value"))
        assertTrue(milestones.findAll().none { it.project?.id == project.id })
    }

    @Test
    fun `구성원이 아니면 만들 수 없다`() {
        val project = newProject()
        mockMvc.perform(post("${base(project)}/milestones").session(login(newUser())).param("title", "x").param("state", "OPEN")).andExpect(status().isForbidden)
        mockMvc.perform(post("${base(project)}/milestones").param("title", "x").param("state", "OPEN")).andExpect(status().isForbidden)
    }

    // --- 수정 ---

    @Test
    fun `수정 폼에는 현재 값이 들어 있다`() {
        val project = newProject()
        val m = newMilestone(project, "v1", State.CLOSED, daysFromNow(4), "설명")
        val doc = page("${base(project)}/milestone/${m.id}/editform", login(manager(project)))
        assertEquals("Edit milestone - ${project.owner}/${project.name}", doc.title())
        assertEquals("${base(project)}/milestone/${m.id}/edit", doc.selectFirst("form#milestone-form")!!.attr("action"))
        assertEquals("v1", doc.selectFirst("input#title")!!.attr("value"))
        assertEquals("설명", doc.selectFirst("textarea[name=contents]")!!.text())
        assertTrue(doc.selectFirst("input#milestone-close")!!.hasAttr("checked"))
        assertFalse(doc.selectFirst("input#milestone-open")!!.hasAttr("checked"))
        assertEquals(SimpleDateFormat("yyyy-MM-dd").format(m.dueDate!!), doc.selectFirst("input#dueDate")!!.attr("value"))
        assertEquals(m.id.toString(), doc.selectFirst("#upload")!!.attr("data-resource-id"))
    }

    @Test
    fun `마일스톤을 고친다`() {
        val project = newProject()
        val m = newMilestone(project, "v1", dueDate = daysFromNow(4))
        mockMvc.perform(
            post("${base(project)}/milestone/${m.id}/edit").session(login(manager(project)))
                .param("title", "v1.1").param("contents", "새 설명").param("state", "CLOSED").param("dueDate", "2031-01-02")
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/milestone/${m.id}"))
        entityManager.flush(); entityManager.clear()
        val changed = milestones.findById(m.id!!).get()
        assertEquals("v1.1", changed.title)
        assertEquals("새 설명", changed.contents)
        assertEquals(State.CLOSED, changed.state)
        assertEquals("2031-01-02 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(changed.dueDate!!))
    }

    @Test
    fun `제목을 바꾸지 않으면 중복 검사를 하지 않고 바꾸면 한다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        newMilestone(project, "v2")
        val session = login(manager(project))
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/edit").session(session).param("title", "v1").param("contents", "c").param("state", "OPEN")).andExpect(status().isSeeOther)
        val doc = Jsoup.parse(
            mockMvc.perform(post("${base(project)}/milestone/${m.id}/edit").session(session).param("title", "v2").param("contents", "c").param("state", "OPEN")).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertTrue(doc.html().contains("This milestone title already exists. Please enter a different title."))
    }

    @Test
    fun `내용이 오지 않으면 413 화면이다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/edit").session(login(manager(project))).param("title", "v1").param("state", "OPEN"))
            .andExpect(status().isPayloadTooLarge)
    }

    @Test
    fun `고치면 사용자의 임시 첨부가 마일스톤으로 옮겨진다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val user = manager(project)
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h1", containerType = ResourceType.USER, containerId = user.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = user.loginId))
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/edit").session(login(user)).param("title", "v1").param("contents", "c").param("state", "OPEN")).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val moved = attachments.findById(temp.id!!).get()
        assertEquals(ResourceType.MILESTONE, moved.containerType)
        assertEquals(m.id, moved.containerId)
    }

    @Test
    fun `수정은 구성원만 할 수 있다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val outsider = login(newUser())
        mockMvc.perform(get("${base(project)}/milestone/${m.id}/editform").session(outsider)).andExpect(status().isForbidden)
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/edit").session(outsider).param("title", "x").param("contents", "c").param("state", "OPEN")).andExpect(status().isForbidden)
        val member = login(join(newUser(), project, RoleType.MEMBER))
        mockMvc.perform(get("${base(project)}/milestone/${m.id}/editform").session(member)).andExpect(status().isOk)
    }

    // --- 삭제, 열기, 닫기 ---

    @Test
    fun `지우면 이슈의 마일스톤이 비워지고 목록으로 간다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val issue = newIssue(project, m, State.OPEN, 1)
        entityManager.flush(); entityManager.clear()
        mockMvc.perform(delete("${base(project)}/milestone/${m.id}/delete").session(login(manager(project))))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/milestones"))
        entityManager.flush(); entityManager.clear()
        assertNull(milestones.findById(m.id!!).orElse(null))
        assertNull(issues.findById(issue.id!!).get().milestone)
    }

    @Test
    fun `Ajax로 지우면 204와 목록 주소를 알린다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        mockMvc.perform(delete("${base(project)}/milestone/${m.id}/delete").session(login(manager(project))).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent).andExpect(header().string("Location", "${base(project)}/milestones"))
    }

    @Test
    fun `다른 프로젝트의 마일스톤은 지울 수 없다`() {
        val project = newProject()
        val otherProject = newProject()
        val other = newMilestone(otherProject, "남의 것")
        val user = manager(project)
        join(user, otherProject, RoleType.MANAGER)
        mockMvc.perform(delete("${base(project)}/milestone/${other.id}/delete").session(login(user))).andExpect(status().isInternalServerError)
        assertNotNull(milestones.findById(other.id!!).orElse(null))
    }

    @Test
    fun `열고 닫는다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val session = login(manager(project))
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/close").session(session)).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/milestone/${m.id}"))
        entityManager.flush(); entityManager.clear()
        assertEquals(State.CLOSED, milestones.findById(m.id!!).get().state)
        mockMvc.perform(post("${base(project)}/milestone/${m.id}/open").session(session)).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.OPEN, milestones.findById(m.id!!).get().state)
    }

    // --- 보기 ---

    @Test
    fun `보기 화면의 머리와 설명`() {
        val project = newProject()
        val m = newMilestone(project, "v1", dueDate = daysFromNow(2), contents = "**굵게**")
        val doc = page("${base(project)}/milestone/${m.id}")
        assertEquals("v1 - ${project.owner}/${project.name}", doc.title())
        assertEquals("v1", doc.selectFirst("h4 a.title")!!.text())
        assertTrue(doc.selectFirst("h4 .due-date strong")!!.text().isNotBlank())
        assertEquals("Open", doc.selectFirst("h4 .badge.badge-issue-open")!!.text())
        assertEquals("<strong>굵게</strong>", doc.selectFirst(".milestone-desc .markdown-wrap p")!!.html())
        assertTrue(doc.select(".progress .bar").isNotEmpty())
    }

    @Test
    fun `설명이 없으면 빈 칸이다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        assertTrue(page("${base(project)}/milestone/${m.id}").select(".content.empty-content").isNotEmpty())
    }

    @Test
    fun `버튼은 권한에 따라 보인다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val anonymous = page("${base(project)}/milestone/${m.id}")
        assertTrue(anonymous.select(".milesion-wrap a[href\$=editform], .milesion-wrap a[href=#deleteConfirm], .milesion-wrap button[data-request-uri\$=close]").isEmpty(), anonymous.select("a[href\$=editform]").outerHtml())
        val doc = page("${base(project)}/milestone/${m.id}", login(manager(project)))
        assertTrue(doc.select("a[href\$=/milestone/${m.id}/editform]").isNotEmpty())
        assertTrue(doc.select("a[href=#deleteConfirm]").isNotEmpty())
        assertEquals("${base(project)}/milestone/${m.id}/close", doc.selectFirst("button[data-request-method=post]")!!.attr("data-request-uri"))
        assertEquals("${base(project)}/milestone/${m.id}/delete", doc.selectFirst("#deleteConfirm button[data-request-method=delete]")!!.attr("data-request-uri"))
    }

    @Test
    fun `닫힌 마일스톤에는 열기 버튼이 있다`() {
        val project = newProject()
        val m = newMilestone(project, "v1", State.CLOSED)
        val doc = page("${base(project)}/milestone/${m.id}", login(manager(project)))
        assertEquals("${base(project)}/milestone/${m.id}/open", doc.selectFirst("button[data-request-method=post]")!!.attr("data-request-uri"))
    }

    @Test
    fun `비공개 프로젝트의 마일스톤은 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val m = newMilestone(project, "v1")
        mockMvc.perform(get("${base(project)}/milestone/${m.id}")).andExpect(status().isForbidden)
    }

    // --- 보기 화면의 이슈 ---

    @Test
    fun `보기 화면은 이슈 탭의 개수와 해당 상태의 이슈 목록을 보인다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        val parent = newIssue(project, m, State.OPEN, 1)
        newIssue(project, m, State.CLOSED, 2)
        issues.save(Issue(project = project, number = 3, title = "자식", state = State.OPEN, milestone = m, parent = parent, createdDate = Date(), updatedDate = Date()))
        newIssue(project, null, State.OPEN, 4)
        entityManager.flush()
        val doc = page("${base(project)}/milestone/${m.id}")
        val tabs = doc.select("#issues ul.nav-tabs li")
        assertEquals(listOf("Open1", "Closed1", "All2"), tabs.map { it.text().replace(" ", "") })
        assertEquals("${base(project)}/milestone/${m.id}?state=open#issues", tabs[0].selectFirst("a")!!.attr("href"))
        assertEquals("active", tabs[0].className().trim())
        // 목록은 마일스톤의 열린 이슈를 번호 내림차순으로 모두 보인다(하위 이슈 포함).
        assertEquals(listOf("#3", "#1"), doc.select("#issues li.post-item .post-id").map { it.text() })
        assertEquals(listOf("#2"), page("${base(project)}/milestone/${m.id}?state=closed").select("#issues li.post-item .post-id").map { it.text() })
        assertEquals(listOf("#3", "#2", "#1"), page("${base(project)}/milestone/${m.id}?state=all").select("#issues li.post-item .post-id").map { it.text() })
    }

    @Test
    fun `보기 화면에는 일괄 변경 막대와 이슈 검색 상자가 있다`() {
        val project = newProject()
        val m = newMilestone(project, "v1")
        newIssue(project, m, State.OPEN, 1)
        val doc = page("${base(project)}/milestone/${m.id}")
        assertTrue(doc.select("#issues .mass-update-wrap form#mass-update-form").isNotEmpty())
        assertEquals("search at current milestone", doc.selectFirst("#issues input.textbox[data-toggle=item-search]")!!.attr("placeholder"))
        assertEquals("issue-item", doc.selectFirst("#issues input.textbox")!!.attr("data-items"))
        assertTrue(doc.select("script").any { "milestone.View" in it.data() && "\"${m.id}\"" in it.data() })
    }
}
