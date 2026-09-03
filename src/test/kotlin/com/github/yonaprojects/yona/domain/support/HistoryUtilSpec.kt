package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant

// yona AbstractPostingApp.addToHistory()/getHistoryMadeBy() 대응 (P2-02).
class HistoryUtilSpec : DescribeSpec({
    describe("HistoryUtil.appendHistory") {
        it("작성자 이름/아이디와 삽입·삭제 건수를 담은 헤더를 새 이력 앞에 붙여야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "Hi, there?",
                newBody = "Hello, mijeong?",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.parse("2026-01-01T00:00:00Z"),
                existingHistory = null
            )

            result shouldStartWith "<div class='history-made-by'>홍길동(gildong) "
            result shouldContain "<span class='added'>"
            result shouldContain "<span class='deleted'>"
            result shouldContain "diff-deleted"
            result shouldContain "diff-added"
        }

        it("기존 history가 있으면 새 이력 뒤에 그대로 이어붙여야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "old",
                newBody = "new",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = "PREVIOUS_HISTORY_MARKER"
            )

            result shouldEndWith "PREVIOUS_HISTORY_MARKER"
        }

        it("삽입만 있고 삭제가 없으면 삭제 배지는 포함하지 않아야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "",
                newBody = "brand new content",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            result shouldContain "<span class='added'>"
            (result.contains("<span class='deleted'>")) shouldBe false
        }

        it("originalBody가 null이면 diff 본문 없이 헤더만 기록해야 한다(yona 원본 동작)") {
            val result = HistoryUtil.appendHistory(
                originalBody = null,
                newBody = "new content",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            (result.contains("diff-added")) shouldBe false
            (result.contains("diff-deleted")) shouldBe false
        }

        it("삭제만 있고 삽입이 없으면 삽입 배지는 포함하지 않아야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "content to remove entirely",
                newBody = "",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            result shouldContain "<span class='deleted'>"
            (result.contains("<span class='added'>")) shouldBe false
        }

        it("updatedDate가 null이면 날짜 없이 기록해야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "old",
                newBody = "new",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = null,
                existingHistory = null
            )

            result shouldContain "at </div>"
        }

        it("newBody가 null이면 빈 문자열로 취급해 전체 삭제로 기록해야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "content to remove entirely",
                newBody = null,
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            result shouldContain "<span class='deleted'>"
            result shouldContain "diff-deleted"
        }

        // historyDiffText()의 EQUAL 청크가 100자를 넘으면(EQUAL_TEXT_ELLIPSIS_SIZE) 앞/뒤 50자만
        // 남기고 "..." 로 생략한다. 두 EQUAL 청크(문서 맨 앞/맨 뒤)를 만들어, 각 청크의 앞부분이
        // oldValue 전체의 시작과 일치하는지/끝부분이 oldValue 전체의 끝과 일치하는지를 판단하는
        // 4가지 내부 분기(청크1-머리 일치, 청크1-꼬리 불일치, 청크2-머리 불일치, 청크2-꼬리 일치)를
        // 한 번에 모두 태운다.
        it("공통 구간이 100자를 넘으면 앞뒤 50자만 남기고 가운데를 생략해야 한다") {
            val common1 = "A".repeat(130)
            val common2 = "B".repeat(130)
            // "OLDMID"/"NEWMID"는 꼬리 "MID"가 겹쳐 diff가 DELETE("OLD")+EQUAL("MID")로 쪼개면서
            // "OLDMID"라는 리터럴 부분문자열이 결과에 남지 않는 문제가 있어, 공통 문자가 전혀 없는
            // 대체 문자열(겹치는 글자 없음)로 교체해 DELETE/INSERT 청크가 온전히 보존되게 한다.
            val result = HistoryUtil.appendHistory(
                originalBody = common1 + "OLDXYZ" + common2,
                newBody = common1 + "NEWABC" + common2,
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            result shouldContain "diff-ellipsis"
            result shouldContain "OLDXYZ"
            result shouldContain "NEWABC"
        }

        it("공통 구간이 100자 이하면 생략 없이 그대로 남겨야 한다") {
            val common = "0123456789"
            val result = HistoryUtil.appendHistory(
                originalBody = common + "OLD" + common,
                newBody = common + "NEW" + common,
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            (result.contains("diff-ellipsis")) shouldBe false
            result shouldContain common
            result shouldContain "OLD"
            result shouldContain "NEW"
        }
    }
})
