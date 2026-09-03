package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

class GitCommitSpec : DescribeSpec({
    val repo = InMemoryRepository(DfsRepositoryDescription())

    fun makeCommit(
        message: String,
        authorName: String = "작성자",
        authorEmail: String = "author@yona.io",
        parents: List<RevCommit> = emptyList()
    ): RevCommit {
        val inserter = repo.newObjectInserter()
        val blobId = inserter.insert(Constants.OBJ_BLOB, message.toByteArray())
        val tree = TreeFormatter()
        tree.append("file.txt", FileMode.REGULAR_FILE, blobId)
        val treeId = inserter.insert(tree)
        val commitBuilder = CommitBuilder()
        commitBuilder.setTreeId(treeId)
        val ident = PersonIdent(authorName, authorEmail)
        commitBuilder.author = ident
        commitBuilder.committer = ident
        commitBuilder.message = message
        parents.forEach { commitBuilder.addParentId(it) }
        val commitId = inserter.insert(commitBuilder)
        inserter.flush()
        return RevWalk(repo).use { it.parseCommit(commitId) }
    }

    describe("실제 RevCommit 기반 위임 동작") {
        it("작성자/커미터 정보가 있는 커밋의 모든 필드를 올바르게 위임해야 한다") {
            val user = User(id = 1L, loginId = "author", name = "작성자유저")
            var resolvedName: String? = "호출안됨"
            var resolvedEmail: String? = "호출안됨"
            val revCommit = makeCommit("첫 줄 메시지")
            val gitCommit = GitCommit(revCommit) { name, email ->
                resolvedName = name
                resolvedEmail = email
                user
            }

            gitCommit.getId() shouldBe revCommit.name
            gitCommit.getShortId() shouldBe revCommit.name.substring(0, 7)
            gitCommit.getMessage() shouldBe revCommit.fullMessage
            gitCommit.getShortMessage() shouldBe revCommit.shortMessage
            gitCommit.getAuthorName() shouldBe "작성자"
            gitCommit.getAuthorEmail() shouldBe "author@yona.io"
            gitCommit.getAuthorDate() shouldBe revCommit.authorIdent.`when`
            gitCommit.getAuthorTimezone() shouldBe revCommit.authorIdent.timeZone
            gitCommit.getCommitterName() shouldBe "작성자"
            gitCommit.getCommitterEmail() shouldBe "author@yona.io"
            gitCommit.getCommitterDate() shouldBe revCommit.committerIdent.`when`
            gitCommit.getCommitterTimezone() shouldBe revCommit.committerIdent.timeZone
            gitCommit.getParentCount() shouldBe 0

            gitCommit.getAuthor() shouldBe user
            resolvedName shouldBe null
            resolvedEmail shouldBe "author@yona.io"
        }

        it("부모 커밋이 있으면 parentCount가 1 이상이어야 한다") {
            val parent = makeCommit("부모 커밋")
            val child = makeCommit("자식 커밋", parents = listOf(parent))
            val gitCommit = GitCommit(child) { _, _ -> null }

            gitCommit.getParentCount() shouldBe 1
        }
    }

    describe("authorIdent/committerIdent/shortMessage가 없는 경우(방어적 null 분기)") {
        it("모든 null-safe 위임 필드가 null을 반환하고, shortId/shortMessage는 기본값으로 대체돼야 한다") {
            val mockRevCommit = mockk<RevCommit>(relaxed = true)
            every { mockRevCommit.name } returns "abc"
            every { mockRevCommit.authorIdent } returns null
            every { mockRevCommit.committerIdent } returns null
            every { mockRevCommit.shortMessage } returns null

            var calledWithName: String? = "호출안됨"
            var calledWithEmail: String? = "호출안됨"
            val gitCommit = GitCommit(mockRevCommit) { name, email ->
                calledWithName = name
                calledWithEmail = email
                null
            }

            gitCommit.getShortId() shouldBe "abc"
            gitCommit.getShortMessage() shouldBe ""
            gitCommit.getAuthorName() shouldBe null
            gitCommit.getAuthorEmail() shouldBe null
            gitCommit.getAuthorDate() shouldBe null
            gitCommit.getAuthorTimezone() shouldBe null
            gitCommit.getCommitterName() shouldBe null
            gitCommit.getCommitterEmail() shouldBe null
            gitCommit.getCommitterDate() shouldBe null
            gitCommit.getCommitterTimezone() shouldBe null

            gitCommit.getAuthor() shouldBe null
            calledWithName shouldBe null
            calledWithEmail shouldBe null
        }
    }
})
