package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PullRequestTimelineItemSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드를 읽을 수 있어야 한다") {
            val pullRequest = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val event = PullRequestEvent(pullRequest = pullRequest)
            val date = Instant.parse("2026-08-25T00:00:00Z")

            val item = PullRequestTimelineItem(date = date, event = event)

            item.date shouldBe date
            item.event shouldBe event
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val pullRequest = PullRequest(
                toProject = Project(),
                fromProject = Project(),
                contributor = User()
            )
            val event = PullRequestEvent(pullRequest = pullRequest)
            val date = Instant.parse("2026-08-25T00:00:00Z")

            val a = PullRequestTimelineItem(date = date, event = event)
            val b = PullRequestTimelineItem(date = date, event = event)
            val c = a.copy(date = Instant.parse("2026-08-26T00:00:00Z"))

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            a.component1() shouldBe date
            a.component2() shouldBe event
            a.toString() shouldBe a.toString()
        }
    }
})
