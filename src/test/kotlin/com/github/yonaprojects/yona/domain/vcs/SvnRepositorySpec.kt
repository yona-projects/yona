package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

// yona SVNRepository.java(playRepository) 대응 (사용자 지시 "DAVServlet, SvnRepository에 대한 전체
// 검증" — 권한 필터(SvnAuthorizationFilterSpec)만 있고 실제 VCS 동작 자체는 검증된 적이 없었다).
// 매 테스트마다 실제 로컬 SVN 저장소를 만들고 SVNKit의 저수준 커밋 에디터로 진짜 리비전을 쌓은 뒤,
// SvnRepository의 각 메서드가 legacy SVNRepository.java와 동일한 결과를 내는지 end-to-end로 검증한다.
class SvnRepositorySpec : DescribeSpec({

    fun newTempBaseDir(): String = Files.createTempDirectory("yona-svn-test").toFile().absolutePath

    // legacy SVNRepository.java는 별도 커밋 헬퍼가 없지만(실제 커밋은 svn 클라이언트/DAVServlet을 통해
    // 일어남), 테스트에서는 SVNKit의 저수준 커밋 에디터로 동일한 결과(실제 리비전)를 만들어낸다.
    fun commitFile(repo: SvnRepository, path: String, content: String, message: String, author: String = "tester"): Long {
        val svnURL = SVNURL.fromFile(repo.getDirectory())
        val segments = path.split("/")

        // 커밋 에디터 세션을 열기 전에 별도의 짧은 연결로 이미 존재하는 경로(파일/디렉토리)를
        // 먼저 확인해둔다 — SVNKit은 하나의 SVNRepository 인스턴스에 대해 커밋 에디터 세션이 열려
        // 있는 동안 같은 인스턴스의 다른 메서드(checkPath 등)를 호출하면 "not reenterable" 에러를
        // 던진다(재진입 불가).
        val probe = SVNRepositoryFactory.create(svnURL)
        val existingDirs = mutableSetOf<String>()
        val fileExists: Boolean
        try {
            var currentPath = ""
            for (i in 0 until segments.size - 1) {
                currentPath = if (currentPath.isEmpty()) segments[i] else "$currentPath/${segments[i]}"
                if (probe.checkPath(currentPath, -1) == SVNNodeKind.DIR) {
                    existingDirs.add(currentPath)
                }
            }
            fileExists = probe.checkPath(path, -1) == SVNNodeKind.FILE
        } finally {
            probe.closeSession()
        }

        val svnRepository = SVNRepositoryFactory.create(svnURL)
        svnRepository.authenticationManager = BasicAuthenticationManager.newInstance(author, CharArray(0))
        try {
            val editor = svnRepository.getCommitEditor(message, null)
            editor.openRoot(-1)

            var currentPath = ""
            for (i in 0 until segments.size - 1) {
                currentPath = if (currentPath.isEmpty()) segments[i] else "$currentPath/${segments[i]}"
                if (existingDirs.contains(currentPath)) {
                    editor.openDir(currentPath, -1)
                } else {
                    editor.addDir(currentPath, null, -1)
                }
            }

            if (fileExists) {
                editor.openFile(path, -1)
            } else {
                editor.addFile(path, null, -1)
            }
            editor.applyTextDelta(path, null)
            val deltaGenerator = SVNDeltaGenerator()
            val checksum = deltaGenerator.sendDelta(path, ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8)), editor, true)
            editor.closeFile(path, checksum)

            for (i in segments.size - 2 downTo 0) {
                editor.closeDir()
            }
            editor.closeDir()

            val info = editor.closeEdit()
            return info.newRevision
        } finally {
            svnRepository.closeSession()
        }
    }

    val userResolver: (String) -> User? = { loginId ->
        User(id = 1L, loginId = loginId, name = "테스트유저$loginId", email = "$loginId@yona.io")
    }

    describe("create()/isEmpty()/delete()") {
        it("create()를 호출하면 유효한 SVN 저장소가 생성되고 초기에는 비어있어야 한다") {
            val repo = SvnRepository("owner1", "proj1", newTempBaseDir(), userResolver)

            repo.create()

            repo.getDirectory().exists() shouldBe true
            repo.isEmpty() shouldBe true
        }

        it("커밋이 하나라도 있으면 isEmpty()가 false여야 한다") {
            val repo = SvnRepository("owner2", "proj2", newTempBaseDir(), userResolver)
            repo.create()

            commitFile(repo, "a.txt", "hello", "첫 커밋")

            repo.isEmpty() shouldBe false
        }

        it("저장소가 아예 없으면 isEmpty()가 true를 반환해야 한다(디렉토리 자체가 없는 경우)") {
            val repo = SvnRepository("owner3", "no-such-proj", newTempBaseDir(), userResolver)

            repo.isEmpty() shouldBe true
        }

        it("디렉토리는 존재하지만 유효한 SVN 저장소가 아니면 SVNException을 잡고 true를 반환해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner33", "not-svn", baseDir, userResolver)
            repo.getDirectory().mkdirs()

            repo.isEmpty() shouldBe true
        }

        it("delete()를 호출하면 저장소 디렉토리가 사라져야 한다") {
            val repo = SvnRepository("owner4", "proj4", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "hello", "커밋")

            repo.delete()

            repo.getDirectory().exists() shouldBe false
        }

        it("디렉토리가 이미 존재하면 mkdirs 없이 그대로 저장소를 초기화해야 한다") {
            val repo = SvnRepository("owner37", "proj37", newTempBaseDir(), userResolver)
            repo.getDirectory().mkdirs()

            repo.create()

            repo.getDirectory().exists() shouldBe true
            repo.isEmpty() shouldBe true
        }

        it("저장소가 없는 상태에서 delete()를 호출해도 예외 없이 안전해야 한다") {
            val repo = SvnRepository("owner38", "no-such-dir", newTempBaseDir(), userResolver)

            repo.delete()

            repo.getDirectory().exists() shouldBe false
        }
    }

    describe("getHistory()/getCommit()/getParentCommitOf()") {
        it("여러 커밋을 만들면 getHistory()가 최신순으로 정확한 메시지/작성자를 반환해야 한다") {
            val repo = SvnRepository("owner5", "proj5", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "v1", "첫 번째 커밋", author = "alice")
            commitFile(repo, "a.txt", "v2", "두 번째 커밋", author = "bob")
            commitFile(repo, "b.txt", "new file", "세 번째 커밋", author = "alice")

            val history = repo.getHistory(0, 25, null, null)

            history.size shouldBe 3
            // legacy와 동일하게 최신 리비전이 먼저 온다(startRevision > endRevision으로 log() 호출).
            history[0].getMessage() shouldBe "세 번째 커밋"
            history[1].getMessage() shouldBe "두 번째 커밋"
            history[2].getMessage() shouldBe "첫 번째 커밋"
        }

        it("path를 지정하면 해당 경로 하위의 로그만 조회해야 한다") {
            val repo = SvnRepository("owner31", "proj31", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "dir/a.txt", "v1", "dir 커밋")
            commitFile(repo, "b.txt", "v1", "루트 커밋")

            val history = repo.getHistory(0, 25, null, "dir")

            history.size shouldBe 1
            history[0].getMessage() shouldBe "dir 커밋"
        }

        it("페이지 범위가 전체 리비전을 초과하면 빈 목록을 반환해야 한다") {
            val repo = SvnRepository("owner32", "proj32", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "v1", "커밋")

            val history = repo.getHistory(10, 10, null, null)

            history shouldBe emptyList()
        }

        it("getCommit(rev)으로 특정 리비전의 커밋을 조회할 수 있어야 한다") {
            val repo = SvnRepository("owner6", "proj6", newTempBaseDir(), userResolver)
            repo.create()
            val rev1 = commitFile(repo, "a.txt", "v1", "리비전1")
            val rev2 = commitFile(repo, "a.txt", "v2", "리비전2")

            val commit1 = repo.getCommit(rev1.toString())
            val commit2 = repo.getCommit(rev2.toString())

            commit1?.getMessage() shouldBe "리비전1"
            commit1?.getId() shouldBe rev1.toString()
            commit2?.getMessage() shouldBe "리비전2"
        }

        // legacy SVNRepository.java의 getCommit()은 "throws IOException, SVNException"으로 선언돼
        // 있고 범위를 벗어난 리비전(SVNKit 자체가 즉시 거부)에서는 예외를 그대로 전파한다 — null 반환은
        // (repository.log()가 예외 없이 빈 결과를 주는) 도달하기 어려운 방어 코드일 뿐이다. 실제로 이
        // 예외는 CodeViewController.showCommit()이 try/catch로 잡아 error/404로 처리하므로(legacy
        // CodeHistoryApp.show()도 동일하게 호출부에서 예외를 잡는 구조), 여기서 검증할 계약은
        // "null을 반환한다"가 아니라 "legacy와 동일하게 SVNException을 던진다"이다.
        it("범위를 벗어난 리비전을 조회하면 legacy와 동일하게 SVNException을 던져야 한다(호출부가 잡아 404 처리)") {
            val repo = SvnRepository("owner7", "proj7", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "v1", "커밋")

            try {
                repo.getCommit("999")
                throw AssertionError("SVNException이 발생해야 한다")
            } catch (e: SVNException) {
                // expected
            }
        }

        it("getParentCommitOf()는 바로 이전 리비전의 커밋을 반환해야 한다") {
            val repo = SvnRepository("owner8", "proj8", newTempBaseDir(), userResolver)
            repo.create()
            val rev1 = commitFile(repo, "a.txt", "v1", "부모 커밋")
            val rev2 = commitFile(repo, "a.txt", "v2", "자식 커밋")

            val parent = repo.getParentCommitOf(rev2.toString())

            parent?.getId() shouldBe rev1.toString()
            parent?.getMessage() shouldBe "부모 커밋"
        }

        it("커밋 작성자(author)를 userResolver로 실제 User 엔티티로 해석해야 한다") {
            val repo = SvnRepository("owner9", "proj9", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "v1", "커밋", author = "alice")

            val commit = repo.getHistory(0, 25, null, null).first()

            commit.getAuthorName() shouldBe "alice"
            commit.getAuthor()?.loginId shouldBe "alice"
            commit.getAuthor()?.name shouldBe "테스트유저alice"
        }

        it("전체 리비전이 페이지 크기보다 충분히 많으면 endRevision이 1로 보정되지 않아야 한다") {
            val repo = SvnRepository("owner39", "proj39", newTempBaseDir(), userResolver)
            repo.create()
            repeat(10) { i -> commitFile(repo, "a.txt", "v$i", "커밋$i") }

            val history = repo.getHistory(0, 5, null, null)

            // startRevision(10)~endRevision(5) 양끝 포함이라 6개(커밋9~커밋4)가 반환된다.
            history.size shouldBe 6
            history[0].getMessage() shouldBe "커밋9"
            history.last().getMessage() shouldBe "커밋4"
        }
    }

    describe("getMetaDataFromPath() — 파일/디렉토리 조회") {
        it("파일 경로를 조회하면 type=file과 실제 파일 내용을 반환해야 한다") {
            val repo = SvnRepository("owner10", "proj10", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "readme.txt", "Hello SVN", "README 추가")

            val meta = repo.getMetaDataFromPath("readme.txt")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "file"
            meta.get("data").asString() shouldBe "Hello SVN"
            meta.get("isBinary").asBoolean() shouldBe false
        }

        it("디렉토리 경로를 조회하면 type=folder와 하위 항목 목록을 반환해야 한다") {
            val repo = SvnRepository("owner11", "proj11", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "src/main.txt", "content", "src 디렉토리 파일 추가")
            commitFile(repo, "readme.txt", "readme", "루트 파일 추가")

            val meta = repo.getMetaDataFromPath("")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "folder"
            val data = meta.get("data")
            data.has("src") shouldBe true
            data.get("src").get("type").asString() shouldBe "folder"
            data.has("readme.txt") shouldBe true
            data.get("readme.txt").get("type").asString() shouldBe "file"
        }

        it("존재하지 않는 경로는 null을 반환해야 한다") {
            val repo = SvnRepository("owner12", "proj12", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            val meta = repo.getMetaDataFromPath("no-such-file.txt")

            meta shouldBe null
        }

        it("특정 리비전(branch 파라미터로 전달)의 경로 상태를 조회할 수 있어야 한다") {
            val repo = SvnRepository("owner13", "proj13", newTempBaseDir(), userResolver)
            repo.create()
            val rev1 = commitFile(repo, "a.txt", "v1", "첫 버전")
            commitFile(repo, "a.txt", "v2", "두번째 버전")

            val metaAtRev1 = repo.getMetaDataFromPath(rev1.toString(), "a.txt")

            metaAtRev1 shouldNotBe null
            metaAtRev1!!.get("data").asString() shouldBe "v1"
        }

        it("branch 파라미터가 숫자가 아니면 NumberFormatException을 잡고 HEAD(-1) 기준으로 조회해야 한다") {
            val repo = SvnRepository("owner30", "proj30", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "latest content", "커밋")

            val meta = repo.getMetaDataFromPath("not-a-number", "a.txt")

            meta shouldNotBe null
            meta!!.get("data").asString() shouldBe "latest content"
        }

        it("파일 내용에 널 바이트가 있으면 바이너리로 판단해야 한다") {
            val repo = SvnRepository("owner36", "proj36", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "bin.dat", "abc" + " " + "def", "바이너리 커밋")

            val meta = repo.getMetaDataFromPath("bin.dat")

            meta shouldNotBe null
            meta!!.get("isBinary").asBoolean() shouldBe true
        }

        it("파일 크기가 1MB를 초과하면 널 바이트가 없어도 바이너리로 판단해야 한다") {
            val repo = SvnRepository("owner41", "proj41", newTempBaseDir(), userResolver)
            repo.create()
            val bigContent = "a".repeat(1024 * 1024 + 1)
            commitFile(repo, "big.txt", bigContent, "대용량 파일 커밋")

            val meta = repo.getMetaDataFromPath("big.txt")

            meta shouldNotBe null
            meta!!.get("isBinary").asBoolean() shouldBe true
            meta.get("data").asString() shouldBe ""
        }

        it("userResolver가 사용자를 찾지 못하면 userName/userLoginId를 빈 문자열로 채워야 한다(디렉토리/파일 공통)") {
            val noSuchUserResolver: (String) -> User? = { null }
            val repo = SvnRepository("owner42", "proj42", newTempBaseDir(), noSuchUserResolver)
            repo.create()
            commitFile(repo, "dir/a.txt", "content", "커밋", author = "ghost")

            val dirMeta = repo.getMetaDataFromPath("dir")
            val fileMeta = repo.getMetaDataFromPath("dir/a.txt")

            dirMeta shouldNotBe null
            dirMeta!!.get("data").get("a.txt").get("userName").asString() shouldBe ""
            dirMeta.get("data").get("a.txt").get("userLoginId").asString() shouldBe ""
            fileMeta shouldNotBe null
            fileMeta!!.get("userName").asString() shouldBe ""
            fileMeta.get("userLoginId").asString() shouldBe ""
        }

        it("확장자로 MIME 타입을 판별할 수 없는 파일은 기본 mimeType(application/octet-stream)으로 채워야 한다") {
            val repo = SvnRepository("owner48", "proj48", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "unknownext.yonatestunknown", "plain text content", "알 수 없는 확장자 커밋")

            val meta = repo.getMetaDataFromPath("unknownext.yonatestunknown")

            meta shouldNotBe null
            meta!!.get("mimeType").asString() shouldBe "application/octet-stream"
        }
    }

    describe("getRawFile()") {
        it("HEAD 리비전의 파일 raw 바이트를 정확히 반환해야 한다") {
            val repo = SvnRepository("owner14", "proj14", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "raw content here", "커밋")

            val bytes = repo.getRawFile("HEAD", "a.txt")

            String(bytes, StandardCharsets.UTF_8) shouldBe "raw content here"
        }

        it("과거 리비전 번호를 지정하면 그 시점의 파일 내용을 반환해야 한다") {
            val repo = SvnRepository("owner15", "proj15", newTempBaseDir(), userResolver)
            repo.create()
            val rev1 = commitFile(repo, "a.txt", "old content", "v1")
            commitFile(repo, "a.txt", "new content", "v2")

            val bytes = repo.getRawFile(rev1.toString(), "a.txt")

            String(bytes, StandardCharsets.UTF_8) shouldBe "old content"
        }

        it("존재하지 않는 파일을 조회하면 FileNotFoundException을 던져야 한다") {
            val repo = SvnRepository("owner16", "proj16", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            try {
                repo.getRawFile("HEAD", "no-such.txt")
                throw AssertionError("FileNotFoundException이 발생해야 한다")
            } catch (e: FileNotFoundException) {
                // expected
            }
        }
    }

    describe("getPatch() — unified diff") {
        it("commitId 하나로 호출하면 그 직전 리비전과의 diff를 반환해야 한다") {
            val repo = SvnRepository("owner17", "proj17", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "line1\nline2\n", "첫 커밋")
            val rev2 = commitFile(repo, "a.txt", "line1\nline2-changed\n", "수정 커밋")

            val patch = repo.getPatch(rev2.toString())

            patch shouldContain "-line2"
            patch shouldContain "+line2-changed"
        }

        it("두 리비전을 지정하면 그 사이의 diff를 반환해야 한다") {
            val repo = SvnRepository("owner18", "proj18", newTempBaseDir(), userResolver)
            repo.create()
            val rev1 = commitFile(repo, "a.txt", "original\n", "v1")
            val rev2 = commitFile(repo, "a.txt", "changed\n", "v2")

            val patch = repo.getPatch(rev1.toString(), rev2.toString())

            patch shouldContain "-original"
            patch shouldContain "+changed"
        }

        it("getDiff()는 legacy와 동일하게 UnsupportedOperationException을 던져야 한다") {
            val repo = SvnRepository("owner19", "proj19", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            try {
                repo.getDiff("1")
                throw AssertionError("UnsupportedOperationException이 발생해야 한다")
            } catch (e: UnsupportedOperationException) {
                // expected — legacy SVNRepository.java도 동일하게 미구현
            }

            try {
                repo.getDiff("1", "2")
                throw AssertionError("UnsupportedOperationException이 발생해야 한다")
            } catch (e: UnsupportedOperationException) {
                // expected
            }
        }
    }

    describe("isFile()") {
        it("파일 경로는 true, 디렉토리 경로는 false를 반환해야 한다") {
            val repo = SvnRepository("owner20", "proj20", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "dir/a.txt", "content", "커밋")

            repo.isFile("dir/a.txt") shouldBe true
            repo.isFile("dir") shouldBe false
        }

        it("특정 리비전 문자열을 받는 오버로드도 동일하게 동작해야 한다") {
            val repo = SvnRepository("owner21", "proj21", newTempBaseDir(), userResolver)
            repo.create()
            val rev = commitFile(repo, "a.txt", "content", "커밋")

            repo.isFile("a.txt", rev.toString()) shouldBe true
        }
    }

    describe("move()/renameTo()") {
        it("move()는 저장소 디렉토리를 새 owner/name 위치로 옮겨야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner22", "old-name", baseDir, userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            val moved = repo.move("owner22", "old-name", "owner22", "new-name")

            moved shouldBe true
            repo.getDirectory().exists() shouldBe false
            val movedRepo = SvnRepository("owner22", "new-name", baseDir, userResolver)
            movedRepo.getDirectory().exists() shouldBe true
            movedRepo.isEmpty() shouldBe false
        }

        it("renameTo()는 move()에 위임해 같은 owner 아래에서 이름만 바꿔야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner23", "before-rename", baseDir, userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            val renamed = repo.renameTo("after-rename")

            renamed shouldBe true
            val renamedRepo = SvnRepository("owner23", "after-rename", baseDir, userResolver)
            renamedRepo.getDirectory().exists() shouldBe true
        }

        it("목적지의 상위 디렉토리가 없으면 새로 만들고 이동해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner34", "proj34", baseDir, userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            val moved = repo.move("owner34", "proj34", "brand-new-owner", "proj34")

            moved shouldBe true
            val movedRepo = SvnRepository("brand-new-owner", "proj34", baseDir, userResolver)
            movedRepo.getDirectory().exists() shouldBe true
        }

        it("원본 저장소가 존재하지 않으면 아무 것도 하지 않고 true를 반환해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner35", "no-such", baseDir, userResolver)

            val moved = repo.move("owner35", "no-such", "owner35", "renamed")

            moved shouldBe true
            File(baseDir, "owner35/renamed").exists() shouldBe false
        }
    }

    describe("Git 전용 개념(브랜치)의 SVN no-op 동작 — legacy와 동일") {
        it("getRefNames()는 HEAD 하나만 반환해야 한다") {
            val repo = SvnRepository("owner24", "proj24", newTempBaseDir(), userResolver)

            repo.getRefNames() shouldBe listOf("HEAD")
        }

        it("getDefaultBranch()는 항상 HEAD를 반환해야 한다") {
            val repo = SvnRepository("owner25", "proj25", newTempBaseDir(), userResolver)

            repo.getDefaultBranch() shouldBe "HEAD"
        }

        it("getBranches()는 빈 목록, getHeadBranch()는 null이어야 한다(SVN에는 브랜치 개념이 없음)") {
            val repo = SvnRepository("owner26", "proj26", newTempBaseDir(), userResolver)

            repo.getBranches() shouldBe emptyList()
            repo.getHeadBranch() shouldBe null
        }

        it("getBlobId()는 항상 null이어야 한다(PR 코드리뷰는 Git 전용 기능)") {
            val repo = SvnRepository("owner27", "proj27", newTempBaseDir(), userResolver)

            repo.getBlobId("1", "a.txt") shouldBe null
        }

        it("isIntermediateFolder()는 항상 false여야 한다") {
            val repo = SvnRepository("owner28", "proj28", newTempBaseDir(), userResolver)

            repo.isIntermediateFolder("any/path") shouldBe false
        }

        it("getArchive()는 legacy와 동일하게 아무 것도 하지 않아야 한다(예외를 던지지 않음)") {
            val repo = SvnRepository("owner29", "proj29", newTempBaseDir(), userResolver)
            repo.create()

            val out = ByteArrayOutputStream()
            repo.getArchive(out, "HEAD")

            out.size() shouldBe 0
        }

        it("setDefaultBranch()/deleteBranch()/createBranch()는 legacy와 동일하게 아무 것도 하지 않아야 한다") {
            val repo = SvnRepository("owner43", "proj43", newTempBaseDir(), userResolver)

            repo.setDefaultBranch("develop")
            repo.deleteBranch("develop")
            repo.createBranch("develop", "HEAD")

            repo.getDefaultBranch() shouldBe "HEAD"
        }
    }

    describe("작성자(author) 정보가 없는 익명 커밋 방어 처리") {
        fun commitFileAnonymous(repo: SvnRepository, path: String, content: String, message: String): Long {
            val svnURL = SVNURL.fromFile(repo.getDirectory())
            val svnRepository = SVNRepositoryFactory.create(svnURL)
            try {
                val editor = svnRepository.getCommitEditor(message, null)
                editor.openRoot(-1)
                editor.addFile(path, null, -1)
                editor.applyTextDelta(path, null)
                val deltaGenerator = SVNDeltaGenerator()
                val checksum = deltaGenerator.sendDelta(
                    path, ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8)), editor, true
                )
                editor.closeFile(path, checksum)
                editor.closeDir()
                return editor.closeEdit().newRevision
            } finally {
                svnRepository.closeSession()
            }
        }

        it("authenticationManager 없이 커밋되어 author 정보가 없으면 author를 빈 문자열로 채워야 한다") {
            val repo = SvnRepository("owner44", "proj44", newTempBaseDir(), userResolver)
            repo.create()
            commitFileAnonymous(repo, "anon.txt", "익명 커밋 내용", "익명 커밋")

            val fileMeta = repo.getMetaDataFromPath("anon.txt")
            val dirMeta = repo.getMetaDataFromPath("")

            fileMeta shouldNotBe null
            fileMeta!!.get("author").asString() shouldBe ""
            dirMeta shouldNotBe null
            dirMeta!!.get("data").get("anon.txt").get("author").asString() shouldBe ""
        }

        it("커밋 메시지 없이(null) 커밋되면 commitMessage를 빈 문자열로 채워야 한다") {
            val svnURLBuild: (SvnRepository) -> SVNURL = { SVNURL.fromFile(it.getDirectory()) }
            val repo = SvnRepository("owner45", "proj45", newTempBaseDir(), userResolver)
            repo.create()
            val svnRepository = SVNRepositoryFactory.create(svnURLBuild(repo))
            try {
                val editor = svnRepository.getCommitEditor(null, null)
                editor.openRoot(-1)
                editor.addFile("nomsg.txt", null, -1)
                editor.applyTextDelta("nomsg.txt", null)
                val deltaGenerator = SVNDeltaGenerator()
                val checksum = deltaGenerator.sendDelta(
                    "nomsg.txt", ByteArrayInputStream("내용".toByteArray(StandardCharsets.UTF_8)), editor, true
                )
                editor.closeFile("nomsg.txt", checksum)
                editor.closeDir()
                editor.closeEdit()
            } finally {
                svnRepository.closeSession()
            }

            val fileMeta = repo.getMetaDataFromPath("nomsg.txt")
            val dirMeta = repo.getMetaDataFromPath("")

            fileMeta shouldNotBe null
            fileMeta!!.get("commitMessage").asString() shouldBe ""
            dirMeta shouldNotBe null
            dirMeta!!.get("data").get("nomsg.txt").get("commitMessage").asString() shouldBe ""
            dirMeta.get("data").get("nomsg.txt").get("msg").asString() shouldBe ""
        }
    }

    describe("getParentCommitOf()/move() 예외 방어 처리") {
        it("존재하지 않는 리비전(범위 밖)의 부모를 조회하면 getCommit()이 SVNException을 던지고, 이를 잡아 null을 반환해야 한다") {
            val repo = SvnRepository("owner46", "proj46", newTempBaseDir(), userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            // commitId=1000 -> getCommit("999") 호출 -> 리비전 999는 존재하지 않아 SVNException 발생 -> catch -> null
            repo.getParentCommitOf("1000") shouldBe null
        }

        it("목적지가 비어있지 않은 디렉토리이면 Files.move가 IOException을 던지고, 이를 잡아 false를 반환해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("owner47", "proj47", baseDir, userResolver)
            repo.create()
            commitFile(repo, "a.txt", "content", "커밋")

            val destDir = File(baseDir, "owner47/dest-not-empty")
            destDir.mkdirs()
            File(destDir, "occupied.txt").writeText("이미 점유된 파일")

            val moved = repo.move("owner47", "proj47", "owner47", "dest-not-empty")

            moved shouldBe false
        }
    }
})
