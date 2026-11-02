package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.svn.SvnRepositories
import org.eclipse.jgit.lib.RepositoryBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 프로젝트 위키 저장소의 위치와 생성, 이동, 삭제를 검증한다 (X-17, W1, D-60).
 *
 * 위키 저장소는 코드 저장소와 별도의 bare git 저장소이며 프로젝트의 VCS와 무관하게 항상 git이다.
 */
class WikiRepositoriesTest {

    @TempDir
    lateinit var dataDir: File

    private fun directory() = DataDirectory(YonaProperties(dataDir = dataDir.path))

    private fun wiki() = WikiRepositories(directory())

    private fun service(): RepositoryService {
        val directory = directory()
        return RepositoryService(GitRepositories(directory), SvnRepositories(directory), wiki(), dev.yona.domain.lfs.LfsStorage(directory))
    }

    private fun project(owner: String = "alice", name: String = "demo", vcs: String = "GIT") =
        Project(owner = owner, name = name, vcs = vcs)

    @Test
    fun `위키 저장소 위치는 코드 저장소 옆의 repo git 소유자 이름 wiki git 이다`() {
        assertEquals(File(dataDir, "repo/git/alice/demo.wiki.git"), wiki().directoryOf("alice", "demo"))
    }

    @Test
    fun `위키 저장소와 같은 프로젝트의 코드 저장소는 서로 다른 위치이다`() {
        val directory = directory()
        val code = GitRepositories(directory).directoryOf("alice", "demo")
        val wiki = WikiRepositories(directory).directoryOf("alice", "demo")
        assertNotEquals(code, wiki)
        assertEquals(code.parentFile, wiki.parentFile)
    }

    @Test
    fun `만들기 전에는 없고 만든 뒤에는 bare 저장소가 있다`() {
        val wiki = wiki()
        assertFalse(wiki.exists("alice", "demo"))
        wiki.create("alice", "demo")
        assertTrue(wiki.exists("alice", "demo"))
        wiki.open("alice", "demo").use {
            assertTrue(it.isBare)
            assertEquals("refs/heads/main", it.exactRef("HEAD").target.name)
        }
    }

    @Test
    fun `설정한 이름이 위키 저장소의 기본 브랜치가 된다`() {
        val properties = YonaProperties(dataDir = dataDir.path, gitDefaultBranch = "trunk")
        val wiki = WikiRepositories(DataDirectory(properties), properties)
        wiki.create("alice", "demo")
        wiki.open("alice", "demo").use { assertEquals("refs/heads/trunk", it.exactRef("HEAD").target.name) }
    }

    @Test
    fun `이미 있으면 다시 만들어도 내용을 지키고 오류가 없다`() {
        val wiki = wiki()
        wiki.create("alice", "demo")
        val marker = File(wiki.directoryOf("alice", "demo"), "marker")
        marker.writeText("keep")
        wiki.create("alice", "demo")
        assertTrue(marker.isFile)
    }

    @Test
    fun `없는 위키 저장소를 열면 예외이다`() {
        assertFailsWith<Exception> { wiki().open("alice", "nothing") }
    }

    @Test
    fun `지우면 사라지고 없어도 오류가 없다`() {
        val wiki = wiki()
        wiki.delete("alice", "none")
        wiki.create("alice", "demo")
        wiki.delete("alice", "demo")
        assertFalse(wiki.exists("alice", "demo"))
    }

    @Test
    fun `다른 소유자나 이름으로 옮긴다`() {
        val wiki = wiki()
        wiki.create("alice", "demo")
        assertTrue(wiki.move("alice", "demo", "bob", "renamed"))
        assertFalse(wiki.exists("alice", "demo"))
        assertTrue(wiki.exists("bob", "renamed"))
    }

    @Test
    fun `옮길 위키가 없으면 성공으로 본다`() {
        assertTrue(wiki().move("alice", "demo", "bob", "renamed"))
        assertFalse(wiki().exists("bob", "renamed"))
    }

    @Test
    fun `프로젝트 저장소를 옮기면 위키 저장소도 함께 옮겨진다`() {
        val service = service()
        service.createRepository(project())
        wiki().create("alice", "demo")
        assertTrue(service.moveRepository(project(), "bob", "demo"))
        assertTrue(wiki().exists("bob", "demo"))
        assertFalse(wiki().exists("alice", "demo"))
    }

    @Test
    fun `프로젝트 이름을 바꾸면 위키 저장소도 따라간다`() {
        val service = service()
        service.createRepository(project())
        wiki().create("alice", "demo")
        assertTrue(service.renameRepository(project(), "newname"))
        assertTrue(wiki().exists("alice", "newname"))
    }

    @Test
    fun `프로젝트를 지울 때 위키 저장소를 지운다`() {
        val service = service()
        wiki().create("alice", "demo")
        service.deleteWiki(project())
        assertFalse(wiki().exists("alice", "demo"))
    }

    @Test
    fun `코드 저장소만 지우면 위키는 남는다 (VCS 바꾸기와 가져오기 정리)`() {
        val service = service()
        service.createRepository(project())
        wiki().create("alice", "demo")
        service.deleteRepository(project())
        assertTrue(wiki().exists("alice", "demo"))
    }

    @Test
    fun `Subversion 프로젝트도 위키는 git 저장소로 만든다`() {
        val svnProject = project(vcs = "Subversion")
        service().createWiki(svnProject)
        wiki().open("alice", "demo").use { assertTrue(it.isBare) }
        RepositoryBuilder().setGitDir(wiki().directoryOf("alice", "demo")).setMustExist(true).build().use { assertTrue(it.isBare) }
    }
}
