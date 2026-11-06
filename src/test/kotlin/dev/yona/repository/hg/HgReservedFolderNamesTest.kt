package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 최상위 폴더 이름이 저장소 내부 폴더 이름(`data`, `meta`)과 같은 프로젝트의 파일을 읽는다. hg4j가 이런 경로를 이미 접두어가 붙은 것으로 보아 필로그를 못 찾던 결함의
 * 회귀 시험이다(hg4j 수정 `9a7560a`, X-7 9a에서 찾았다).
 */
class HgReservedFolderNamesTest {

    @TempDir lateinit var dataDir: File

    @Test
    fun `data와 meta와 dh 폴더 아래 파일도 읽는다`() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        repos.create("t", "p"); val dir = repos.directoryOf("t", "p")
        val files = mapOf("data/x.txt" to "hello data\n", "meta/m.txt" to "hello meta\n", "dh/z.txt" to "hello dh\n", "other/y.txt" to "hello other\n")
        HgTestSupport.commit(dir, "c", files)
        HgTestSupport.bookmark(dir, "main")
        repos.open("t", "p").use { h ->
            val node = HgRevisions.head(h)!!
            files.forEach { (path, content) -> assertThat(String(h.cat().setFile(path).setRevision(node).call())).describedAs(path).isEqualTo(content) }
        }
        assertThat(HgTestSupport.verifies(dir)).isTrue()
    }

    @Test
    fun `서버가 hg4j로 받은 data 폴더 파일도 실제 hg가 읽는다`() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        repos.create("t", "origin"); val origin = repos.directoryOf("t", "origin")
        HgTestSupport.commit(origin, "c", mapOf("data/x.txt" to "hello data\n"))
        HgTestSupport.bookmark(origin, "main")
        // 포크는 hg4j가 가져와 저장소 파일을 쓴다. 실제 hg가 그 저장소를 검증하고 읽을 수 있어야 한다
        repos.cloneLocal("t", "origin", "u", "fork")
        val fork = repos.directoryOf("u", "fork")
        assertThat(HgTestSupport.verifies(fork)).isTrue()
        assertThat(HgTestSupport.hg(fork, "cat", "-r", "main", "data/x.txt")).isEqualTo("hello data\n")
    }
}
