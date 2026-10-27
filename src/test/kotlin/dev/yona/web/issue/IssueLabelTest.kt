package dev.yona.web.issue

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 라벨과 라벨 분류 관리를 검증한다.
 *
 * 원본: `app/controllers/IssueLabelApp.java`, `app/models/IssueLabel.java`, `app/models/IssueLabelCategory.java`,
 * `app/views/project/{issuelabels,partial_issuelabels_list,partial_issuelabels_editlabel,partial_issuelabels_editcategory}.scala.html`,
 * `app/views/common/issueLabelColor.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueLabelTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var titleHeads: dev.yona.domain.project.TitleHeadRepository
    @Autowired private lateinit var mentionRepository: dev.yona.domain.notification.MentionRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lb$n", name = "lb$n", email = "lb$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "lo$n", name = "lp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun manager(project: Project) = login(join(newUser(), project, RoleType.MANAGER))

    private fun newCategory(project: Project, name: String, exclusive: Boolean = false) =
        categories.save(IssueLabelCategory(project = project, name = name, isExclusive = exclusive))

    private fun newLabel(project: Project, category: IssueLabelCategory, name: String, color: String = "#f44336") =
        labels.save(IssueLabel(project = project, category = category, name = name, color = color))

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    // --- 관리 화면 ---

    @Test
    fun `관리 화면은 프로젝트를 고칠 수 있는 사용자만 볼 수 있다`() {
        val project = newProject()
        val member = login(join(newUser(), project, RoleType.MEMBER))
        mockMvc.perform(get("${base(project)}/issue/labelsform").session(member)).andExpect(status().isForbidden)
        mockMvc.perform(get("${base(project)}/issue/labelsform")).andExpect(status().isForbidden)
        mockMvc.perform(get("${base(project)}/issue/labelsform").session(manager(project))).andExpect(status().isOk)
    }

    @Test
    fun `관리 화면의 제목과 새 라벨 폼과 복사 폼`() {
        val project = newProject()
        val page = Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/labelsform").session(manager(project))).andReturn().response.contentAsString)
        assertEquals("Label - ${project.owner}/${project.name}", page.title())
        val copy = page.selectFirst("form#copyLabel")!!
        assertEquals("${base(project)}/copyLabels", copy.attr("action"))
        assertEquals(listOf("owner", "projectName"), copy.select("input[type=text]").map { it.attr("name") })
        val form = page.selectFirst("form#frmNewLabel")!!
        assertEquals("${base(project)}/issue/labels", form.attr("action"))
        assertEquals(listOf("category", "name", "color"), form.select("input[type=text]").map { it.attr("name") })
        assertEquals(17, form.select("button.btn-preset-color").size)
        assertTrue(page.select("#editCategory, #editLabel").size == 2)
        assertTrue(page.select("script").any { "issue.LabelEditor" in it.data() && "${base(project)}/issue/labels" in it.data().replace("\\/", "/") })
    }

    @Test
    fun `라벨이 없으면 빈 목록 안내가 나온다`() {
        val project = newProject()
        val page = Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/labelsform").session(manager(project))).andReturn().response.contentAsString)
        assertEquals("No label exists", page.selectFirst("#labelsList .error-wrap p")?.text())
    }

    @Test
    fun `라벨은 분류별로 묶이고 이름순으로 나온다`() {
        val project = newProject()
        val bug = newCategory(project, "Bug", exclusive = true)
        val area = newCategory(project, "Area")
        newLabel(project, bug, "urgent")
        val web = newLabel(project, area, "web", "#2196f3")
        newLabel(project, area, "api")
        val page = Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/labelsform").session(manager(project))).andReturn().response.contentAsString)
        assertEquals(setOf("Bug", "Area"), page.select("#labelsList .category-wrap").map { it.attr("data-category-name") }.toSet())
        val areaBlock = page.selectFirst("#labelsList .category-wrap[data-category-name=Area]")!!
        assertEquals(listOf("api", "web"), areaBlock.select("span.issue-label").map { it.text() })
        assertTrue(areaBlock.select("i.category-exclusive").hasClass("multiple"))
        assertTrue(page.selectFirst("#labelsList .category-wrap[data-category-name=Bug] i.category-exclusive")!!.hasClass("single"))
        val edit = areaBlock.selectFirst("button[data-label-color=#2196f3]")!!
        assertEquals("${base(project)}/issue/label/${web.id}", edit.attr("data-update-uri"))
        assertEquals("web", edit.attr("data-label-name"))
        assertEquals("${base(project)}/issue/label/${web.id}/delete", areaBlock.selectFirst("button[data-label-id=${web.id}][data-delete-uri]")!!.attr("data-delete-uri"))
        assertEquals("${base(project)}/issue/label/category/${area.id}", areaBlock.selectFirst("button[data-category-update-uri]")!!.attr("data-category-update-uri"))
        assertEquals("${base(project)}/issue/labels.css", page.selectFirst("#labelsList link[rel=stylesheet]")?.attr("href"))
    }

    // --- 목록 ---

    @Test
    fun `JSON으로 라벨 목록을 받는다`() {
        val project = newProject()
        val area = newCategory(project, "Area")
        val api = newLabel(project, area, "api", "#111111")
        mockMvc.perform(get("${base(project)}/issue/labels").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/json"))
            .andExpect(jsonPath("$[0].id").value(api.id.toString()))
            .andExpect(jsonPath("$[0].category").value("Area"))
            .andExpect(jsonPath("$[0].categoryId").value(area.id.toString()))
            .andExpect(jsonPath("$[0].color").value("#111111"))
            .andExpect(jsonPath("$[0].name").value("api"))
    }

    @Test
    fun `JSON을 받지 못하는 요청은 406이다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/issue/labels").header(HttpHeaders.ACCEPT, "text/html")).andExpect(status().isNotAcceptable)
    }

    @Test
    fun `PJAX 요청에는 목록 조각을 캐시하지 않게 돌려준다`() {
        val project = newProject()
        newLabel(project, newCategory(project, "Area"), "api")
        val result = mockMvc.perform(get("${base(project)}/issue/labels").header("X-PJAX", "true").header(HttpHeaders.ACCEPT, "text/html"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store")).andReturn()
        val fragment = Jsoup.parse(result.response.contentAsString)
        assertEquals(1, fragment.select(".category-wrap").size)
        assertNull(fragment.selectFirst("html > head > title")?.takeIf { it.text().isNotBlank() })
    }

    @Test
    fun `비공개 프로젝트의 목록은 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("${base(project)}/issue/labels")).andExpect(status().isForbidden)
    }

    // --- 라벨 추가 ---

    @Test
    fun `새 분류와 함께 라벨을 만든다`() {
        val project = newProject()
        mockMvc.perform(
            post("${base(project)}/issue/labels").session(manager(project)).header(HttpHeaders.ACCEPT, "application/json")
                .param("labelName", "bug").param("labelColor", "#f44336").param("categoryName", "Type").param("categoryIsExclusive", "true")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("bug")).andExpect(jsonPath("$.color").value("#f44336"))
            .andExpect(jsonPath("$.category").value("Type"))
        val category = categories.findAll().single { it.project?.id == project.id }
        assertEquals("Type", category.name)
        assertEquals(true, category.isExclusive)
        assertEquals(listOf("bug"), labels.findAll().filter { it.project?.id == project.id }.map { it.name })
    }

    @Test
    fun `있는 분류에는 분류를 새로 만들지 않는다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        mockMvc.perform(
            post("${base(project)}/issue/labels").session(manager(project)).header(HttpHeaders.ACCEPT, "application/json")
                .param("labelName", "bug").param("labelColor", "#fff").param("categoryName", "Type")
        ).andExpect(status().isCreated).andExpect(jsonPath("$.categoryId").value(type.id.toString()))
        assertEquals(1, categories.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `같은 분류에 같은 이름이 있으면 204이다`() {
        val project = newProject()
        newLabel(project, newCategory(project, "Type"), "bug")
        mockMvc.perform(
            post("${base(project)}/issue/labels").session(manager(project))
                .param("labelName", "bug").param("labelColor", "#fff").param("categoryName", "Type")
        ).andExpect(status().isNoContent)
        assertEquals(1, labels.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `JSON을 받지 않는 요청에는 본문 없이 201이다`() {
        val project = newProject()
        mockMvc.perform(
            post("${base(project)}/issue/labels").session(manager(project)).header(HttpHeaders.ACCEPT, "text/html")
                .param("labelName", "bug").param("labelColor", "#fff").param("categoryName", "Type")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `필수 항목이 비면 항목별 오류를 JSON으로 알린다`() {
        val project = newProject()
        mockMvc.perform(post("${base(project)}/issue/labels").session(manager(project)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.labelName[0]").value("Name is a required field."))
            .andExpect(jsonPath("$.labelColor[0]").value("Color field is required."))
            .andExpect(jsonPath("$.categoryName[0]").value("Category is a required field."))
        mockMvc.perform(
            post("${base(project)}/issue/labels").session(manager(project))
                .param("labelName", "x".repeat(256)).param("labelColor", "#fff").param("categoryName", "y".repeat(256))
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.labelName[0]").value("Name length must be less than 250 characters."))
            .andExpect(jsonPath("$.categoryName[0]").value("Category length must be less than 250 characters."))
    }

    @Test
    fun `공개 프로젝트의 비구성원과 익명은 라벨을 만들 수 없다`() {
        val project = newProject()
        val outsider = login(newUser())
        listOf(outsider, MockHttpSession()).forEach {
            mockMvc.perform(post("${base(project)}/issue/labels").session(it).param("labelName", "a").param("labelColor", "#fff").param("categoryName", "c"))
                .andExpect(status().isForbidden)
        }
    }

    @Test
    fun `프로젝트 구성원은 라벨을 만들 수 있다`() {
        val project = newProject()
        val member = login(join(newUser(), project, RoleType.MEMBER))
        mockMvc.perform(post("${base(project)}/issue/labels").session(member).param("labelName", "a").param("labelColor", "#fff").param("categoryName", "c"))
            .andExpect(status().isCreated)
    }

    // --- 라벨 삭제 ---

    @Test
    fun `라벨을 지우면 이슈에서도 빠지고 비게 된 분류도 지워진다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val bug = newLabel(project, type, "bug")
        val issue = issues.save(Issue(project = project, number = 1, title = "t", state = State.OPEN, createdDate = Date(), updatedDate = Date(), labels = mutableSetOf(bug)))
        entityManager.flush()
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(manager(project)).param("_method", "delete"))
            .andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        assertNull(labels.findById(bug.id!!).orElse(null))
        assertNull(categories.findById(type.id!!).orElse(null))
        assertTrue(issues.findById(issue.id!!).get().labels.isEmpty())
    }

    @Test
    fun `라벨을 지우면 이슈를 update로 저장한다 (v1_6 IssueLabel_delete의 issue_save, 제목 머리말 빈도와 멘션 재계산)`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val bug = newLabel(project, type, "bug")
        val mentioned = users.save(User(loginId = "lblm${++sequence}", name = "멘션", email = "lblm$sequence@example.com", state = UserState.ACTIVE))
        val issue = issues.save(Issue(project = project, number = 1, title = "[버그] 제목", body = "확인 @${mentioned.loginId}", state = State.OPEN, createdDate = Date(), updatedDate = Date(), labels = mutableSetOf(bug)))
        entityManager.flush()
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(manager(project)).param("_method", "delete")).andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        val heads = titleHeads.findAll().filter { it.project?.id == project.id && it.headKeyword == "버그" }
        assertEquals(1, heads.size)
        assertEquals(1, heads.single().frequency)
        assertEquals(listOf(mentioned.loginId), mentionRepository.findAll().filter { it.resourceType == ResourceType.ISSUE_POST && it.resourceId == issue.id.toString() }.mapNotNull { it.user?.loginId })
    }

    @Test
    fun `분류에 다른 라벨이 남아 있으면 분류는 남는다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val bug = newLabel(project, type, "bug")
        newLabel(project, type, "feature")
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(manager(project)).param("_method", "delete")).andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        assertNotNull(categories.findById(type.id!!).orElse(null))
    }

    @Test
    fun `_method가 delete가 아니면 400이다`() {
        val project = newProject()
        val bug = newLabel(project, newCategory(project, "Type"), "bug")
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(manager(project)).param("_method", "put")).andExpect(status().isBadRequest)
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(manager(project))).andExpect(status().isBadRequest)
        assertNotNull(labels.findById(bug.id!!).orElse(null))
    }

    @Test
    fun `구성원이 아닌 사용자는 라벨을 지울 수 없다`() {
        val project = newProject()
        val bug = newLabel(project, newCategory(project, "Type"), "bug")
        mockMvc.perform(post("${base(project)}/issue/label/${bug.id}/delete").session(login(newUser())).param("_method", "delete")).andExpect(status().isForbidden)
    }

    // --- 라벨 수정 ---

    @Test
    fun `라벨의 이름과 색과 분류를 바꾼다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val area = newCategory(project, "Area")
        val bug = newLabel(project, type, "bug")
        mockMvc.perform(put("${base(project)}/issue/label/${bug.id}").session(manager(project)).param("name", "defect").param("color", "#000").param("category.id", area.id.toString()))
            .andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        val changed = labels.findById(bug.id!!).get()
        assertEquals("defect", changed.name)
        assertEquals("#000", changed.color)
        assertEquals(area.id, changed.category?.id)
        assertEquals(project.id, changed.project?.id)
    }

    @Test
    fun `라벨 수정에서 필수 항목이 비면 400이다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val bug = newLabel(project, type, "bug")
        mockMvc.perform(put("${base(project)}/issue/label/${bug.id}").session(manager(project)).param("category.id", type.id.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.name[0]").value("Name is a required field."))
            .andExpect(jsonPath("$.color[0]").value("Color field is required."))
    }

    @Test
    fun `다른 프로젝트의 분류로는 옮길 수 없다`() {
        val project = newProject()
        val foreign = newCategory(newProject(), "Foreign")
        val bug = newLabel(project, newCategory(project, "Type"), "bug")
        mockMvc.perform(put("${base(project)}/issue/label/${bug.id}").session(manager(project)).param("name", "bug").param("color", "#fff").param("category.id", foreign.id.toString()))
            .andExpect(status().isBadRequest)
    }

    // --- 분류 ---

    @Test
    fun `분류 목록과 한 분류를 JSON으로 받는다`() {
        val project = newProject()
        val type = newCategory(project, "Type", exclusive = true)
        newCategory(project, "Area")
        mockMvc.perform(get("${base(project)}/issue/label/categories").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].name").value("Area")).andExpect(jsonPath("$[1].name").value("Type"))
            .andExpect(jsonPath("$[1].isExclusive").value("true"))
        mockMvc.perform(get("${base(project)}/issue/label/category/${type.id}").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(type.id.toString())).andExpect(jsonPath("$.name").value("Type"))
            // v1.6는 `.as("application/json")`라 문자 집합이 없다
            .andExpect(header().string("Content-Type", "application/json"))
        mockMvc.perform(get("${base(project)}/issue/label/categories").header(HttpHeaders.ACCEPT, "text/html")).andExpect(status().isNotAcceptable)
        mockMvc.perform(get("${base(project)}/issue/label/category/${type.id}").header(HttpHeaders.ACCEPT, "text/html")).andExpect(status().isNotAcceptable)
    }

    @Test
    fun `분류의 이름과 단일 선택 여부를 바꾼다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        mockMvc.perform(put("${base(project)}/issue/label/category/${type.id}").session(manager(project)).param("name", "Kind").param("isExclusive", "true").param("project.id", project.id.toString()))
            .andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        val changed = categories.findById(type.id!!).get()
        assertEquals("Kind", changed.name)
        assertEquals(true, changed.isExclusive)
    }

    @Test
    fun `같은 프로젝트의 다른 분류와 이름이 겹치면 400이다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        newCategory(project, "Area")
        mockMvc.perform(put("${base(project)}/issue/label/category/${type.id}").session(manager(project)).param("name", "Area").param("project.id", project.id.toString()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `분류를 만든다`() {
        val project = newProject()
        mockMvc.perform(
            post("${base(project)}/issue/label/categories").session(manager(project)).header(HttpHeaders.ACCEPT, "application/json")
                .param("name", "Type").param("isExclusive", "true").param("project.id", project.id.toString())
        ).andExpect(status().isCreated).andExpect(jsonPath("$.name").value("Type")).andExpect(jsonPath("$.isExclusive").value("true"))
        mockMvc.perform(
            post("${base(project)}/issue/label/categories").session(manager(project)).param("name", "Type").param("project.id", project.id.toString())
        ).andExpect(status().isNoContent)
        mockMvc.perform(post("${base(project)}/issue/label/categories").session(manager(project)).param("project.id", project.id.toString()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `분류를 지우면 속한 라벨도 함께 지워진다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val bug = newLabel(project, type, "bug")
        mockMvc.perform(delete("${base(project)}/issue/label/category/${type.id}").session(manager(project))).andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        assertNull(categories.findById(type.id!!).orElse(null))
        assertNull(labels.findById(bug.id!!).orElse(null))
    }

    // --- 복사 ---

    @Test
    fun `다른 프로젝트의 라벨을 복사하고 이미 있는 것은 건너뛴다`() {
        val source = newProject()
        val target = newProject()
        val type = newCategory(source, "Type", exclusive = true)
        newLabel(source, type, "bug", "#111")
        newLabel(source, type, "feature", "#222")
        val targetType = newCategory(target, "Type")
        newLabel(target, targetType, "bug", "#999")
        mockMvc.perform(post("${base(target)}/copyLabels").session(manager(target)).param("owner", source.owner!!).param("projectName", source.name!!))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(target)}/issue/labelsform"))
        entityManager.flush(); entityManager.clear()
        val copied = labels.findAll().filter { it.project?.id == target.id }
        assertEquals(setOf("bug", "feature"), copied.map { it.name }.toSet())
        assertEquals("#999", copied.single { it.name == "bug" }.color)
        assertEquals(1, categories.findAll().count { it.project?.id == target.id })
    }

    @Test
    fun `읽을 수 없는 프로젝트의 라벨은 복사하지 않는다`() {
        val source = newProject(ProjectScope.PRIVATE)
        val target = newProject()
        newLabel(source, newCategory(source, "Type"), "bug")
        mockMvc.perform(post("${base(target)}/copyLabels").session(manager(target)).param("owner", source.owner!!).param("projectName", source.name!!))
            .andExpect(redirectedUrl("${base(target)}/issue/labelsform"))
        assertEquals(0, labels.findAll().count { it.project?.id == target.id })
    }

    // --- 스타일 ---

    @Test
    fun `라벨 색 스타일 시트는 배경에 따라 글자색이 정해진다`() {
        val project = newProject()
        val type = newCategory(project, "Type")
        val light = newLabel(project, type, "light", "#ffeb3b")
        val dark = newLabel(project, type, "dark", "#3f51b5")
        val short = newLabel(project, type, "short", "#000")
        val css = mockMvc.perform(get("${base(project)}/issue/labels.css")).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/css")).andReturn().response.contentAsString
        assertTrue(".issue-label[data-label-id=\"${light.id}\"]{" in css)
        assertTrue("box-shadow: inset 2px 0 0px #ffeb3b;" in css)
        val active = { l: IssueLabel -> css.substringAfter(".issue-label.active[data-label-id=\"${l.id}\"]{").substringBefore("}") }
        assertTrue("color: dimgray;" in active(light))
        assertTrue("background-color: #3f51b5;" in active(dark) && "color: white;" in active(dark))
        // v1.6의 3자리 색 처리 결함: 흰색으로 계산되어 어두운 색에도 dimgray가 된다.
        assertTrue("color: dimgray;" in active(short))
    }

    @Test
    fun `스타일 시트는 ETag가 같으면 304이다`() {
        val project = newProject()
        newLabel(project, newCategory(project, "Type"), "bug")
        val etag = mockMvc.perform(get("${base(project)}/issue/labels.css")).andReturn().response.getHeader("ETag")!!
        mockMvc.perform(get("${base(project)}/issue/labels.css").header("If-None-Match", etag)).andExpect(status().isNotModified)
        newLabel(project, newCategory(project, "Area"), "web")
        mockMvc.perform(get("${base(project)}/issue/labels.css").header("If-None-Match", etag)).andExpect(status().isOk)
    }
}
