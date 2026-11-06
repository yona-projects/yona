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

/** Mercurial 저장소 안 LFS 저장소의 사용량과 LFS 파일 목록(X-7 9a의 LFS 화면, `decisions.md` D-90)을 실제 `hg` 저장소로 확인한다. */
class HgLfsStoreTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var store: HgLfsStore
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        store = HgLfsStore(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
    }

    /** 서버 저장소 폴더에서 lfs 확장으로 커밋해 LFS 파일을 만든다(본문은 저장소의 `.hg/store/lfs/objects`에 들어간다). */
    private fun lfsCommit(path: String, content: String, message: String = "LFS 파일"): String {
        File(dir, path).parentFile.mkdirs(); File(dir, path).writeText(content)
        val env = arrayOf("--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "--config", "experimental.lfs.disableusercache=true")
        HgTestSupport.hg(dir, *env, "add", path)
        HgTestSupport.hg(dir, *env, "commit", "-m", message, "-d", "2026-11-03 10:00 +0900")
        return HgTestSupport.hg(dir, "log", "-r", ".", "--template", "{node}")
    }

    @Test
    fun `LFS 파일이 없으면 사용량은 0이고 목록은 비어 있다`() {
        HgTestSupport.commit(dir, "일반 파일", mapOf("a.txt" to "one\n"))
        HgTestSupport.bookmark(dir, "main")
        assertThat(store.usage("tester", "proj")).isEqualTo(HgLfsStore.Usage(0, 0))
        assertThat(store.trackedFiles("tester", "proj")).isEmpty()
    }

    @Test
    fun `사용량은 저장소 안 LFS 저장소의 객체 수와 바이트이고 목록은 기본 위치 머리의 LFS 파일이다`() {
        HgTestSupport.commit(dir, "일반 파일", mapOf("a.txt" to "one\n"))
        val big = "큰 파일 본문 ".repeat(40); val bigger = "더 큰 파일 본문 ".repeat(60)
        lfsCommit("assets/big.bin", big); lfsCommit("assets/bigger.bin", bigger, "LFS 파일 둘")
        HgTestSupport.bookmark(dir, "main")

        val usage = store.usage("tester", "proj")
        assertThat(usage.objects).isEqualTo(2)
        assertThat(usage.bytes).isEqualTo(big.toByteArray().size + bigger.toByteArray().size.toLong())
        val files = store.trackedFiles("tester", "proj")
        assertThat(files.map { it.path }).containsExactly("assets/big.bin", "assets/bigger.bin")
        assertThat(files.map { it.size }).containsExactly(big.toByteArray().size.toLong(), bigger.toByteArray().size.toLong())
        assertThat(files.all { it.oid.length == 64 }).isTrue()
    }

    @Test
    fun `최상위 data 폴더 아래의 LFS 파일도 목록에 나온다`() {
        HgTestSupport.commit(dir, "일반 파일", mapOf("a.txt" to "one\n"))
        lfsCommit("data/big.bin", "큰 파일 본문 ".repeat(40))
        HgTestSupport.bookmark(dir, "main")
        assertThat(store.trackedFiles("tester", "proj").map { it.path }).containsExactly("data/big.bin")
    }

    @Test
    fun `커밋이 없는 저장소도 오류 없이 빈 결과이다`() {
        assertThat(store.usage("tester", "proj")).isEqualTo(HgLfsStore.Usage(0, 0))
        assertThat(store.trackedFiles("tester", "proj")).isEmpty()
    }
}
