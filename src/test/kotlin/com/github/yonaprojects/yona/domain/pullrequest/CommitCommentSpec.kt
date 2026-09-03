package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.UserIdent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class CommitCommentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val comment = CommitComment()
            val project = Project()
            val author = UserIdent(id = 1L, loginId = "gildong", name = "홍길동")
            val date = Instant.parse("2026-01-01T00:00:00Z")

            comment.id = 10L
            comment.project = project
            comment.path = "a.txt"
            comment.line = 5
            comment.side = CodeRange.Side.A
            comment.contents = "댓글 내용"
            comment.createdDate = date
            comment.author = author
            comment.commitId = "abcdef"

            comment.id shouldBe 10L
            comment.project shouldBe project
            comment.path shouldBe "a.txt"
            comment.line shouldBe 5
            comment.side shouldBe CodeRange.Side.A
            comment.contents shouldBe "댓글 내용"
            comment.createdDate shouldBe date
            comment.author shouldBe author
            comment.commitId shouldBe "abcdef"
        }
    }

    describe("hasLocation()") {
        it("path가 null이면 false여야 한다") {
            CommitComment(path = null, line = 5).hasLocation() shouldBe false
        }

        it("path가 공백뿐이면 false여야 한다") {
            CommitComment(path = "   ", line = 5).hasLocation() shouldBe false
        }

        it("path는 있지만 line이 null이면 false여야 한다") {
            CommitComment(path = "a.txt", line = null).hasLocation() shouldBe false
        }

        it("path와 line이 모두 있으면 true여야 한다") {
            CommitComment(path = "a.txt", line = 5).hasLocation() shouldBe true
        }
    }
})
