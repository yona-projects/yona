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

    // --- 포크(X-7 7a) ---

    private fun logOf(dir: File) = HgTestSupport.hg(dir, "log", "--template", "{desc|firstline}\\n")

    @Test
    fun `포크는 체인지셋과 이름표와 태그를 그대로 복제하고 실제 hg가 검증한다`() {
        hg.create("tester", "origin")
        val origin = hg.directoryOf("tester", "origin")
        val c0 = HgTestSupport.commit(origin, "첫 커밋", mapOf("a.txt" to "one\n"))
        HgTestSupport.commit(origin, "둘째 커밋", mapOf("a.txt" to "one\ntwo\n"), "2026-11-02 10:00 +0900")
        HgTestSupport.bookmark(origin, "main")
        HgTestSupport.tag(origin, "v1", c0)

        hg.cloneLocal("tester", "origin", "alice", "forked")

        val fork = hg.directoryOf("alice", "forked")
        // 실패하면 hg verify의 출력이 메시지에 실린다
        HgTestSupport.hg(fork, "verify")
        assertThat(logOf(fork)).isEqualTo(logOf(origin))
        assertThat(HgTestSupport.hg(fork, "bookmarks", "--template", "{bookmark}\\n")).contains("main")
        assertThat(HgTestSupport.hg(fork, "tags", "--template", "{tag}\\n")).contains("v1")
        // 서버 저장소에는 작업 파일이 없어도 태그와 이름표가 목록에 나온다(태그는 머리의 .hgtags에서 읽는다)
        assertThat(hg.refNames("alice", "forked")).containsExactly("refs/heads/main", "refs/tags/v1")
        // 원본은 그대로이다
        assertThat(HgTestSupport.verifies(origin)).isTrue()
    }

    @Test
    fun `포크 저장소는 서버 저장소라 작업 파일이 없다`() {
        hg.create("tester", "origin")
        HgTestSupport.commit(hg.directoryOf("tester", "origin"), "첫 커밋", mapOf("a.txt" to "one\n"))
        hg.cloneLocal("tester", "origin", "alice", "forked")
        assertThat(File(hg.directoryOf("alice", "forked"), "a.txt")).doesNotExist()
    }

    @Test
    fun `secret 체인지셋은 포크로 복제하지 않는다`() {
        hg.create("tester", "origin")
        val origin = hg.directoryOf("tester", "origin")
        HgTestSupport.commit(origin, "공개 커밋", mapOf("a.txt" to "one\n"))
        File(origin, "s.txt").writeText("s\n")
        HgTestSupport.hg(origin, "add", "s.txt")
        HgTestSupport.hg(origin, "--config", "phases.new-commit=secret", "commit", "-m", "비공개 커밋", "-d", "2026-11-02 10:00 +0900")
        assertThat(logOf(origin)).contains("비공개 커밋")

        hg.cloneLocal("tester", "origin", "alice", "forked")

        assertThat(logOf(hg.directoryOf("alice", "forked"))).doesNotContain("비공개 커밋").contains("공개 커밋")
    }

    @Test
    fun `원본이 없으면 포크는 예외이고 만들다 만 디렉터리를 남기지 않는다`() {
        assertThatThrownBy { hg.cloneLocal("tester", "nothing", "alice", "forked") }.isInstanceOf(java.io.IOException::class.java)
        assertThat(hg.directoryOf("alice", "forked")).doesNotExist()
    }

    @Test
    fun `태그는 작업 파일이 없는 저장소에서도 머리의 hgtags로 읽고 지운 태그와 옮긴 태그를 반영한다`() {
        hg.create("tester", "origin")
        val origin = hg.directoryOf("tester", "origin")
        val c0 = HgTestSupport.commit(origin, "첫 커밋", mapOf("a.txt" to "one\n"))
        val c1 = HgTestSupport.commit(origin, "둘째 커밋", mapOf("a.txt" to "one\ntwo\n"), "2026-11-02 10:00 +0900")
        HgTestSupport.tag(origin, "v1", c0)
        HgTestSupport.tag(origin, "moved", c0, "2026-11-04 10:00 +0900")
        HgTestSupport.hg(origin, "tag", "--local", "-r", c1, "localtag")
        HgTestSupport.hg(origin, "tag", "-f", "-r", c1, "-d", "2026-11-05 10:00 +0900", "-m", "옮김", "moved")
        HgTestSupport.hg(origin, "tag", "--remove", "-d", "2026-11-06 10:00 +0900", "-m", "지움", "v1")
        hg.cloneLocal("tester", "origin", "alice", "forked")

        val tags = hg.open("alice", "forked").use { HgRevisions.tags(it) }
        assertThat(tags.keys).containsExactlyInAnyOrder("moved")
        assertThat(tags["moved"]).isEqualTo(c1)
    }
}
