package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class HistoryDtoSpec : DescribeSpec({
    describe("HistoryDto") {
        it("기본값으로 생성할 수 있어야 한다") {
            val dto = HistoryDto()

            dto.who shouldBe ""
            dto.userPageUrl shouldBe "#"
            dto.userAvatarUrl shouldBe "/images/default-avatar-64.png"
            dto.where shouldBe ""
            dto.what shouldBe ""
            dto.how shouldBe ""
            dto.shortTitle shouldBe ""
            dto.url shouldBe ""
        }

        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val dto = HistoryDto()
            val now = Instant.parse("2026-01-01T00:00:00Z")

            dto.who = "홍길동"
            dto.userPageUrl = "/users/gildong"
            dto.userAvatarUrl = "/images/gildong.png"
            dto.whenInstant = now
            dto.where = "yona/project"
            dto.what = "commit"
            dto.how = "created"
            dto.shortTitle = "커밋 메시지"
            dto.url = "/yona/project/commit/abcdef"

            dto.who shouldBe "홍길동"
            dto.userPageUrl shouldBe "/users/gildong"
            dto.userAvatarUrl shouldBe "/images/gildong.png"
            dto.whenInstant shouldBe now
            dto.where shouldBe "yona/project"
            dto.what shouldBe "commit"
            dto.how shouldBe "created"
            dto.shortTitle shouldBe "커밋 메시지"
            dto.url shouldBe "/yona/project/commit/abcdef"
        }

        it("모든 필드가 생성자 인자로 채워져 생성할 수 있어야 한다") {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val dto = HistoryDto(
                who = "홍길동",
                userPageUrl = "/users/gildong",
                userAvatarUrl = "/images/gildong.png",
                whenInstant = now,
                where = "yona/project",
                what = "issue",
                how = "updated",
                shortTitle = "이슈 제목",
                url = "/yona/project/issues/1"
            )

            dto.who shouldBe "홍길동"
            dto.what shouldBe "issue"
        }

        it("equals()/hashCode()는 모든 필드가 같으면 동등해야 한다") {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val a = HistoryDto(who = "a", whenInstant = now)
            val b = HistoryDto(who = "a", whenInstant = now)
            val c = HistoryDto(who = "b", whenInstant = now)

            (a == b) shouldBe true
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
        }

        it("copy()로 일부 필드만 바꾼 복사본을 만들 수 있어야 한다") {
            val original = HistoryDto(who = "홍길동", what = "commit")
            val copied = original.copy(what = "issue")

            copied.who shouldBe "홍길동"
            copied.what shouldBe "issue"
            (original == copied) shouldBe false
        }

        it("toString()에 필드 값이 포함되어야 한다") {
            val dto = HistoryDto(who = "홍길동")

            dto.toString() shouldBe dto.toString()
            (dto.toString().contains("홍길동")) shouldBe true
        }

        it("componentN() 구조 분해가 정상 동작해야 한다") {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val dto = HistoryDto(
                who = "홍길동",
                userPageUrl = "/users/gildong",
                userAvatarUrl = "/images/gildong.png",
                whenInstant = now,
                where = "yona/project",
                what = "commit",
                how = "created",
                shortTitle = "제목",
                url = "/url"
            )

            val (who, userPageUrl, userAvatarUrl, whenInstant, where, what, how, shortTitle, url) = dto

            who shouldBe "홍길동"
            userPageUrl shouldBe "/users/gildong"
            userAvatarUrl shouldBe "/images/gildong.png"
            whenInstant shouldBe now
            where shouldBe "yona/project"
            what shouldBe "commit"
            how shouldBe "created"
            shortTitle shouldBe "제목"
            url shouldBe "/url"
        }
    }
})
