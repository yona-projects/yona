package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PullRequestMergeEventSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드를 읽을 수 있어야 한다") {
            val sender = User(id = 1L)
            val event = PullRequestMergeEvent(pullRequestId = 10L, sender = sender, isNewPullRequest = true)

            event.pullRequestId shouldBe 10L
            event.sender shouldBe sender
            event.isNewPullRequest shouldBe true
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val sender = User(id = 1L)
            val a = PullRequestMergeEvent(pullRequestId = 10L, sender = sender, isNewPullRequest = true)
            val b = PullRequestMergeEvent(pullRequestId = 10L, sender = sender, isNewPullRequest = true)
            val c = a.copy(isNewPullRequest = false)

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            a.component1() shouldBe 10L
            a.component2() shouldBe sender
            a.component3() shouldBe true
            a.toString() shouldBe a.toString()
        }
    }
})
