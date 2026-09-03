package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class IssueFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val form = IssueForm()

            form.title shouldBe ""
            form.body shouldBe ""
            form.assigneeLoginId shouldBe null
            form.milestoneId shouldBe null
            form.dueDate shouldBe null
            form.labelIds shouldBe null
            form.parentIssueId shouldBe null
            form.targetProjectId shouldBe null

            form.title = "제목"
            form.body = "본문"
            form.assigneeLoginId = "login1"
            form.milestoneId = 1L
            form.dueDate = "2026-08-25"
            form.labelIds = listOf(1L, 2L)
            form.parentIssueId = 3L
            form.targetProjectId = 4L

            form.title shouldBe "제목"
            form.body shouldBe "본문"
            form.assigneeLoginId shouldBe "login1"
            form.milestoneId shouldBe 1L
            form.dueDate shouldBe "2026-08-25"
            form.labelIds shouldBe listOf(1L, 2L)
            form.parentIssueId shouldBe 3L
            form.targetProjectId shouldBe 4L
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val a = IssueForm(title = "제목", body = "본문")
            val b = IssueForm(title = "제목", body = "본문")
            val c = a.copy(title = "다른제목")

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            c.title shouldBe "다른제목"
            c.body shouldBe "본문"

            a.component1() shouldBe "제목"
            a.component2() shouldBe "본문"
            a.component3() shouldBe null
            a.component4() shouldBe null
            a.component5() shouldBe null
            a.component6() shouldBe null
            a.component7() shouldBe null
            a.component8() shouldBe null

            a.toString() shouldBe a.toString()
        }
    }
})
