package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import java.util.Locale

// yona views/common/notificationMail.scala.html 대응 (P1-27).
class NotificationMailRendererSpec : DescribeSpec({
    val messageSource = mockk<MessageSource>()
    val bodyProcessor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)
    val renderer = NotificationMailRenderer(messageSource, bodyProcessor, "https://yona.example.com", "Yona")
    val locale = Locale.KOREAN

    beforeTest {
        every { messageSource.getMessage(any<String>(), any(), any<Locale>()) } answers {
            val args = secondArg<Array<Any>?>().orEmpty()
            firstArg<String>() + args.joinToString("") { it.toString() }
        }
        every { messageSource.getMessage(any<String>(), any(), any<String>(), any()) } answers { firstArg() }
    }

    describe("render") {
        it("본문 메시지를 그대로 포함해야 한다") {
            val html = renderer.render("본문 내용", null, ResourceType.ISSUE_POST, "1", false, locale)

            html shouldContain "본문 내용"
        }

        it("urlToView가 있으면 회신 가능 여부에 따라 다른 안내 메시지 키를 쓴다") {
            every { messageSource.getMessage("notification.linkToViewHtml", any(), locale) } returns "일반 링크"
            every { messageSource.getMessage("notification.replyOrLinkToViewHtml", any(), locale) } returns "회신 가능 링크"

            val htmlNoReply = renderer.render("내용", "https://yona.example.com/issue/1", ResourceType.ISSUE_POST, "1", false, locale)
            val htmlWithReply = renderer.render("내용", "https://yona.example.com/issue/1", ResourceType.ISSUE_POST, "1", true, locale)

            htmlNoReply shouldContain "일반 링크"
            htmlWithReply shouldContain "회신 가능 링크"
        }

        it("urlToView가 없으면 링크 안내를 넣지 않는다") {
            val html = renderer.render("내용", null, ResourceType.ISSUE_POST, "1", false, locale)

            html shouldNotContain "notification.linkToViewHtml"
        }

        it("리소스 unwatch 링크에 resourceType/resourceId를 담아야 한다") {
            val html = renderer.render("내용", null, ResourceType.BOARD_POST, "42", false, locale)

            html shouldContain "resource.type=BOARD_POST"
            html shouldContain "resource.id=42"
        }

        // getMessage(code, args, locale) ?: "Unwatch" / ?: "Notification settings" 두 엘비스는
        // 구조적으로 도달 불가능하다: MockK로 `every { messageSource.getMessage(...) } answers { null }`
        // (와 `returns null`)을 시도했더니 Kotlin 컴파일러가 "Null cannot be a value of a non-null
        // type 'String'"로 거부했다 — Spring MessageSource의 이 3-인자 오버로드는 Kotlin이 보는
        // 시그니처상 non-null String을 반환하도록 선언돼 있어(누락 시 NoSuchMessageException을
        // 던짐, null을 반환하지 않음), 실제로 null을 만들어낼 방법이 없다.
    }

    describe("renderPlain") {
        it("고정 안내 문구를 덧붙인다(legacy와 동일하게 urlToView/lang과 무관)") {
            val plain = renderer.renderPlain("본문")

            plain shouldContain "본문"
            plain shouldContain "Yona"
        }
    }
})
