package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import dev.yona.util.SiteUrls
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 알림 메일 HTML의 링크와 이미지 후처리를 검증한다.
 *
 * 원본: `NotificationMail.handleLinks`, `handleImages`, `removeHeadAnchor`(`app/models/NotificationMail.java:560-640`).
 */
class MailHtmlTest {

    private fun html(noreferrer: Boolean = false, context: String = "") =
        MailHtml(SiteUrls(YonaProperties(hostname = "yona.example.com", scheme = "https", noreferrer = noreferrer, applicationContext = context)), YonaProperties(hostname = "yona.example.com", noreferrer = noreferrer))

    @Test
    fun `상대 링크와 이미지 주소를 절대 주소로 바꾼다`() {
        val result = html().finish("<a href=\"/a/b\">x</a><img src=\"/files/1.png\">")
        assertTrue(result.contains("href=\"https://yona.example.com/a/b\""), result)
        assertTrue(result.contains("src=\"https://yona.example.com/files/1.png\""), result)
    }

    @Test
    fun `이미 절대 주소인 링크는 그대로 둔다`() {
        assertTrue(html().finish("<a href=\"https://other.com/x\">x</a>").contains("href=\"https://other.com/x\""))
    }

    @Test
    fun `noreferrer를 켜면 외부 링크에만 rel을 붙인다`() {
        val result = html(noreferrer = true).finish("<a href=\"https://other.com/x\">외부</a><a href=\"https://yona.example.com/y\">내부</a><a href=\"/z\">상대</a>")
        assertTrue(result.contains("<a href=\"https://other.com/x\" rel=\" noreferrer\">"), result)
        assertTrue(result.contains("<a href=\"https://yona.example.com/y\">"), result)
        assertTrue(result.contains("<a href=\"https://yona.example.com/z\">"), result)
    }

    @Test
    fun `이미지는 최대 너비를 주고 원본을 여는 링크로 감싼다`() {
        val result = html().finish("<img src=\"https://other.com/a.png\" style=\"border:1px\">")
        assertTrue(result.contains("<a href=\"https://other.com/a.png\" target=\"_blank\" style=\"border:0;outline:0;\"><img src=\"https://other.com/a.png\" style=\"max-width:1024px;border:1px\"></a>"), result)
    }

    @Test
    fun `제목 앵커의 머리 글자와 클래스 이름을 함께 지운다`() {
        assertEquals("<a class=\"\"></a>", MailHtml.removeHeadAnchor("<a class=\"head-anchor\">#</a>"))
    }

    @Test
    fun `서식을 정리하지 않은 HTML 문서를 돌려준다`() {
        val result = html().finish("<p>a</p>\n<p>b</p>")
        assertTrue(result.startsWith("<html>"), result)
        assertTrue(result.contains("<p>a</p>\n<p>b</p>"), result)
    }
}
