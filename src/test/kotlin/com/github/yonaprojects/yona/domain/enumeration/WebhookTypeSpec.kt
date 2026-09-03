package com.github.yonaprojects.yona.domain.enumeration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class WebhookTypeSpec : DescribeSpec({
    describe("WebhookType") {
        it("각 값의 value/name이 정확해야 한다") {
            WebhookType.SIMPLE.value shouldBe 0
            WebhookType.DETAIL_SLACK.value shouldBe 1
            WebhookType.DETAIL_HANGOUT_CHAT.value shouldBe 2
            WebhookType.JSON.value shouldBe 3
        }

        it("valueOf()/values()가 정상 동작해야 한다") {
            WebhookType.valueOf("SIMPLE") shouldBe WebhookType.SIMPLE
            WebhookType.values().size shouldBe 4
        }
    }
})
