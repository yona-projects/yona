package com.github.yonaprojects.yona.domain.notification

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI

/**
 * yona `models/NotificationMail.java`의 `handleLinks()`/`handleImages()`/`removeHeadAnchor()`
 * 대응 (P1-27). 알림 메일 HTML 본문을 발송 직전에 후처리한다.
 */
@Component
class NotificationMailBodyProcessor(
    @Value("\${yona.hostname:localhost}")
    private val hostname: String,
    @Value("\${yona.base-url:}")
    private val baseUrl: String,
    @Value("\${application.noreferrer:false}")
    private val noreferrerEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(NotificationMailBodyProcessor::class.java)

    fun process(html: String): String {
        val doc = Jsoup.parse(html)
        handleLinks(doc)
        handleImages(doc)
        doc.outputSettings().prettyPrint(false)
        return removeHeadAnchor(doc.html())
    }

    // 모든 링크를 절대경로로 만들고, 필요하면 rel=noreferrer를 붙인다.
    fun handleLinks(doc: Document) {
        val attrNames = arrayOf("src", "href")

        for (attrName in attrNames) {
            for (tag in doc.select("*[$attrName]")) {
                var isNoreferrerRequired = false
                val uriString = tag.attr(attrName)

                if (noreferrerEnabled && attrName == "href") {
                    isNoreferrerRequired = true
                }

                try {
                    val uri = URI(uriString)

                    if (!uri.isAbsolute) {
                        tag.attr(attrName, baseUrl + (if (uriString.startsWith("/")) uriString else "/$uriString"))
                    }

                    if (uri.host == null || uri.host == hostname) {
                        isNoreferrerRequired = false
                    }
                } catch (e: Exception) {
                    logger.info("A malformed URI is detected while checking an email to send", e)
                }

                if (isNoreferrerRequired) {
                    tag.attr("rel", "${tag.attr("rel")} noreferrer")
                }
            }
        }
    }

    fun handleImages(doc: Document) {
        for (img: Element in doc.select("img")) {
            img.attr("style", "max-width:1024px;" + img.attr("style"))
            img.wrap("""<a href="${img.attr("src")}" target="_blank" style="border:0;outline:0;"></a>""")
        }
    }

    private fun removeHeadAnchor(html: String): String {
        return html.replace(Regex("head-anchor\">#</a>"), "\"></a>")
    }
}
