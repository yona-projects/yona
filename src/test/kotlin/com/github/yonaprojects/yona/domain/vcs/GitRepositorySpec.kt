package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.storage.file.WindowCacheConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream

// yona GitRepository.java(playRepository) 대응. GitRepository 자체는 "커밋 생성" API를 제공하지
// 않는다(실제 서비스에서는 git push/GitServlet을 통해 서버 밖에서 커밋이 쌓인다) — SvnRepositorySpec이
// SVNKit 저수준 커밋 에디터로 진짜 리비전을 만들어낸 것과 동일한 방식으로, 여기서는 JGit의 저수준
// ObjectInserter/TreeFormatter/CommitBuilder API를 직접 사용해 bare 저장소에 진짜 커밋을 쌓은 뒤,
// GitRepository의 각 메서드가 그 커밋들에 대해 올바르게 동작하는지 end-to-end로 검증한다.
class GitRepositorySpec : DescribeSpec({

    fun newTempBaseDir(): String = Files.createTempDirectory("yona-git-test").toFile().absolutePath

    val userResolver: (String?, String?) -> User? = { _, email ->
        if (email.isNullOrEmpty()) {
            null
        } else {
            val loginId = email.substringBefore("@")
            User(id = 1L, loginId = loginId, name = "테스트유저$loginId", email = email)
        }
    }

    fun openRepo(gitRepo: GitRepository): Repository =
        FileRepositoryBuilder().setGitDir(gitRepo.getDirectory()).build()

    // JGit의 Git.init() 기본 브랜치명은 이 머신의 전역 git 설정(init.defaultBranch, 예: "main")을
    // 따른다(JGit이 SystemReader로 사용자 gitconfig를 읽기 때문에 "master"로 하드코딩하면 환경에 따라
    // 깨진다). 커밋 픽스처를 쌓을 기본 브랜치명을 실제로 생성해보고 확인해 동적으로 사용한다.
    val defaultBranchRef: String = run {
        val probeRepo = GitRepository("probe-owner-xyz", "probe-project-xyz", newTempBaseDir(), userResolver)
        probeRepo.create()
        val branch = openRepo(probeRepo).use { it.fullBranch } ?: "refs/heads/master"
        probeRepo.delete()
        branch
    }

    // path -> (내용, 파일모드) 맵으로부터 git 트리 오브젝트를 재귀적으로 만든다(중첩 디렉토리 지원).
    // git 트리 엔트리 정렬 규칙(디렉토리는 이름 뒤에 '/'가 붙은 것처럼 정렬)을 그대로 따른다.
    fun insertTree(inserter: ObjectInserter, entries: Map<String, Pair<ByteArray, FileMode>>): ObjectId {
        val directBlobs = linkedMapOf<String, Pair<ByteArray, FileMode>>()
        val subDirs = linkedMapOf<String, MutableMap<String, Pair<ByteArray, FileMode>>>()

        for ((path, value) in entries) {
            val slashIdx = path.indexOf('/')
            if (slashIdx < 0) {
                directBlobs[path] = value
            } else {
                val dirName = path.substring(0, slashIdx)
                val rest = path.substring(slashIdx + 1)
                subDirs.getOrPut(dirName) { linkedMapOf() }[rest] = value
            }
        }

        val names = (directBlobs.keys + subDirs.keys).sortedBy { name ->
            if (subDirs.containsKey(name)) "$name/" else name
        }

        val formatter = TreeFormatter()
        for (name in names) {
            if (subDirs.containsKey(name)) {
                val subTreeId = insertTree(inserter, subDirs.getValue(name))
                formatter.append(name, FileMode.TREE, subTreeId)
            } else {
                val (content, mode) = directBlobs.getValue(name)
                val blobId = inserter.insert(Constants.OBJ_BLOB, content)
                formatter.append(name, mode, blobId)
            }
        }
        return inserter.insert(formatter)
    }

    // 특정 브랜치(ref)에 대해 파일 상태를 누적해가며 실제 커밋을 쌓는 테스트 전용 빌더.
    // add/modify/delete/rename 등은 모두 이전 커밋의 파일 집합에 변경분을 반영해 새 트리를 만드는
    // 방식으로 표현한다(실제 git이 워킹트리 스테이징을 거쳐 커밋을 만드는 것과 최종 결과가 동일하다).
    class GitTestRepo(
        private val repo: Repository,
        private val branchRef: String,
        private val insertTreeFn: (ObjectInserter, Map<String, Pair<ByteArray, FileMode>>) -> ObjectId
    ) {
        private val files = linkedMapOf<String, Pair<ByteArray, FileMode>>()
        var lastCommit: RevCommit? = null

        fun put(path: String, content: String, mode: FileMode = FileMode.REGULAR_FILE): GitTestRepo {
            files[path] = content.toByteArray(StandardCharsets.UTF_8) to mode
            return this
        }

        fun putBytes(path: String, content: ByteArray, mode: FileMode = FileMode.REGULAR_FILE): GitTestRepo {
            files[path] = content to mode
            return this
        }

        fun remove(path: String): GitTestRepo {
            files.remove(path)
            return this
        }

        fun commit(message: String, author: String = "tester"): RevCommit {
            val inserter = repo.newObjectInserter()
            val treeId = insertTreeFn(inserter, files.toMap())
            val builder = CommitBuilder()
            builder.setTreeId(treeId)
            builder.message = message
            val ident = PersonIdent(author, "$author@example.com")
            builder.author = ident
            builder.committer = ident
            lastCommit?.let { builder.addParentId(it) }
            val commitId = inserter.insert(builder)
            inserter.flush()

            val revWalk = RevWalk(repo)
            val revCommit = revWalk.parseCommit(commitId)

            val refUpdate = repo.updateRef(branchRef)
            refUpdate.setNewObjectId(commitId)
            refUpdate.forceUpdate()

            lastCommit = revCommit
            return revCommit
        }
    }

    fun testRepo(repo: Repository, branchRef: String = defaultBranchRef) =
        GitTestRepo(repo, branchRef, ::insertTree)

    describe("create()/delete()/isEmpty()/getDirectory()") {
        it("create()를 호출하면 bare 저장소 디렉토리가 생성되고 초기에는 비어있다") {
            val repo = GitRepository("o1", "p1", newTempBaseDir(), userResolver)

            repo.create()

            repo.getDirectory().exists() shouldBe true
            repo.isEmpty() shouldBe true
        }

        it("이미 gitDir이 존재하는 상태에서 다시 create()를 호출해도 mkdirs()를 건너뛰고 정상 동작한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o1b", "p1b", baseDir, userResolver)

            repo.create()
            repo.create()

            repo.getDirectory().exists() shouldBe true
            repo.isEmpty() shouldBe true
        }

        // 사용자 요청 — 새 프로젝트 기본 브랜치를 "master" 대신 "main"으로. 위 defaultBranchRef 프로브가
        // 보여주듯 호스트 git의 init.defaultBranch에 맡기면 환경마다 달라지므로, defaultBranch
        // 생성자 인자로 애플리케이션이 결정론적으로 강제할 수 있어야 한다(RepositoryService가
        // yona.git.default-branch 설정값을 여기로 넘겨줌).
        it("defaultBranch 생성자 인자를 지정하면 그 이름으로 초기 브랜치가 만들어져야 한다") {
            val repo = GitRepository("o1c", "p1c", newTempBaseDir(), userResolver, defaultBranch = "main")

            repo.create()

            val fullBranch = openRepo(repo).use { it.fullBranch }
            fullBranch shouldBe "refs/heads/main"
        }

        it("커밋이 하나라도 있으면 isEmpty()가 false다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o2", "p2", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "hello").commit("첫 커밋")

            repo.isEmpty() shouldBe false
        }

        it("delete()를 호출하면 저장소 디렉토리가 사라진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o3", "p3", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "hello").commit("커밋")

            repo.delete()

            repo.getDirectory().exists() shouldBe false
        }

        it("delete()는 디렉토리가 없어도 예외 없이 아무 것도 하지 않는다") {
            val repo = GitRepository("o4", "no-such-proj", newTempBaseDir(), userResolver)

            repo.delete()

            repo.getDirectory().exists() shouldBe false
        }

        it("getDirectory()는 baseDir/owner/project.git 경로를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o5", "p5", baseDir, userResolver)

            repo.getDirectory() shouldBe File(File(baseDir), "o5/p5.git")
        }
    }

    describe("isIntermediateFolder()") {
        // legacy GitRepository.java의 isIntermediateFolder() 대응: 해당 경로가 폴더이고, 그 안의
        // 유일한 항목이 폴더 하나뿐이면(파일 없음, 자식 폴더 1개) "중간 폴더"로 true를 반환한다.
        // 코드 브라우저가 조상 경로들을 중첩 표시할 때 이런 중간 폴더 단계는 건너뛰기 위해 쓰인다.
        it("a/b/c/file.txt만 있으면(각 단계가 자식 폴더 하나뿐) a와 a/b는 중간 폴더로 true다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6", "p6", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a/b/c/file.txt", "content").commit("커밋")

            repo.isIntermediateFolder("a") shouldBe true
            repo.isIntermediateFolder("a/b") shouldBe true
        }

        it("a/b/c는 유일한 항목이 파일(file.txt)이므로 중간 폴더가 아니다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6b", "p6b", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a/b/c/file.txt", "content").commit("커밋")

            repo.isIntermediateFolder("a/b/c") shouldBe false
        }

        it("폴더 안에 파일과 폴더가 섞여 있으면 중간 폴더가 아니다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6c", "p6c", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo))
                .put("a/file1.txt", "content")
                .put("a/b/file2.txt", "content2")
                .commit("커밋")

            repo.isIntermediateFolder("a") shouldBe false
        }

        it("폴더 안에 자식 폴더가 2개 이상이면 중간 폴더가 아니다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6d", "p6d", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo))
                .put("a/b1/file1.txt", "content")
                .put("a/b2/file2.txt", "content2")
                .commit("커밋")

            repo.isIntermediateFolder("a") shouldBe false
        }

        it("루트(\"\")는 항상 false다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6e", "p6e", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a/b/file.txt", "content").commit("커밋")

            repo.isIntermediateFolder("") shouldBe false
        }

        it("존재하지 않는 경로는 false다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6f", "p6f", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a/b/file.txt", "content").commit("커밋")

            repo.isIntermediateFolder("no-such/path") shouldBe false
        }

        it("파일 경로(폴더가 아님)는 false다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o6g", "p6g", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a/b/file.txt", "content").commit("커밋")

            repo.isIntermediateFolder("a/b/file.txt") shouldBe false
        }

        it("커밋이 없는 빈 저장소에서는 false다") {
            val repo = GitRepository("o6h", "p6h", newTempBaseDir(), userResolver)
            repo.create()

            repo.isIntermediateFolder("any/path") shouldBe false
        }
    }

    describe("getMetaDataFromPath() / treeAsJson() / fileAsJson()") {
        it("빈 경로(\"\")로 조회하면 루트 폴더의 하위 항목 목록(type=folder)을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o7", "p7", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo))
                .put("readme.txt", "readme")
                .put("src/main.txt", "content")
                .commit("초기 커밋", author = "alice")

            val meta = repo.getMetaDataFromPath("")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "folder"
            val data = meta.get("data")
            data.get("readme.txt").get("type").asString() shouldBe "file"
            data.get("src").get("type").asString() shouldBe "folder"
            data.get("readme.txt").get("author").asString() shouldBe "alice"
            data.get("readme.txt").get("commitMessage").asString() shouldBe "초기 커밋"
            data.get("readme.txt").get("size").asLong() shouldBe "readme".toByteArray().size.toLong()
        }

        it("파일 경로로 조회하면 type=file과 실제 내용을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o8", "p8", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "Hello Git").commit("커밋", author = "bob")

            val meta = repo.getMetaDataFromPath("a.txt")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "file"
            meta.get("data").asString() shouldBe "Hello Git"
            meta.get("isBinary").asBoolean() shouldBe false
            meta.get("author").asString() shouldBe "bob"
            meta.get("userLoginId").asString() shouldBe "bob"
        }

        it("검색 대상 경로보다 먼저 정렬되는 파일(서브트리 아님)을 건너뛰고 목표 경로를 찾는다") {
            // getMetaDataFromPath()의 탐색 루프(while(treeWalk.next())) 안에서
            // "else if (treeWalk.isSubtree)"가 false인 경우(찾는 경로가 아닌 일반 파일)를 건너뛰는
            // 분기를 검증한다. "a.txt"가 "target.txt"보다 먼저 정렬되므로 먼저 방문된다.
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o9b", "p9b", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "not the target").put("target.txt", "found me").commit("커밋")

            val meta = repo.getMetaDataFromPath("target.txt")

            meta shouldNotBe null
            meta!!.get("data").asString() shouldBe "found me"
        }

        it("git 커밋 작성자가 yona 사용자와 매칭되지 않으면(userResolver가 null) author 필드는 채워지고 user 관련 필드는 빈 문자열이다") {
            val baseDir = newTempBaseDir()
            val neverResolves: (String?, String?) -> User? = { _, _ -> null }
            val repo = GitRepository("o9c", "p9c", baseDir, neverResolves)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").put("dir/b.txt", "content2").commit("외부 기여자 커밋", author = "outsider")

            val fileMeta = repo.getMetaDataFromPath("a.txt")
            val folderMeta = repo.getMetaDataFromPath("")

            fileMeta!!.get("author").asString() shouldBe "outsider"
            fileMeta.get("userName").asString() shouldBe ""
            fileMeta.get("userLoginId").asString() shouldBe ""
            folderMeta!!.get("data").get("a.txt").get("userName").asString() shouldBe ""
            folderMeta.get("data").get("a.txt").get("userLoginId").asString() shouldBe ""
        }

        it("중첩된 디렉토리(2단계 이상) 안의 파일 경로도 조회할 수 있다 - 검색 도중 여러 번 enterSubtree된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o9", "p9", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("src/sub/deep.txt", "deep content").commit("커밋")

            val meta = repo.getMetaDataFromPath("src/sub/deep.txt")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "file"
            meta.get("data").asString() shouldBe "deep content"
        }

        it("중첩된 디렉토리 경로 자체를 조회하면 type=folder를 반환한다(검색 후 enterSubtree)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o10", "p10", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("src/sub/deep.txt", "deep content").commit("커밋")

            val meta = repo.getMetaDataFromPath("src/sub")

            meta shouldNotBe null
            meta!!.get("type").asString() shouldBe "folder"
            meta.get("data").get("deep.txt").get("type").asString() shouldBe "file"
        }

        it("존재하지 않는 경로는 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o11", "p11", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getMetaDataFromPath("no-such-file.txt") shouldBe null
        }

        it("branch 파라미터가 빈 문자열이면 HEAD와 동일하게 취급한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o12", "p12", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            val viaEmpty = repo.getMetaDataFromPath("", "a.txt")
            val viaHead = repo.getMetaDataFromPath("HEAD", "a.txt")

            viaEmpty!!.get("data").asString() shouldBe viaHead!!.get("data").asString()
        }

        it("존재하지 않는 브랜치명을 지정하면 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o13", "p13", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getMetaDataFromPath("refs/heads/no-such-branch", "a.txt") shouldBe null
        }

        it("특정 브랜치를 지정하면 그 브랜치 시점의 내용을 반환한다(master와 다른 내용)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o14", "p14", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val master = testRepo(jgitRepo)
            master.put("a.txt", "master content").commit("master 커밋")
            val feature = GitTestRepo(jgitRepo, "refs/heads/feature", ::insertTree)
            feature.lastCommit = master.lastCommit
            feature.put("a.txt", "feature content").commit("feature 커밋")

            val onMaster = repo.getMetaDataFromPath(defaultBranchRef, "a.txt")
            val onFeature = repo.getMetaDataFromPath("refs/heads/feature", "a.txt")

            onMaster!!.get("data").asString() shouldBe "master content"
            onFeature!!.get("data").asString() shouldBe "feature content"
        }

        it("한 파일에 여러 커밋이 있으면 가장 최근 커밋 정보를 반환한다(git log maxCount=1)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o15", "p15", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val builder = testRepo(jgitRepo)
            builder.put("a.txt", "v1").commit("첫 번째 수정", author = "alice")
            builder.put("a.txt", "v2").commit("두 번째 수정", author = "bob")

            val meta = repo.getMetaDataFromPath("a.txt")

            meta!!.get("commitMessage").asString() shouldBe "두 번째 수정"
            meta.get("author").asString() shouldBe "bob"
            meta.get("data").asString() shouldBe "v2"
        }

        it("바이너리 내용(0바이트 포함)은 isBinary=true이고 data는 빈 문자열이다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o16", "p16", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).putBytes("bin.dat", byteArrayOf(1, 2, 0, 3)).commit("바이너리 커밋")

            val meta = repo.getMetaDataFromPath("bin.dat")

            meta!!.get("isBinary").asBoolean() shouldBe true
            meta.get("data").asString() shouldBe ""
        }

        it("MAX_FILE_SIZE_CAN_BE_VIEWED를 초과하는 파일은 0바이트가 없어도 isBinary=true다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o17", "p17", baseDir, userResolver)
            repo.create()
            val bigContent = "a".repeat((PlayRepository.MAX_FILE_SIZE_CAN_BE_VIEWED + 10).toInt())
            testRepo(openRepo(repo)).put("big.txt", bigContent).commit("큰 파일 커밋")

            val meta = repo.getMetaDataFromPath("big.txt")

            meta!!.get("isBinary").asBoolean() shouldBe true
            meta.get("size").asLong() shouldBe bigContent.toByteArray().size.toLong()
        }
    }

    describe("getRawFile()") {
        it("HEAD 리비전의 파일 raw 바이트를 정확히 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o18", "p18", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "raw content here").commit("커밋")

            val bytes = repo.getRawFile("HEAD", "a.txt")

            String(bytes, StandardCharsets.UTF_8) shouldBe "raw content here"
        }

        it("과거 커밋을 지정하면 그 시점의 파일 내용을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o19", "p19", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            val commit1 = builder.put("a.txt", "old content").commit("v1")
            builder.put("a.txt", "new content").commit("v2")

            val bytes = repo.getRawFile(commit1.name, "a.txt")

            String(bytes, StandardCharsets.UTF_8) shouldBe "old content"
        }

        it("존재하지 않는 리비전을 조회하면 FileNotFoundException을 던진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o20", "p20", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            try {
                repo.getRawFile("no-such-rev", "a.txt")
                throw AssertionError("FileNotFoundException이 발생해야 한다")
            } catch (e: FileNotFoundException) {
                // expected
            }
        }

        it("존재하지 않는 파일 경로를 조회하면 FileNotFoundException을 던진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o21", "p21", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            try {
                repo.getRawFile("HEAD", "no-such.txt")
                throw AssertionError("FileNotFoundException이 발생해야 한다")
            } catch (e: FileNotFoundException) {
                // expected
            }
        }
    }

    describe("getPatch()") {
        it("commitId 하나로 호출하면 부모 커밋과의 diff를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o22", "p22", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "line1\nline2\n").commit("첫 커밋")
            val commit2 = builder.put("a.txt", "line1\nline2-changed\n").commit("수정 커밋")

            val patch = repo.getPatch(commit2.name)

            patch shouldContain "-line2"
            patch shouldContain "+line2-changed"
        }

        it("부모가 없는 최초 커밋을 조회하면 빈 트리와의 diff(전체 추가)를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o23", "p23", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "hello\n").commit("최초 커밋")

            val patch = repo.getPatch(commit1.name)

            patch shouldContain "+hello"
        }

        it("존재하지 않는 commitId는 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o24", "p24", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getPatch("no-such-rev") shouldBe ""
        }

        it("두 리비전을 지정하면 그 사이의 diff를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o25", "p25", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            val commit1 = builder.put("a.txt", "original\n").commit("v1")
            val commit2 = builder.put("a.txt", "changed\n").commit("v2")

            val patch = repo.getPatch(commit1.name, commit2.name)

            patch shouldContain "-original"
            patch shouldContain "+changed"
        }

        it("revA가 존재하지 않으면 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o26", "p26", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getPatch("no-such-rev", commit1.name) shouldBe ""
        }

        it("revB가 존재하지 않으면 빈 문자열을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o27", "p27", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getPatch(commit1.name, "no-such-rev") shouldBe ""
        }
    }

    describe("getDiff() - commitId 단일/두 리비전/크로스 리포지토리") {
        it("commitId 하나로 호출하면 부모와의 FileDiff 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o28", "p28", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "line1\n").commit("첫 커밋")
            val commit2 = builder.put("a.txt", "line1\nline2\n").commit("수정 커밋")

            val diffs = repo.getDiff(commit2.name)

            diffs.size shouldBe 1
            val fileDiff = diffs[0] as FileDiff
            fileDiff.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fileDiff.commitB shouldBe commit2.name
        }

        it("부모가 없는 최초 커밋을 조회하면 전체가 ADD로 표시된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o29", "p29", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "hello\n").commit("최초 커밋")

            val diffs = repo.getDiff(commit1.name)

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.ADD
        }

        it("존재하지 않는 commitId는 빈 리스트를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o30", "p30", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getDiff("no-such-rev") shouldBe emptyList()
        }

        it("두 리비전을 지정하면 그 사이의 FileDiff 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o31", "p31", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            val commit1 = builder.put("a.txt", "v1\n").commit("v1")
            val commit2 = builder.put("a.txt", "v2\n").commit("v2")

            val diffs = repo.getDiff(commit1.name, commit2.name)

            diffs.size shouldBe 1
        }

        it("revA가 존재하지 않으면 빈 트리와의 diff(ADD)로 처리된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o32", "p32", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "content\n").commit("커밋")

            val diffs = repo.getDiff("no-such-rev", commit1.name)

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.ADD
        }

        it("revB가 존재하지 않으면 빈 트리와의 diff(DELETE)로 처리된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o33", "p33", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "content\n").commit("커밋")

            val diffs = repo.getDiff(commit1.name, "no-such-rev")

            diffs.size shouldBe 1
            (diffs[0] as FileDiff).changeType shouldBe DiffEntry.ChangeType.DELETE
        }

        it("서로 다른 두 GitRepository(fork) 사이의 diff를 계산할 수 있다") {
            val baseDirA = newTempBaseDir()
            val repoA = GitRepository("o34a", "pA", baseDirA, userResolver)
            repoA.create()
            val commitA = testRepo(openRepo(repoA)).put("a.txt", "from A\n").commit("A의 커밋")

            val baseDirB = newTempBaseDir()
            val repoB = GitRepository("o34b", "pB", baseDirB, userResolver)
            repoB.create()
            val commitB = testRepo(openRepo(repoB)).put("a.txt", "from B\n").commit("B의 커밋")

            val diffs = repoA.getDiff(commitA.name, repoB, commitB.name)

            diffs.size shouldBe 1
            diffs[0].commitA shouldBe commitA.name
            diffs[0].commitB shouldBe commitB.name
            diffs[0].changeType shouldBe DiffEntry.ChangeType.MODIFY
        }
    }

    describe("getFileDiffs() - 파일별 diff 상세(getDiff를 통해 검증)") {
        it("새 파일 추가(ADD)는 pathB/사이즈/텍스트 내용이 채워진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o35", "p35", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("new.txt", "added content\n").commit("추가")

            val diffs = repo.getDiff(commit1.name)

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.ADD
            fd.pathB shouldBe "new.txt"
            fd.pathA shouldBe null
            fd.isBinaryB shouldBe false
            fd.b shouldNotBe null
        }

        it("파일 수정(MODIFY)은 editList와 hunks가 계산된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o36", "p36", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", (0..9).joinToString("\n") { "line$it" } + "\n").commit("v1")
            val commit2 = builder.put("a.txt", (0..9).joinToString("\n") { if (it == 5) "CHANGED" else "line$it" } + "\n")
                .commit("v2")

            val diffs = repo.getDiff(commit2.name)

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fd.editList shouldNotBe null
            fd.getHunks() shouldNotBe null
        }

        it("이전 커밋(treeA != null)이 있는 상태에서 새 파일을 추가하면 그 파일 항목은 ADD로 표시된다") {
            // getFileDiffs 내부의 "treeA != null && changeType in [DELETE,MODIFY,RENAME,COPY]" 검사에서
            // treeA(이전 트리)는 존재하지만 이 특정 파일의 changeType은 ADD(그 목록에 없음)인 조합을
            // 검증한다 - 최초 커밋(treeA == null)에서의 ADD와는 다른 경로다.
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o36b", "p36b", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("existing.txt", "v1\n").commit("v1")
            val commit2 = builder.put("existing.txt", "v2\n").put("new.txt", "brand new\n").commit("v2 + 새 파일")

            val diffs = repo.getDiff(commit2.name)

            diffs.size shouldBe 2
            val added = diffs.first { (it as FileDiff).pathB == "new.txt" } as FileDiff
            added.changeType shouldBe DiffEntry.ChangeType.ADD
            added.pathA shouldBe null
        }

        it("파일 삭제(DELETE)는 pathA만 채워지고 pathB는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o37", "p37", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "content\n").commit("추가")
            val commit2 = builder.remove("a.txt").commit("삭제")

            val diffs = repo.getDiff(commit2.name)

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.DELETE
            fd.pathA shouldBe "a.txt"
            fd.pathB shouldBe null
            fd.a shouldNotBe null
        }

        it("내용이 거의 동일한 파일을 옮기면(60% 이상 유사) RENAME으로 감지된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o38", "p38", baseDir, userResolver)
            repo.create()
            val content = (0..9).joinToString("\n") { "line$it" } + "\n"
            val builder = testRepo(openRepo(repo))
            builder.put("old.txt", content).commit("추가")
            val commit2 = builder.remove("old.txt").put("new.txt", content + "line10\n").commit("이름변경")

            val diffs = repo.getDiff(commit2.name)

            diffs.size shouldBe 1
            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.RENAME
            fd.pathA shouldBe "old.txt"
            fd.pathB shouldBe "new.txt"
        }

        it("동일한 삭제 원본을 두 개의 새 파일이 공유하면 하나는 RENAME, 나머지는 COPY로 감지될 수 있다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o39", "p39", baseDir, userResolver)
            repo.create()
            val content = "identical shared content\n"
            val builder = testRepo(openRepo(repo))
            builder.put("orig.txt", content).commit("추가")
            val commit2 = builder.remove("orig.txt")
                .put("renamed.txt", content)
                .put("copied.txt", content)
                .commit("이름변경+복사")

            val diffs = repo.getDiff(commit2.name)

            val changeTypes = diffs.map { (it as FileDiff).changeType }
            // RenameDetector는 동일한 삭제 소스를 여러 추가 파일이 공유할 때 하나는 RENAME, 나머지는
            // COPY로 표시한다(실제로 이 리포지토리 설정에서 그렇게 동작하는지 여기서 직접 검증한다).
            changeTypes.contains(DiffEntry.ChangeType.RENAME) shouldBe true
            (changeTypes.contains(DiffEntry.ChangeType.COPY) || changeTypes.count { it == DiffEntry.ChangeType.ADD } == 1) shouldBe true
        }

        it("바이너리 파일 추가는 isBinaryB=true이고 b는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o40", "p40", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).putBytes("bin.dat", byteArrayOf(1, 2, 0, 3)).commit("바이너리 추가")

            val diffs = repo.getDiff(commit1.name)

            val fd = diffs[0] as FileDiff
            fd.isBinaryB shouldBe true
            fd.b shouldBe null
        }

        it("바이너리 파일 수정은 isBinaryA/isBinaryB 모두 true이고 hunks 계산을 건너뛴다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o41", "p41", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.putBytes("bin.dat", byteArrayOf(1, 2, 0, 3)).commit("바이너리 추가")
            val commit2 = builder.putBytes("bin.dat", byteArrayOf(9, 9, 0, 9, 9)).commit("바이너리 수정")

            val diffs = repo.getDiff(commit2.name)

            val fd = diffs[0] as FileDiff
            fd.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fd.isBinaryA shouldBe true
            fd.isBinaryB shouldBe true
            fd.a shouldBe null
            fd.b shouldBe null
            fd.editList shouldBe null
        }

        it("바이너리 파일 삭제는 isBinaryA=true이고 a는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o42", "p42", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.putBytes("bin.dat", byteArrayOf(1, 2, 0, 3)).commit("바이너리 추가")
            val commit2 = builder.remove("bin.dat").commit("바이너리 삭제")

            val diffs = repo.getDiff(commit2.name)

            val fd = diffs[0] as FileDiff
            fd.isBinaryA shouldBe true
            fd.a shouldBe null
        }

        it("누적 변경 라인 수가 diffLineLimit(20000)을 초과하면 이후 파일에 OTHERS_SIZE_EXCEEDED가 기록된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o43", "p43", baseDir, userResolver)
            repo.create()
            val hugeContent = (1..20001).joinToString("\n") { "l" } + "\n"
            val commit1 = testRepo(openRepo(repo))
                .put("huge.txt", hugeContent)
                .put("small.txt", "small\n")
                .commit("거대한 파일 + 작은 파일 동시 추가")

            val diffs = repo.getDiff(commit1.name)

            diffs.size shouldBe 2
            val bySmall = diffs.first { (it as FileDiff).pathB == "small.txt" } as FileDiff
            bySmall.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) shouldBe true
        }

        it("누적 diff 문자 수가 diffSizeLimit(1,000,000)을 초과하면 이후 파일에 OTHERS_SIZE_EXCEEDED가 기록된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o44", "p44", baseDir, userResolver)
            repo.create()
            val hugeLine = "x".repeat(1_000_001)
            val commit1 = testRepo(openRepo(repo))
                .put("huge.txt", hugeLine)
                .put("small.txt", "small\n")
                .commit("거대한 한 줄 파일 + 작은 파일 동시 추가")

            val diffs = repo.getDiff(commit1.name)

            val bySmall = diffs.first { (it as FileDiff).pathB == "small.txt" } as FileDiff
            bySmall.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) shouldBe true
        }

        it("변경 파일 수가 diffFileLimit(1000)을 초과하면 그 이후는 결과에서 잘린다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o45", "p45", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            for (i in 0 until 1002) {
                builder.put("f$i.txt", "x")
            }
            val commit1 = builder.commit("1002개 파일 동시 추가")

            val diffs = repo.getDiff(commit1.name)

            // diffFileLimit=1000: result.size > 1000이 되는 순간(1002번째 항목) break되므로 최종 1001개만 남는다.
            diffs.size shouldBe 1001
        }

        it("LargeObjectException 발생 시(old 내용이 스트리밍 임계값을 초과) A_SIZE_EXCEEDED가 기록된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o46", "p46", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("big.txt", "b".repeat(500)).commit("큰 내용으로 추가")
            val commit2 = builder.put("big.txt", "small").commit("작은 내용으로 수정")

            try {
                val cfg = WindowCacheConfig()
                cfg.streamFileThreshold = 100
                cfg.install()

                val diffs = repo.getDiff(commit2.name)

                val fd = diffs[0] as FileDiff
                fd.changeType shouldBe DiffEntry.ChangeType.MODIFY
                fd.hasError(FileDiff.Error.A_SIZE_EXCEEDED) shouldBe true
                fd.a shouldBe null
            } finally {
                // 전역 WindowCache 설정이므로 다른 테스트/전체 회귀에 영향 주지 않도록 기본값으로 복원한다.
                WindowCacheConfig().install()
            }
        }

        it("LargeObjectException 발생 시(new 내용이 스트리밍 임계값을 초과) B_SIZE_EXCEEDED가 기록된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o47", "p47", baseDir, userResolver)
            repo.create()

            try {
                val cfg = WindowCacheConfig()
                cfg.streamFileThreshold = 100
                cfg.install()

                val commit1 = testRepo(openRepo(repo)).put("big.txt", "b".repeat(500)).commit("큰 파일 추가")
                val diffs = repo.getDiff(commit1.name)

                val fd = diffs[0] as FileDiff
                fd.changeType shouldBe DiffEntry.ChangeType.ADD
                fd.hasError(FileDiff.Error.B_SIZE_EXCEEDED) shouldBe true
                fd.b shouldBe null
            } finally {
                WindowCacheConfig().install()
            }
        }
    }

    describe("getHistory()") {
        it("여러 커밋을 만들면 최신순으로 정확한 메시지/작성자를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o48", "p48", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "v1").commit("첫 번째 커밋", author = "alice")
            builder.put("a.txt", "v2").commit("두 번째 커밋", author = "bob")
            builder.put("b.txt", "new file").commit("세 번째 커밋", author = "alice")

            val history = repo.getHistory(0, 25, null, null)

            history.size shouldBe 3
            history[0].getMessage() shouldContain "세 번째 커밋"
            history[1].getMessage() shouldContain "두 번째 커밋"
            history[2].getMessage() shouldContain "첫 번째 커밋"
        }

        it("pageNum/pageSize로 페이지네이션할 수 있다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o49", "p49", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            repeat(5) { i -> builder.put("a.txt", "v$i").commit("커밋$i") }

            val page0 = repo.getHistory(0, 2, null, null)
            val page1 = repo.getHistory(1, 2, null, null)

            page0.size shouldBe 2
            page1.size shouldBe 2
            page0[0].getId() shouldNotBe page1[0].getId()
        }

        it("path를 지정하면 그 경로를 건드린 커밋만 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o50", "p50", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "v1").commit("a 파일 커밋")
            builder.put("b.txt", "v1").commit("b 파일 커밋")

            val history = repo.getHistory(0, 25, null, "a.txt")

            history.size shouldBe 1
            history[0].getMessage() shouldContain "a 파일 커밋"
        }

        it("path가 빈 문자열이면 필터링하지 않는다(전체 히스토리)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o51", "p51", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "v1").commit("a 커밋")
            builder.put("b.txt", "v1").commit("b 커밋")

            repo.getHistory(0, 25, null, "").size shouldBe 2
        }

        it("untilRev를 지정하면 그 커밋부터 거슬러 올라간 히스토리를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o52", "p52", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            val commit1 = builder.put("a.txt", "v1").commit("첫 커밋")
            builder.put("a.txt", "v2").commit("두번째 커밋")

            val history = repo.getHistory(0, 25, commit1.name, null)

            history.size shouldBe 1
            history[0].getId() shouldBe commit1.name
        }

        it("untilRev가 존재하지 않는 리비전이어도 예외 없이 HEAD 기준 히스토리로 처리된다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o53", "p53", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋")

            val history = repo.getHistory(0, 25, "no-such-rev", null)

            history.size shouldBe 1
        }

        // annotated 태그는 ref가 커밋이 아니라 별도의 태그 오브젝트를 가리켜, peel(커밋까지 따라가기)
        // 없이 LogCommand에 그대로 넘기면 IncorrectObjectTypeException("not a commit")이 난다 —
        // 실제 서버(코드 브라우저 커밋 히스토리 화면)에서 500으로 재현된 실사용 버그.
        it("untilRev로 annotated 태그 이름을 지정해도 500 없이 그 커밋부터 거슬러 올라간 히스토리를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o54", "p54", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "v1").commit("첫 커밋")
            testRepo(openRepo(repo)).put("a.txt", "v2").commit("두번째 커밋")
            repo.createTag("v1.0.0", commit1.name, message = "릴리즈", taggerName = "alice", taggerEmail = "alice@example.com")

            val history = repo.getHistory(0, 25, "refs/tags/v1.0.0", null)

            history.size shouldBe 1
            history[0].getId() shouldBe commit1.name
        }

        it("untilRev로 lightweight 태그 이름을 지정해도 그 커밋부터 거슬러 올라간 히스토리를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o55", "p55", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "v1").commit("첫 커밋")
            testRepo(openRepo(repo)).put("a.txt", "v2").commit("두번째 커밋")
            repo.createTag("v1.0.1", commit1.name, message = null, taggerName = null, taggerEmail = null)

            val history = repo.getHistory(0, 25, "refs/tags/v1.0.1", null)

            history.size shouldBe 1
            history[0].getId() shouldBe commit1.name
        }
    }

    describe("getCommit()") {
        it("존재하는 커밋을 조회할 수 있다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o54", "p54", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋 메시지")

            val commit = repo.getCommit(commit1.name)

            commit shouldNotBe null
            commit!!.getMessage() shouldContain "커밋 메시지"
            commit.getId() shouldBe commit1.name
        }

        it("존재하지 않는 리비전은 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o55", "p55", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋")

            repo.getCommit("no-such-rev") shouldBe null
        }
    }

    describe("getRefNames()") {
        it("커밋이 없는 저장소는 빈 목록을 반환한다") {
            val repo = GitRepository("o56", "p56", newTempBaseDir(), userResolver)
            repo.create()

            repo.getRefNames() shouldBe emptyList()
        }

        it("여러 브랜치가 있으면 refs/heads/* 이름 목록을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o57", "p57", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val master = testRepo(jgitRepo)
            master.put("a.txt", "v1").commit("master 커밋")
            val feature = GitTestRepo(jgitRepo, "refs/heads/feature", ::insertTree)
            feature.lastCommit = master.lastCommit
            feature.put("a.txt", "v2").commit("feature 커밋")

            val refNames = repo.getRefNames()

            refNames.contains(defaultBranchRef) shouldBe true
            refNames.contains("refs/heads/feature") shouldBe true
        }
    }

    describe("isFile()") {
        it("파일 경로는 true를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o58", "p58", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("dir/a.txt", "content").commit("커밋")

            repo.isFile("dir/a.txt") shouldBe true
        }

        it("디렉토리 경로는 false를 반환한다(isSubtree)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o59", "p59", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("dir/a.txt", "content").commit("커밋")

            repo.isFile("dir") shouldBe false
        }

        it("존재하지 않는 리비전 문자열을 받는 오버로드는 false를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o60", "p60", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.isFile("a.txt", "no-such-rev") shouldBe false
        }

        it("존재하지 않는 경로는 false를 반환한다(treeWalk null)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o61", "p61", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.isFile("no-such.txt", "HEAD") shouldBe false
        }
    }

    describe("renameTo() / move()") {
        it("move()는 저장소 디렉토리를 새 owner/name 위치로 옮긴다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o62", "old-name", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            val moved = repo.move("o62", "old-name", "o62", "new-name")

            moved shouldBe true
            repo.getDirectory().exists() shouldBe false
            val movedRepo = GitRepository("o62", "new-name", baseDir, userResolver)
            movedRepo.getDirectory().exists() shouldBe true
            movedRepo.isEmpty() shouldBe false
        }

        it("목적지 owner 디렉토리가 아직 없으면 move()가 mkdirs()로 새로 만든다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o62b", "old-name", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            val moved = repo.move("o62b", "old-name", "brand-new-owner", "new-name")

            moved shouldBe true
            val movedRepo = GitRepository("brand-new-owner", "new-name", baseDir, userResolver)
            movedRepo.getDirectory().exists() shouldBe true
        }

        it("renameTo()는 move()에 위임해 같은 owner 아래에서 이름만 바꾼다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o63", "before-rename", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            val renamed = repo.renameTo("after-rename")

            renamed shouldBe true
            val renamedRepo = GitRepository("o63", "after-rename", baseDir, userResolver)
            renamedRepo.getDirectory().exists() shouldBe true
        }

        it("src가 존재하지 않으면 아무 것도 하지 않고 true를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o64", "no-such-src", baseDir, userResolver)

            val moved = repo.move("o64", "no-such-src", "o64", "dest")

            moved shouldBe true
        }

        it("목적지 경로에 IO 오류가 발생하면 false를 반환한다(부모 경로가 파일로 막혀있는 경우)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o65", "src-proj", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            // dest의 부모 디렉토리가 되어야 할 경로에 일반 파일을 미리 만들어 mkdirs/move가 실패하게 만든다.
            val blockingFile = File(File(baseDir), "blocked-owner")
            blockingFile.parentFile.mkdirs()
            blockingFile.writeText("i am a file, not a directory")

            val moved = repo.move("o65", "src-proj", "blocked-owner", "dest-proj")

            moved shouldBe false
            repo.getDirectory().exists() shouldBe true
        }
    }

    describe("getDefaultBranch() / setDefaultBranch()") {
        it("갓 생성된 저장소는 기본적으로 이 환경의 git init.defaultBranch 설정을 따르는 브랜치를 가리킨다") {
            val repo = GitRepository("o66", "p66", newTempBaseDir(), userResolver)
            repo.create()

            repo.getDefaultBranch() shouldBe defaultBranchRef
        }

        it("HEAD 파일이 없으면(비정상 상태) 하드코딩된 fallback인 refs/heads/master를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o67", "p67", baseDir, userResolver)
            repo.create()
            File(repo.getDirectory(), "HEAD").delete()

            repo.getDefaultBranch() shouldBe "refs/heads/master"
        }

        it("setDefaultBranch()에 refs/ 접두사 없는 이름을 주면 refs/heads/<name>으로 변환해 HEAD를 옮긴다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o68", "p68", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val master = testRepo(jgitRepo)
            master.put("a.txt", "v1").commit("master 커밋")
            val feature = GitTestRepo(jgitRepo, "refs/heads/feature", ::insertTree)
            feature.lastCommit = master.lastCommit
            feature.put("a.txt", "v2").commit("feature 커밋")

            repo.setDefaultBranch("feature")

            repo.getDefaultBranch() shouldBe "refs/heads/feature"
        }

        it("setDefaultBranch()에 refs/로 시작하는 전체 경로를 주면 그대로 사용한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o69", "p69", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val master = testRepo(jgitRepo)
            master.put("a.txt", "v1").commit("master 커밋")
            val feature = GitTestRepo(jgitRepo, "refs/heads/feature2", ::insertTree)
            feature.lastCommit = master.lastCommit
            feature.put("a.txt", "v2").commit("feature2 커밋")

            repo.setDefaultBranch("refs/heads/feature2")

            repo.getDefaultBranch() shouldBe "refs/heads/feature2"
        }
    }

    describe("getBranches()") {
        it("커밋이 없으면 빈 목록을 반환한다") {
            val repo = GitRepository("o70", "p70", newTempBaseDir(), userResolver)
            repo.create()

            repo.getBranches() shouldBe emptyList()
        }

        it("여러 브랜치가 있으면 각 브랜치의 최신 커밋/작성자 정보를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o71", "p71", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val master = testRepo(jgitRepo)
            master.put("a.txt", "v1").commit("master 커밋", author = "alice")
            val feature = GitTestRepo(jgitRepo, "refs/heads/feature", ::insertTree)
            feature.lastCommit = master.lastCommit
            feature.put("a.txt", "v2").commit("feature 커밋", author = "bob")

            val branches = repo.getBranches()

            branches.size shouldBe 2
            val featureBranch = branches.first { it.name == "refs/heads/feature" }
            featureBranch.headCommit.getMessage() shouldContain "feature 커밋"
            featureBranch.user?.loginId shouldBe "bob"
            featureBranch.shortName shouldBe "feature"
        }
    }

    describe("getHeadBranch()") {
        it("커밋이 하나도 없는 저장소는 null을 반환한다(unborn HEAD, targetRef objectId 없음)") {
            val repo = GitRepository("o72", "p72", newTempBaseDir(), userResolver)
            repo.create()

            repo.getHeadBranch() shouldBe null
        }

        it("HEAD 파일 자체가 없으면 null을 반환한다(headRef null)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o73", "p73", baseDir, userResolver)
            repo.create()
            File(repo.getDirectory(), "HEAD").delete()

            repo.getHeadBranch() shouldBe null
        }

        it("정상적인 심볼릭 HEAD는 해당 브랜치 정보를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o74", "p74", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋", author = "carol")

            val headBranch = repo.getHeadBranch()

            headBranch shouldNotBe null
            headBranch!!.name shouldBe defaultBranchRef
            headBranch.user?.loginId shouldBe "carol"
        }

        it("detached HEAD(커밋을 직접 가리킴)는 심볼릭이 아닌 ref 자체 정보를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o75", "p75", baseDir, userResolver)
            repo.create()
            val jgitRepo = openRepo(repo)
            val commit1 = testRepo(jgitRepo).put("a.txt", "v1").commit("커밋")

            val refUpdate = jgitRepo.updateRef(Constants.HEAD, true)
            refUpdate.setNewObjectId(commit1.id)
            refUpdate.forceUpdate()

            val headBranch = repo.getHeadBranch()

            headBranch shouldNotBe null
            headBranch!!.headCommit.getId() shouldBe commit1.name
        }
    }

    describe("deleteBranch() / createBranch()") {
        it("createBranch()로 새 브랜치를 만들면 getBranches()에 나타난다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o76", "p76", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.createBranch("feature-x", defaultBranchRef)

            repo.getBranches().any { it.shortName == "feature-x" } shouldBe true
        }

        it("createBranch()는 refs/heads/ 접두사를 제거하고 브랜치 이름을 사용한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o77", "p77", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.createBranch("refs/heads/feature-y", defaultBranchRef)

            repo.getBranches().any { it.shortName == "feature-y" } shouldBe true
        }

        it("deleteBranch()로 브랜치를 삭제하면 getBranches()에서 사라진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o78", "p78", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")
            repo.createBranch("to-delete", defaultBranchRef)

            repo.deleteBranch("to-delete")

            repo.getBranches().any { it.shortName == "to-delete" } shouldBe false
        }
    }

    // yona-wiki P3-10 — getBranches()/deleteBranch()/createBranch()와 동일한 패턴의 git 태그 지원.
    describe("getTagNames() / getTags() / deleteTag() / createTag()") {
        it("태그가 없으면 getTagNames()/getTags() 모두 빈 목록을 반환한다") {
            val repo = GitRepository("o90", "p90", newTempBaseDir(), userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.getTagNames() shouldBe emptyList()
            repo.getTags() shouldBe emptyList()
        }

        it("createTag()에 message를 주지 않으면 lightweight 태그가 만들어지고 getTagNames()에 refs/tags/* 이름으로 나타난다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o91", "p91", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.createTag("v1.0", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            repo.getTagNames() shouldBe listOf("refs/tags/v1.0")
        }

        it("createTag()는 refs/tags/ 접두사를 제거하고 태그 이름을 사용한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o92", "p92", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.createTag("refs/tags/v2.0", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            repo.getTagNames() shouldBe listOf("refs/tags/v2.0")
        }

        it("lightweight 태그는 getTags()에서 annotated=false, tagger는 커밋 작성자로 채워지고 message는 null이다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o93", "p93", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋", author = "alice")

            repo.createTag("v1.0", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            val tags = repo.getTags()
            tags.size shouldBe 1
            val tag = tags.first()
            tag.shortName shouldBe "v1.0"
            tag.annotated shouldBe false
            tag.message shouldBe null
            tag.tagger?.loginId shouldBe "alice"
            tag.targetCommit.getMessage() shouldContain "master 커밋"
        }

        it("createTag()에 message를 주면 annotated 태그가 만들어지고 getTags()에서 태거/메시지가 채워진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o94", "p94", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋", author = "alice")

            repo.createTag(
                "v2.0", defaultBranchRef,
                message = "릴리즈 메모", taggerName = "테스트유저bob", taggerEmail = "bob@example.com"
            )

            val tags = repo.getTags()
            tags.size shouldBe 1
            val tag = tags.first()
            tag.shortName shouldBe "v2.0"
            tag.annotated shouldBe true
            tag.message shouldBe "릴리즈 메모"
            tag.tagger?.loginId shouldBe "bob"
            // annotated 태그의 targetCommit은 태그 오브젝트가 아니라 그 태그가 최종적으로 가리키는
            // 커밋으로 peel되어야 한다(RevWalk.parseCommit()의 자동 peel 동작과 일관).
            tag.targetCommit.getMessage() shouldContain "master 커밋"
        }

        it("createTag()에 taggerName/taggerEmail을 주지 않으면 기본 identity(\"yona\"/\"yona@yona.io\")로 annotated 태그를 만든다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o95", "p95", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            repo.createTag("v3.0", defaultBranchRef, message = "메시지만", taggerName = null, taggerEmail = null)

            val tag = repo.getTags().first()
            tag.annotated shouldBe true
            tag.message shouldBe "메시지만"
            // userResolver는 이메일의 @ 앞부분을 loginId로 매칭하므로("yona@yona.io" -> "yona") 기본
            // identity도 실제 사용자처럼 조회된다 — 시스템 계정으로 특별 취급하지 않는다는 뜻.
            tag.tagger?.loginId shouldBe "yona"
        }

        it("존재하지 않는 시작점으로 createTag()를 호출하면 IllegalArgumentException을 던진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o96", "p96", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                repo.createTag("v1.0", "no-such-rev", message = null, taggerName = null, taggerEmail = null)
            }
        }

        it("deleteTag()로 태그를 삭제하면 getTagNames()/getTags()에서 사라진다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o97", "p97", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")
            repo.createTag("to-delete", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            repo.deleteTag("to-delete")

            repo.getTagNames() shouldBe emptyList()
            repo.getTags() shouldBe emptyList()
        }

        it("deleteTag()는 refs/tags/ 접두사를 제거하고 태그 이름을 사용한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o98", "p98", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")
            repo.createTag("to-delete-2", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            repo.deleteTag("refs/tags/to-delete-2")

            repo.getTagNames() shouldBe emptyList()
        }

        it("브랜치와 태그가 둘 다 있어도 getTagNames()는 태그만, getRefNames()는 브랜치만 반환한다(네임스페이스 분리)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o99", "p99", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("master 커밋")
            repo.createBranch("feature-z", defaultBranchRef)
            repo.createTag("v1.0", defaultBranchRef, message = null, taggerName = null, taggerEmail = null)

            repo.getRefNames().sorted() shouldBe listOf(defaultBranchRef, "refs/heads/feature-z").sorted()
            repo.getTagNames() shouldBe listOf("refs/tags/v1.0")
        }
    }

    describe("getParentCommitOf()") {
        it("부모가 있는 커밋은 부모 커밋의 id를 반환한다") {
            // 주의(실제 버그 발견): GitRepository.getParentCommitOf()는 commit.getParent(0)이 반환한
            // RevCommit을 그대로 GitCommit으로 감싸 반환하는데, RevWalk는 parseCommit(objectId)으로
            // 지정한 커밋 자신의 본문만 파싱하고 그 부모(getParent(0))는 body를 파싱하지 않은 채로
            // 둔다. 그 결과 반환된 Commit의 getMessage()/getShortMessage()/getAuthorName() 등을
            // 호출하면 RawParseUtils.commitMessage(RawParseUtils.java:1340)에서
            // "Cannot read the array length because b is null" NPE가 발생한다(실제로 재현 확인함).
            // getId()는 파싱 없이도 ObjectId만으로 얻어지므로 예외 없이 동작한다 - 여기서는 버그를
            // 우회하기 위해서가 아니라 실제로 안전하게 동작하는 부분만 검증한다.
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o79", "p79", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            val commit1 = builder.put("a.txt", "v1").commit("부모 커밋")
            val commit2 = builder.put("a.txt", "v2").commit("자식 커밋")

            val parent = repo.getParentCommitOf(commit2.name)

            parent shouldNotBe null
            parent!!.getId() shouldBe commit1.name
        }

        it("[버그 재현] getParentCommitOf()가 반환한 부모 Commit의 getMessage()는 NPE를 던진다(RevCommit 부모 body 미파싱)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o79b", "p79b", baseDir, userResolver)
            repo.create()
            val builder = testRepo(openRepo(repo))
            builder.put("a.txt", "v1").commit("부모 커밋")
            val commit2 = builder.put("a.txt", "v2").commit("자식 커밋")

            val parent = repo.getParentCommitOf(commit2.name)

            try {
                parent!!.getMessage()
                throw AssertionError("현재 구현에서는 NullPointerException이 발생해야 한다(부모 RevCommit body 미파싱 버그)")
            } catch (e: NullPointerException) {
                // 현재 구현의 실제 동작(버그)을 고정해 회귀를 감지한다.
            }
        }

        it("부모가 없는 최초 커밋은 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o80", "p80", baseDir, userResolver)
            repo.create()
            val commit1 = testRepo(openRepo(repo)).put("a.txt", "v1").commit("최초 커밋")

            repo.getParentCommitOf(commit1.name) shouldBe null
        }

        it("존재하지 않는 commitId는 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o81", "p81", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋")

            repo.getParentCommitOf("no-such-rev") shouldBe null
        }
    }

    describe("isEmpty()") {
        it("HEAD를 resolve할 수 없으면(커밋 없음) true다") {
            val repo = GitRepository("o82", "p82", newTempBaseDir(), userResolver)
            repo.create()

            repo.isEmpty() shouldBe true
        }

        it("HEAD를 resolve할 수 있으면(커밋 있음) false다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o83", "p83", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "v1").commit("커밋")

            repo.isEmpty() shouldBe false
        }
    }

    describe("getArchive()") {
        it("존재하지 않는 브랜치명을 지정하면 아무 것도 쓰지 않는다(출력 스트림이 비어있음)") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o84", "p84", baseDir, userResolver)
            repo.create()

            val out = ByteArrayOutputStream()
            repo.getArchive(out, "no-such-branch")

            out.size() shouldBe 0
        }

        it("일반 파일/중첩 디렉토리/실행 파일을 포함해 zip으로 묶고, 심볼릭 링크는 제외한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o85", "p85", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo))
                .put("readme.txt", "root readme")
                .put("src/main.txt", "nested content")
                .put("run.sh", "#!/bin/sh\necho hi", mode = FileMode.EXECUTABLE_FILE)
                .put("link.txt", "target.txt", mode = FileMode.SYMLINK)
                .commit("아카이브용 커밋")

            val out = ByteArrayOutputStream()
            repo.getArchive(out, "HEAD")

            val entries = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = String(zis.readBytes(), StandardCharsets.UTF_8)
                    entry = zis.nextEntry
                }
            }

            entries["readme.txt"] shouldBe "root readme"
            entries["src/main.txt"] shouldBe "nested content"
            entries["run.sh"] shouldBe "#!/bin/sh\necho hi"
            // 심볼릭 링크는 REGULAR_FILE도 EXECUTABLE_FILE도 아니므로 zip에 포함되지 않는다.
            entries.containsKey("link.txt") shouldBe false
        }
    }

    describe("getBlobId()") {
        it("revision이 빈 문자열이면 저장소를 열지 않고 바로 null을 반환한다") {
            val repo = GitRepository("o86", "no-such-repo-at-all", newTempBaseDir(), userResolver)

            repo.getBlobId("", "a.txt") shouldBe null
        }

        it("존재하지 않는 revision은 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o87", "p87", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getBlobId("no-such-rev", "a.txt") shouldBe null
        }

        it("존재하지 않는 경로는 null을 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o88", "p88", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            repo.getBlobId("HEAD", "no-such.txt") shouldBe null
        }

        it("존재하는 revision/경로는 blob sha를 반환한다") {
            val baseDir = newTempBaseDir()
            val repo = GitRepository("o89", "p89", baseDir, userResolver)
            repo.create()
            testRepo(openRepo(repo)).put("a.txt", "content").commit("커밋")

            val blobId = repo.getBlobId("HEAD", "a.txt")

            blobId shouldNotBe null
            blobId!!.length shouldBe 40
        }
    }
})
