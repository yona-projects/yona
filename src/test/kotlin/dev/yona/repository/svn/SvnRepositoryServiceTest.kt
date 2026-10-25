package dev.yona.repository.svn

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.repository.GitRepositories
import dev.yona.repository.RepositoryService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 프로젝트의 저장소 종류가 Subversion일 때 저장소 진입점(`RepositoryService`)의 동작을 검증한다.
 * 원본: `app/playRepository/RepositoryService.java`, `SVNRepository.java`.
 */
class SvnRepositoryServiceTest {

    @TempDir
    lateinit var dataDir: File

    private fun service(): RepositoryService {
        val directory = DataDirectory(YonaProperties(dataDir = dataDir.path))
        return RepositoryService(GitRepositories(directory), SvnRepositories(directory))
    }

    private fun project(name: String = "demo") = Project(owner = "alice", name = name, vcs = "Subversion")

    @Test
    fun `저장소를 만들면 repo svn 아래에 생긴다`() {
        service().createRepository(project())
        assertTrue(File(dataDir, "repo/svn/alice/demo/format").isFile)
    }

    @Test
    fun `이미 있는 저장소를 다시 만들면 지우고 새로 만든다`() {
        val service = service()
        service.createRepository(project())
        val marker = File(dataDir, "repo/svn/alice/demo/marker.txt").apply { writeText("old") }
        service.createRepository(project())
        assertFalse(marker.exists())
        assertTrue(File(dataDir, "repo/svn/alice/demo/format").isFile)
    }

    @Test
    fun `저장소를 지우고 없는 저장소를 지워도 오류가 아니다`() {
        val service = service()
        service.createRepository(project())
        service.deleteRepository(project())
        assertFalse(File(dataDir, "repo/svn/alice/demo").exists())
        service.deleteRepository(project("nothing"))
    }

    @Test
    fun `저장소를 옮기고 이름을 바꾼다`() {
        val service = service()
        service.createRepository(project())
        assertTrue(service.moveRepository(project(), "bob", "moved"))
        assertTrue(File(dataDir, "repo/svn/bob/moved/format").isFile)
        assertTrue(service.renameRepository(Project(owner = "bob", name = "moved", vcs = "Subversion"), "renamed"))
        assertTrue(File(dataDir, "repo/svn/bob/renamed/format").isFile)
    }

    @Test
    fun `브랜치는 HEAD뿐이고 기본 브랜치를 바꿔도 아무 일도 없다`() {
        val service = service()
        service.createRepository(project())
        assertEquals(listOf("HEAD"), service.refNames(project()))
        assertEquals("HEAD", service.defaultBranch(project()))
        service.setDefaultBranch(project(), "anything")
        assertEquals("HEAD", service.defaultBranch(project()))
    }

    @Test
    fun `Git 전용 동작은 지원하지 않는다`() {
        val service = service()
        service.createRepository(project())
        assertFailsWith<UnsupportedOperationException> { service.openRepository(project()) }
        assertFailsWith<UnsupportedOperationException> { service.cloneRepository(project(), project("fork")) }
    }
}
