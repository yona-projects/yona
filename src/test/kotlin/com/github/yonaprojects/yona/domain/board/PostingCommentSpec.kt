package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Instant

class PostingCommentSpec : DescribeSpec({
    describe("PostingComment") {
        it("객체를 생성하고 속성을 설정할 수 있어야 한다") {
            val project = mockk<Project>()
            val posting = Posting(id = 1L, project = project)
            val parentComment = PostingComment(id = 2L, posting = posting)
            val now = Instant.now()
            
            val comment = PostingComment(
                id = 1L,
                contents = "Test Content",
                createdDate = now,
                authorId = 100L,
                authorLoginId = "user1",
                authorName = "User One",
                projectId = 1L,
                posting = posting,
                parentComment = parentComment
            )

            comment.id shouldBe 1L
            comment.contents shouldBe "Test Content"
            comment.posting shouldBe posting
            comment.parentComment shouldBe parentComment
            comment.projectId shouldBe 1L
        }

        it("posting/parentComment를 생성 후에도 재할당할 수 있어야 한다") {
            val project = mockk<Project>()
            val posting = Posting(id = 1L, project = project)
            val otherPosting = Posting(id = 2L, project = project)
            val comment = PostingComment(id = 1L, posting = posting)
            val otherParent = PostingComment(id = 3L, posting = posting)

            comment.posting = otherPosting
            comment.parentComment = otherParent

            comment.posting shouldBe otherPosting
            comment.parentComment shouldBe otherParent
        }
    }
})
