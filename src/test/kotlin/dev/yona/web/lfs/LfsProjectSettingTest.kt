package dev.yona.web.lfs

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.lfs.LfsLockService
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 설정의 "Git LFS" 화면(X-8 단계 5, `decisions.md` D-79 2번, 5번)을 검증한다. 프로젝트 관리자만 열고, 저장 사용량(객체 수와 용량)과 현재 잠금 목록을 보이며,
 * 잠근 사람이 떠난 잠금을 관리자가 강제로 풀 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-setting"])
class LfsProjectSettingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var storage: LfsStorage
    @Autowired private lateinit var locks: LfsLockService
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    @BeforeEach
    fun enable() { settings.setLfsEnabled(true) }

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-setting").deleteRecursively() }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lps$k-$stamp", name = "lps$k", email = "lps$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun project(vcs: String = "GIT"): Project {
        val p = projects.save(Project(owner = "lpo$stamp", name = "lpp${++n}", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    private fun join(p: Project, u: User, role: RoleType) { projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(role.id).get())) }

    private fun page(s: MockHttpSession, path: String): Document = Jsoup.parse(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `프로젝트 설정 탭에 Git LFS 탭이 있다`() {
        val p = project(); val manager = user(); join(p, manager, RoleType.MANAGER)
        val tab = page(login(manager), "/${p.owner}/${p.name}/settingform").selectFirst("ul.nav-tabs a[href\$=/setting/lfs]")
        assertNotNull(tab)
    }

    @Test
    fun `관리자만 열고 사용량과 잠금 목록을 보인다`() {
        val p = project(); val manager = user(); val member = user(); val owner = user(); join(p, manager, RoleType.MANAGER); join(p, member, RoleType.MEMBER)
        val a = "aaa".toByteArray(); val b = "bbbbb".toByteArray()
        val digest = { bytes: ByteArray -> java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        storage.store(p, digest(a), a.inputStream(), Long.MAX_VALUE); storage.store(p, digest(b), b.inputStream(), Long.MAX_VALUE)
        locks.create(p, owner, "assets/logo.psd", null)
        val path = "/${p.owner}/${p.name}/setting/lfs"
        mockMvc.perform(get(path).session(login(member))).andExpect(status().isForbidden)
        val doc = page(login(manager), path)
        assertTrue(doc.selectFirst("#lfs-usage")!!.text().contains("2") && doc.selectFirst("#lfs-usage")!!.text().contains("8"), doc.selectFirst("#lfs-usage")!!.text())
        val row = doc.select("table#lfs-locks tr.lfs-lock-row").single()
        assertTrue(row.text().contains("assets/logo.psd") && row.text().contains(owner.loginId!!))
        assertNotNull(row.selectFirst("form[action\$=/setting/lfs/locks/${locks.list(p, null, null, null, 10).locks.single().id}/delete] button"))
    }

    @Test
    fun `잠금이 없으면 안내하고 강제로 풀면 잠금이 사라진다`() {
        val p = project(); val manager = user(); val owner = user(); join(p, manager, RoleType.MANAGER); val s = login(manager)
        assertNotNull(page(s, "/${p.owner}/${p.name}/setting/lfs").selectFirst("#lfs-locks-empty"))
        val id = (locks.create(p, owner, "x.bin", null) as LfsLockService.Created).lock.id!!
        mockMvc.perform(post("/${p.owner}/${p.name}/setting/lfs/locks/$id/delete").session(login(owner))).andExpect(status().isForbidden)
        mockMvc.perform(post("/${p.owner}/${p.name}/setting/lfs/locks/$id/delete").session(s)).andExpect(status().is3xxRedirection)
        assertEquals(0, locks.list(p, null, null, null, 10).locks.size)
        mockMvc.perform(post("/${p.owner}/${p.name}/setting/lfs/locks/$id/delete").session(s)).andExpect(status().isNotFound)
    }

    @Test
    fun `Subversion 프로젝트에서는 열 수 없다`() {
        val p = project("Subversion"); val manager = user(); join(p, manager, RoleType.MANAGER)
        val status = mockMvc.perform(get("/${p.owner}/${p.name}/setting/lfs").session(login(manager))).andReturn().response.status
        assertTrue(status in setOf(400, 403, 404), "status=$status")
    }
}
