package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Mercurial 저장소의 위치와 만들기, 지우기, 옮기기, 참조 이름, 기본 위치를 확인한다. 실제 `hg`로 읽어 본다. */
class HgRepositoriesTest {

    @TempDir lateinit var dataDir: File
    private lateinit var hg: HgRepositories

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        hg = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
    }

    @Test
    fun `저장소는 데이터 디렉터리의 repo hg 아래 소유자와 이름 위치에 만든다`() {
        hg.create("tester", "proj")
        val dir = File(dataDir, "repo/hg/tester/proj")
        assertThat(hg.directoryOf("tester", "proj")).isEqualTo(dir)
        assertThat(File(dir, ".hg")).isDirectory
        // 실제 hg가 읽을 수 있는 저장소이다
        assertThat(HgTestSupport.verifies(dir)).isTrue()
    }

    @Test
    fun `지우면 디렉터리가 사라지고 없는 저장소를 지워도 오류가 없다`() {
        hg.create("tester", "proj")
        hg.delete("tester", "proj")
        assertThat(hg.directoryOf("tester", "proj")).doesNotExist()
        hg.delete("tester", "proj")
    }

    @Test
    fun `옮기면 새 위치에서 열리고 원래 위치에 없으면 성공이다`() {
        hg.create("tester", "proj")
        val node = HgTestSupport.commit(hg.directoryOf("tester", "proj"), "처음", mapOf("a.txt" to "a\n"))
        assertThat(hg.move("tester", "proj", "other", "renamed")).isTrue()
        assertThat(hg.directoryOf("tester", "proj")).doesNotExist()
        assertThat(HgTestSupport.hg(hg.directoryOf("other", "renamed"), "log", "-r", "0", "--template", "{node}")).isEqualTo(node)
        assertThat(hg.move("nobody", "none", "x", "y")).isTrue()
    }

    @Test
    fun `비어 있으면 참조 이름이 없고 기본 위치는 HEAD이다`() {
        hg.create("tester", "proj")
        assertThat(hg.refNames("tester", "proj")).isEmpty()
        assertThat(hg.defaultBranch("tester", "proj")).isEqualTo("HEAD")
    }

    @Test
    fun `이름표는 refs heads, 태그는 refs tags로 이름순으로 알려 주고 tip은 뺀다`() {
        hg.create("tester", "proj")
        val dir = hg.directoryOf("tester", "proj")
        HgTestSupport.commit(dir, "첫째", mapOf("a.txt" to "a\n"))
        HgTestSupport.bookmark(dir, "zeta"); HgTestSupport.bookmark(dir, "alpha")
        HgTestSupport.tag(dir, "v1", "0")
        assertThat(hg.refNames("tester", "proj")).containsExactly("refs/heads/alpha", "refs/heads/zeta", "refs/tags/v1")
    }

    @Test
    fun `기본 위치는 main, master, @ 이름표 순으로 고르고 없으면 HEAD이다`() {
        hg.create("tester", "proj")
        val dir = hg.directoryOf("tester", "proj")
        HgTestSupport.commit(dir, "첫째", mapOf("a.txt" to "a\n"))
        HgTestSupport.bookmark(dir, "feature")
        assertThat(hg.defaultBranch("tester", "proj")).isEqualTo("HEAD")
        HgTestSupport.bookmark(dir, "@")
        assertThat(hg.defaultBranch("tester", "proj")).isEqualTo("refs/heads/@")
        HgTestSupport.bookmark(dir, "master")
        assertThat(hg.defaultBranch("tester", "proj")).isEqualTo("refs/heads/master")
        HgTestSupport.bookmark(dir, "main")
        assertThat(hg.defaultBranch("tester", "proj")).isEqualTo("refs/heads/main")
    }

    @Test
    fun `없는 저장소를 열면 예외이다`() {
        assertThatThrownBy { hg.open("tester", "none") }.isInstanceOf(java.io.IOException::class.java)
    }
}
