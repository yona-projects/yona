package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Base64

/**
 * Mercurial 커밋의 서명(X-7 9c, `decisions.md` D-88)을 실제 `hg` 저장소에서 읽는다. 서명은 서명한 체인지셋 자신이 아니라 **나중 커밋의 `.hgsigs`**에 들어
 * 있으므로, 어떤 커밋이 서명됐는지는 보고 있는 리비전에서 보이는 `.hgsigs`로 정한다.
 */
class HgSignaturesTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgCommitReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File
    private val key = TestGpgKey.generate(userId = "Signer <signer@example.com>")

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgCommitReader(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
    }

    /** `.hgsigs`의 한 줄이다: `<노드> 0 <서명 base64>`. 서명은 `노드\n`에 대한 분리 서명이다(hgext/gpg와 같다). */
    private fun sigLine(node: String): String {
        val binary = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(java.io.ByteArrayInputStream(key.sign("$node\n".toByteArray(Charsets.US_ASCII)).toByteArray())).readAllBytes()
        return "$node 0 ${Base64.getEncoder().encodeToString(binary)}"
    }

    @Test
    fun `서명은 나중 커밋의 hgsigs에서 찾고 서명한 커밋에만 붙으며 서명 원문은 노드와 줄바꿈이다`() {
        val c0 = HgTestSupport.commit(dir, "서명할 커밋", mapOf("a.txt" to "one\n"), "2026-11-01 10:00 +0900", "작성자 <signer@example.com>")
        val c1 = HgTestSupport.commit(dir, "서명 안 한 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900", "작성자 <signer@example.com>")
        val c2 = HgTestSupport.commit(dir, "서명을 더하는 커밋", mapOf(".hgsigs" to sigLine(c0) + "\n"), "2026-11-03 10:00 +0900", "작성자 <signer@example.com>")
        HgTestSupport.bookmark(dir, "main")

        val signed = reader.commit(project, c0)!!
        assertThat(signed.signature).isNotNull()
        assertThat(String(signed.signature!!.data, Charsets.US_ASCII)).isEqualTo("$c0\n")
        assertThat(reader.commit(project, c1)!!.signature).isNull()
        // 서명을 더하는 커밋은 자기 자신을 서명할 수 없다
        assertThat(reader.commit(project, c2)!!.signature).isNull()
        assertThat(reader.history(project, 0, 10, null, null)!!.associate { it.id to (it.signature != null) }).containsEntry(c0, true).containsEntry(c1, false).containsEntry(c2, false)
    }

    @Test
    fun `보고 있는 리비전에서 보이지 않는 hgsigs의 서명은 쓰지 않는다`() {
        val c0 = HgTestSupport.commit(dir, "서명할 커밋", mapOf("a.txt" to "one\n"), "2026-11-01 10:00 +0900", "작성자 <signer@example.com>")
        val c1 = HgTestSupport.commit(dir, "서명을 더하는 커밋", mapOf(".hgsigs" to sigLine(c0) + "\n"), "2026-11-02 10:00 +0900", "작성자 <signer@example.com>")
        HgTestSupport.bookmark(dir, "main")
        // 서명이 담긴 c1에서 보면 c0이 서명됐고, 그 앞의 c0에서 보면(서명이 아직 없다) 서명되지 않았다
        assertThat(reader.history(project, 0, 10, c1, null)!!.first { it.id == c0 }.signature).isNotNull()
        assertThat(reader.history(project, 0, 10, c0, null)!!.first { it.id == c0 }.signature).isNull()
    }

    @Test
    fun `읽을 수 없는 hgsigs나 이상한 줄은 서명 없음으로 본다`() {
        val c0 = HgTestSupport.commit(dir, "커밋", mapOf("a.txt" to "one\n", ".hgsigs" to "이건 서명 줄이 아니다\n"), "2026-11-01 10:00 +0900", "작성자 <signer@example.com>")
        HgTestSupport.bookmark(dir, "main")
        assertThat(reader.commit(project, c0)!!.signature).isNull()
    }
}
