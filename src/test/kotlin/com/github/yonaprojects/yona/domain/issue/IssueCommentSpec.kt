package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class IssueCommentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드(상속 필드 포함)의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val issue = Issue(project = project)
            val comment = IssueComment(issue = issue)

            val otherIssue = Issue(project = project)
            val parentComment = IssueComment(issue = issue)
            val voter = User(id = 2L)
            val createdDate = Instant.parse("2026-08-25T00:00:00Z")

            comment.id = 10L
            comment.contents = "댓글 내용"
            comment.createdDate = createdDate
            comment.authorId = 100L
            comment.authorLoginId = "gildong"
            comment.authorName = "홍길동"
            comment.projectId = 1L
            comment.issue = otherIssue
            comment.parentComment = parentComment
            comment.voters = mutableSetOf(voter)

            comment.id shouldBe 10L
            comment.contents shouldBe "댓글 내용"
            comment.createdDate shouldBe createdDate
            comment.authorId shouldBe 100L
            comment.authorLoginId shouldBe "gildong"
            comment.authorName shouldBe "홍길동"
            comment.projectId shouldBe 1L
            comment.issue shouldBe otherIssue
            comment.parentComment shouldBe parentComment
            comment.voters shouldBe mutableSetOf(voter)
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val issue = Issue(project = project)
            val comment = IssueComment(issue = issue)

            comment.id shouldBe null
            comment.contents shouldBe ""
            comment.parentComment shouldBe null
            comment.voters shouldBe mutableSetOf()
        }
    }
})
