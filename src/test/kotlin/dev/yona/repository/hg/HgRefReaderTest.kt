package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.repository.TagAuthor
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Date

/** Mercurial 저장소의 브랜치(이름표)와 태그 읽기, 이름표 삭제를 실제 `hg`로 만든 저장소에서 확인한다. */
class HgRefReaderTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgRefReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File
    private lateinit var c0: String
    private lateinit var c1: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgRefReader(repos, HgTagWriter(repos))
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
        c0 = HgTestSupport.commit(dir, "첫째", mapOf("a.txt" to "a\n"), "2026-11-01 10:00 +0900")
        c1 = HgTestSupport.commit(dir, "둘째", mapOf("a.txt" to "b\n"), "2026-11-02 10:00 +0900")
    }

    private fun bookmarks() = HgTestSupport.hg(dir, "bookmarks").lines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `브랜치는 이름표이고 머리 커밋이 최근인 순서이다`() {
        HgTestSupport.bookmark(dir, "old", "0"); HgTestSupport.bookmark(dir, "new", "1")
        val branches = reader.branches(project)
        assertThat(branches.map { it.name }).containsExactly("refs/heads/new", "refs/heads/old")
        assertThat(branches[0].headCommitId).isEqualTo(c1); assertThat(branches[0].headCommitShortId).isEqualTo(c1.take(12))
        // 2026-11-02 10:00 +0900
        assertThat(branches[0].committerDate).isEqualTo(Date(1793581200L * 1000))
        assertThat(branches[1].headCommitId).isEqualTo(c0)
    }

    @Test
    fun `이름표가 없으면 브랜치도 기본 브랜치도 없다`() {
        assertThat(reader.branches(project)).isEmpty()
        assertThat(reader.headBranch(project)).isNull()
        repos.create("tester", "empty")
        assertThat(reader.headBranch(Project(owner = "tester", name = "empty", vcs = "Mercurial"))).isNull()
    }

    @Test
    fun `기본 브랜치는 main, master, @ 이름표 순으로 고른다`() {
        HgTestSupport.bookmark(dir, "feature", "0")
        assertThat(reader.headBranch(project)).isNull()
        HgTestSupport.bookmark(dir, "master", "0")
        assertThat(reader.headBranch(project)!!.name).isEqualTo("refs/heads/master")
        HgTestSupport.bookmark(dir, "main", "1")
        val head = reader.headBranch(project)!!
        assertThat(head.name).isEqualTo("refs/heads/main"); assertThat(head.headCommitId).isEqualTo(c1)
    }

    @Test
    fun `이름표를 지우면 다른 이름표는 남는다`() {
        HgTestSupport.bookmark(dir, "keep", "0"); HgTestSupport.bookmark(dir, "drop", "1")
        reader.deleteBranch(project, "refs/heads/drop")
        assertThat(reader.branches(project).map { it.name }).containsExactly("refs/heads/keep")
        // 짧은 이름도 받는다
        reader.deleteBranch(project, "keep")
        assertThat(reader.branches(project)).isEmpty()
        assertThat(bookmarks()).noneMatch { it.contains("keep") || it.contains("drop") }
    }

    @Test
    fun `태그는 가상 태그 tip을 빼고 태그한 커밋 시각이 최근인 순서이다`() {
        HgTestSupport.tag(dir, "v1", "0")
        HgTestSupport.tag(dir, "v2", "1", "2026-11-04 10:00 +0900")
        val tags = reader.tags(project)
        assertThat(tags.map { it.shortName }).containsExactly("v2", "v1")
        assertThat(tags[0].name).isEqualTo("refs/tags/v2"); assertThat(tags[0].commitId).isEqualTo(c1)
        assertThat(tags[0].commitDate).isEqualTo(Date(1793581200L * 1000)); assertThat(tags[0].annotated).isFalse()
        assertThat(tags.map { it.shortName }).doesNotContain("tip")
        assertThat(reader.findTag(project, "v1")!!.commitId).isEqualTo(c0)
        assertThat(reader.findTag(project, "none")).isNull()
    }

    // --- 태그 만들기와 지우기(X-7 9단계 보완, A-389): 작업 폴더 없이 `.hgtags`를 바꾸는 체인지셋 ---

    private val author = TagAuthor("태거", "tagger@example.com")

    private fun tagLines() = HgTestSupport.hg(dir, "cat", "-r", "main", ".hgtags").lines().filter { it.isNotBlank() }

    @Test
    fun `태그를 만들면 기본 위치 위에 태그 체인지셋이 생기고 이름표가 따라 움직이며 실제 hg가 읽는다`() {
        HgTestSupport.bookmark(dir, "main")
        val created = reader.createTag(project, "v1", c0, null, author)
        assertThat(created.error).isNull()
        assertThat(created.tag!!.shortName).isEqualTo("v1"); assertThat(created.tag!!.commitId).isEqualTo(c0)
        // 원본 hg의 `hg tag`와 같은 형식: .hgtags에 "노드 이름" 한 줄, 새 체인지셋은 기본 위치의 위에 쌓이고 이름표 main이 그 체인지셋으로 옮겨진다
        assertThat(tagLines()).containsExactly("$c0 v1")
        val tagging = HgTestSupport.hg(dir, "log", "-r", "main", "--template", "{desc|firstline}|{author}|{p1node}")
        assertThat(tagging).isEqualTo("Added tag v1 for changeset ${c0.take(12)}|태거 <tagger@example.com>|$c1")
        assertThat(HgTestSupport.hg(dir, "tags")).contains("v1").contains(c0.take(12))
        assertThat(HgTestSupport.verifies(dir)).isTrue()
    }

    @Test
    fun `대상을 주지 않으면 기본 위치의 끝에 태그를 단다`() {
        HgTestSupport.bookmark(dir, "main")
        assertThat(reader.createTag(project, "latest", null, null, author).tag!!.commitId).isEqualTo(c1)
    }

    @Test
    fun `잘못된 이름, 같은 이름, 없는 대상, 빈 저장소는 오류로 돌려준다`() {
        HgTestSupport.bookmark(dir, "main")
        for (bad in listOf("tip", "a:b", "a\nb", "", "12", ".", "null", " v1")) {
            assertThat(reader.createTag(project, bad, c0, null, author).error).describedAs(bad).isEqualTo(dev.yona.repository.TagCreateError.INVALID_NAME)
        }
        assertThat(reader.createTag(project, "v1", c0, null, author).error).isNull()
        assertThat(reader.createTag(project, "v1", c1, null, author).error).isEqualTo(dev.yona.repository.TagCreateError.ALREADY_EXISTS)
        assertThat(reader.createTag(project, "v2", "f".repeat(40), null, author).error).isEqualTo(dev.yona.repository.TagCreateError.NO_TARGET)
        repos.create("tester", "empty")
        assertThat(reader.createTag(Project(owner = "tester", name = "empty", vcs = "Mercurial"), "v1", null, null, author).error).isEqualTo(dev.yona.repository.TagCreateError.EMPTY_REPOSITORY)
    }

    @Test
    fun `메시지를 주면 태그 체인지셋의 설명으로 쓴다(Mercurial에는 주석 태그가 없다)`() {
        HgTestSupport.bookmark(dir, "main")
        reader.createTag(project, "v1", c0, "첫 배포", author)
        assertThat(HgTestSupport.hg(dir, "log", "-r", "main", "--template", "{desc}")).isEqualTo("첫 배포")
    }

    @Test
    fun `태그를 지우면 목록에서 사라지고 hgtags 파일에 영 노드가 남으며 없는 태그는 false이다`() {
        HgTestSupport.bookmark(dir, "main")
        reader.createTag(project, "v1", c0, null, author); reader.createTag(project, "v2", c1, null, author)
        assertThat(reader.deleteTag(project, "v1")).isTrue()
        assertThat(reader.tags(project).map { it.shortName }).containsExactly("v2")
        assertThat(tagLines().last()).isEqualTo("${"0".repeat(40)} v1")
        assertThat(HgTestSupport.hg(dir, "log", "-r", "main", "--template", "{desc|firstline}")).isEqualTo("Removed tag v1")
        assertThat(reader.deleteTag(project, "v1")).isFalse()
        assertThat(reader.deleteTag(project, "없는태그")).isFalse()
        assertThat(HgTestSupport.verifies(dir)).isTrue()
    }
}
