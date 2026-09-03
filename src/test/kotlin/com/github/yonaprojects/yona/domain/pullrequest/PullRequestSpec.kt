package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PullRequestSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val toProject = Project(id = 1L, name = "to", owner = "owner1")
            val fromProject = Project(id = 2L, name = "from", owner = "owner2")
            val contributor = User(id = 3L)
            val receiver = User(id = 4L)
            val reviewer = User(id = 5L)
            val now = Instant.now()

            val pullRequest = PullRequest(
                toProject = toProject,
                fromProject = fromProject,
                contributor = contributor
            )

            pullRequest.id = 10L
            pullRequest.title = "제목"
            pullRequest.body = "본문"
            pullRequest.toProject = toProject
            pullRequest.fromProject = fromProject
            pullRequest.toBranch = "main"
            pullRequest.fromBranch = "feature"
            pullRequest.contributor = contributor
            pullRequest.receiver = receiver
            pullRequest.created = now
            pullRequest.updated = now
            pullRequest.received = now
            pullRequest.state = State.CLOSED
            pullRequest.isConflict = true
            pullRequest.isMerging = true
            pullRequest.lastCommitId = "abc123"
            pullRequest.mergedCommitIdFrom = "def456"
            pullRequest.mergedCommitIdTo = "ghi789"
            pullRequest.number = 42L
            pullRequest.reviewers = mutableSetOf(reviewer)

            pullRequest.id shouldBe 10L
            pullRequest.title shouldBe "제목"
            pullRequest.body shouldBe "본문"
            pullRequest.toProject shouldBe toProject
            pullRequest.fromProject shouldBe fromProject
            pullRequest.toBranch shouldBe "main"
            pullRequest.fromBranch shouldBe "feature"
            pullRequest.contributor shouldBe contributor
            pullRequest.receiver shouldBe receiver
            pullRequest.created shouldBe now
            pullRequest.updated shouldBe now
            pullRequest.received shouldBe now
            pullRequest.state shouldBe State.CLOSED
            pullRequest.isConflict shouldBe true
            pullRequest.isMerging shouldBe true
            pullRequest.lastCommitId shouldBe "abc123"
            pullRequest.mergedCommitIdFrom shouldBe "def456"
            pullRequest.mergedCommitIdTo shouldBe "ghi789"
            pullRequest.number shouldBe 42L
            pullRequest.reviewers shouldBe mutableSetOf(reviewer)
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val toProject = Project(id = 1L, name = "to", owner = "owner1")
            val fromProject = Project(id = 2L, name = "from", owner = "owner2")
            val contributor = User(id = 3L)

            val pullRequest = PullRequest(
                toProject = toProject,
                fromProject = fromProject,
                contributor = contributor
            )

            pullRequest.id shouldBe null
            pullRequest.title shouldBe ""
            pullRequest.body shouldBe null
            pullRequest.toBranch shouldBe ""
            pullRequest.fromBranch shouldBe ""
            pullRequest.receiver shouldBe null
            pullRequest.created shouldBe null
            pullRequest.updated shouldBe null
            pullRequest.received shouldBe null
            pullRequest.state shouldBe State.OPEN
            pullRequest.isConflict shouldBe false
            pullRequest.isMerging shouldBe false
            pullRequest.lastCommitId shouldBe null
            pullRequest.mergedCommitIdFrom shouldBe null
            pullRequest.mergedCommitIdTo shouldBe null
            pullRequest.number shouldBe null
            pullRequest.reviewers shouldBe mutableSetOf()
        }
    }
})
