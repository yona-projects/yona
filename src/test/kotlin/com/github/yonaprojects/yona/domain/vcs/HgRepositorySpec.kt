package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.Hg
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.eclipse.jgit.diff.DiffEntry
import java.io.File
import java.nio.file.Files

// yona-wiki P3-12(Mercurial 지원) 1라운드 — GitRepositorySpec/SvnRepositorySpec와 동일한 스타일로,
// 실제 로컬 hg4j 저장소를 만들고 HgRepository의 각 메서드가 기대한 결과를 내는지 end-to-end로
// 검증한다. hg4j 자체의 포셀린 API(add/commit)로 커밋을 직접 쌓은 뒤 HgRepository로 조회한다.
class HgRepositorySpec : DescribeSpec({

    fun newTempBaseDir(): String = Files.createTempDirectory("yona-hg-test").toFile().absolutePath

    fun commitFile(repoDir: File, path: String, content: String, message: String, author: String = "tester <tester@example.com>") {
        val file = File(repoDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
        Hg.open(repoDir).use { hg ->
            hg.add().addFile(path).call()
            hg.commit().setAuthor(author).setMessage(message).call()
        }
    }

    fun noUser(name: String?, email: String?): User? = null

    fun removeFile(repoDir: File, path: String, message: String, author: String = "tester <tester@example.com>") {
        Hg.open(repoDir).use { hg ->
            hg.remove().setFile(path).call()
            hg.commit().setAuthor(author).setMessage(message).call()
        }
    }

    describe("create()") {
        it("빈 디렉터리에 새 hg 저장소를 만든다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)

            repo.create()

            File(repo.getDirectory(), ".hg").exists() shouldBe true
        }
    }

    describe("isEmpty()") {
        it("커밋이 없으면 true") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()

            repo.isEmpty() shouldBe true
        }

        it("커밋이 하나라도 있으면 false") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "hello", "첫 커밋")

            repo.isEmpty() shouldBe false
        }
    }

    describe("getDefaultBranch()") {
        it("항상 \"default\"를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()

            repo.getDefaultBranch() shouldBe "default"
        }
    }

    describe("커밋/조회") {
        it("getRawFile로 특정 리비전의 파일 내용을 읽을 수 있다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "README.md", "hello world", "초기 커밋")

            val content = repo.getRawFile("tip", "README.md")

            String(content) shouldBe "hello world"
        }

        it("getHistory로 커밋 목록을 최신순으로 가져온다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            commitFile(repo.getDirectory(), "a.txt", "2", "커밋2")

            val history = repo.getHistory(0, 10, null, null)

            history.size shouldBe 2
            history[0].getShortMessage() shouldBe "커밋2"
            history[1].getShortMessage() shouldBe "커밋1"
        }

        it("getCommit(\"tip\")으로 최신 커밋을 가져온다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            val commit = repo.getCommit("tip")

            commit.shouldNotBeNull()
            commit.getShortMessage() shouldBe "커밋1"
            commit.getAuthorName() shouldBe "tester"
            commit.getAuthorEmail() shouldBe "tester@example.com"
        }

        it("getParentCommitOf로 부모 커밋을 가져온다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            commitFile(repo.getDirectory(), "a.txt", "2", "커밋2")

            val tip = repo.getCommit("tip")!!
            val parent = repo.getParentCommitOf(tip.getId())

            parent.shouldNotBeNull()
            parent.getShortMessage() shouldBe "커밋1"
        }

        it("루트 커밋의 부모는 없다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            val tip = repo.getCommit("tip")!!
            repo.getParentCommitOf(tip.getId()).shouldBeNull()
        }
    }

    describe("getMetaDataFromPath") {
        it("파일 경로면 type=file과 내용을 담은 JSON을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "README.md", "hello", "초기 커밋")

            val meta = repo.getMetaDataFromPath("tip", "README.md")

            meta.shouldNotBeNull()
            meta.get("type").asText() shouldBe "file"
            meta.get("data").asText() shouldBe "hello"
        }

        it("루트 경로면 type=folder와 최상위 항목 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "README.md", "hello", "초기 커밋")
            commitFile(repo.getDirectory(), "src/main.txt", "code", "src 추가")

            val meta = repo.getMetaDataFromPath("tip", "")

            meta.shouldNotBeNull()
            meta.get("type").asText() shouldBe "folder"
            val data = meta.get("data")
            data.get("README.md").get("type").asText() shouldBe "file"
            data.get("src").get("type").asText() shouldBe "folder"
        }

        it("존재하지 않는 경로면 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "README.md", "hello", "초기 커밋")

            repo.getMetaDataFromPath("tip", "no-such-file.txt").shouldBeNull()
        }
    }

    describe("delete()/move()") {
        it("delete()는 저장소 디렉터리를 지운다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()

            repo.delete()

            repo.getDirectory().exists() shouldBe false
        }

        it("move()는 저장소를 새 owner/name 경로로 옮긴다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            val moved = repo.move("owner", "project", "owner2", "project2")

            moved shouldBe true
            File(File(baseDir), "owner2/project2/.hg").exists() shouldBe true
        }
    }

    // yona-wiki P3-12 2라운드 — bookmark를 git 모양 "브랜치"에 매핑한 CRUD. 실제 hg4j
    // BookmarkCommand로 직접 만든 bookmark를 HgRepository 쪽에서 조회/삭제가 되는지, 그리고
    // HgRepository.createBranch()/deleteBranch()로 만들고 지운 것도 hg4j 쪽에서 보이는지 양방향으로
    // 검증한다.
    describe("브랜치(bookmark) CRUD") {
        it("getBranches()는 hg4j로 직접 만든 bookmark를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            Hg.open(repo.getDirectory()).use { hg -> hg.bookmark().setBookmarkName("feature-x").call() }

            val branches = repo.getBranches()

            branches.map { it.shortName } shouldBe listOf("feature-x")
            branches[0].headCommit.getShortMessage() shouldBe "커밋1"
        }

        it("createBranch()로 만든 브랜치가 hg4j bookmark로도 보인다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            val tip = repo.getCommit("tip")!!

            repo.createBranch("refs/heads/new-branch", "tip")

            val bookmarks = Hg.open(repo.getDirectory()).use { hg -> hg.bookmark().call() }
            bookmarks["new-branch"] shouldBe tip.getId()
            repo.getBranches().map { it.shortName } shouldBe listOf("new-branch")
        }

        it("이미 존재하는 브랜치 이름으로 createBranch()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            repo.createBranch("dup", "tip")

            shouldThrow<IllegalArgumentException> {
                repo.createBranch("dup", "tip")
            }
        }

        it("존재하지 않는 시작점으로 createBranch()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.createBranch("orphan", "no-such-revision")
            }
        }

        it("deleteBranch()로 지운 브랜치는 getBranches()에서 사라진다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            repo.createBranch("to-delete", "tip")

            repo.deleteBranch("refs/heads/to-delete")

            repo.getBranches() shouldBe emptyList()
        }

        it("'tip'이라는 이름으로 createBranch()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.createBranch("tip", "tip")
            }
        }

        it("존재하지 않는 브랜치를 deleteBranch()로 지우려 하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.deleteBranch("no-such-branch")
            }
        }

        it("getHeadBranch()는 active bookmark를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            // -r 없이 이름만 지정하면 자동으로 active가 된다(hg4j BookmarkCommand 주석 참고).
            Hg.open(repo.getDirectory()).use { hg -> hg.bookmark().setBookmarkName("active-one").call() }

            val head = repo.getHeadBranch()

            head.shouldNotBeNull()
            head.shortName shouldBe "active-one"
        }

        it("active bookmark가 없으면 getHeadBranch()는 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            repo.getHeadBranch().shouldBeNull()
        }
    }

    describe("태그 CRUD") {
        it("createTag()로 만든 태그가 getTagNames()/getTags()에 보인다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            val tip = repo.getCommit("tip")!!

            repo.createTag("v1.0", "tip", null, null, null)

            repo.getTagNames() shouldBe listOf("refs/tags/v1.0")
            val tags = repo.getTags()
            tags.size shouldBe 1
            tags[0].shortName shouldBe "v1.0"
            tags[0].targetCommit.getId() shouldBe tip.getId()
            // Mercurial 태그는 git 스타일 annotated 태그가 아니다 — tagger/message는 항상 비어 있다.
            tags[0].annotated shouldBe false
            tags[0].message shouldBe null
        }

        it("getTagNames()/getTags()는 pseudo-tag \"tip\"을 걸러낸다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            repo.getTagNames() shouldBe emptyList()
            repo.getTags() shouldBe emptyList()
        }

        it("이미 존재하는 태그 이름으로 createTag()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            repo.createTag("dup-tag", "tip", null, null, null)

            shouldThrow<IllegalArgumentException> {
                repo.createTag("dup-tag", "tip", null, null, null)
            }
        }

        it("'tip'이라는 이름으로 createTag()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.createTag("tip", "tip", null, null, null)
            }
        }

        it("존재하지 않는 시작점으로 createTag()를 호출하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.createTag("orphan-tag", "no-such-revision", null, null, null)
            }
        }

        it("deleteTag()로 지운 태그는 getTagNames()에서 사라진다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")
            repo.createTag("to-delete", "tip", null, null, null)

            repo.deleteTag("refs/tags/to-delete")

            repo.getTagNames() shouldBe emptyList()
        }

        it("존재하지 않는 태그를 deleteTag()로 지우려 하면 예외가 발생한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "project", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "1", "커밋1")

            shouldThrow<IllegalArgumentException> {
                repo.deleteTag("no-such-tag")
            }
        }
    }

    // yona-wiki P3-12 2라운드 마무리 — GitRepositorySpec의 "getPatch()"/"getDiff() - commitId
    // 단일/두 리비전"/"getFileDiffs() - 파일별 diff 상세" describe 블록과 동일한 구성/신뢰 수준으로,
    // hg4j 포셀린 API(commitFile/removeFile)로 실제 로컬 저장소를 만들어 검증한다.
    describe("getPatch()") {
        it("commitId 하나로 호출하면 부모 커밋과의 diff를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch1", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "line1\nline2\n", "첫 커밋")
            commitFile(repo.getDirectory(), "a.txt", "line1\nline2-changed\n", "수정 커밋")
            val commit2 = repo.getCommit("tip")!!

            val patch = repo.getPatch(commit2.getId())

            patch shouldContain "-line2"
            patch shouldContain "+line2-changed"
        }

        it("부모가 없는 최초 커밋을 조회하면 빈 매니페스트와의 diff(전체 추가)를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch2", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "hello\n", "최초 커밋")
            val commit1 = repo.getCommit("tip")!!

            val patch = repo.getPatch(commit1.getId())

            patch shouldContain "+hello"
        }

        it("존재하지 않는 commitId는 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch3", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content", "커밋")

            repo.getPatch("no-such-rev") shouldBe ""
        }

        it("두 리비전을 지정하면 그 사이의 diff를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch4", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "original\n", "v1")
            val commit1 = repo.getCommit("tip")!!
            commitFile(repo.getDirectory(), "a.txt", "changed\n", "v2")
            val commit2 = repo.getCommit("tip")!!

            val patch = repo.getPatch(commit1.getId(), commit2.getId())

            patch shouldContain "-original"
            patch shouldContain "+changed"
        }

        it("revA가 존재하지 않으면 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch5", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content", "커밋")
            val commit1 = repo.getCommit("tip")!!

            repo.getPatch("no-such-rev", commit1.getId()) shouldBe ""
        }

        it("revB가 존재하지 않으면 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-patch6", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content", "커밋")
            val commit1 = repo.getCommit("tip")!!

            repo.getPatch(commit1.getId(), "no-such-rev") shouldBe ""
        }
    }

    describe("getDiff() - commitId 단일/두 리비전") {
        it("commitId 하나로 호출하면 부모와의 FileDiff 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff1", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "line1\n", "첫 커밋")
            commitFile(repo.getDirectory(), "a.txt", "line1\nline2\n", "수정 커밋")
            val commit2 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit2.getId())

            diffs.size shouldBe 1
            val fileDiff = diffs[0] as FileDiff
            fileDiff.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fileDiff.commitB shouldBe commit2.getId()
        }

        it("부모가 없는 최초 커밋을 조회하면 전체가 ADD로 표시된다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff2", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "hello\n", "최초 커밋")
            val commit1 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit1.getId())

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.ADD
            (diffs[0] as FileDiff).commitA.shouldBeNull()
        }

        it("존재하지 않는 commitId는 빈 리스트를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff3", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content", "커밋")

            repo.getDiff("no-such-rev") shouldBe emptyList()
        }

        it("두 리비전을 지정하면 그 사이의 FileDiff 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff4", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "v1\n", "v1")
            val commit1 = repo.getCommit("tip")!!
            commitFile(repo.getDirectory(), "a.txt", "v2\n", "v2")
            val commit2 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit1.getId(), commit2.getId())

            diffs.size shouldBe 1
        }

        it("revA가 존재하지 않으면 빈 매니페스트와의 diff(ADD)로 처리된다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff5", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content\n", "커밋")
            val commit1 = repo.getCommit("tip")!!

            val diffs = repo.getDiff("no-such-rev", commit1.getId())

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.ADD
        }

        it("revB가 존재하지 않으면 빈 매니페스트와의 diff(DELETE)로 처리된다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-diff6", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content\n", "커밋")
            val commit1 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit1.getId(), "no-such-rev")

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.DELETE
        }
    }

    describe("getFileDiffs() - 파일별 diff 상세(getDiff를 통해 검증)") {
        it("새 파일 추가(ADD)는 pathB/텍스트 내용이 채워지고 pathA는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-fd1", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "new.txt", "added content\n", "추가")
            val commit1 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit1.getId())

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.ADD
            fd.pathB shouldBe "new.txt"
            fd.pathA shouldBe null
            fd.isBinaryB shouldBe false
            fd.b shouldNotBe null
            fd.b!!.getString(0) shouldBe "added content"
        }

        it("파일 수정(MODIFY)은 editList와 hunks가 계산된다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-fd2", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", (0..9).joinToString("\n") { "line$it" } + "\n", "v1")
            commitFile(repo.getDirectory(), "a.txt", (0..9).joinToString("\n") { if (it == 5) "CHANGED" else "line$it" } + "\n", "v2")
            val commit2 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit2.getId())

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fd.editList shouldNotBe null
            fd.getHunks() shouldNotBe null
        }

        it("이전 커밋이 있는 상태에서 새 파일을 추가하면 그 파일 항목만 ADD로 표시된다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-fd3", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "existing.txt", "v1\n", "v1")
            commitFile(repo.getDirectory(), "existing.txt", "v2\n", "v2 수정")
            Hg.open(repo.getDirectory()).use { hg ->
                File(repo.getDirectory(), "new.txt").writeText("brand new\n")
                hg.add().addFile("new.txt").call()
                hg.commit().setAuthor("tester <tester@example.com>").setMessage("새 파일 추가").call()
            }
            val commit3 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit3.getId())

            diffs.size shouldBe 1
            val added = diffs[0] as FileDiff
            added.pathB shouldBe "new.txt"
            added.changeType shouldBe DiffEntry.ChangeType.ADD
            added.pathA shouldBe null
        }

        it("파일 삭제(DELETE)는 pathA만 채워지고 pathB는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-fd4", baseDir, ::noUser)
            repo.create()
            commitFile(repo.getDirectory(), "a.txt", "content\n", "추가")
            removeFile(repo.getDirectory(), "a.txt", "삭제")
            val commit2 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit2.getId())

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.DELETE
            fd.pathA shouldBe "a.txt"
            fd.pathB shouldBe null
            fd.a shouldNotBe null
        }

        it("바이너리(NUL 포함) 파일 추가는 isBinaryB=true이고 b는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = HgRepository("owner", "p-fd5", baseDir, ::noUser)
            repo.create()
            val binFile = File(repo.getDirectory(), "bin.dat")
            binFile.writeBytes(byteArrayOf(1, 2, 0, 3))
            Hg.open(repo.getDirectory()).use { hg ->
                hg.add().addFile("bin.dat").call()
                hg.commit().setAuthor("tester <tester@example.com>").setMessage("바이너리 추가").call()
            }
            val commit1 = repo.getCommit("tip")!!

            val diffs = repo.getDiff(commit1.getId())

            val fd = diffs[0] as FileDiff
            fd.isBinaryB shouldBe true
            fd.b shouldBe null
        }
    }
})
