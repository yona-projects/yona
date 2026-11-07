package dev.yona.web.lfs

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코드 화면의 Mercurial LFS 파일(X-7 9단계 보완, `decisions.md` A-385)을 검증한다. Git은 저장소에 포인터가 있어 LFS 안내와 내려받기 링크를 더하지만([LfsCodeBrowserTest]),
 * Mercurial의 파일 읽기(`hg cat`, hgweb)는 LFS 본문을 풀어서 돌려주므로 화면이 실제 본문을 그대로 보여 주고 안내가 필요하지 않다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-lfs-code"])
class HgLfsCodeBrowserTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var settings: SiteSettingService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000
    private val body = "큰 파일 본문 ".repeat(40)

    @BeforeEach
    fun setUp() { assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다"); settings.setLfsEnabled(true) }

    @AfterEach
    fun clean() { settings.setLfsEnabled(true) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val p = projects.save(Project(owner = "hlco$stamp", name = "hlcp${++n}", vcs = "Mercurial", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun lfsCommit(p: Project) {
        val dir = hgRepositories.directoryOf(p.owner!!, p.name!!)
        HgTestSupport.commit(dir, "일반 파일", mapOf("readme.txt" to "plain\n"))
        File(dir, "assets").mkdirs(); File(dir, "assets/big.bin").writeText(body)
        val env = arrayOf("--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "--config", "experimental.lfs.disableusercache=true")
        HgTestSupport.hg(dir, *env, "add", "assets/big.bin")
        HgTestSupport.hg(dir, *env, "commit", "-m", "LFS 파일", "-d", "2026-11-03 10:00 +0900")
        HgTestSupport.bookmark(dir, "main")
    }

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun oidOf(p: Project) = File(hgRepositories.directoryOf(p.owner!!, p.name!!), ".hg/store/lfs/objects").walkTopDown().single { it.isFile }.let { it.parentFile.name + it.name }

    @Test
    fun `코드 화면은 LFS 파일을 포인터가 아니라 실제 본문으로 보여 주고 LFS 안내는 더하지 않는다`() {
        val p = project(); lfsCommit(p)
        val doc = page("/${p.owner}/${p.name}/code/main/assets/big.bin")
        assertTrue(doc.selectFirst("#codeVal")!!.text().contains(body.trim()), "실제 본문이 보여야 한다")
        assertTrue(!doc.selectFirst("#codeVal")!!.text().contains("git-lfs.github.com"), "포인터 텍스트가 보이면 안 된다")
        assertNull(doc.selectFirst("#lfs-pointer"))
    }

    @Test
    fun `원본 파일 보기도 실제 본문을 준다`() {
        val p = project(); lfsCommit(p)
        val node = HgTestSupport.hg(hgRepositories.directoryOf(p.owner!!, p.name!!), "log", "-r", "main", "--template", "{node}")
        mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/$node/assets/big.bin")).andExpect(status().isOk).andExpect(content().bytes(body.toByteArray()))
    }
}
