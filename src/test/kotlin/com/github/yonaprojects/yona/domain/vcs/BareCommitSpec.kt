package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.file.Files

// 커밋 트리(nested path 포함)를 경로->내용 맵으로 풀어 읽는 공용 헬퍼. 여러 테스트에서 커밋 결과 검증에 재사용한다.
private fun readTreeFiles(repository: Repository, commitId: ObjectId): Map<String, String> {
    val revWalk = RevWalk(repository)
    val commit = revWalk.parseCommit(commitId)
    val treeWalk = TreeWalk(repository)
    treeWalk.addTree(commit.tree)
    treeWalk.isRecursive = true

    val filesInTree = mutableMapOf<String, String>()
    while (treeWalk.next()) {
        val loader = repository.open(treeWalk.getObjectId(0))
        filesInTree[treeWalk.pathString] = String(loader.bytes, Charsets.UTF_8)
    }
    treeWalk.close()
    revWalk.close()
    return filesInTree
}

private fun seedInitialCommit(bareDir: File, branch: String, filePath: String, content: String) {
    val tempWorkingDir = Files.createTempDirectory("yona-barecommit-seed").toFile()
    val git = Git.init().setDirectory(tempWorkingDir).call()
    try {
        val file = File(tempWorkingDir, filePath)
        file.parentFile.mkdirs()
        file.writeText(content)

        git.add().addFilepattern(filePath).call()
        git.commit().setSign(false).setAuthor("seed", "seed@yona.io").setMessage("seed").call()

        val config = git.repository.config
        config.setString("remote", "origin", "url", bareDir.absolutePath)
        config.save()

        git.push()
            .setRemote("origin")
            .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
            .call()
    } finally {
        git.close()
    }
}

