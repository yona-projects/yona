package dev.yona.web.lfs

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 설정의 Mercurial "LFS" 화면(X-7 9a, `decisions.md` D-90)을 검증한다. 같은 주소(`/setting/lfs`)가 Git이면 Git LFS 화면, Mercurial이면 이 화면이다. 큰 파일 본문을
 * 저장소 안에 hg4j가 두므로 용량 제한과 잠금은 없고, 프로젝트 관리자에게 사용량, 올라온 LFS 파일 목록, 클라이언트 설정 안내를 보인다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-lfs-setting"])
class HgLfsProjectSettingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hls$k-$stamp", name = "hls$k", email = "hls$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun project(vcs: String = "Mercurial"): Project {
        val p = projects.save(Project(owner = "hlo$stamp", name = "hlp${++n}", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "Mercurial") repositories.createRepository(p)
        return p
    }

    private fun join(p: Project, u: User, role: RoleType) { projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(role.id).get())) }

    private fun page(s: MockHttpSession, path: String): Document = Jsoup.parse(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    /** 서버 저장소 폴더에서 lfs 확장으로 LFS 파일 하나를 커밋한다. */
    private fun lfsCommit(p: Project, path: String, content: String) {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val dir = hgRepositories.directoryOf(p.owner!!, p.name!!)
        HgTestSupport.commit(dir, "일반 파일", mapOf("a.txt" to "one\n"))
        File(dir, path).parentFile.mkdirs(); File(dir, path).writeText(content)
        val env = arrayOf("--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "--config", "experimental.lfs.disableusercache=true")
        HgTestSupport.hg(dir, *env, "add", path)
        HgTestSupport.hg(dir, *env, "commit", "-m", "LFS 파일", "-d", "2026-11-03 10:00 +0900")
        HgTestSupport.bookmark(dir, "main")
    }

    @Test
    fun `프로젝트 설정 탭에 LFS 탭이 있다`() {
        val p = project(); val manager = user(); join(p, manager, RoleType.MANAGER)
        assertNotNull(page(login(manager), "/${p.owner}/${p.name}/settingform").selectFirst("ul.nav-tabs a[href\$=/setting/lfs]"))
    }

    @Test
    fun `관리자만 열고 사용량과 LFS 파일 목록과 클라이언트 설정 안내를 보인다`() {
        val p = project(); val manager = user(); val member = user(); join(p, manager, RoleType.MANAGER); join(p, member, RoleType.MEMBER)
        val body = "큰 파일 본문 ".repeat(40)
        lfsCommit(p, "assets/logo.bin", body)
        val path = "/${p.owner}/${p.name}/setting/lfs"
        mockMvc.perform(get(path).session(login(member))).andExpect(status().isForbidden)
        val doc = page(login(manager), path)
        val usage = doc.selectFirst("#lfs-usage")!!.text()
        assertTrue(usage.contains("1") && usage.contains("${body.toByteArray().size}".take(1)), usage)
        val row = doc.select("table#lfs-files tr.lfs-file-row").single()
        assertTrue(row.text().contains("assets/logo.bin"), row.text())
        assertTrue(doc.selectFirst("#lfs-guide")!!.text().contains("extensions") || doc.selectFirst("#lfs-guide")!!.text().contains("lfs"), "클라이언트 설정 안내")
        // 용량 제한과 잠금은 Mercurial에 없다고 알린다. 잠금 표와 강제 풀기는 없다
        assertNotNull(doc.selectFirst("#lfs-limits"))
        assertNull(doc.selectFirst("table#lfs-locks"))
    }

    @Test
    fun `LFS 파일이 없으면 빈 안내이다`() {
        val p = project(); val manager = user(); join(p, manager, RoleType.MANAGER)
        val doc = page(login(manager), "/${p.owner}/${p.name}/setting/lfs")
        assertNotNull(doc.selectFirst("#lfs-files-empty"))
        assertNull(doc.selectFirst("table#lfs-files"))
    }

    @Test
    fun `Subversion 프로젝트에는 이 화면이 없다`() {
        val p = project("Subversion"); val manager = user(); join(p, manager, RoleType.MANAGER)
        mockMvc.perform(get("/${p.owner}/${p.name}/setting/lfs").session(login(manager))).andExpect(status().isBadRequest)
        assertNull(page(login(manager), "/${p.owner}/${p.name}/settingform").selectFirst("ul.nav-tabs a[href\$=/setting/lfs]"))
    }

    @Test
    fun `잠금 강제 풀기는 Mercurial에서 막힌다`() {
        val p = project(); val manager = user(); join(p, manager, RoleType.MANAGER)
        mockMvc.perform(post("/${p.owner}/${p.name}/setting/lfs/locks/1/delete").session(login(manager))).andExpect(status().isBadRequest)
    }
}
