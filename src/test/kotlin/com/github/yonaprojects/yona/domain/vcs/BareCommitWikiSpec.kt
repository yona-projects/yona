package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.file.Files

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

// BareCommit.commitPage()/deletePage()(P3-42 위키 페이지 저장/삭제) 대응 — 실제 파일시스템에
// bare 저장소를 만들어 진짜 JGit 커밋으로 검증한다(GitRepositorySpec/BareCommitSpec과 동일한 방식).
class BareCommitWikiSpec : DescribeSpec({

    fun newRepoDir(repoName: String = "myproj.wiki"): Pair<File, File> {
        val gitBaseDir = Files.createTempDirectory("yona-barecommit-wiki-test").toFile()
        val bareDir = File(gitBaseDir, "tester/$repoName.git")
        Git.init().setDirectory(bareDir).setBare(true).setInitialBranch("main").call().close()
        return gitBaseDir to bareDir
    }

    val project = Project(id = 1L, owner = "tester", name = "myproj")
    val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

    describe("BareCommit.commitPage() — repoNameOverride로 위키 전용 bare 저장소에 커밋") {
        it("빈 저장소에 신규 페이지를 커밋하면 부모 없이 그 경로에 파일이 생긴다") {
            val (gitBaseDir, bareDir) = newRepoDir()
            val bare = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare.setRefName(Constants.R_HEADS + "main")

            val commitId = bare.commitPage("main", null, "Home.md", "# Home\n내용", "Create Home")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/main") shouldBe commitId
                readTreeFiles(repository, commitId!!)["Home.md"] shouldBe "# Home\n내용"
            } finally {
                repository.close()
            }
        }

        it("중첩 경로(슬래시 포함, P3-42 7번)로 페이지를 만들 수 있다") {
            val (gitBaseDir, bareDir) = newRepoDir()
            val bare = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare.setRefName(Constants.R_HEADS + "main")

            val commitId = bare.commitPage("main", null, "Guides/Setup.md", "설치 방법", "Create Guides/Setup")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                readTreeFiles(repository, commitId!!)["Guides/Setup.md"] shouldBe "설치 방법"
            } finally {
                repository.close()
            }
        }

        it("기존 페이지를 수정하면 그 경로의 내용만 바뀌고 다른 페이지는 그대로 남는다") {
            val (gitBaseDir, bareDir) = newRepoDir()
            val bare1 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare1.setRefName(Constants.R_HEADS + "main")
            bare1.commitPage("main", null, "Home.md", "v1", "Create Home")
            val bare1b = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare1b.setRefName(Constants.R_HEADS + "main")
            bare1b.commitPage("main", null, "_Sidebar.md", "sidebar", "Create sidebar")

            val bare2 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare2.setRefName(Constants.R_HEADS + "main")
            val commitId = bare2.commitPage("main", "Home.md", "Home.md", "v2", "Update Home")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                val files = readTreeFiles(repository, commitId!!)
                files["Home.md"] shouldBe "v2"
                files["_Sidebar.md"] shouldBe "sidebar"
            } finally {
                repository.close()
            }
        }

        it("oldPath != newPath면 이름변경까지 한 커밋으로 반영한다(옛 경로는 트리에서 사라짐)") {
            val (gitBaseDir, bareDir) = newRepoDir()
            val bare1 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare1.setRefName(Constants.R_HEADS + "main")
            bare1.commitPage("main", null, "OldTitle.md", "본문", "Create OldTitle")

            val bare2 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare2.setRefName(Constants.R_HEADS + "main")
            val commitId = bare2.commitPage("main", "OldTitle.md", "Guides/NewTitle.md", "본문", "Rename OldTitle to Guides/NewTitle")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                val files = readTreeFiles(repository, commitId!!)
                files.containsKey("OldTitle.md") shouldBe false
                files["Guides/NewTitle.md"] shouldBe "본문"
            } finally {
                repository.close()
            }
        }

        it("커밋 메시지를 그대로 반영한다(P3-42 4번 — 커밋 메시지 커스터마이징)") {
            val (gitBaseDir, _) = newRepoDir()
            val bare = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare.setRefName(Constants.R_HEADS + "main")
            val commitId = bare.commitPage("main", null, "Home.md", "내용", "커스텀 커밋 메시지입니다")

            val bareDir2 = File(gitBaseDir, "tester/myproj.wiki.git")
            val repository = FileRepositoryBuilder().setGitDir(bareDir2).build()
            try {
                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(commitId)
                commit.fullMessage shouldBe "커스텀 커밋 메시지입니다"
                revWalk.close()
            } finally {
                repository.close()
            }
        }
    }

    describe("BareCommit.deletePage()") {
        it("삭제한 페이지는 새 커밋의 트리에서 사라지고 다른 페이지는 남는다") {
            val (gitBaseDir, bareDir) = newRepoDir()
            val bare1 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare1.setRefName(Constants.R_HEADS + "main")
            bare1.commitPage("main", null, "Home.md", "home", "Create Home")
            val bare1b = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare1b.setRefName(Constants.R_HEADS + "main")
            bare1b.commitPage("main", null, "Old.md", "old", "Create Old")

            val bare2 = BareCommit(project, user, gitBaseDir.absolutePath, defaultBranch = "main", repoNameOverride = "myproj.wiki")
            bare2.setRefName(Constants.R_HEADS + "main")
            val commitId = bare2.deletePage("main", "Old.md", "Delete Old")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                val files = readTreeFiles(repository, commitId!!)
                files.containsKey("Old.md") shouldBe false
                files["Home.md"] shouldBe "home"
            } finally {
                repository.close()
            }
        }
    }

    describe("BareCommit repoNameOverride 미지정 시 기존 동작 무변경") {
        it("repoNameOverride를 생략하면 여전히 project.name.git에 커밋한다") {
            val gitBaseDir = Files.createTempDirectory("yona-barecommit-legacy-test").toFile()
            val bareDir = File(gitBaseDir, "tester/myproj.git")
            Git.init().setDirectory(bareDir).setBare(true).setInitialBranch("main").call().close()

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            val commitId = bare.commitTextFile("README.md", "hello", "init")

            commitId shouldNotBe null
            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/main") shouldBe commitId
            } finally {
                repository.close()
            }
        }
    }
})
