package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 코드 화면(코드, 커밋 이력, 커밋 보기, 브랜치, 태그, 원본, 내려받기)이 실제 `hg`로 만든 저장소에서 Git과 같은 화면으로 나오는지 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-web"])
class HgCodeWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories

    private var sequence = 0
    private lateinit var project: Project
    private lateinit var dir: File
    private lateinit var c0: String
    private lateinit var c1: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        project = projects.save(Project(owner = "hwo$n", name = "hwp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        dir = hgRepositories.directoryOf(project.owner!!, project.name!!)
        c0 = HgTestSupport.commit(dir, "첫 커밋", mapOf("README.md" to "# 안녕\n", "src/main.txt" to "one\ntwo\n"), "2026-11-01 10:00 +0900", "Alice <alice@example.com>")
        c1 = HgTestSupport.commit(dir, "둘째 커밋\n\n설명", mapOf("src/main.txt" to "one\ntwo\nthree\n", "new.txt" to "새 파일\n"), "2026-11-02 10:00 +0900", "Alice <alice@example.com>")
        HgTestSupport.bookmark(dir, "main")
        HgTestSupport.bookmark(dir, "release", "0")
    }

    private fun base() = "/${project.owner}/${project.name}"

    private fun page(path: String): Document =
        Jsoup.parse(mockMvc.perform(get(java.net.URI.create(base() + path))).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `코드 주소는 기본 이름표의 화면으로 보내고 그 화면이 폴더 목록을 보여 준다`() {
        val result = mockMvc.perform(get(java.net.URI.create(base() + "/code"))).andReturn()
        assertEquals(303, result.response.status)
        assertEquals("${base()}/code/main", result.response.getHeader("Location"))
        val html = page("/code/main").html()
        assertTrue("README.md" in html && "src" in html && "new.txt" in html, html.take(400))
    }

    @Test
    fun `이름표가 하나도 없으면 HEAD로 보내고 default 브랜치의 끝을 보여 준다`() {
        HgTestSupport.hg(dir, "bookmark", "-d", "main"); HgTestSupport.hg(dir, "bookmark", "-d", "release")
        val result = mockMvc.perform(get(java.net.URI.create(base() + "/code"))).andReturn()
        assertEquals(303, result.response.status)
        assertEquals("${base()}/code/HEAD", result.response.getHeader("Location"))
        val html = page("/code/HEAD").html()
        assertTrue("new.txt" in html, html.take(400))
    }

    @Test
    fun `이름표를 정해 코드와 하위 폴더와 파일을 본다`() {
        assertTrue("new.txt" !in page("/code/refs%2Fheads%2Frelease").html())
        assertTrue("main.txt" in page("/code/main/src").html())
        val file = page("/code/main/src/main.txt").html()
        assertTrue("three" in file, file.take(300))
    }

    @Test
    fun `원본 파일을 내려받는다`() {
        val body = mockMvc.perform(get(java.net.URI.create(base() + "/rawcode/main/src/main.txt"))).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("one\ntwo\nthree\n", body)
    }

    @Test
    fun `커밋 이력은 최근 커밋이 앞이고 메시지가 나온다`() {
        val html = page("/commits").text()
        assertTrue("둘째 커밋" in html && "첫 커밋" in html, html.take(400))
        assertTrue(html.indexOf("둘째 커밋") < html.indexOf("첫 커밋"))
    }

    @Test
    fun `커밋 보기는 변경된 파일의 diff를 보여 준다`() {
        val html = page("/commit/$c1").html()
        assertTrue("둘째 커밋" in html, html.take(300))
        assertTrue("new.txt" in html && "three" in html, html.take(600))
    }

    @Test
    fun `짧은 노드로도 커밋을 본다`() {
        val html = page("/commit/${c1.take(12)}").html()
        assertTrue("둘째 커밋" in html)
    }

    @Test
    fun `브랜치 화면은 이름표를 나열한다`() {
        val html = page("/branches").html()
        assertTrue("main" in html && "release" in html, html.take(500))
    }

    @Test
    fun `태그 화면이 열린다`() {
        HgTestSupport.tag(dir, "v1", "0")
        val html = page("/tags").html()
        assertTrue("v1" in html, html.take(400))
    }

    @Test
    fun `이름표를 zip으로 내려받는다`() {
        val bytes = mockMvc.perform(get(java.net.URI.create(base() + "/code/main/download"))).andExpect(status().isOk).andReturn().response.contentAsByteArray
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue("src/main.txt" in names && "new.txt" in names, names.toString())
    }

    @Test
    fun `프로젝트 홈이 열린다`() {
        val html = page("").html()
        assertTrue(html.isNotEmpty())
    }
}
