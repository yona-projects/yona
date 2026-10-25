package dev.yona.web.home

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import org.eclipse.jgit.lib.Constants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 저장소 초기화(`GET /_init`)를 검증한다.
 *
 * 원본: `app/controllers/Application.java:106-126`(`init`, `makeTestRepository`), `app/playRepository/RepositoryService.java:71-74`.
 * 로그인 없이 모든 프로젝트의 저장소를 지우고 다시 만드는 v1.6의 동작을 그대로 따른다(D-27, R-66).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-repo-init"])
class RepositoryInitTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-repo-init").deleteRecursively()
    }

    private fun project(vcs: String = "GIT"): Project {
        val n = ++sequence
        return projects.save(Project(owner = "rio$n", name = "rip$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
    }

    private fun head(project: Project) = repositories.openRepository(project).use { it.resolve(Constants.HEAD) }

    @Test
    fun `로그인하지 않아도 호출할 수 있고 홈으로 303 이동한다`() {
        mockMvc.perform(get("/_init")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
    }

    @Test
    fun `모든 프로젝트의 저장소를 지우고 빈 저장소로 다시 만든다`() {
        val a = project(); val b = project()
        repositories.createRepository(a); repositories.createRepository(b)
        GitTestSupport.commitFiles(repositories, a, mapOf("a.txt" to "a".toByteArray()))
        GitTestSupport.commitFiles(repositories, b, mapOf("b.txt" to "b".toByteArray()))
        assertNotNull(head(a))
        mockMvc.perform(get("/_init")).andExpect(status().isSeeOther)
        assertNull(head(a), "이전 커밋이 남아 있으면 안 된다")
        assertNull(head(b))
    }

    @Test
    fun `저장소가 없던 프로젝트도 만든다`() {
        val p = project()
        mockMvc.perform(get("/_init")).andExpect(status().isSeeOther)
        assertTrue(File("build/tmp/it-repo-init/repo/git/${p.owner}/${p.name}.git/HEAD").isFile)
    }

    @Test
    fun `Subversion 프로젝트도 다시 만든다`() {
        val p = project("Subversion")
        mockMvc.perform(get("/_init")).andExpect(status().isSeeOther)
        assertTrue(File("build/tmp/it-repo-init/repo/svn/${p.owner}/${p.name}/format").isFile)
    }

    @Test
    fun `한 프로젝트가 실패해도 나머지는 계속한다`() {
        val bad = project("Mercurial"); val good = project()
        mockMvc.perform(get("/_init")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        assertTrue(File("build/tmp/it-repo-init/repo/git/${good.owner}/${good.name}.git/HEAD").isFile)
        assertEquals("Mercurial", bad.vcs)
    }
}
