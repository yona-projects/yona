package com.github.yonaprojects.yona.domain.pullrequest

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import java.time.Instant

// CommentThread는 abstract class라 직접 인스턴스화할 수 없어, 구체 서브클래스인
// CodeCommentThread로 상속받은 로직(isOnPullRequest/addComment/removeComment/
// getFirstReviewComment/getChildCommentsSizeToString/hasChildComments)을 검증한다.
class CommentThreadSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val thread = CodeCommentThread()
            val pr = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val author = UserIdent(id = 1L, loginId = "gildong", name = "홍길동")
            val date = Instant.parse("2026-01-01T00:00:00Z")

            val project = Project()
            val comments = mutableListOf<ReviewComment>()

            thread.id = 10L
            thread.author = author
            thread.state = CommentThread.ThreadState.CLOSED
            thread.createdDate = date
            thread.pullRequest = pr
            thread.project = project
            thread.reviewComments = comments
            thread.prevCommitId = "abc"
            thread.commitId = "def"

            thread.id shouldBe 10L
            thread.author shouldBe author
            thread.state shouldBe CommentThread.ThreadState.CLOSED
            thread.createdDate shouldBe date
            thread.pullRequest shouldBe pr
            thread.project shouldBe project
            thread.reviewComments shouldBe comments
            thread.prevCommitId shouldBe "abc"
            thread.commitId shouldBe "def"
        }

        it("ThreadState enum 값이 OPEN/CLOSED 둘 다 존재해야 한다") {
            CommentThread.ThreadState.values().toList() shouldBe listOf(CommentThread.ThreadState.OPEN, CommentThread.ThreadState.CLOSED)
        }
    }

    describe("isOnPullRequest()") {
        it("pullRequest가 있으면 true여야 한다") {
            val pr = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val thread = CodeCommentThread(pullRequest = pr)
            thread.isOnPullRequest() shouldBe true
        }

        it("pullRequest가 없으면 false여야 한다") {
            val thread = CodeCommentThread(pullRequest = null)
            thread.isOnPullRequest() shouldBe false
        }
    }

    describe("addComment()/removeComment()") {
        it("addComment()는 목록에 추가하고 댓글의 thread를 자신으로 설정해야 한다") {
            val thread = CodeCommentThread()
            val comment = ReviewComment(contents = "댓글")

            thread.addComment(comment)

            thread.reviewComments shouldBe mutableListOf(comment)
            comment.thread shouldBe thread
        }

        it("removeComment()는 목록에서 제거하고 댓글의 thread를 null로 설정해야 한다") {
            val thread = CodeCommentThread()
            val comment = ReviewComment(contents = "댓글")
            thread.addComment(comment)

            thread.removeComment(comment)

            thread.reviewComments shouldBe mutableListOf()
            comment.thread shouldBe null
        }
    }

    describe("getFirstReviewComment()") {
        it("댓글이 없으면 IllegalStateException을 던져야 한다") {
            val thread = CodeCommentThread()

            shouldThrow<IllegalStateException> {
                thread.getFirstReviewComment()
            }
        }

        it("댓글이 여러 개면 createdDate가 가장 이른 댓글을 반환해야 한다") {
            val thread = CodeCommentThread()
            val older = ReviewComment(contents = "먼저", createdDate = Instant.parse("2026-01-01T00:00:00Z"))
            val newer = ReviewComment(contents = "나중", createdDate = Instant.parse("2026-01-02T00:00:00Z"))
            thread.addComment(newer)
            thread.addComment(older)

            thread.getFirstReviewComment() shouldBe older
        }
    }

    describe("getChildCommentsSizeToString()/hasChildComments()") {
        it("댓글이 없으면 빈 문자열과 false를 반환해야 한다") {
            val thread = CodeCommentThread()

            thread.getChildCommentsSizeToString() shouldBe ""
            thread.hasChildComments() shouldBe false
        }

        it("댓글이 1개면 빈 문자열과 false를 반환해야 한다") {
            val thread = CodeCommentThread()
            thread.addComment(ReviewComment(contents = "댓글1"))

            thread.getChildCommentsSizeToString() shouldBe ""
            thread.hasChildComments() shouldBe false
        }

        it("댓글이 2개 이상이면 (개수-1)과 true를 반환해야 한다") {
            val thread = CodeCommentThread()
            thread.addComment(ReviewComment(contents = "댓글1"))
            thread.addComment(ReviewComment(contents = "댓글2"))
            thread.addComment(ReviewComment(contents = "댓글3"))

            thread.getChildCommentsSizeToString() shouldBe "2"
            thread.hasChildComments() shouldBe true
        }
    }
})
