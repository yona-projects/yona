package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.GitCommit
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class PullRequestMergeResultSpec : DescribeSpec({
    fun newPullRequest() = PullRequest(
        toProject = Project(id = 1L, name = "to", owner = "owner1"),
        fromProject = Project(id = 2L, name = "from", owner = "owner2"),
        contributor = User(id = 3L)
    )

    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val pullRequest = newPullRequest()
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            result.gitCommits shouldBe emptyList()
            result.newCommits shouldBe emptyList()
            result.pullRequest shouldBe pullRequest

            val commit = mockk<GitCommit>()
            val newPullRequestCommit = PullRequestCommit(pullRequest = pullRequest)
            val newPullRequest = newPullRequest()

            result.gitCommits = listOf(commit)
            result.newCommits = listOf(newPullRequestCommit)
            result.pullRequest = newPullRequest

            result.gitCommits shouldBe listOf(commit)
            result.newCommits shouldBe listOf(newPullRequestCommit)
            result.pullRequest shouldBe newPullRequest
        }
    }

    describe("hasDiffCommits()") {
        it("gitCommits가 비어있지 않으면 true여야 한다") {
            val result = PullRequestMergeResult(
                gitCommits = listOf(mockk<GitCommit>()),
                pullRequest = newPullRequest()
            )

            result.hasDiffCommits() shouldBe true
        }

        it("gitCommits가 비어있으면 false여야 한다") {
            val result = PullRequestMergeResult(pullRequest = newPullRequest())

            result.hasDiffCommits() shouldBe false
        }
    }

    describe("conflicts()") {
        it("pullRequest.isConflict가 true면 true여야 한다") {
            val pullRequest = newPullRequest().apply { isConflict = true }
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            result.conflicts() shouldBe true
        }

        it("pullRequest.isConflict가 false면 false여야 한다") {
            val pullRequest = newPullRequest().apply { isConflict = false }
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            result.conflicts() shouldBe false
        }
    }

    describe("setConflictStateOfPullRequest()") {
        it("pullRequest.isConflict를 true로 설정해야 한다") {
            val pullRequest = newPullRequest().apply { isConflict = false }
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            result.setConflictStateOfPullRequest()

            pullRequest.isConflict shouldBe true
        }
    }

    describe("setResolvedStateOfPullRequest()") {
        it("pullRequest.isConflict를 false로 설정해야 한다") {
            val pullRequest = newPullRequest().apply { isConflict = true }
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            result.setResolvedStateOfPullRequest()

            pullRequest.isConflict shouldBe false
        }
    }

    describe("setMergedStateOfPullRequest()") {
        it("pullRequest의 isConflict/state/receiver를 병합 완료 상태로 설정해야 한다") {
            val pullRequest = newPullRequest().apply { isConflict = true }
            val result = PullRequestMergeResult(pullRequest = pullRequest)
            val receiver = User(id = 9L)

            result.setMergedStateOfPullRequest(receiver)

            pullRequest.isConflict shouldBe false
            pullRequest.state shouldBe State.MERGED
            pullRequest.receiver shouldBe receiver
        }
    }
})
