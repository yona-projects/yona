package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Instant

class PostingSpec : DescribeSpec({
    describe("Posting") {
        it("객체를 생성하고 속성을 설정할 수 있어야 한다") {
            val project = mockk<Project>()
            val now = Instant.now()
            val parentPosting = Posting(id = 2L, project = project)
            val label = mockk<IssueLabel>()
            
            val posting = Posting(
                id = 1L,
                title = "Test Title",
                body = "Test Body",
                history = "Test History",
                createdDate = now,
                updatedDate = now,
                authorId = 100L,
                authorLoginId = "user1",
                authorName = "User One",
                updatedByAuthorId = 100L,
                updatedByAuthorLoginId = "user1",
                updatedByAuthorName = "User One",
                project = project,
                number = 1L,
                numOfComments = 5,
                notice = true,
                readme = false,
                parent = parentPosting,
                labels = mutableSetOf(label)
            )

            posting.id shouldBe 1L
            posting.title shouldBe "Test Title"
            posting.notice shouldBe true
            posting.readme shouldBe false
            posting.parent shouldBe parentPosting
            posting.labels.size shouldBe 1
            posting.labels.first() shouldBe label
        }

        it("notice/readme/parent/labels를 생성 후에도 재할당할 수 있어야 한다") {
            val project = mockk<Project>()
            val posting = Posting(id = 1L, project = project)
            val newParent = Posting(id = 2L, project = project)
            val newLabel = mockk<IssueLabel>()

            posting.notice = true
            posting.readme = true
            posting.parent = newParent
            posting.labels = mutableSetOf(newLabel)

            posting.notice shouldBe true
            posting.readme shouldBe true
            posting.parent shouldBe newParent
            posting.labels shouldBe mutableSetOf(newLabel)
        }
    }
})
