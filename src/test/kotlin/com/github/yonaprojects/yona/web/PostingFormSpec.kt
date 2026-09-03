package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PostingFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val form = PostingForm()

            form.title shouldBe ""
            form.body shouldBe ""
            form.notice shouldBe false
            form.readme shouldBe false
            form.temporaryUploadFiles shouldBe null
            form.sendNotificationMail shouldBe false
            form.issueTemplate shouldBe null
            form.path shouldBe null
            form.branch shouldBe null
            form.lineEnding shouldBe null

            form.title = "제목"
            form.body = "본문"
            form.notice = true
            form.readme = true
            form.temporaryUploadFiles = "a.png,b.png"
            form.sendNotificationMail = true
            form.issueTemplate = "true"
            form.path = "docs/a.md"
            form.branch = "main"
            form.lineEnding = "UNIX"

            form.title shouldBe "제목"
            form.body shouldBe "본문"
            form.notice shouldBe true
            form.readme shouldBe true
            form.temporaryUploadFiles shouldBe "a.png,b.png"
            form.sendNotificationMail shouldBe true
            form.issueTemplate shouldBe "true"
            form.path shouldBe "docs/a.md"
            form.branch shouldBe "main"
            form.lineEnding shouldBe "UNIX"
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val a = PostingForm(title = "제목", body = "본문")
            val b = PostingForm(title = "제목", body = "본문")
            val c = a.copy(title = "다른제목")

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            c.title shouldBe "다른제목"
            c.body shouldBe "본문"

            a.component1() shouldBe "제목"
            a.component2() shouldBe "본문"
            a.component3() shouldBe false
            a.component4() shouldBe false
            a.component5() shouldBe null
            a.component6() shouldBe false
            a.component7() shouldBe null
            a.component8() shouldBe null
            a.component9() shouldBe null
            a.component10() shouldBe null

            a.toString() shouldBe a.toString()
        }
    }
})
