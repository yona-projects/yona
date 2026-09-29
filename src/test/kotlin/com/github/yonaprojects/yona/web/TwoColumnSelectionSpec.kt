package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.ui.ConcurrentModel

class TwoColumnSelectionSpec : DescribeSpec({
    describe("복합 선택 키 파싱") {
        it("type:owner/project/number 형식을 분해해야 한다") {
            TwoColumnSelection.parseKey("issue:yona/hello/12") shouldBe
                TwoColumnSelection.Key("issue", "yona", "hello", 12L)
        }

        it("프로젝트 이름에 점과 하이픈이 있어도 분해해야 한다") {
            TwoColumnSelection.parseKey("post:my-org/proj.name-1/3") shouldBe
                TwoColumnSelection.Key("post", "my-org", "proj.name-1", 3L)
        }

        it("null이거나 형식이 잘못된 값은 null이어야 한다") {
            TwoColumnSelection.parseKey(null).shouldBeNull()
            TwoColumnSelection.parseKey("").shouldBeNull()
            TwoColumnSelection.parseKey("12").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona/hello").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona/hello/abc").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona/hello/0").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona/hello/-1").shouldBeNull()
            TwoColumnSelection.parseKey(":yona/hello/1").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona//1").shouldBeNull()
            TwoColumnSelection.parseKey("issue:yona/hello/1/2").shouldBeNull()
        }

        it("키를 다시 문자열로 만들면 원본과 같아야 한다") {
            TwoColumnSelection.Key("pull", "yona", "hello", 7L).toString() shouldBe "pull:yona/hello/7"
        }
    }

    describe("선택 파라미터명 설정") {
        it("기본 파라미터 selected를 제거한 URL을 모델에 담아야 한다") {
            val request = MockHttpServletRequest("GET", "/o/p/board").apply { queryString = "page=2&selected=5" }
            val model = ConcurrentModel()

            TwoColumnSelection.addToModel(request, model, 5L)

            model.getAttribute("selectionClearUrl") shouldBe "/o/p/board?page=2"
            model.getAttribute("selectionBaseUrl") shouldBe "/o/p/board?page=2&"
            model.getAttribute("selectionParam") shouldBe "selected"
        }

        it("지정한 파라미터명만 제거하고 selected는 그대로 보존해야 한다") {
            val request = MockHttpServletRequest("GET", "/user/issues").apply { queryString = "selected=tab&detail=issue%3Ao%2Fp%2F1" }
            val model = ConcurrentModel()

            TwoColumnSelection.addToModel(request, model, "issue:o/p/1", param = "detail")

            model.getAttribute("selectionClearUrl") shouldBe "/user/issues?selected=tab"
            model.getAttribute("selectionBaseUrl") shouldBe "/user/issues?selected=tab&"
            model.getAttribute("selected") shouldBe "issue:o/p/1"
            model.getAttribute("selectionParam") shouldBe "detail"
        }
    }
})
