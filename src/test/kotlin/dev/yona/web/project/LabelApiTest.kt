package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.LabelRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 라벨 JSON API를 검증한다: 전역 `GET /labels`, `GET /categories`와 프로젝트의 `GET`, `POST /{user}/{project}/labels`,
 * `POST /{user}/{project}/labels/{id}`.
 *
 * 원본: `app/controllers/LabelApp.java:44-124`, `app/controllers/ProjectApp.java:1138-1264`, `models.Project.attachLabel`, `detachLabel`.
 */
@AutoConfigureMockMvc
@Transactional
class LabelApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var labels: LabelRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lb$n", name = "lb$n", email = "lb$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User) = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project =
        projects.save(Project(owner = "lbo${++sequence}", name = "lbp$sequence", vcs = "GIT", projectScope = scope)).also {
            menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        }

    private fun manager(project: Project) = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun member(project: Project) = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun label(category: String, name: String) = labels.save(Label(category = category, name = name))

    private fun unique(prefix: String) = "$prefix${System.nanoTime()}"

    // --- 전역 GET /labels ---

    @Test
    fun `labels는 이름 목록을 JSON으로 돌려준다`() {
        val category = unique("cat"); label(category, "alpha"); label(category, "beta")
        mockMvc.perform(get("/labels").param("category", category).param("limit", "10").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[?(@=='alpha')]").exists())
    }

    @Test
    fun `labels는 이름과 분류를 대소문자 없이 부분 일치로 거른다`() {
        val category = unique("Lang"); label(category, "Kotlin"); label(category, "Java")
        mockMvc.perform(get("/labels").param("category", category.lowercase()).param("query", "KOT").param("limit", "10").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0]").value("Kotlin"))
    }

    @Test
    fun `labels는 한도보다 많으면 한도만큼 돌려주고 Content-Range를 붙인다`() {
        val category = unique("many"); (1..3).forEach { label(category, "n$it") }
        mockMvc.perform(get("/labels").param("category", category).param("limit", "2").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2)).andExpect(header().string("Content-Range", "items 2/3"))
    }

    @Test
    fun `labels는 limit이 없으면 400 No limit이다`() {
        mockMvc.perform(get("/labels").header("Accept", "application/json")).andExpect(status().isBadRequest)
            .andExpect { assertEquals("No limit", it.response.contentAsString) }
    }

    @Test
    fun `labels는 JSON을 받지 않으면 406이다`() {
        mockMvc.perform(get("/labels").param("limit", "1").header("Accept", "text/html")).andExpect(status().isNotAcceptable)
    }

    // --- 전역 GET /categories ---

    @Test
    fun `categories는 겹치지 않는 분류 목록을 돌려준다`() {
        val category = unique("catx"); label(category, "a"); label(category, "b")
        mockMvc.perform(get("/categories").param("query", category.uppercase()).param("limit", "10").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0]").value(category))
    }

    @Test
    fun `categories는 한도보다 많으면 Content-Range를 붙인다`() {
        val prefix = unique("cr"); (1..3).forEach { label("$prefix-$it", "x") }
        mockMvc.perform(get("/categories").param("query", prefix).param("limit", "2").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2)).andExpect(header().string("Content-Range", "items 2/3"))
    }

    @Test
    fun `categories는 limit이 없으면 400이고 JSON을 받지 않으면 406이다`() {
        mockMvc.perform(get("/categories").header("Accept", "application/json")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/categories").param("limit", "1").header("Accept", "text/html")).andExpect(status().isNotAcceptable)
    }

    // --- 프로젝트 GET /{user}/{project}/labels ---

    @Test
    fun `프로젝트 라벨 목록은 id를 키로 한 분류와 이름을 돌려준다`() {
        val p = project(); val l = label(unique("c"), "n"); p.labels.add(l); projects.save(p)
        mockMvc.perform(get("/${p.owner}/${p.name}/labels").header("Accept", "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.${l.id}.name").value("n")).andExpect(jsonPath("$.${l.id}.category").value(l.category))
    }

    @Test
    fun `프로젝트 라벨 목록은 JSON을 받지 않으면 406이다`() {
        val p = project()
        mockMvc.perform(get("/${p.owner}/${p.name}/labels").header("Accept", "text/html")).andExpect(status().isNotAcceptable)
    }

    @Test
    fun `비공개 프로젝트의 라벨 목록은 구성원이 아니면 볼 수 없다`() {
        val p = project(ProjectScope.PRIVATE)
        mockMvc.perform(get("/${p.owner}/${p.name}/labels").session(login(user())).header("Accept", "application/json")).andExpect(status().isForbidden)
    }

    // --- 프로젝트 POST /{user}/{project}/labels ---

    @Test
    fun `관리자가 새 라벨을 붙이면 라벨을 만들고 201로 돌려준다`() {
        val p = project(); val s = login(manager(p)); val category = unique("c")
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(s).param("category", category).param("name", "fresh"))
            .andExpect(status().isCreated).andExpect(jsonPath("$.*.name").value("fresh")).andExpect(jsonPath("$.*.category").value(category))
        val created = labels.findAll().single { it.category == category }
        assertTrue(projects.findById(p.id!!).get().labels.any { it.id == created.id })
    }

    @Test
    fun `이미 있는 라벨을 붙이면 새로 만들지 않고 200으로 돌려준다`() {
        val p = project(); val s = login(manager(p)); val l = label(unique("c"), "shared")
        val before = labels.count()
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(s).param("category", l.category!!).param("name", "shared"))
            .andExpect(status().isOk).andExpect(jsonPath("$.${l.id}.name").value("shared"))
        assertEquals(before, labels.count())
    }

    @Test
    fun `이미 붙은 라벨을 다시 붙이면 204이다`() {
        val p = project(); val s = login(manager(p)); val l = label(unique("c"), "dup"); p.labels.add(l); projects.save(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(s).param("category", l.category!!).param("name", "dup")).andExpect(status().isNoContent)
    }

    @Test
    fun `이름이 없으면 400이다`() {
        val p = project(); val s = login(manager(p))
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(s).param("category", "x")).andExpect(status().isBadRequest)
    }

    @Test
    fun `구성원이 아니면 라벨을 붙일 수 없고 구성원은 붙일 수 있다`() {
        val p = project()
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(login(user())).param("category", unique("c")).param("name", "n")).andExpect(status().isForbidden)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels").session(login(member(p))).param("category", unique("c")).param("name", "n")).andExpect(status().isCreated)
    }

    // --- 프로젝트 POST /{user}/{project}/labels/{id} ---

    @Test
    fun `_method가 delete이면 라벨을 떼고 다른 프로젝트가 쓰지 않으면 라벨도 지운다`() {
        val p = project(); val s = login(manager(p)); val l = label(unique("c"), "gone"); p.labels.add(l); projects.save(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels/${l.id}").session(s).param("_method", "DELETE")).andExpect(status().isNoContent)
        assertFalse(projects.findById(p.id!!).get().labels.any { it.id == l.id })
        assertNull(labels.findById(l.id!!).orElse(null))
    }

    @Test
    fun `다른 프로젝트가 같은 라벨을 쓰면 라벨은 남긴다`() {
        val p = project(); val other = project(); val s = login(manager(p)); val l = label(unique("c"), "kept")
        p.labels.add(l); projects.save(p); other.labels.add(l); projects.save(other)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels/${l.id}").session(s).param("_method", "delete")).andExpect(status().isNoContent)
        assertNotNull(labels.findById(l.id!!).orElse(null))
        assertTrue(projects.findById(other.id!!).get().labels.any { it.id == l.id })
    }

    @Test
    fun `_method가 delete가 아니면 400이고 없는 라벨은 404이다`() {
        val p = project(); val s = login(manager(p)); val l = label(unique("c"), "x")
        mockMvc.perform(post("/${p.owner}/${p.name}/labels/${l.id}").session(s).param("_method", "put")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels/999999999").session(s).param("_method", "delete")).andExpect(status().isNotFound)
    }

    @Test
    fun `구성원이 아니면 라벨을 뗄 수 없다`() {
        val p = project(); val l = label(unique("c"), "x"); p.labels.add(l); projects.save(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/labels/${l.id}").session(login(user())).param("_method", "delete")).andExpect(status().isForbidden)
    }
}
