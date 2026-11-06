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
        return VcsBackends.of(GitLifecycle(GitRepositories(directory)), dev.yona.repository.svn.SvnLifecycle(SvnRepositories(directory)))
    }

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
}
