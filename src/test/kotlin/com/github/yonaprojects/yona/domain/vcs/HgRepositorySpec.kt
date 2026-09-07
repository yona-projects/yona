package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.Hg
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
})
