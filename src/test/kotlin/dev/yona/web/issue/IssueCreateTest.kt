package dev.yona.web.issue

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
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
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 등록 폼(`GET issueform`)과 등록(`POST issues/latest`)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:311-387, 562-660`, `app/views/issue/{create,partial_assignee,partial_select_subtask}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/issue-create-test"])
class IssueCreateTest : AbstractIntegrationTest() {

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
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ic$n", name = name ?: "ic$n", email = "ic$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, milestone: Boolean = true): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = milestone, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun form(project: Project, query: String = "", session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("${base(project)}/issueform$query").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun submit(project: Project, session: MockHttpSession, vararg params: Pair<String, String>) =
        mockMvc.perform(multipart("${base(project)}/issues/latest").session(session).apply { params.forEach { (k, v) -> param(k, v) } })

    // --- 폼 ---

    @Test
    fun `등록 폼은 로그인해야 열리고 공개 프로젝트는 아무 사용자나 쓸 수 있다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/issueform")).andExpect(status().is3xxRedirection)
        form(project, session = login(newUser()))
    }

    @Test
    fun `비공개 프로젝트의 등록 폼은 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("${base(project)}/issueform").session(login(newUser()))).andExpect(status().isForbidden)
        form(project, session = login(join(newUser(), project)))
    }

    @Test
    fun `폼의 제목과 입력란`() {
        val project = newProject()
        val doc = form(project, session = login(newUser()))
        assertEquals("New issue - ${project.owner}/${project.name}", doc.title())
        val form = doc.selectFirst("form#issue-form")!!
        assertEquals("${base(project)}/issues/latest", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        assertEquals("multipart/form-data", form.attr("enctype"))
        val title = form.selectFirst("input#title[name=title]")!!
        assertEquals("250", title.attr("maxlength")); assertEquals("1", title.attr("tabindex")); assertEquals("Title", title.attr("placeholder"))
        val body = form.selectFirst("textarea[name=body]")!!
        assertEquals("2", body.attr("tabindex")); assertEquals("", body.text())
        assertEquals("false", form.selectFirst("input#isDraft[name=isDraft]")!!.attr("value"))
        assertEquals("", form.selectFirst("input[name=referCommentId]")!!.attr("value"))
        assertTrue(form.select("#upload[data-resource-type=ISSUE_POST]").isNotEmpty())
        assertTrue(form.select("button#button-save, button#draft-save-btn").size == 2)
        assertTrue(doc.select("script").any { "issue.Write" in it.data() && "\"sMode\"" in it.data() && "if(false)" in it.data() })
    }

    @Test
    fun `저장소의 ISSUE_TEMPLATE_md가 본문의 처음 내용이다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("ISSUE_TEMPLATE.md" to "## 재현 방법\n\n".toByteArray()))
        assertEquals("## 재현 방법", form(project, session = login(newUser())).selectFirst("textarea[name=body]")!!.text().trim())
    }

    @Test
    fun `부모 이슈를 지정하면 하위 이슈 선택이 펼쳐지고 템플릿을 쓰지 않는다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("ISSUE_TEMPLATE.md" to "템플릿".toByteArray()))
        val parent = issues.save(Issue(project = project, number = 1, title = "부모", state = State.OPEN, createdDate = Date(), updatedDate = Date()))
        issues.save(Issue(project = project, number = 2, title = "다른", state = State.OPEN, createdDate = Date(), updatedDate = Date()))
        val doc = form(project, "?parentIssueId=${parent.id}", login(newUser()))
        assertEquals("", doc.selectFirst("textarea[name=body]")!!.text())
        assertTrue(doc.select(".subtask-wrap.show").isNotEmpty())
        val options = doc.select("select#parentId option")
        assertTrue(options.first { it.attr("value") == parent.id.toString() }.hasAttr("selected"))
        assertEquals("#1. 부모", options.first { it.attr("value") == parent.id.toString() }.text().trim())
        assertEquals(1, options.count { it.attr("value") == parent.id.toString() })
        assertTrue(options.any { it.text().trim() == "#2. 다른" })
        assertFalse(doc.selectFirst("select#parentId")!!.hasAttr("disabled"))
    }

    @Test
    fun `하위 이슈 선택이 접혀 있으면 프로젝트와 부모 선택은 비활성이다`() {
        val project = newProject()
        val doc = form(project, session = login(newUser()))
        assertTrue(doc.select(".subtask-wrap.show").isEmpty())
        assertTrue(doc.selectFirst("select#targetProjectId")!!.hasAttr("disabled"))
        assertEquals(project.id.toString(), doc.selectFirst("select#targetProjectId option")!!.attr("value"))
    }

    @Test
    fun `오른쪽 메뉴의 담당자, 마일스톤, 마감일, 라벨`() {
        val project = newProject()
        val member = join(newUser(), project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        milestones.save(Milestone(project = project, title = "닫힘", state = State.CLOSED))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "버그", color = "#f00"))
        val doc = form(project, session = login(member))
        assertTrue(doc.select("input#assignee[name=assigneeLoginId]").isNotEmpty())
        val select = doc.selectFirst("select#milestoneId")!!
        assertEquals(listOf("-1", milestone.id.toString()), select.select("option").map { it.attr("value") })
        assertTrue(doc.select("input#issueDueDate[name=dueDate]").isNotEmpty())
        assertEquals(label.id.toString(), doc.selectFirst("select#labelIds option[value]:not([value=''])")!!.attr("value"))
        // v1.6 create.scala.html은 partial_select_label에 data-close-on-select=false를 넘겨 여러 라벨을 연달아 고르게 한다
        assertEquals("false", doc.selectFirst("select#labelIds")!!.attr("data-close-on-select"))
    }

    @Test
    fun `열린 마일스톤이 없으면 새 마일스톤 링크를 보이고 마일스톤 메뉴를 끄면 선택이 없다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val doc = form(project, session = login(member))
        assertEquals("${base(project)}/newMilestoneForm", doc.selectFirst("#milestoneOption a")!!.attr("href"))
        val noMenu = newProject(milestone = false)
        assertTrue(form(noMenu, session = login(join(newUser(), noMenu))).select("#milestoneOption").isEmpty())
    }

    @Test
    fun `댓글에서 이슈를 만들면 본문에 원문 링크가 들어간다`() {
        val project = newProject()
        val user = join(newUser(), project)
        val session = login(user)
        val issue = issues.save(Issue(project = project, number = 1, title = "원본", state = State.OPEN, createdDate = Date(), updatedDate = Date()))
        val comment = dev.yona.domain.comment.IssueComment(contents = "고쳐야 할 점", createdDate = Date(), authorLoginId = "someone", issue = issue, projectId = project.id!!)
        entityManager.persist(comment)
        entityManager.flush()
        mockMvc.perform(get("${base(project)}/issues").session(session)).andExpect(status().isOk)
        val doc = Jsoup.parse(mockMvc.perform(get("/user/issues/new?commentId=${comment.id}").session(session).header("Host", "yona.test:9000")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(
            "고쳐야 할 점\n\n_Originally posted by @someone in http://yona.test:9000${base(project)}/issue/1#comment-${comment.id}",
            doc.selectFirst("textarea[name=body]")!!.wholeText().trim()
        )
        assertEquals(comment.id.toString(), doc.selectFirst("input[name=referCommentId]")!!.attr("value"))
        mockMvc.perform(get("/user/issues/new?commentId=999999").session(session)).andExpect(status().isNotFound)
    }

    // --- 등록 ---

    @Test
    fun `등록하면 이슈가 만들어지고 보기 화면으로 간다`() {
        val project = newProject()
        val author = newUser()
        mockMvc.perform(
            multipart("${base(project)}/issues/latest").session(login(author))
                .param("title", "첫 이슈").param("body", "본문").param("dueDate", "2030-05-06").param("isDraft", "false").param("assigneeLoginId", "")
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/1"))
        val issue = issues.findByProjectIdAndNumber(project.id!!, 1)!!
        assertEquals("첫 이슈", issue.title); assertEquals("본문", issue.body); assertEquals(State.OPEN, issue.state)
        assertEquals(author.loginId, issue.authorLoginId)
        assertEquals("2030-05-06 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(issue.dueDate!!))
        assertNull(issue.assignee)
    }

    @Test
    fun `댓글에서 만든 이슈는 원 댓글의 이슈에 파생 이슈 댓글을 단다`() {
        val project = newProject(); val user = join(newUser(), project)
        val originProject = newProject()
        val origin = issues.save(Issue(project = originProject, number = 1, title = "원본", state = State.OPEN, numOfComments = 1, createdDate = Date(1_000), updatedDate = Date(1_000)))
        val comment = dev.yona.domain.comment.IssueComment(contents = "고쳐야 할 점", createdDate = Date(), authorLoginId = "someone", issue = origin, projectId = originProject.id!!)
        entityManager.persist(comment); entityManager.flush()
        mockMvc.perform(
            multipart("${base(project)}/issues/latest").session(login(user)).header("Host", "yona.test:9000")
                .param("title", "파생").param("body", "b").param("isDraft", "false").param("assigneeLoginId", "").param("referCommentId", comment.id.toString())
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        val added = entityManager.createQuery("select c from IssueComment c where c.issue.id = :id and c.authorLoginId = :login", dev.yona.domain.comment.IssueComment::class.java)
            .setParameter("id", origin.id).setParameter("login", user.loginId).resultList.single()
        assertEquals("Derived issue: http://yona.test:9000${base(project)}/issue/1", added.contents)
        assertEquals(comment.id, added.parentComment?.id)
        // v1.6 newReferComment는 newComment와 달리 이슈의 updatedDate를 바꾸지 않는다
        assertEquals(Date(1_000), issues.findById(origin.id!!).get().updatedDate)
    }

    @Test
    fun `초안으로 만들면 파생 이슈 댓글을 달지 않는다`() {
        val project = newProject(); val user = join(newUser(), project)
        val originProject = newProject()
        val origin = issues.save(Issue(project = originProject, number = 1, title = "원본", state = State.OPEN, numOfComments = 1, createdDate = Date(), updatedDate = Date()))
        val comment = dev.yona.domain.comment.IssueComment(contents = "c", createdDate = Date(), authorLoginId = "someone", issue = origin, projectId = originProject.id!!)
        entityManager.persist(comment); entityManager.flush()
        submit(project, login(user), "title" to "초안", "body" to "b", "isDraft" to "true", "assigneeLoginId" to "", "referCommentId" to comment.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(0, entityManager.createQuery("select count(c) from IssueComment c where c.authorLoginId = :login", Long::class.javaObjectType).setParameter("login", user.loginId).singleResult)
    }

    @Test
    fun `담당자, 마일스톤, 라벨을 함께 정한다`() {
        val project = newProject()
        val author = join(newUser(), project); val assignee = join(newUser(), project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val a = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        val b = labels.save(IssueLabel(project = project, category = category, name = "b", color = "#fff"))
        submit(
            project, login(author), "title" to "t", "body" to "b", "assigneeLoginId" to assignee.loginId!!, "milestoneId" to milestone.id.toString(),
            "labelIds" to a.id.toString(), "labelIds" to b.id.toString()
        ).andExpect(status().isSeeOther)
        val issue = issues.findByProjectIdAndNumber(project.id!!, 1)!!
        assertEquals(assignee.id, issue.assignee?.user?.id); assertEquals(milestone.id, issue.milestone?.id)
        assertEquals(setOf(a.id, b.id), issue.labels.map { it.id }.toSet())
    }

    @Test
    fun `초안으로 저장하면 초안 상태이다`() {
        val project = newProject()
        submit(project, login(newUser()), "title" to "t", "body" to "b", "isDraft" to "true").andExpect(status().isSeeOther)
        val issue = issues.findByProjectIdAndNumber(project.id!!, 1)!!
        assertEquals(State.DRAFT, issue.state); assertEquals(true, issue.isDraft)
    }

    @Test
    fun `하위 이슈로 등록한다`() {
        val project = newProject()
        val parent = issues.save(Issue(project = project, number = 1, title = "부모", state = State.OPEN, createdDate = Date(), updatedDate = Date()))
        project.lastIssueNumber = 1; projects.save(project)
        submit(project, login(newUser()), "title" to "자식", "body" to "b", "parentIssueId" to parent.id.toString(), "targetProjectId" to project.id.toString())
            .andExpect(status().isSeeOther)
        assertEquals(parent.id, issues.findByProjectIdAndNumber(project.id!!, 2)!!.parent?.id)
    }

    @Test
    fun `제목이 없으면 오류와 함께 폼을 다시 보여 준다`() {
        val project = newProject()
        val doc = Jsoup.parse(submit(project, login(newUser()), "title" to "", "body" to "b").andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(doc.select("input#title.error").isNotEmpty())
        assertEquals("This field is required", doc.selectFirst("div.message div")?.text())
        assertEquals(0, issues.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `본문이 오지 않으면 413 화면이다`() {
        val project = newProject()
        submit(project, login(newUser()), "title" to "t").andExpect(status().isPayloadTooLarge)
        assertEquals(0, issues.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `다른 프로젝트를 지정하면 그 프로젝트에 만들고 거기로 간다`() {
        val project = newProject(); val target = newProject()
        val user = join(newUser(), target)
        submit(project, login(user), "title" to "t", "body" to "b", "targetProjectId" to target.id.toString())
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(target)}/issue/1"))
        assertEquals(1, issues.findAll().count { it.project?.id == target.id })
        assertEquals(0, issues.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `만들 수 없는 프로젝트나 없는 프로젝트를 지정하면 거부한다`() {
        val project = newProject(); val privateTarget = newProject(ProjectScope.PRIVATE)
        val user = newUser()
        submit(project, login(user), "title" to "t", "body" to "b", "targetProjectId" to privateTarget.id.toString()).andExpect(status().isForbidden)
        submit(project, login(user), "title" to "t", "body" to "b", "targetProjectId" to "999999").andExpect(status().isBadRequest)
    }

    @Test
    fun `로그인하지 않았거나 만들 수 없으면 등록할 수 없다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(multipart("${base(project)}/issues/latest").param("title", "t").param("body", "b")).andExpect(status().isForbidden)
        submit(project, login(newUser()), "title" to "t", "body" to "b").andExpect(status().isForbidden)
    }

    @Test
    fun `임시 첨부는 이슈로 옮겨진다`() {
        val project = newProject()
        val user = newUser()
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h-${System.nanoTime()}", containerType = ResourceType.USER, containerId = user.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = user.loginId))
        submit(project, login(user), "title" to "t", "body" to "b", "temporaryUploadFiles" to temp.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val moved = attachments.findById(temp.id!!).get()
        assertEquals(ResourceType.ISSUE_POST, moved.containerType)
        assertEquals(issues.findByProjectIdAndNumber(project.id!!, 1)!!.id, moved.containerId)
    }

    @Test
    fun `직접 만들기는 최근 방문한 프로젝트의 폼이다`() {
        val project = newProject()
        val user = join(newUser(), project)
        val session = login(user)
        mockMvc.perform(get("${base(project)}/issues").session(session)).andExpect(status().isOk)
        val doc = Jsoup.parse(mockMvc.perform(get("/user/issues/new").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("${base(project)}/issues/latest", doc.selectFirst("form#issue-form")!!.attr("action"))
        assertTrue(doc.select("script").any { "if(true)" in it.data() && "subtask-message" in it.data() })
    }

    @Test
    fun `방문한 프로젝트가 없으면 안내와 함께 주소는 그대로 첫 화면을 그린다 (v1_6 Application_index 직접 호출)`() {
        val session = login(newUser())
        mockMvc.perform(get("/user/issues/new").session(session)).andExpect(status().isOk).andExpect(forwardedUrl("/"))
        mockMvc.perform(get("/user/issues/new/mine").session(session)).andExpect(status().isOk).andExpect(forwardedUrl("/"))
    }
}
