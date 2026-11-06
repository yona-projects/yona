package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Mercurial LFS의 수명 주기(X-7 9a, `decisions.md` D-90)를 실제 `hg`로 확인한다. Git LFS는 본문이 저장소 밖에 있어 포크가 원본에서 빌려 읽지만(`LfsLifecycleTest`),
 * Mercurial의 본문은 저장소 안(`.hg/store/lfs`)에 있어 포크와 풀 리퀘스트 가져오기가 본문을 함께 옮겨야 하고, 개명과 이전은 저장소 디렉터리와 함께 움직인다.
 */
class HgLfsLifecycleTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var refs: HgPullRefs
    private lateinit var store: HgLfsStore
    private lateinit var origin: File
    private val env = arrayOf("--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "--config", "experimental.lfs.disableusercache=true")
    private val body = "큰 파일 본문 ".repeat(40)

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        refs = HgPullRefs(repos)
        store = HgLfsStore(repos)
        repos.create("t", "origin"); origin = repos.directoryOf("t", "origin")
        HgTestSupport.commit(origin, "일반 파일", mapOf("a.txt" to "one\n"))
        lfsCommit(origin, "assets/big.bin", body)
        HgTestSupport.bookmark(origin, "main")
    }

    private fun lfsCommit(dir: File, path: String, content: String) {
        File(dir, path).parentFile.mkdirs(); File(dir, path).writeText(content)
        HgTestSupport.hg(dir, *env, "add", path)
        HgTestSupport.hg(dir, *env, "commit", "-m", "LFS $path", "-d", "2026-11-03 10:00 +0900")
    }

    /** 실제 hg가 [dir]의 [rev]에서 [path]를 읽은 내용이다(LFS 본문을 그 저장소의 LFS 저장소에서 읽는다). */
    private fun cat(dir: File, rev: String, path: String) = HgTestSupport.hg(dir, *env, "cat", "-r", rev, path)

    @Test
    fun `포크는 LFS 본문까지 가져가 실제 hg가 읽을 수 있다`() {
        repos.cloneLocal("t", "origin", "u", "fork")
        val fork = repos.directoryOf("u", "fork")
        assertThat(store.usage("u", "fork")).isEqualTo(store.usage("t", "origin")).isEqualTo(HgLfsStore.Usage(1, body.toByteArray().size.toLong()))
        assertThat(store.trackedFiles("u", "fork").map { it.path }).containsExactly("assets/big.bin")
        assertThat(cat(fork, "main", "assets/big.bin")).isEqualTo(body)
        assertThat(HgTestSupport.verifies(fork)).isTrue()
    }

    @Test
    fun `포크와 원본의 본문은 서로 독립이다`() {
        repos.cloneLocal("t", "origin", "u", "fork")
        // 원본의 본문을 지워도 포크는 자기 본문을 가진다
        File(origin, ".hg/store/lfs").deleteRecursively()
        assertThat(store.usage("t", "origin").objects).isEqualTo(0)
        assertThat(store.usage("u", "fork").objects).isEqualTo(1)
        assertThat(cat(repos.directoryOf("u", "fork"), "main", "assets/big.bin")).isEqualTo(body)
    }

    @Test
    fun `풀 리퀘스트로 가져온 체인지셋의 LFS 본문도 받는 저장소로 옮긴다`() {
        repos.cloneLocal("t", "origin", "u", "fork")
        val fork = repos.directoryOf("u", "fork")
        val more = "새로 올린 큰 파일 ".repeat(40)
        HgTestSupport.hg(fork, "update", "-C", "main")
        lfsCommit(fork, "assets/new.bin", more)
        val head = HgTestSupport.hg(fork, "log", "-r", ".", "--template", "{node}")
        HgTestSupport.hg(fork, "bookmark", "-f", "-r", head, "main")

        refs.fetch("t", "origin", "u", "fork", head, refs.headRef(1))

        assertThat(store.usage("t", "origin").objects).isEqualTo(2)
        assertThat(cat(origin, head, "assets/new.bin")).isEqualTo(more)
    }

    @Test
    fun `개명이나 이전은 LFS 본문과 함께 저장소 디렉터리가 움직인다`() {
        assertThat(repos.move("t", "origin", "v", "renamed")).isTrue()
        assertThat(store.usage("t", "origin")).isEqualTo(HgLfsStore.Usage(0, 0))
        assertThat(store.usage("v", "renamed").objects).isEqualTo(1)
        assertThat(cat(repos.directoryOf("v", "renamed"), "main", "assets/big.bin")).isEqualTo(body)
    }

    @Test
    fun `저장소를 지우면 LFS 본문도 함께 지워진다`() {
        repos.delete("t", "origin")
        assertThat(repos.directoryOf("t", "origin")).doesNotExist()
        assertThat(store.usage("t", "origin")).isEqualTo(HgLfsStore.Usage(0, 0))
    }
}
