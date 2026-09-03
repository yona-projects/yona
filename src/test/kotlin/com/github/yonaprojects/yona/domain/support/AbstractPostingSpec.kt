package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// AbstractPosting은 추상 클래스(@MappedSuperclass)라 직접 인스턴스화할 수 없어, 구체 서브클래스인
// Posting으로 상속 프로퍼티 접근자를 검증한다. 순수 데이터 홀더로 분기는 없다.
class AbstractPostingSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 상속 필드를 읽고 쓸 수 있어야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val posting: AbstractPosting = Posting(project = project)

            val otherProject = Project(id = 2L, name = "other", owner = "owner2")
            val createdDate = Instant.parse("2026-01-01T00:00:00Z")
            val updatedDate = Instant.parse("2026-01-02T00:00:00Z")

            posting.id = 10L
            posting.title = "제목"
            posting.body = "본문"
            posting.history = "이력"
            posting.createdDate = createdDate
            posting.updatedDate = updatedDate
            posting.authorId = 100L
            posting.authorLoginId = "gildong"
            posting.authorName = "홍길동"
            posting.updatedByAuthorId = 200L
            posting.updatedByAuthorLoginId = "editor"
            posting.updatedByAuthorName = "편집자"
            posting.project = otherProject
            posting.number = 5L
            posting.numOfComments = 3

            posting.id shouldBe 10L
            posting.title shouldBe "제목"
            posting.body shouldBe "본문"
            posting.history shouldBe "이력"
            posting.createdDate shouldBe createdDate
            posting.updatedDate shouldBe updatedDate
            posting.authorId shouldBe 100L
            posting.authorLoginId shouldBe "gildong"
            posting.authorName shouldBe "홍길동"
            posting.updatedByAuthorId shouldBe 200L
            posting.updatedByAuthorLoginId shouldBe "editor"
            posting.updatedByAuthorName shouldBe "편집자"
            posting.project shouldBe otherProject
            posting.number shouldBe 5L
            posting.numOfComments shouldBe 3
        }

        it("기본값(모두 생략)으로도 인스턴스화할 수 있어야 한다") {
            val project = Project(id = 3L, name = "proj2", owner = "owner3")
            val posting: AbstractPosting = Posting(project = project)

            posting.id shouldBe null
            posting.title shouldBe ""
            posting.body shouldBe null
            posting.history shouldBe null
            posting.createdDate shouldBe null
            posting.updatedDate shouldBe null
            posting.authorId shouldBe null
            posting.authorLoginId shouldBe null
            posting.authorName shouldBe null
            posting.updatedByAuthorId shouldBe null
            posting.updatedByAuthorLoginId shouldBe null
            posting.updatedByAuthorName shouldBe null
            posting.number shouldBe null
            posting.numOfComments shouldBe 0
        }
    }
})
