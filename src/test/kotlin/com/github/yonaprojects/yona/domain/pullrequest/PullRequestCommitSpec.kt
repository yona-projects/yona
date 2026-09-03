package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.GitCommit
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Date

class PullRequestCommitSpec : DescribeSpec({
    val pullRequest = PullRequest(toProject = Project(), fromProject = Project(), contributor = User())

    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val prc = PullRequestCommit(pullRequest = pullRequest)
            val now = Instant.now()

            prc.id = 1L
            prc.pullRequest = pullRequest
            prc.commitId = "abc123"
            prc.authorDate = now
            prc.created = now
            prc.commitMessage = "메시지"
            prc.commitShortId = "abc"
            prc.authorEmail = "a@b.com"
            prc.state = PullRequestCommit.State.PRIOR

            prc.id shouldBe 1L
            prc.pullRequest shouldBe pullRequest
            prc.commitId shouldBe "abc123"
            prc.authorDate shouldBe now
            prc.created shouldBe now
            prc.commitMessage shouldBe "메시지"
            prc.commitShortId shouldBe "abc"
            prc.authorEmail shouldBe "a@b.com"
            prc.state shouldBe PullRequestCommit.State.PRIOR
        }
    }

    describe("getCommitShortMessage()") {
        it("commitMessage가 빈 문자열이면 빈 문자열을 반환해야 한다") {
            val prc = PullRequestCommit(pullRequest = pullRequest, commitMessage = "")
            prc.getCommitShortMessage() shouldBe ""
        }

        it("줄바꿈이 없으면 commitMessage를 그대로 반환해야 한다") {
            val prc = PullRequestCommit(pullRequest = pullRequest, commitMessage = "한 줄 메시지")
            prc.getCommitShortMessage() shouldBe "한 줄 메시지"
        }

        it("여러 줄이면 첫 줄만 반환해야 한다") {
            val prc = PullRequestCommit(pullRequest = pullRequest, commitMessage = "첫줄\n둘째줄\n셋째줄")
            prc.getCommitShortMessage() shouldBe "첫줄"
        }
    }

    describe("bindPullRequestCommit()") {
        it("GitCommit의 값들을 정상적으로 매핑해야 한다(작성일 있음, 메시지 있음)") {
            val date = Date()
            val gitCommit = mockk<GitCommit>()
            every { gitCommit.getId() } returns "fullsha1234567890"
            every { gitCommit.getShortId() } returns "fullsha"
            every { gitCommit.getMessage() } returns "커밋 메시지"
            every { gitCommit.getAuthorEmail() } returns "author@example.com"
            every { gitCommit.getAuthorDate() } returns date

            val result = PullRequestCommit.bindPullRequestCommit(gitCommit, pullRequest)

            result.commitId shouldBe "fullsha1234567890"
            result.commitShortId shouldBe "fullsha"
            result.commitMessage shouldBe "커밋 메시지"
            result.authorEmail shouldBe "author@example.com"
            result.authorDate shouldBe date.toInstant()
            result.state shouldBe PullRequestCommit.State.CURRENT
            result.pullRequest shouldBe pullRequest
        }

        it("GitCommit의 메시지와 작성일이 null이면 기본값(빈 문자열/null)으로 매핑해야 한다") {
            val gitCommit = mockk<GitCommit>()
            every { gitCommit.getId() } returns "sha2"
            every { gitCommit.getShortId() } returns "sha"
            every { gitCommit.getMessage() } returns null
            every { gitCommit.getAuthorEmail() } returns null
            every { gitCommit.getAuthorDate() } returns null

            val result = PullRequestCommit.bindPullRequestCommit(gitCommit, pullRequest)

            result.commitMessage shouldBe ""
            result.authorEmail shouldBe null
            result.authorDate shouldBe null
        }
    }
})
