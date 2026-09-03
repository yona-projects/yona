package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CommitResponseSpec : DescribeSpec({
    describe("CommitResponse") {
        val base = CommitResponse(
            id = "abc123",
            shortId = "abc123",
            message = "커밋 메시지",
            shortMessage = "커밋 메시지",
            authorName = "홍길동",
            authorEmail = "gildong@example.com",
            authorDate = 1000L,
            committerName = "홍길동",
            committerEmail = "gildong@example.com",
            committerDate = 1000L
        )

        it("모든 프로퍼티를 생성자로 설정한 그대로 읽을 수 있어야 한다") {
            base.id shouldBe "abc123"
            base.shortId shouldBe "abc123"
            base.message shouldBe "커밋 메시지"
            base.shortMessage shouldBe "커밋 메시지"
            base.authorName shouldBe "홍길동"
            base.authorEmail shouldBe "gildong@example.com"
            base.authorDate shouldBe 1000L
            base.committerName shouldBe "홍길동"
            base.committerEmail shouldBe "gildong@example.com"
            base.committerDate shouldBe 1000L
        }

        it("message/authorName/authorEmail/committerName/committerEmail이 null이어도 정상 생성돼야 한다") {
            val nullable = CommitResponse(
                id = "def456",
                shortId = "def456",
                message = null,
                shortMessage = "",
                authorName = null,
                authorEmail = null,
                authorDate = 0L,
                committerName = null,
                committerEmail = null,
                committerDate = 0L
            )

            nullable.message shouldBe null
            nullable.authorName shouldBe null
            nullable.authorEmail shouldBe null
            nullable.committerName shouldBe null
            nullable.committerEmail shouldBe null
        }

        it("동일 인스턴스 및 모든 필드가 같은 다른 인스턴스는 equals()가 true여야 한다") {
            val same = CommitResponse(
                id = "abc123", shortId = "abc123", message = "커밋 메시지", shortMessage = "커밋 메시지",
                authorName = "홍길동", authorEmail = "gildong@example.com", authorDate = 1000L,
                committerName = "홍길동", committerEmail = "gildong@example.com", committerDate = 1000L
            )

            (base == base) shouldBe true
            (base == same) shouldBe true
            base.hashCode() shouldBe same.hashCode()
        }

        it("필드가 다르면 equals()가 false여야 한다") {
            val different = base.copy(id = "other")

            (base == different) shouldBe false
        }

        it("null 및 다른 타입과 비교하면 false여야 한다") {
            base.equals(null) shouldBe false
            base.equals("not a commit response") shouldBe false
        }

        it("copy()로 일부 필드만 바꾼 새 인스턴스를 만들 수 있어야 한다") {
            val copied = base.copy(shortMessage = "변경된 메시지")

            copied.shortMessage shouldBe "변경된 메시지"
            copied.id shouldBe base.id
        }

        it("toString()이 예외 없이 문자열을 반환해야 한다") {
            base.toString().isNotEmpty() shouldBe true
        }

        it("componentN()으로 각 필드를 구조 분해할 수 있어야 한다") {
            val (
                id, shortId, message, shortMessage, authorName,
                authorEmail, authorDate, committerName, committerEmail, committerDate
            ) = base

            id shouldBe base.id
            shortId shouldBe base.shortId
            message shouldBe base.message
            shortMessage shouldBe base.shortMessage
            authorName shouldBe base.authorName
            authorEmail shouldBe base.authorEmail
            authorDate shouldBe base.authorDate
            committerName shouldBe base.committerName
            committerEmail shouldBe base.committerEmail
            committerDate shouldBe base.committerDate
        }
    }
})