// yona GitUtil.commitTextFile()가 위임하는 BareCommit.commitTextFile(branchName, path, text, message)
// (BareCommit.java:249-286, "Bare commit" 오버로드) 대응 (P1-135). yona는 이 오버로드로
// 1) 지정 브랜치(refs/heads/<branch>)에만 커밋하고, 2) DirCache+TreeWalk 재귀 순회로
// 하위 경로(nested path) 파일도 기존 트리를 보존하며 반영한다.
class BareCommitSpec : DescribeSpec({
    describe("BareCommit.commitTextFile(branchName, path, text, message)") {
        it("지정한 브랜치에만 커밋을 반영하고 다른 브랜치(master)는 건드리지 않는다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            seedInitialCommit(bareDir, "develop", "README.md", "root file")

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            bare.setRefName(Constants.R_HEADS + "develop")
            val commitId = bare.commitTextFile("develop", "src/main/Foo.kt", "package foo", "add nested file")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/develop") shouldBe commitId
                repository.findRef("refs/heads/master") shouldBe null

                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(commitId)
                val treeWalk = TreeWalk(repository)
                treeWalk.addTree(commit.tree)
                treeWalk.isRecursive = true

                val filesInTree = mutableMapOf<String, String>()
                while (treeWalk.next()) {
                    val loader = repository.open(treeWalk.getObjectId(0))
                    filesInTree[treeWalk.pathString] = String(loader.bytes, Charsets.UTF_8)
                }
                treeWalk.close()
                revWalk.close()

                filesInTree["src/main/Foo.kt"] shouldBe "package foo"
                filesInTree["README.md"] shouldBe "root file"
            } finally {
                repository.close()
            }
        }

        // getRefUpdate()의 headObjectId==null 분기, getCommitBuilder()의 setParentId 미호출 분기,
        // createTemporaryIndex()의 headId==null 분기(기존 트리 병합 생략)를 함께 검증한다 — 이전 커밋이
        // 전혀 없는 완전히 새 브랜치에 첫 커밋을 반영하는 케이스.
        it("커밋이 하나도 없는 새 브랜치에 첫 커밋을 반영하면 부모 없이 커밋해야 한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()
            // seedInitialCommit()을 호출하지 않는다 -- "feature" 브랜치는 어떤 커밋도 존재하지 않는 상태다.

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            bare.setRefName(Constants.R_HEADS + "feature")
            val commitId = bare.commitTextFile("feature", "docs/readme.txt", "hello", "new branch first commit")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/feature") shouldBe commitId

                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(commitId)
                commit.parentCount shouldBe 0
                revWalk.close()

                readTreeFiles(repository, commitId!!)["docs/readme.txt"] shouldBe "hello"
            } finally {
                repository.close()
            }
        }

        // createTemporaryIndex() 트리 순회 루프의 walkPath == path 분기(신규 blob으로 덮어쓰고 기존
        // 엔트리는 건너뜀) 검증 -- 같은 경로에 다시 커밋해 기존 파일을 덮어쓰는 케이스.
        it("이미 존재하는 경로에 다시 커밋하면 새 blob으로 덮어써야 한다(walkPath == path 분기)") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            seedInitialCommit(bareDir, "develop", "docs/readme.txt", "old content")

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            bare.setRefName(Constants.R_HEADS + "develop")
            val commitId = bare.commitTextFile("develop", "docs/readme.txt", "new content", "overwrite readme")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                val files = readTreeFiles(repository, commitId!!)
                files.size shouldBe 1
                files["docs/readme.txt"] shouldBe "new content"
            } finally {
                repository.close()
            }
        }

        // getRefUpdate() 이후 forceUpdate()의 when(rc) 분기 중 REJECTED/LOCK_FAILURE ->
        // ConcurrentRefUpdateException 분기 검증. 대상 ref에 대한 .lock 파일을 미리 만들어 JGit의
        // RefDirectoryUpdate가 락 획득에 실패(LOCK_FAILURE)하도록 강제로 재현한다(실제 동시 수정 충돌과
        // 동일한 저수준 메커니즘).
        it("ref lock을 동시에 선점당하면 ConcurrentRefUpdateException으로 감싼 RuntimeException을 던져야 한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            seedInitialCommit(bareDir, "develop", "README.md", "root file")

            val lockFile = File(bareDir, "refs/heads/develop.lock")
            lockFile.parentFile.mkdirs()
            lockFile.createNewFile()
            try {
                val project = Project(id = 1L, owner = "tester", name = "repo")
                val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

                val bare = BareCommit(project, user, gitBaseDir.absolutePath)
                bare.setRefName(Constants.R_HEADS + "develop")

                val thrown = shouldThrow<RuntimeException> {
                    bare.commitTextFile("develop", "src/main/Foo.kt", "package foo", "should fail to lock")
                }
                thrown.cause.shouldBeInstanceOf<ConcurrentRefUpdateException>()
            } finally {
                lockFile.delete()
            }
        }
    }

    // yona BareCommit.java의 레거시 3-인자 commitTextFile(fileNameWithPath, contents, message) 오버로드 대응.
    // setRefName()으로 지정한(기본값 refs/heads/master) 단일 브랜치의 "루트 트리"만 다루며, createTreeWith()가
    // 기존 루트 트리를 알파벳순으로 순회하면서 새/기존 파일을 병합한다(중첩 경로는 다루지 않는다 -- 파일명만 사용).
    describe("BareCommit.commitTextFile(fileNameWithPath, contents, message) - 레거시 3-인자 오버로드") {
        it("연속 커밋으로 createTreeWith()의 모든 병합 분기(신규 트리/중간 삽입/말미 삽입/덮어쓰기)를 거쳐야 한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-legacy-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            // 1) 첫 커밋: 저장소에 아직 ref가 없어 headObjectId==zeroId -- createTreeWith()의 if 분기
            //    (신규 트리 생성), 부모 없는 커밋을 검증한다.
            val commit1 = BareCommit(project, user, gitBaseDir.absolutePath)
                .commitTextFile("m.txt", "m content", "commit m")
            commit1 shouldNotBe null

            // 2) "a.txt"는 기존 "m.txt"보다 알파벳순으로 앞선다 -- nameForComparison > fileName 분기
            //    (새 파일을 먼저 삽입한 뒤 기존 엔트리를 이어붙임).
            val commit2 = BareCommit(project, user, gitBaseDir.absolutePath)
                .commitTextFile("a.txt", "a content", "commit a")
            commit2 shouldNotBe null

            // 3) "z.txt"는 기존 "a.txt","m.txt" 모두보다 뒤에 온다 -- 루프 내내 else(그대로 복사) 분기만
            //    타다가 루프 종료 후 !isInserted 분기로 말미에 삽입된다.
            val commit3 = BareCommit(project, user, gitBaseDir.absolutePath)
                .commitTextFile("z.txt", "z content", "commit z")
            commit3 shouldNotBe null

            // 4) 이미 존재하는 "m.txt"에 다시 커밋 -- nameForComparison == fileName 분기(덮어쓰기).
            val commit4 = BareCommit(project, user, gitBaseDir.absolutePath)
                .commitTextFile("m.txt", "m content v2", "update m")
            commit4 shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/main") shouldBe commit4

                val revWalk = RevWalk(repository)
                val firstCommit = revWalk.parseCommit(commit1)
                firstCommit.parentCount shouldBe 0
                val secondCommit = revWalk.parseCommit(commit2)
                secondCommit.parentCount shouldBe 1
                secondCommit.getParent(0).id shouldBe commit1
                revWalk.close()

                val files = readTreeFiles(repository, commit4!!)
                files.size shouldBe 3
                files["a.txt"] shouldBe "a content"
                files["m.txt"] shouldBe "m content v2"
                files["z.txt"] shouldBe "z content"
            } finally {
                repository.close()
            }
        }

        // 사용자 요청 — 새 프로젝트 기본 브랜치를 "master" 대신 "main"으로 만들고 싶어함. 호스트 git의
        // init.defaultBranch 설정에 기대는 대신(GitRepositorySpec.kt의 defaultBranchRef 프로브 주석
        // 참고 — 환경마다 달라져 깨질 수 있음), 애플리케이션 설정(yona.git.default-branch, 기본값 "main")
        // 으로 결정론적으로 고정한다. BareCommit도 같은 값을 따라야 한다 — 그러지 않으면 새로 만든
        // 저장소(HEAD가 refs/heads/main을 가리킴, 아직 커밋 없음)에 README 체크박스 등으로 첫 커밋을
        // 올릴 때 setRefName() 없이 이 3-인자 오버로드를 쓰는 경로(BoardViewController)가 여전히
        // refs/heads/master에 커밋해버려 main은 계속 비어있고 master만 생기는 불일치가 생긴다.
        it("defaultBranch 생성자 인자를 지정하면 setRefName() 없이도 그 브랜치에 커밋해야 한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-defaultbranch-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).setInitialBranch("main").call().close()

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val commitId = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main")
                .commitTextFile("README.md", "# repo", "initial commit")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/main") shouldBe commitId
                repository.findRef("refs/heads/master") shouldBe null
            } finally {
                repository.close()
            }
        }

        // createTreeWith() 루프의 treeParser.entryFileMode == FileMode.TREE 분기 검증. 이 레거시
        // 오버로드 자체는 항상 루트 파일만 커밋하므로 스스로 디렉터리 엔트리를 만들 수 없다 -- 대신
        // seedInitialCommit()으로 저장소를 미리 하위 디렉터리가 있는 상태로 만들어 재현한다.
        it("루트 트리에 디렉터리 엔트리가 있으면 TREE 파일모드로 인식해 그대로 보존해야 한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-legacy-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            seedInitialCommit(bareDir, "main", "src/foo.txt", "nested content")

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            val commitId = bare.commitTextFile("b.txt", "b content", "add root file before src dir")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                val files = readTreeFiles(repository, commitId!!)
                files["b.txt"] shouldBe "b content"
                files["src/foo.txt"] shouldBe "nested content"
            } finally {
                repository.close()
            }
        }
    }

        describe("Coverage addition for BareCommit") {
            it("should handle null parentFile in commitTextFile (4-args)") {
                val gitBaseDir = Files.createTempDirectory("yona-barecommit-cov").toFile()
                val bareDir = File(gitBaseDir, "tester/repo.git")
                Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = Project(id = 1L, owner = "tester", name = "repo")
                val user = User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val bare = BareCommit(project, user, gitBaseDir.absolutePath)
                
                // "root.txt" has no parent directory, so file.parentFile is null.
                val commitId = bare.commitTextFile("develop", "root.txt", "content", "msg")
                commitId shouldNotBe null
            }
            
            it("should handle unreachable branches using reflection") {
                val gitBaseDir = Files.createTempDirectory("yona-barecommit-cov2").toFile()
                val bareDir = File(gitBaseDir, "tester/repo.git")
                Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = Project(id = 1L, owner = "tester", name = "repo")
                val user = User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val bare = BareCommit(project, user, gitBaseDir.absolutePath)
                
                // Access private field headObjectId and set to null to cover branch in createTreeWith
                val field = BareCommit::class.java.getDeclaredField("headObjectId")
                field.isAccessible = true
                field.set(bare, null)
                
                val inserterMethod = BareCommit::class.java.getDeclaredMethod("createTreeWith", ObjectInserter::class.java, String::class.java, ObjectId::class.java)
                inserterMethod.isAccessible = true
                
                val repo = FileRepositoryBuilder().setGitDir(bareDir).build()
                repo.newObjectInserter().use { inserter ->
                    val zeroBlob = ObjectId.zeroId()
                    val treeId = inserterMethod.invoke(bare, inserter, "test.txt", zeroBlob) as ObjectId
                    treeId shouldNotBe null
                }
                repo.close()
            }
        }
    

        describe("Coverage addition for BareCommit - Constructor nulls") {
            it("should handle null name and email in constructor") {
                val gitBaseDir = Files.createTempDirectory("yona-barecommit-cov3").toFile()
                val bareDir = File(gitBaseDir, "tester/repo.git")
                Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = Project(id = 1L, owner = "tester", name = "repo")
                
                // Create User via Unsafe or just use MockK if possible? No, MockK doesn't like returning null for non-null types.
                // We can use Java reflection to set the field to null directly.
                val user = User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val nameField = User::class.java.getDeclaredField("name")
                nameField.isAccessible = true
                nameField.set(user, null)
                
                val emailField = User::class.java.getDeclaredField("email")
                emailField.isAccessible = true
                emailField.set(user, null)
                
                val bare = BareCommit(project, user, gitBaseDir.absolutePath)
                bare shouldNotBe null
            }
        }
    
})
