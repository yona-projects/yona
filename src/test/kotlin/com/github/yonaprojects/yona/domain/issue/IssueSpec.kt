package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class IssueSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("Issue 자신의 필드 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val issue = Issue(project = project)

            val milestone = Milestone(project = project)
            val assignee = Assignee(user = User(id = 2L), project = project)
            val parent = Issue(project = project)
            val label = IssueLabel(
                category = IssueLabelCategory(project = project),
                project = project
            )
            val voter = User(id = 3L)
            val dueDate = Instant.parse("2026-08-25T00:00:00Z")

            val sharer = IssueSharer(issue = issue, user = User(id = 4L), loginId = "sharer")

            issue.state = State.CLOSED
            issue.dueDate = dueDate
            issue.milestone = milestone
            issue.assignee = assignee
            issue.parent = parent
            issue.weight = 5
            issue.isDraft = true
            issue.labels = mutableSetOf(label)
            issue.voters = mutableSetOf(voter)
            issue.sharers = mutableSetOf(sharer)

            issue.state shouldBe State.CLOSED
            issue.dueDate shouldBe dueDate
            issue.milestone shouldBe milestone
            issue.assignee shouldBe assignee
            issue.parent shouldBe parent
            issue.weight shouldBe 5
            issue.isDraft shouldBe true
            issue.labels shouldBe mutableSetOf(label)
            issue.voters shouldBe mutableSetOf(voter)
            issue.sharers shouldBe mutableSetOf(sharer)
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val issue = Issue(project = project)

            issue.state shouldBe State.OPEN
            issue.dueDate shouldBe null
            issue.milestone shouldBe null
            issue.assignee shouldBe null
            issue.parent shouldBe null
            issue.weight shouldBe 0
            issue.isDraft shouldBe false
            issue.labels shouldBe mutableSetOf()
            issue.voters shouldBe mutableSetOf()
            issue.sharers shouldBe mutableSetOf()
        }
    }
})
