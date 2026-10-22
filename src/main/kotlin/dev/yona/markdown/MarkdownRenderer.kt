package dev.yona.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.owasp.html.Sanitizers
import org.springframework.stereotype.Component

/**
 * 마크다운을 정화된 HTML로 바꾸는 서버 측 렌더러이다. v1.6 `utils.Markdown.render(String)`에 해당한다.
 *
 * v1.6은 `marked.js`(옵션 `gfm`, `tables`, `breaks`, `smartLists`)로 변환한 뒤 OWASP 정책으로 정화했다.
 * v2는 `commonmark-java`에 GFM 확장을 붙여 같은 역할을 수행한다([ 결정 D-17 ]). 줄바꿈은 `<br>`로 바꾸고(`breaks: true`),
 * 제목에는 `id`를 붙이며(`headerIds: true`), 결과는 v1.6과 같은 정화 정책을 거친다.
 *
 * 이 클래스는 스레드 안전하다. 프로젝트 문맥이 필요한 후처리(이슈 링크, 자동 링크)는 별도 단계에서 추가한다.
 */
@Component
class MarkdownRenderer {

    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TaskListItemsExtension.create(),
        HeadingAnchorExtension.create()
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .softbreak("<br>\n")
        .build()

    /** 마크다운 [source]를 변환하고 정화한 HTML을 돌려준다. 빈 문자열은 그대로 돌려준다. */
    fun render(source: String): String {
        if (source.isEmpty()) return source
        return sanitize(matchYobiMarkup(htmlRenderer.render(parser.parse(source))))
    }

    /**
     * v1.6의 `marked.js`가 커스터마이즈된 마크업에 맞춘다.
     *
     * - 제목: `id`에 `yb-header-` 접두어를 붙이고 끝에 `<a href="#id" class="head-anchor">#</a>`를 덧붙인다.
     * - 작업 목록: 항목에 `task-list-item`, 체크박스에 `task-list-item-checkbox` 클래스를 주고 `disabled`는 붙이지 않는다.
     */
    private fun matchYobiMarkup(html: String): String {
        val body = Jsoup.parseBodyFragment(html).body()
        body.select("h1[id],h2[id],h3[id],h4[id],h5[id],h6[id]").forEach { heading ->
            val id = HEADER_ID_PREFIX + heading.id()
            heading.attr("id", id)
            heading.appendElement("a").attr("href", "#$id").addClass("head-anchor").text("#")
        }
        body.select("li > input[type=checkbox]").forEach { checkbox ->
            checkbox.removeAttr("disabled").addClass("task-list-item-checkbox")
            checkbox.parent()?.addClass("task-list-item")
        }
        return body.html()
    }

    /** 이미 HTML인 [source]를 v1.6과 같은 정책으로 정화한다. */
    fun sanitize(source: String): String = POLICY.sanitize(source)

    private companion object {
        const val HEADER_ID_PREFIX = "yb-header-"

        /** v1.6 `Markdown.sanitizerPolicy`와 같은 허용 목록이다. */
        val POLICY: PolicyFactory = Sanitizers.FORMATTING
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.BLOCKS)
            .and(
                HtmlPolicyBuilder()
                    .allowUrlProtocols("http", "https", "mailto", "file", "zpl")
                    .allowElements("video", "source", "a", "input", "pre", "br", "hr", "iframe", "ol", "span")
                    .allowAttributes("href", "name", "target").onElements("a")
                    .allowAttributes("src", "type", "target").onElements("source")
                    .allowAttributes(
                        "data-setup", "controls", "preload", "type", "autoplay", "responsive",
                        "height", "width", "fluid", "liveui", "src"
                    ).onElements("video")
                    .allowAttributes("type", "disabled", "checked").onElements("input")
                    .allowAttributes("start").onElements("ol")
                    .allowAttributes("width", "height", "src", "frameborder", "allow", "allowfullscreen").onElements("iframe")
                    .allowAttributes("class", "id", "style", "width", "height").globally()
                    .toFactory()
            )
    }
}
