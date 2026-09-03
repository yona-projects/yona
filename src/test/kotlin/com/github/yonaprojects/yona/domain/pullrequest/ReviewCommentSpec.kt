package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.user.UserIdent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ReviewCommentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val comment = ReviewComment()

            comment.id shouldBe null
            comment.contents shouldBe ""
            comment.author shouldBe null
            comment.thread shouldBe null

            val author = UserIdent(id = 1L, loginId = "login", name = "이름")
            val thread = CodeCommentThread()
            val createdDate = Instant.parse("2026-08-25T00:00:00Z")

            comment.id = 10L
            comment.contents = "댓글 내용"
            comment.createdDate = createdDate
            comment.author = author
            comment.thread = thread

            comment.id shouldBe 10L
            comment.contents shouldBe "댓글 내용"
            comment.createdDate shouldBe createdDate
            comment.author shouldBe author
            comment.thread shouldBe thread
        }
    }
})
