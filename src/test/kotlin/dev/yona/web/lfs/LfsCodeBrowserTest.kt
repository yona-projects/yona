package dev.yona.web.lfs

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코드 보기의 LFS 포인터 안내와 내려받기(X-8 단계 5, `decisions.md` D-79 10번)를 검증한다. 포인터 파일은 원래 내용을 그대로 보이면서 위에 "Git LFS로 저장된 파일"과 크기,
 * 내려받기 링크를 더하고, 링크(`/소유자/프로젝트/lfs/oid`)는 코드를 읽을 수 있는 사용자(웹 세션)에게 객체를 내려 준다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-code"])
class LfsCodeBrowserTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var storage: LfsStorage
    @Autowired private lateinit var settings: SiteSettingService

    private var n = 0

    @BeforeEach
    fun enable() { settings.setLfsEnabled(true) }

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-code").deleteRecursively() }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val k = ++n
        val p = projects.save(Project(owner = "lco$k", name = "lcp$k", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun pointer(oid: String, size: Long) = "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize $size\n"

    private fun oidOf(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun page(path: String) = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `포인터 파일은 원래 내용을 그대로 두고 LFS 안내와 내려받기 링크를 더한다`() {
        val p = project(); val data = "큰 파일 내용".toByteArray(); val oid = oidOf(data); storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        GitTestSupport.pushCommits(repositories, p, TestCommit("lfs", mapOf("assets/big.bin" to pointer(oid, data.size.toLong()).toByteArray(), "readme.txt" to "plain".toByteArray())))
        val doc = page("/${p.owner}/${p.name}/code/master/assets/big.bin")
        val note = assertNotNull(doc.selectFirst("#lfs-pointer"))
        assertTrue(note.text().contains("${data.size}"), note.text())
        assertEquals("/${p.owner}/${p.name}/lfs/$oid?name=big.bin", note.selectFirst("a.lfs-download")!!.attr("href"))
        assertTrue(doc.selectFirst("#codeVal")!!.text().contains("oid sha256:$oid"))
        assertNull(page("/${p.owner}/${p.name}/code/master/readme.txt").selectFirst("#lfs-pointer"))
    }

    @Test
    fun `서버에 객체가 없으면 내려받기 링크 대신 없다고 알린다`() {
        val p = project(); val oid = "e".repeat(64)
        GitTestSupport.pushCommits(repositories, p, TestCommit("lfs", mapOf("big.bin" to pointer(oid, 10).toByteArray())))
        val note = assertNotNull(page("/${p.owner}/${p.name}/code/master/big.bin").selectFirst("#lfs-pointer"))
        assertNull(note.selectFirst("a.lfs-download")); assertNotNull(note.selectFirst(".lfs-missing"))
    }

    @Test
    fun `LFS를 끄면 안내를 보이지 않는다`() {
        val p = project(); val oid = "f".repeat(64); settings.setLfsEnabled(false)
        GitTestSupport.pushCommits(repositories, p, TestCommit("lfs", mapOf("big.bin" to pointer(oid, 10).toByteArray())))
        assertNull(page("/${p.owner}/${p.name}/code/master/big.bin").selectFirst("#lfs-pointer"))
    }

    @Test
    fun `내려받기는 코드를 읽을 수 있으면 객체를 첨부 파일로 주고 없으면 404이다`() {
        val p = project(); val data = "내려받을 내용".toByteArray(); val oid = oidOf(data); storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(get("/${p.owner}/${p.name}/lfs/$oid").param("name", "big.bin")).andExpect(status().isOk).andExpect(content().bytes(data))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("big.bin")))
        mockMvc.perform(get("/${p.owner}/${p.name}/lfs/${"a".repeat(64)}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/lfs/zz")).andExpect(status().isNotFound)
    }

    @Test
    fun `비공개 프로젝트의 객체는 구성원이 아니면 받을 수 없다`() {
        val p = project(ProjectScope.PRIVATE); val data = "비공개".toByteArray(); val oid = oidOf(data); storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        val result = mockMvc.perform(get("/${p.owner}/${p.name}/lfs/$oid")).andReturn().response.status
        assertTrue(result in setOf(302, 303, 401, 403), "status=$result")
    }
}
