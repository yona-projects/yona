package com.github.yonaprojects.yona.domain.notification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

// yona models/NotificationMail.java의 handleLinks()/handleImages()/removeHeadAnchor() 대응 (P1-27).
class NotificationMailBodyProcessorSpec : DescribeSpec({
    describe("process") {
        it("상대경로 링크를 baseUrl 기준 절대경로로 바꾼다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<a href="/issue/1">link</a>""")

            result shouldContain """href="https://yona.example.com/issue/1""""
        }

        it("noreferrer 설정이 켜져 있고 외부 호스트로 가는 링크면 rel=noreferrer를 붙인다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="https://external.example.com/page">link</a>""")

            result shouldContain "noreferrer"
        }

        it("noreferrer 설정이 켜져 있어도 같은 호스트로 가는 링크에는 붙이지 않는다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="https://yona.example.com/issue/1">link</a>""")

            result shouldNotContain "noreferrer"
        }

        it("이미지에 max-width 스타일을 추가하고 새 탭으로 여는 링크로 감싼다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<img src="https://yona.example.com/img.png">""")

            result shouldContain "max-width:1024px"
            result shouldContain """<a href="https://yona.example.com/img.png" target="_blank""""
        }

        it("head-anchor 앵커 텍스트를 제거한다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<a class="head-anchor">#</a>""")

            result shouldNotContain "head-anchor\">#</a>"
        }

        // handleLinks()의 catch(e: Exception) 분기 — URI로 파싱할 수 없는(공백 포함) 값이면
        // 예외를 로깅만 하고 원래 값을 그대로 둔 채 계속 진행해야 한다.
        it("URI로 파싱할 수 없는 값이면 예외를 로깅만 하고 원래 값을 그대로 둔다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="http://example.com/path with spaces">link</a>""")

            result shouldContain """href="http://example.com/path with spaces""""
        }

        // handleLinks()의 uri.host == null 분기(호스트가 없는 상대경로) — noreferrer가 켜져 있어도
        // 절대경로로 재작성된 뒤에도 자기 자신에게 rel=noreferrer를 붙이지 않아야 한다.
        it("호스트가 없는 상대경로 href는 noreferrer가 켜져 있어도 붙이지 않는다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="/issue/1">link</a>""")

            result shouldNotContain "noreferrer"
        }

        // noreferrerEnabled && attrName == "href" 복합조건 — noreferrerEnabled=true여도 attrName이
        // "src"(img 태그)이면 두 번째 피연산자가 false가 되어 noreferrer 대상이 아니어야 한다.
        // 기존 이미지 테스트는 noreferrerEnabled=false로만 실행돼 이 조합이 비어 있었다.
        it("noreferrer 설정이 켜져 있어도 img의 src 속성에는 noreferrer 판정 자체를 하지 않는다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<img src="https://external.example.com/img.png">""")

            result shouldNotContain "noreferrer"
        }

        // uriString.startsWith("/") false 분기 — "/"로 시작하지 않는 상대경로는 앞에 "/"를 붙여
        // 이어붙여야 한다.
        it("슬래시로 시작하지 않는 상대경로 href도 baseUrl 뒤에 슬래시를 붙여 절대경로로 바꾼다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<a href="issue/1">link</a>""")

            result shouldContain """href="https://yona.example.com/issue/1""""
        }
    }
})
