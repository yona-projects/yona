package com.github.yonaprojects.yona.domain.issue

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class RecentIssueSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val recentIssue = RecentIssue()

            val now = Instant.now()
            recentIssue.id = 1L
            recentIssue.userId = 2L
            recentIssue.issueId = 3L
            recentIssue.postingId = 4L
            recentIssue.title = "제목"
            recentIssue.url = "/project/owner/name/issue/1"
            recentIssue.createdDate = now

            recentIssue.id shouldBe 1L
            recentIssue.userId shouldBe 2L
            recentIssue.issueId shouldBe 3L
            recentIssue.postingId shouldBe 4L
            recentIssue.title shouldBe "제목"
            recentIssue.url shouldBe "/project/owner/name/issue/1"
            recentIssue.createdDate shouldBe now
        }

        it("issueId/postingId는 nullable이라 null로도 설정 가능해야 한다") {
            val recentIssue = RecentIssue(issueId = null, postingId = null)

            recentIssue.issueId shouldBe null
            recentIssue.postingId shouldBe null
        }

        it("기본값만으로 생성 가능해야 한다") {
            val recentIssue = RecentIssue()

            recentIssue.id shouldBe null
            recentIssue.userId shouldBe 0L
            recentIssue.issueId shouldBe null
            recentIssue.postingId shouldBe null
            recentIssue.title shouldBe ""
            recentIssue.url shouldBe ""
        }
    }
})
