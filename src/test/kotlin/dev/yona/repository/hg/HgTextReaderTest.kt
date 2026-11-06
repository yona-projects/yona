package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Mercurial 저장소의 파일 텍스트와 README 읽기를 실제 `hg`로 만든 저장소에서 확인한다. */
class HgTextReaderTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgTextReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgTextReader(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
    }

    @Test
    fun `비어 있는 저장소는 읽을 것이 없다`() {
        assertThat(reader.readText(project, "a.txt")).isNull()
        assertThat(reader.readme(project)).isNull()
        assertThat(reader.readmeFileName(project)).isNull()
        assertThat(reader.readmeForPosting(project)).isNull()
    }

    @Test
    fun `파일은 기본 위치의 최신 내용이고 없거나 폴더이면 null이다`() {
        HgTestSupport.commit(dir, "처음", mapOf("dir/a.txt" to "안녕\n"))
        HgTestSupport.commit(dir, "수정", mapOf("dir/a.txt" to "안녕하세요\n"), "2026-11-02 10:00 +0900")
        assertThat(reader.readText(project, "dir/a.txt")).isEqualTo("안녕하세요\n")
        assertThat(reader.readText(project, "dir")).isNull()
        assertThat(reader.readText(project, "none.txt")).isNull()
    }

    @Test
    fun `기본 위치는 이름표가 있으면 그 이름표이다`() {
        HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "첫째\n"))
        HgTestSupport.commit(dir, "둘째", mapOf("a.txt" to "둘째\n"), "2026-11-02 10:00 +0900")
        HgTestSupport.bookmark(dir, "main", "0")
        assertThat(reader.readText(project, "a.txt")).isEqualTo("첫째\n")
    }

    @Test
    fun `README는 README md가 readme md보다 먼저이다`() {
        HgTestSupport.commit(dir, "처음", mapOf("readme.md" to "소문자\n", "README.md" to "대문자\n"))
        assertThat(reader.readmeFileName(project)).isEqualTo("README.md")
        assertThat(reader.readme(project)).isEqualTo("대문자\n")
        assertThat(reader.readmeForPosting(project)).isEqualTo("대문자\n")
    }

    @Test
    fun `README가 없거나 하위 폴더에만 있으면 null이다`() {
        HgTestSupport.commit(dir, "처음", mapOf("docs/README.md" to "안쪽\n", "a.txt" to "a\n"))
        assertThat(reader.readme(project)).isNull()
        assertThat(reader.readmeForPosting(project)).isNull()
    }

    @Test
    fun `게시글용 README는 markdown 확장자도 받는다`() {
        HgTestSupport.commit(dir, "처음", mapOf("README.markdown" to "마크다운\n"))
        assertThat(reader.readmeForPosting(project)).isEqualTo("마크다운\n")
        // 일반 README 읽기는 md 두 가지만 본다
        assertThat(reader.readme(project)).isNull()
    }
}
