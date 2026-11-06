package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.vcs.RefUpdate
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 풀 리퀘스트의 숨은 참조(D-87)를 실제 `hg` 저장소에서 확인한다(X-7 8a). 보내는 쪽(포크) 체인지셋을 받는 저장소로 가져와 secret 단계로 두고
 * 예약 이름표 `yobi/pull/<번호>/head`, `merged`로 가리키며, 병합하면 공개 단계로 올린다.
 */
class HgPullRefsTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var refs: HgPullRefs
    private lateinit var origin: File
    private lateinit var fork: File
    private lateinit var base: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        refs = HgPullRefs(repos)
        repos.create("tester", "origin")
        origin = repos.directoryOf("tester", "origin")
        base = HgTestSupport.commit(origin, "기준", mapOf("a.txt" to "a\n"))
        HgTestSupport.bookmark(origin, "main")
        repos.cloneLocal("tester", "origin", "alice", "fork")
        fork = repos.directoryOf("alice", "fork")
    }

    /** 포크에 커밋을 더하고 그 노드를 돌려준다. */
    private fun forkCommit(message: String, file: String): String =
        HgTestSupport.commit(fork, message, mapOf(file to "$message\n"), "2026-11-02 10:00 +0900")

    private fun phase(dir: File, node: String) = HgTestSupport.hg(dir, "log", "-r", node, "--template", "{phase}").trim()

    private fun clonedPullOnly(): File {
        val target = File(dataDir, "check-${System.nanoTime()}")
        // 실제 hg가 pull로 복제할 때 보이는 체인지셋만 온다(secret은 숨는다)
        HgTestSupport.hg(dataDir, "clone", "--pull", origin.path, target.path)
        return target
    }

    @Test
    fun `보내는 쪽 체인지셋은 받는 저장소에 secret으로 오고 이름표가 가리키며 기존 체인지셋의 단계는 그대로이다`() {
        val head = forkCommit("기능", "f.txt")
        val baseBefore = phase(origin, base)
        val node = refs.fetch("tester", "origin", "alice", "fork", head, refs.headRef(7))

        assertThat(node).isEqualTo(head)
        assertThat(phase(origin, head)).isEqualTo("secret")
        // 받는 저장소에 이미 있던 체인지셋의 단계는 바꾸지 않는다
        assertThat(phase(origin, base)).isEqualTo(baseBefore).isNotEqualTo("secret")
        assertThat(HgTestSupport.hg(origin, "bookmarks", "--template", "{bookmark}={node}\\n")).contains("yobi/pull/7/head=$head")
        assertThat(HgTestSupport.verifies(origin)).isTrue()
    }

    @Test
    fun `secret 체인지셋과 예약 이름표는 pull로 복제하는 사용자에게 보이지 않는다`() {
        val head = forkCommit("기능", "f.txt")
        refs.fetch("tester", "origin", "alice", "fork", head, refs.headRef(7))

        val cloned = clonedPullOnly()
        assertThat(HgTestSupport.hg(cloned, "log", "--template", "{node}\\n")).doesNotContain(head)
        assertThat(HgTestSupport.hg(cloned, "bookmarks", "--template", "{bookmark}\\n")).doesNotContain("yobi")
    }

    @Test
    fun `예약 이름표는 브랜치 목록과 이름 풀이에 나오지 않는다`() {
        val head = forkCommit("기능", "f.txt")
        refs.fetch("tester", "origin", "alice", "fork", head, refs.headRef(7))

        assertThat(repos.refNames("tester", "origin")).containsExactly("refs/heads/main")
        repos.open("tester", "origin").use { hg ->
            assertThat(HgRevisions.bookmarks(hg).keys).containsExactly("main")
            assertThat(HgRevisions.resolve(hg, "refs/heads/yobi/pull/7/head")).isNull()
        }
    }

    @Test
    fun `같은 체인지셋을 다시 가져와도 오류가 없고 보내는 쪽이 새 커밋을 올리면 이름표가 옮겨진다`() {
        val first = forkCommit("기능", "f.txt")
        refs.fetch("tester", "origin", "alice", "fork", first, refs.headRef(7))
        assertThat(refs.fetch("tester", "origin", "alice", "fork", first, refs.headRef(7))).isEqualTo(first)

        val second = forkCommit("기능 둘", "g.txt")
        refs.fetch("tester", "origin", "alice", "fork", second, refs.headRef(7))
        assertThat(HgTestSupport.hg(origin, "bookmarks", "--template", "{bookmark}={node}\\n")).contains("yobi/pull/7/head=$second").doesNotContain("=$first")
        assertThat(phase(origin, second)).isEqualTo("secret")
        assertThat(phase(origin, first)).isEqualTo("secret")
    }

    @Test
    fun `이름표를 지워도 secret 체인지셋은 남는다`() {
        val head = forkCommit("기능", "f.txt")
        refs.fetch("tester", "origin", "alice", "fork", head, refs.headRef(7))
        refs.delete("tester", "origin", refs.headRef(7))

        assertThat(HgTestSupport.hg(origin, "bookmarks", "--template", "{bookmark}\\n")).doesNotContain("yobi")
        assertThat(phase(origin, head)).isEqualTo("secret")
        assertThat(HgTestSupport.verifies(origin)).isTrue()
    }

    @Test
    fun `병합하면 병합 체인지셋과 가져온 체인지셋을 공개로 올려 pull로 복제하는 사용자에게 보인다`() {
        val head = forkCommit("기능", "f.txt")
        refs.fetch("tester", "origin", "alice", "fork", head, refs.headRef(7))
        val tip = HgTestSupport.hg(origin, "log", "-r", "main", "--template", "{node}")

        // 받는 쪽 이름표를 가져온 체인지셋으로 옮기고 공개로 올린다(빨리감기와 같은 모양, 병합 커밋은 8c)
        refs.publish("tester", "origin", head)
        assertThat(phase(origin, head)).isEqualTo("public")
        HgTestSupport.hg(origin, "bookmark", "-f", "-r", head, "main")
        val cloned = clonedPullOnly()
        assertThat(HgTestSupport.hg(cloned, "log", "--template", "{node}\\n")).contains(head).contains(tip)
    }

    @Test
    fun `예약 이름표 푸시는 refs yobi 아래 ref로 바뀌어 예약된 이름으로 거부된다`() {
        repos.open("tester", "origin").use { hg ->
            val log = HgRevisions.changelog(hg)
            val update = HgPushUpdates.bookmark(log, "yobi/pull/7/head", "", base)
            assertThat(update.ref).isEqualTo("refs/yobi/pull/7/head")
            assertThat(update.type).isEqualTo(dev.yona.domain.vcs.RefUpdateType.CREATE)
            assertThat(HgPushUpdates.bookmark(log, "feature", "", base).ref).isEqualTo("refs/heads/feature")
        }
    }
}
