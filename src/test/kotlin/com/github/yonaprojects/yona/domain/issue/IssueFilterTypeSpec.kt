package com.github.yonaprojects.yona.domain.issue

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona models/enumeration/IssueFilterType.java:16-23 getValue() 대응 (P2-52).
class IssueFilterTypeSpec : DescribeSpec({
    describe("IssueFilterType.getValue") {
        it("assigned/created/mentioned/favorite/all 값을 각 enum으로 변환해야 한다") {
            IssueFilterType.getValue("assigned") shouldBe IssueFilterType.ASSIGNED
            IssueFilterType.getValue("created") shouldBe IssueFilterType.CREATED
            IssueFilterType.getValue("mentioned") shouldBe IssueFilterType.MENTIONED
            IssueFilterType.getValue("favorite") shouldBe IssueFilterType.FAVORITE
            IssueFilterType.getValue("all") shouldBe IssueFilterType.ALL
        }

        it("매칭되는 값이 없으면 IllegalArgumentException을 던져야 한다") {
            val exception = shouldThrow<IllegalArgumentException> {
                IssueFilterType.getValue("kakao")
            }
            exception.message shouldBe "No matching issue filter type found for [kakao]"
        }
    }
})
