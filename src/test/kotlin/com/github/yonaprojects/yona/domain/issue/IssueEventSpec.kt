package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class IssueEventSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(name = "p", owner = "owner")
            val issue = Issue(project = project)
            val otherIssue = Issue(project = project)
            val now = Instant.now()

            val event = IssueEvent(issue = issue)

            event.id = 1L
            event.issue = otherIssue
            event.senderLoginId = "login"
            event.senderEmail = "a@b.com"
            event.oldValue = "old"
            event.newValue = "new"
            event.created = now
            event.eventType = EventType.NEW_COMMENT

            event.id shouldBe 1L
            event.issue shouldBe otherIssue
            event.senderLoginId shouldBe "login"
            event.senderEmail shouldBe "a@b.com"
            event.oldValue shouldBe "old"
            event.newValue shouldBe "new"
            event.created shouldBe now
            event.eventType shouldBe EventType.NEW_COMMENT
        }

        it("기본값으로 생성하면 senderLoginId/senderEmail/oldValue/newValue는 null, eventType은 ISSUE_REFERRED_FROM_COMMIT이어야 한다") {
            val project = Project(name = "p", owner = "owner")
            val issue = Issue(project = project)

            val event = IssueEvent(issue = issue)

            event.id shouldBe null
            event.issue shouldBe issue
            event.senderLoginId shouldBe null
            event.senderEmail shouldBe null
            event.oldValue shouldBe null
            event.newValue shouldBe null
            event.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
        }
    }
})
