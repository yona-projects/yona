package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * yona `views/common/notificationMail.scala.html` 대응 (P1-27). 렌더링 시점에 이미 locale이
 * 확정돼 있으므로(수신자별 언어로 미리 나뉜 뒤 호출됨), Thymeleaf 템플릿 엔진을 거치지 않고
 * 문자열을 직접 구성한다 — DiffUtil/HistoryUtil과 동일한 접근.
 */
@Component
class NotificationMailRenderer(
    private val messageSource: MessageSource,
    private val bodyProcessor: NotificationMailBodyProcessor,
    @Value("\${yona.base-url:}")
    private val baseUrl: String,
    @Value("\${yona.site-name:Yona}")
    private val siteName: String
) {
    // yona getRenderedMail() + handleLinks/handleImages 대응.
    fun render(message: String, urlToView: String?, resourceType: ResourceType, resourceId: String, acceptsReply: Boolean, locale: Locale): String {
        val fontFamily = "'Helvetica Neue','Helvetica','Arial','나눔고딕','NanumGothic','NanumGothicOTF','Apple SD Gothic Neo','맑은 고딕',sans-serif"

        val html = StringBuilder()
        html.append("""<div style="font-family:$fontFamily;">""")
        html.append(message)
        html.append("</div>")
        html.append("""<hr style="border:0; border-bottom:1px solid #ddd; margin:20px 0;">""")

        if (!urlToView.isNullOrBlank()) {
            val key = if (acceptsReply) "notification.replyOrLinkToViewHtml" else "notification.linkToViewHtml"
            html.append(messageSource.getMessage(key, arrayOf(siteName, urlToView, "_blank"), locale))
        }

        val unwatchUrl = "$baseUrl/unwatch?resource.type=${resourceType.name}&resource.id=$resourceId"
        val unwatchAnchor = footerLink(unwatchUrl, messageSource.getMessage("notification.unwatch", null, locale) ?: "Unwatch")
        val settingsUrl = "$baseUrl/user/editform/notifications"
        val settingsAnchor = footerLink(settingsUrl, messageSource.getMessage("userinfo.changeNotifications", null, locale) ?: "Notification settings")

        html.append("""<div style="max-width:410px;margin-top:20px;color:#989898;text-align:justify;word-break:break-all;font-size:11px;font-family:$fontFamily;">""")
        html.append(messageSource.getMessage("notification.off.unwatch", arrayOf(unwatchAnchor), locale))
        html.append("<br>")
        html.append(messageSource.getMessage("notification.off.settings", arrayOf(settingsAnchor), locale))
        html.append("</div>")

        return bodyProcessor.process(html.toString())
    }

    // yona getPlainMessage(lang, message, urlToView, acceptsReply)의 실제 동작 그대로: urlToView/lang과
    // 무관하게 고정 안내 문구만 덧붙인다(legacy 원본에도 두 인자는 실질적으로 쓰이지 않는다).
    fun renderPlain(message: String): String {
        return "$message\n\n Yona 에서 자세히 보거나 혹은 이 메일에 직접 회신하실 수도 있습니다."
    }

    private fun footerLink(link: String, anchorText: String): String {
        return """<a href="$link" target="_blank" style="color:#4399e2; text-decoration:underline;">$anchorText</a>"""
    }
}
