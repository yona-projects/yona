package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PullRequestEventSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val pullRequest = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val event = PullRequestEvent(pullRequest = pullRequest)

            val newPullRequest = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val now = Instant.now()

            event.id = 1L
            event.pullRequest = newPullRequest
            event.senderLoginId = "sender"
            event.eventType = EventType.NEW_COMMENT
            event.oldValue = "old"
            event.newValue = "new"
            event.created = now

            event.id shouldBe 1L
            event.pullRequest shouldBe newPullRequest
            event.senderLoginId shouldBe "sender"
            event.eventType shouldBe EventType.NEW_COMMENT
            event.oldValue shouldBe "old"
            event.newValue shouldBe "new"
            event.created shouldBe now
        }

        it("기본값으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val pullRequest = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val event = PullRequestEvent(pullRequest = pullRequest)

            event.id shouldBe null
            event.pullRequest shouldBe pullRequest
            event.senderLoginId shouldBe null
            event.eventType shouldBe EventType.PULL_REQUEST_STATE_CHANGED
            event.oldValue shouldBe null
            event.newValue shouldBe null
        }
    }
})
