package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class IssueMassUpdateFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val form = IssueMassUpdateForm()

            form.issues shouldBe mutableListOf()
            form.state shouldBe null
            form.assignee shouldBe null
            form.milestone shouldBe null
            form.attachingLabelIds shouldBe mutableListOf()
            form.detachingLabelIds shouldBe mutableListOf()

            val issueIdForm = IssueIdForm().apply { id = 1L }
            val assigneeIdForm = AssigneeIdForm()
            val milestoneIdForm = MilestoneIdForm()

            form.issues = listOf(issueIdForm)
            form.state = "CLOSED"
            form.assignee = assigneeIdForm
            form.milestone = milestoneIdForm
            form.attachingLabelIds = listOf(1L, 2L)
            form.detachingLabelIds = listOf(3L)

            form.issues shouldBe listOf(issueIdForm)
            form.state shouldBe "CLOSED"
            form.assignee shouldBe assigneeIdForm
            form.milestone shouldBe milestoneIdForm
            form.attachingLabelIds shouldBe listOf(1L, 2L)
            form.detachingLabelIds shouldBe listOf(3L)
        }
    }

    describe("IssueIdForm 프로퍼티 접근자") {
        it("id의 getter/setter가 정상 동작해야 한다") {
            val issueIdForm = IssueIdForm()

            issueIdForm.id shouldBe null

            issueIdForm.id = 42L

            issueIdForm.id shouldBe 42L
        }
    }
})
