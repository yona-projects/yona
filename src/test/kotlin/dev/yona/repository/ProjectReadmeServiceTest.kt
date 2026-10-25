package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.support.GitTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 프로젝트 README 조회를 검증한다.
 *
 * 원본: `app/models/Project.java:349-383` (`readme()`, `getReadmeFileName()`),
 * `app/playRepository/GitRepository.java:257-272, 628-639`, `app/utils/FileUtil.java:67-79`.
 */
class ProjectReadmeServiceTest {

    @TempDir
    lateinit var dataDir: File

    private lateinit var repositories: RepositoryService
    private lateinit var readme: ProjectReadmeService
    private val project = Project(owner = "alice", name = "demo", vcs = "GIT")

    @BeforeEach
    fun setUp() {
        repositories = DataDirectory(YonaProperties(dataDir = dataDir.path)).let { RepositoryService(GitRepositories(it), dev.yona.repository.svn.SvnRepositories(it)) }
        readme = ProjectReadmeService(repositories, dev.yona.repository.svn.SvnRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path))))
        repositories.createRepository(project)
    }

    private fun commit(files: Map<String, ByteArray>) = GitTestSupport.commitFiles(repositories, project, files)

    @Test
    fun `커밋이 없는 저장소는 README가 없다`() {
        assertNull(readme.fileName(project))
        assertNull(readme.read(project))
    }

    @Test
    fun `README md 를 찾아 내용을 읽는다`() {
        commit(mapOf("README.md" to "# 안녕".toByteArray()))
        assertEquals("README.md", readme.fileName(project))
        assertEquals("# 안녕", readme.read(project))
    }

    @Test
    fun `소문자 readme md 도 찾는다`() {
        commit(mapOf("readme.md" to "hello".toByteArray()))
        assertEquals("readme.md", readme.fileName(project))
    }

    @Test
    fun `README md 가 readme md 보다 우선한다`() {
        commit(mapOf("README.md" to "upper".toByteArray(), "readme.md" to "lower".toByteArray()))
        assertEquals("upper", readme.read(project))
    }

    @Test
    fun `README가 아닌 파일만 있으면 없다`() {
        commit(mapOf("OTHER.md" to "x".toByteArray()))
        assertNull(readme.fileName(project))
        assertNull(readme.read(project))
    }

    @Test
    fun `UTF-8이 아닌 인코딩도 감지해 읽는다`() {
        val text = "한글로 작성한 설명서입니다. 프로젝트 소개와 사용 방법을 적었습니다.\n".repeat(5)
        commit(mapOf("README.md" to text.toByteArray(Charset.forName("EUC-KR"))))
        assertEquals(text, readme.read(project))
    }

    @Test
    fun `저장소 디렉터리가 없으면 README가 없다`() {
        repositories.deleteRepository(project)
        assertNull(readme.read(project))
    }
}
