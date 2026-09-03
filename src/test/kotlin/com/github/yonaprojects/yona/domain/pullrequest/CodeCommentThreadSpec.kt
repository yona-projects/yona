package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CodeCommentThreadSpec : DescribeSpec({
    fun pullRequest() = PullRequest(toProject = Project(), fromProject = Project(), contributor = User())

    describe("프로퍼티 접근자") {
        it("codeRange/codeAuthors의 getter/setter가 정상 동작해야 한다") {
            val thread = CodeCommentThread()
            val range = CodeRange(path = "a.txt")
            val author = User(id = 1L, loginId = "author")

            thread.codeRange = range
            thread.codeAuthors = mutableListOf(author)

            thread.codeRange shouldBe range
            thread.codeAuthors shouldBe mutableListOf(author)
        }
    }

    describe("isCommitComment()") {
        it("prevCommitId가 빈 문자열(기본값)이면 true여야 한다") {
            CodeCommentThread().isCommitComment() shouldBe true
        }

        it("prevCommitId가 비어있지 않으면 false여야 한다") {
            CodeCommentThread(prevCommitId = "abc123").isCommitComment() shouldBe false
        }
    }

    describe("isOnChangesOfPullRequest()") {
        it("pullRequest가 null이면 false여야 한다(commitId와 무관하게 단락 평가)") {
            CodeCommentThread(pullRequest = null, commitId = "abc").isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest가 있지만 commitId가 null이면 false여야 한다") {
            CodeCommentThread(pullRequest = pullRequest(), commitId = null).isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest가 있지만 commitId가 빈 문자열이면 false여야 한다") {
            CodeCommentThread(pullRequest = pullRequest(), commitId = "").isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest가 있고 commitId도 비어있지 않으면 true여야 한다") {
            CodeCommentThread(pullRequest = pullRequest(), commitId = "abc123").isOnChangesOfPullRequest() shouldBe true
        }
    }

    describe("isOnAllChangesOfPullRequest()") {
        it("isOnChangesOfPullRequest()가 false면 false여야 한다(단락 평가)") {
            CodeCommentThread(pullRequest = null, commitId = "abc", prevCommitId = "prev")
                .isOnAllChangesOfPullRequest() shouldBe false
        }

        it("isOnChangesOfPullRequest()가 true이지만 prevCommitId가 빈 문자열이면 false여야 한다") {
            CodeCommentThread(pullRequest = pullRequest(), commitId = "abc123", prevCommitId = "")
                .isOnAllChangesOfPullRequest() shouldBe false
        }

        it("isOnChangesOfPullRequest()가 true이고 prevCommitId도 비어있지 않으면 true여야 한다") {
            CodeCommentThread(pullRequest = pullRequest(), commitId = "abc123", prevCommitId = "prev123")
                .isOnAllChangesOfPullRequest() shouldBe true
        }
    }
})
