package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// NonRangedCodeCommentThread는 CommentThread(추상 클래스)의 서브클래스로, 고유 프로퍼티는 없고
// 상속받은 프로퍼티를 그대로 생성자에 전달할 뿐이다(CommentThread 자체의 접근자 테스트는
// CommentThreadSpec.kt에서 이미 다룬다). 이 클래스 고유의 로직은 isOnChangesOfPullRequest() 하나뿐이다.
class NonRangedCodeCommentThreadSpec : DescribeSpec({
    fun pullRequest() = PullRequest(
        toProject = Project(),
        fromProject = Project(),
        contributor = User()
    )

    describe("기본 생성") {
        it("기본값만으로 생성할 수 있어야 한다") {
            val thread = NonRangedCodeCommentThread()

            thread.id shouldBe null
            thread.pullRequest shouldBe null
            thread.prevCommitId shouldBe ""
            thread.commitId shouldBe null
        }

        it("모든 필드를 채워 생성할 수 있어야 한다") {
            val pr = pullRequest()
            val thread = NonRangedCodeCommentThread(
                id = 1L,
                pullRequest = pr,
                project = Project(),
                prevCommitId = "prev",
                commitId = "commit"
            )

            thread.id shouldBe 1L
            thread.pullRequest shouldBe pr
            thread.prevCommitId shouldBe "prev"
            thread.commitId shouldBe "commit"
        }
    }

    describe("isOnChangesOfPullRequest()") {
        it("pullRequest가 없으면 false여야 한다") {
            val thread = NonRangedCodeCommentThread(pullRequest = null, commitId = "commit")
            thread.isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest는 있지만 commitId가 null이면 false여야 한다") {
            val thread = NonRangedCodeCommentThread(pullRequest = pullRequest(), commitId = null)
            thread.isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest는 있지만 commitId가 빈 문자열이면 false여야 한다") {
            val thread = NonRangedCodeCommentThread(pullRequest = pullRequest(), commitId = "")
            thread.isOnChangesOfPullRequest() shouldBe false
        }

        it("pullRequest와 commitId가 모두 있으면 true여야 한다") {
            val thread = NonRangedCodeCommentThread(pullRequest = pullRequest(), commitId = "c1")
            thread.isOnChangesOfPullRequest() shouldBe true
        }
    }
})
