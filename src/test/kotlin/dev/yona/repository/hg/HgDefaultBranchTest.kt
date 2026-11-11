package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsCapabilities
import dev.yona.domain.vcs.VcsCapability
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Mercurial의 "기본으로 설정"을 검증한다(UI 계약 재조사 code-F7의 남은 부분, 선생님 결정 2026-11-11). v1.6에서 옮긴 단추가 요청은 성공하는데 아무 일도 하지 않았다.
 * 기본 브랜치는 Git의 `HEAD`처럼 **북마크 이름을 가리키는 포인터**이다(저장소 안 `.hg/yona-default-branch`). 리비전에 고정하는 `@` 북마크로 구현하면 그 북마크에
 * 새 커밋이 쌓여도 기본 위치가 낡은 리비전에 남으므로 이름을 기억한다. 실제 `hg`로 만든 저장소에서 확인한다.
 */
class HgDefaultBranchTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgRefReader
    private lateinit var lifecycle: HgLifecycle
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgRefReader(repos, HgTagWriter(repos))
        lifecycle = HgLifecycle(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
        HgTestSupport.commit(dir, "첫째", mapOf("a.txt" to "a\n"), "2026-11-01 10:00 +0900")
        HgTestSupport.bookmark(dir, "main")
        HgTestSupport.bookmark(dir, "feature")
    }

    @Test
    fun `Mercurial도 기본 브랜치 능력을 가진다`() {
        assertThat(VcsCapabilities.supports("Mercurial", VcsCapability.DEFAULT_BRANCH)).isTrue()
    }

    @Test
    fun `기본으로 정하면 그 북마크가 기본 브랜치이고 새 커밋을 따라간다`() {
        assertThat(repos.defaultBranch("tester", "proj")).isEqualTo("refs/heads/main")
        lifecycle.setDefaultBranch("tester", "proj", "refs/heads/feature")
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("refs/heads/feature")
        assertThat(reader.headBranch(project)!!.name).isEqualTo("refs/heads/feature")
        // feature에 새 커밋이 쌓이면 기본 브랜치의 머리도 그 커밋이다(`@`처럼 낡은 리비전에 고정되지 않는다)
        HgTestSupport.hg(dir, "update", "-q", "feature")
        val next = HgTestSupport.commit(dir, "둘째", mapOf("a.txt" to "b\n"), "2026-11-02 10:00 +0900")
        assertThat(reader.headBranch(project)!!.headCommitId).isEqualTo(next)
        // 기본 위치(코드 화면의 파일과 이력)도 같은 곳을 본다
        assertThat(repos.open("tester", "proj").use { HgRevisions.head(it) }).isEqualTo(next)
    }

    @Test
    fun `다시 다른 북마크를 기본으로 정할 수 있고 이름 앞의 refs heads는 있어도 없어도 된다`() {
        lifecycle.setDefaultBranch("tester", "proj", "feature")
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("refs/heads/feature")
        lifecycle.setDefaultBranch("tester", "proj", "refs/heads/main")
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("refs/heads/main")
    }

    @Test
    fun `기본 북마크를 지우면 기존 순서(main, master, @, 기본 위치)로 돌아가고 오류가 나지 않는다`() {
        lifecycle.setDefaultBranch("tester", "proj", "refs/heads/feature")
        reader.deleteBranch(project, "refs/heads/feature")
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("refs/heads/main")
        assertThat(reader.headBranch(project)!!.name).isEqualTo("refs/heads/main")
        reader.deleteBranch(project, "refs/heads/main")
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("HEAD")
        assertThat(reader.headBranch(project)!!.name).isEqualTo("HEAD")
    }

    @Test
    fun `없는 북마크나 가상 이름은 기본으로 정할 수 없다`() {
        assertThatThrownBy { lifecycle.setDefaultBranch("tester", "proj", "refs/heads/nothing") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lifecycle.setDefaultBranch("tester", "proj", "HEAD") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(lifecycle.defaultBranch("tester", "proj")).isEqualTo("refs/heads/main")
    }

    @Test
    fun `정한 기본 북마크는 다른 이름(main)보다 앞선다`() {
        HgTestSupport.hg(dir, "update", "-q", "feature")
        val next = HgTestSupport.commit(dir, "feature의 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900")
        assertThat(reader.headBranch(project)!!.headCommitId).isNotEqualTo(next)
        lifecycle.setDefaultBranch("tester", "proj", "feature")
        assertThat(reader.headBranch(project)!!.headCommitId).isEqualTo(next)
    }
}
