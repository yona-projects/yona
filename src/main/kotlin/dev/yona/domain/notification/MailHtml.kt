package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import dev.yona.util.SiteUrls
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException

/**
 * 알림 메일 HTML을 마무리한다. v1.6 `NotificationMail.getRenderedMail`, `handleLinks`, `handleImages`, `removeHeadAnchor`
 * (`app/models/NotificationMail.java:540-640`)에 해당한다.
 *
 * 메일은 사이트 밖에서 열리므로 모든 `src`, `href`를 절대 주소로 바꾸고, 설정에 따라 외부 링크에 `rel="noreferrer"`를 붙인다.
 * 이미지는 최대 너비를 제한하고 원본을 새 창으로 여는 링크로 감싼다. 일부 메일 서비스가 일반 텍스트 대체 본문을 흉하게 보여 주므로
 * 서식용 공백은 넣지 않는다.
 */
@Component
class MailHtml(private val siteUrls: SiteUrls, private val properties: YonaProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** [content] HTML을 메일용으로 마무리하고 머리 앵커의 `#`을 지운다. */
    fun finish(content: String): String {
        val doc = Jsoup.parse(content)
        handleLinks(doc)
        handleImages(doc)
        doc.outputSettings().prettyPrint(false)
        return removeHeadAnchor(doc.html())
    }

    private fun handleLinks(doc: Document) {
        // v1.6 Config.getHostname(): 설정이 없으면 서버의 기본 호스트 이름이다
        val hostname = dev.yona.markdown.SiteHostname(properties).name()
        for (attrName in listOf("src", "href")) {
            for (tag in doc.select("*[$attrName]")) {
                var noreferrerRequired = properties.noreferrer && attrName == "href"
                val value = tag.attr(attrName)
                try {
                    val uri = URI(value)
                    if (!uri.isAbsolute) tag.attr(attrName, siteUrls.absolute(value))
                    if (uri.host == null || uri.host == hostname) noreferrerRequired = false
                } catch (e: URISyntaxException) {
                    log.info("메일 본문에서 잘못된 주소를 발견했다: {}", value, e)
                }
                if (noreferrerRequired) tag.attr("rel", tag.attr("rel") + " noreferrer")
            }
        }
    }

    private fun handleImages(doc: Document) {
        for (img in doc.select("img")) {
            img.attr("style", "max-width:1024px;" + img.attr("style"))
            img.wrap("<a href=\"${img.attr("src")}\" target=\"_blank\" style=\"border:0;outline:0;\"></a>")
        }
    }

    companion object {
        /** 제목 앵커(`head-anchor`)의 `#` 글자를 지운다 (v1.6 `removeHeadAnchor`). */
        fun removeHeadAnchor(html: String): String = html.replace("head-anchor\">#</a>", "\"></a>")
    }
}
