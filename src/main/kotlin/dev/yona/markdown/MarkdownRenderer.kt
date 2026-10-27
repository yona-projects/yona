package dev.yona.markdown

import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.owasp.html.Sanitizers
import org.springframework.stereotype.Component

/**
 * 마크다운을 정화된 HTML로 바꾸는 서버 측 렌더러이다. v1.6 `utils.Markdown.render(String)`에 해당한다.
 *
 * v1.6은 `marked.js`(옵션 `gfm`, `tables`, `breaks`, `smartLists`)로 변환한 뒤 OWASP 정책으로 정화했다.
 * v2도 같은 `marked.js`를 [MarkedEngine]에서 그대로 실행하고 같은 정화 정책을 거친다([ 결정 D-17 ], 되돌림 A-151).
 * 줄바꿈은 `<br>`로 바뀌고(`breaks: true`) 제목에는 `id`와 `head-anchor` 링크가 붙는다(`headerIds: true`).
 *
 * 변환 결과는 [RenderedMarkdownCache]에 보관해 같은 원문을 다시 변환하지 않는다. 이 클래스는 스레드 안전하다. 프로젝트 문맥이 필요한 후처리(이슈 링크, 자동 링크)는 별도 단계에서 추가한다.
 */
@Component
class MarkdownRenderer(private val cache: RenderedMarkdownCache, private val marked: MarkedEngine = MarkedEngine()) {

    /** 마크다운 [source]를 변환하고 정화한 HTML을 돌려준다. 결과는 캐시에 보관한다. 빈 문자열은 그대로 돌려준다. */
    fun render(source: String): String {
        if (source.isEmpty()) return source
        return cache.getOrRender(source) { sanitize(toHtml(source)) }
    }

    /** 마크다운을 정화하기 전의 HTML로 바꾼다. 빈 문자열은 그대로 돌려준다. */
    private fun toHtml(source: String): String = marked.render(source)

    /**
     * 프로젝트 문맥의 렌더링 파이프라인이다. v1.6 `Markdown.renderWithHighlight`에 해당한다.
     * 변환한 HTML에 [postProcess]를 적용하고 정화한 결과를 캐시에 보관한다.
     *
     * v1.6의 캐시 동작을 그대로 따른다. 이미 보관된 결과가 있으면 **그 결과를 돌려주되**, 같은 원문을 다시 변환해
     * 캐시를 새 결과로 바꾼다. 따라서 원문이 같으면 변환 결과가 바뀌어도 한 번 늦게 반영된다. 또한 프로젝트 문맥이 없는
     * [render]와 같은 캐시를 같은 키로 쓴다([refactoring-backlog.md] R-16).
     */
    fun renderWithPipeline(source: String, postProcess: (String) -> String): String {
        val render = { sanitize(postProcess(toHtml(source))) }
        val cached = cache.getIfPresent(source)
        if (cached != null) {
            cache.put(source, render())
            return cached
        }
        return render().also { cache.put(source, it) }
    }

    /** 이미 HTML인 [source]를 v1.6과 같은 정책으로 정화한다. */
    fun sanitize(source: String): String = POLICY.sanitize(source)

    private companion object {
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
