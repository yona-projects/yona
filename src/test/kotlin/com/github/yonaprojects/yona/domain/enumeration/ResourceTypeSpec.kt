package com.github.yonaprojects.yona.domain.enumeration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ResourceTypeSpec : DescribeSpec({
    describe("resource()") {
        it("생성자로 전달된 내부 문자열 값을 그대로 반환해야 한다") {
            ResourceType.ISSUE_POST.resource() shouldBe "issue_post"
            ResourceType.NOT_A_RESOURCE.resource() shouldBe ""
        }
    }

    describe("asPathSegment()") {
        it("ISSUE_POST는 issue로 매핑되어야 한다") {
            ResourceType.ISSUE_POST.asPathSegment() shouldBe "issue"
        }

        it("BOARD_POST는 post로 매핑되어야 한다") {
            ResourceType.BOARD_POST.asPathSegment() shouldBe "post"
        }

        it("COMMENT_THREAD는 review로 매핑되어야 한다") {
            ResourceType.COMMENT_THREAD.asPathSegment() shouldBe "review"
        }

        it("COMMIT은 commit으로 매핑되어야 한다") {
            ResourceType.COMMIT.asPathSegment() shouldBe "commit"
        }

        it("그 외 타입은 내부 값을 그대로 반환해야 한다(else 분기)") {
            ResourceType.PROJECT.asPathSegment() shouldBe "project"
            ResourceType.WEBHOOK.asPathSegment() shouldBe "webhook"
        }
    }

    describe("ResourceType.getValue()") {
        it("일치하는 값이 있으면 해당 enum 상수를 반환해야 한다") {
            ResourceType.getValue("issue_post") shouldBe ResourceType.ISSUE_POST
            ResourceType.getValue("webhook") shouldBe ResourceType.WEBHOOK
        }

        it("일치하는 값이 없으면 IllegalArgumentException을 던져야 한다") {
            shouldThrow<IllegalArgumentException> {
                ResourceType.getValue("no-such-resource-type")
            }
        }
    }

    describe("Kotlin이 자동 생성하는 values()/valueOf()") {
        it("values()는 모든 상수를 포함해야 한다") {
            ResourceType.values() shouldBe ResourceType.entries.toTypedArray()
        }

        it("valueOf()는 이름으로 상수를 조회할 수 있어야 한다") {
            ResourceType.valueOf("ISSUE_POST") shouldBe ResourceType.ISSUE_POST
        }
    }
})
