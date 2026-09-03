package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// Comment는 추상 클래스(@MappedSuperclass)라 직접 인스턴스화할 수 없어, 구체 서브클래스인
// PostingComment로 상속 프로퍼티 접근자를 검증한다. 순수 데이터 홀더로 분기는 없다.
class CommentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 상속 필드를 읽고 쓸 수 있어야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val posting = Posting(project = project)
            val comment: Comment = PostingComment(posting = posting)

            val createdDate = Instant.parse("2026-01-01T00:00:00Z")

            comment.id = 10L
            comment.contents = "댓글 내용"
            comment.createdDate = createdDate
            comment.authorId = 100L
            comment.authorLoginId = "gildong"
            comment.authorName = "홍길동"
            comment.projectId = 1L

            comment.id shouldBe 10L
            comment.contents shouldBe "댓글 내용"
            comment.createdDate shouldBe createdDate
            comment.authorId shouldBe 100L
            comment.authorLoginId shouldBe "gildong"
            comment.authorName shouldBe "홍길동"
            comment.projectId shouldBe 1L
        }

        it("기본값(모두 생략)으로도 인스턴스화할 수 있어야 한다") {
            val project = Project(id = 2L, name = "proj2", owner = "owner2")
            val posting = Posting(project = project)
            val comment: Comment = PostingComment(posting = posting)

            comment.id shouldBe null
            comment.contents shouldBe ""
            comment.createdDate shouldBe null
            comment.authorId shouldBe null
            comment.authorLoginId shouldBe null
            comment.authorName shouldBe null
            comment.projectId shouldBe null
        }
    }
})
