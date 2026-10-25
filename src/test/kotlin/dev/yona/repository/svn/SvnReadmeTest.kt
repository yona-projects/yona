package dev.yona.repository.svn

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.repository.GitRepositories
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryFileReader
import dev.yona.repository.RepositoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Subversion 프로젝트의 README와 이슈 템플릿 읽기를 검증한다. 원본: `Project.getReadmeFileName`, `readme`, `getIssueTemplate`
 * (`app/models/Project.java:350-395`).
 */
class SvnReadmeTest {

    @TempDir
    lateinit var dataDir: File

    private lateinit var svn: SvnRepositories
    private lateinit var readme: ProjectReadmeService
    private lateinit var reader: RepositoryFileReader
    private val project = Project(owner = "alice", name = "demo", vcs = "Subversion")

    @BeforeEach
    fun setUp() {
        val directory = DataDirectory(YonaProperties(dataDir = dataDir.path))
        svn = SvnRepositories(directory)
        val repositories = RepositoryService(GitRepositories(directory), svn)
        readme = ProjectReadmeService(repositories, svn)
        reader = RepositoryFileReader(repositories, svn)
        repositories.createRepository(project)
    }

    private fun commit(files: Map<String, String>) = SvnFixtures.commit(svn.directoryOf("alice", "demo"), "alice", "msg", files.mapValues { it.value.toByteArray() })

    @Test
    fun `커밋이 없으면 README가 없다`() {
        assertNull(readme.fileName(project))
        assertNull(readme.read(project))
    }

    @Test
    fun `최상위의 README md를 먼저 readme md를 그다음 찾는다`() {
        commit(mapOf("readme.md" to "소문자"))
        assertEquals("readme.md", readme.fileName(project))
        assertEquals("소문자", readme.read(project))
        commit(mapOf("README.md" to "대문자"))
        assertEquals("README.md", readme.fileName(project))
        assertEquals("대문자", readme.read(project))
    }

    @Test
    fun `최상위에 없으면 trunk 아래에서 찾는다`() {
        commit(mapOf("trunk/README.md" to "트렁크"))
        assertEquals("/trunk/README.md", readme.fileName(project))
        assertEquals("트렁크", readme.read(project))
    }

    @Test
    fun `trunk 아래도 소문자 이름을 찾는다`() {
        commit(mapOf("trunk/readme.md" to "소문자 트렁크"))
        assertEquals("/trunk/readme.md", readme.fileName(project))
    }

    @Test
    fun `저장소 파일을 문자열로 읽고 없으면 null이다`() {
        commit(mapOf("ISSUE_TEMPLATE.md" to "템플릿", "dir/x.txt" to "x"))
        assertEquals("템플릿", reader.readText(project, "ISSUE_TEMPLATE.md"))
        assertNull(reader.readText(project, "none.md"))
        assertNull(reader.readText(project, "dir"))
    }
}
