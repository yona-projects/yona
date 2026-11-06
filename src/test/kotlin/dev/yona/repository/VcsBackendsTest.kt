package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.vcs.VcsType
import dev.yona.repository.svn.SvnRepositories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** 저장소 종류별 수명 구현을 고르는 등록부와, 그 위에서 도는 [RepositoryService]의 동작을 확인한다. */
class VcsBackendsTest {

    @TempDir
    lateinit var dataDir: File

    private fun backends(): VcsBackends {
        val directory = DataDirectory(YonaProperties(dataDir = dataDir.path))
        val git = GitRepositories(directory)
        val svn = SvnRepositories(directory)
        return VcsBackends.of(
            GitBackend(GitLifecycle(git), GitCommitReader(git)),
            dev.yona.repository.svn.SvnBackend(dev.yona.repository.svn.SvnLifecycle(svn), dev.yona.repository.svn.SvnCommitReader(svn))
        )
    }

    private fun project(vcs: String, name: String) = dev.yona.domain.project.Project(owner = "tester", name = name, vcs = vcs)

    @Test
    fun `열 값으로 종류별 수명 구현을 고른다`() {
        val backends = backends()
        assertThat(backends.lifecycle("GIT").type).isEqualTo(VcsType.GIT)
        assertThat(backends.lifecycle("Subversion").type).isEqualTo(VcsType.SUBVERSION)
    }

    @Test
    fun `모르는 값과 비어 있는 값은 지원하지 않는 VCS이다`() {
        val backends = backends()
        assertThatThrownBy { backends.lifecycle("Mercurial") }.isInstanceOf(UnsupportedOperationException::class.java).hasMessage("지원하지 않는 VCS입니다: Mercurial")
        assertThatThrownBy { backends.lifecycle(null) }.isInstanceOf(UnsupportedOperationException::class.java).hasMessage("지원하지 않는 VCS입니다: null")
    }

    @Test
    fun `Subversion은 브랜치가 HEAD 하나이고 기본 브랜치를 바꾸지 못한다`() {
        val svn = backends().lifecycle("Subversion")
        svn.create("tester", "svnproj")
        assertThat(svn.refNames("tester", "svnproj")).containsExactly("HEAD")
        assertThat(svn.defaultBranch("tester", "svnproj")).isEqualTo("HEAD")
        // v1.6 `setDefaultBranch`는 Subversion에서 아무 일도 하지 않는다
        svn.setDefaultBranch("tester", "svnproj", "refs/heads/other")
        assertThat(svn.defaultBranch("tester", "svnproj")).isEqualTo("HEAD")
        svn.delete("tester", "svnproj")
    }

    @Test
    fun `Subversion은 포크와 원격 가져오기를 지원하지 않는다`() {
        val svn = backends().lifecycle("Subversion")
        assertThatThrownBy { svn.cloneLocal("a", "b", "c", "d") }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { svn.cloneRemote("a", "b", "http://x", null, null) }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `Git 수명 구현은 만든 저장소의 참조 이름과 기본 브랜치를 알려 준다`() {
        val git = backends().lifecycle("GIT")
        git.create("tester", "gitproj")
        assertThat(git.refNames("tester", "gitproj")).isEmpty()
        assertThat(git.defaultBranch("tester", "gitproj")).startsWith("refs/heads/")
        git.setDefaultBranch("tester", "gitproj", "refs/heads/develop")
        assertThat(git.defaultBranch("tester", "gitproj")).isEqualTo("refs/heads/develop")
        assertThat(git.move("tester", "gitproj", "tester", "renamed")).isTrue()
        assertThat(git.defaultBranch("tester", "renamed")).isEqualTo("refs/heads/develop")
        git.delete("tester", "renamed")
    }

    @Test
    fun `열 값으로 종류별 커밋 읽기 구현을 고른다`() {
        val backends = backends()
        assertThat(backends.commits("GIT")).isInstanceOf(GitCommitReader::class.java)
        assertThat(backends.commits("Subversion")).isInstanceOf(dev.yona.repository.svn.SvnCommitReader::class.java)
        assertThatThrownBy { backends.commits("Mercurial") }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `커밋이 없는 Git 저장소는 이력과 커밋이 없다`() {
        val backends = backends()
        val project = project("GIT", "emptygit")
        backends.lifecycle("GIT").create("tester", "emptygit")
        val commits = backends.commits("GIT")
        // HEAD를 풀 수 없으면 이력은 null이다(v1.6 `getHistory`)
        assertThat(commits.history(project, 0, 10, null, null)).isNull()
        assertThat(commits.commit(project, "0000000000000000000000000000000000000000")).isNull()
        assertThat(commits.commits(project, listOf("deadbeef"))).isEmpty()
        assertThat(commits.commits(project, emptyList())).isEmpty()
    }

    @Test
    fun `커밋이 없는 Subversion 저장소의 이력은 비어 있고 첫 리비전은 부모가 없다`() {
        val backends = backends()
        val project = project("Subversion", "emptysvn")
        backends.lifecycle("Subversion").create("tester", "emptysvn")
        val commits = backends.commits("Subversion")
        assertThat(commits.history(project, 0, 10, null, null)).isEmpty()
        assertThat(commits.commit(project, "abc")).isNull()
        assertThat(commits.parentOf(project, "1")).isNull()
        // Subversion은 한 번에 여러 커밋을 읽는 기능이 없어 빈 맵이다
        assertThat(commits.commits(project, listOf("1"))).isEmpty()
    }
}
