package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Git 저장소의 위치와 생성, 삭제를 검증한다.
 *
 * 원본: `app/playRepository/GitRepository.java:92-96, 186-189, 647-653, 959-965`,
 * `app/playRepository/RepositoryService.java:62-74`.
 */
class GitRepositoryServiceTest {

    @TempDir
    lateinit var dataDir: File

    private fun service() = DataDirectory(YonaProperties(dataDir = dataDir.path)).let { RepositoryService(GitRepositories(it), dev.yona.repository.svn.SvnRepositories(it)) }

    private fun project(owner: String = "alice", name: String = "demo", vcs: String = "GIT") =
        Project(owner = owner, name = name, vcs = vcs)

    @Test
    fun `저장소 위치는 repo git 소유자 이름 git 이다`() {
        val git = GitRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        assertEquals(File(dataDir, "repo/git/alice/demo.git"), git.directoryOf("alice", "demo"))
    }

    @Test
    fun `저장소를 만들면 bare 저장소가 생긴다`() {
        service().createRepository(project())
        val directory = File(dataDir, "repo/git/alice/demo.git")
        assertTrue(directory.isDirectory)
        RepositoryBuilder().setGitDir(directory).setMustExist(true).build().use {
            assertTrue(it.isBare, "작업 트리가 없는 bare 저장소여야 한다")
            assertTrue(File(directory, "HEAD").isFile)
        }
    }

    @Test
    fun `이미 있는 저장소를 다시 만들면 지우고 새로 만든다`() {
        val service = service()
        service.createRepository(project())
        val marker = File(dataDir, "repo/git/alice/demo.git/marker.txt").apply { writeText("old") }
        service.createRepository(project())
        assertFalse(marker.exists(), "이전 내용이 남아 있으면 안 된다")
        assertTrue(File(dataDir, "repo/git/alice/demo.git/HEAD").isFile)
    }

    @Test
    fun `저장소를 지우면 디렉터리 전체가 사라진다`() {
        val service = service()
        service.createRepository(project())
        service.deleteRepository(project())
        assertFalse(File(dataDir, "repo/git/alice/demo.git").exists())
    }

    @Test
    fun `없는 저장소를 지워도 오류가 아니다`() {
        service().deleteRepository(project("nobody", "nothing"))
    }

    @Test
    fun `저장소를 연 상태에서도 지울 수 있다`() {
        val service = service()
        service.createRepository(project())
        service.openRepository(project()).use { Git(it).use { } }
        service.deleteRepository(project())
        assertFalse(File(dataDir, "repo/git/alice/demo.git").exists())
    }

    @Test
    fun `한글 이름도 저장소를 만든다`() {
        service().createRepository(project("앨리스", "프로젝트"))
        assertTrue(File(dataDir, "repo/git/앨리스/프로젝트.git/HEAD").isFile)
    }

    @Test
    fun `알 수 없는 VCS는 지원하지 않는다`() {
        assertFailsWith<UnsupportedOperationException> { service().createRepository(project(vcs = "Mercurial")) }
    }

    @Test
    fun `Subversion 저장소는 repo svn 아래에 만든다`() {
        service().createRepository(project(vcs = "Subversion"))
        assertTrue(File(dataDir, "repo/svn/alice/demo/format").isFile)
    }
}
