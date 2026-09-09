package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.owasp.html.Sanitizers
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URISyntaxException

@Service
class MarkdownServiceImpl(
    private val autoLinkRenderer: AutoLinkRenderer,
    private val repositoryService: RepositoryService,
    // Markdown.java의 transformIssueLink()/extractIssueLink() 대응에 필요한 의존성.
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl,
    private val messageSource: MessageSource,
    // Markdown.java의 checkReferrer()가 쓰는 application.noreferrer 설정 대응.
    // NotificationMailBodyProcessor와 동일한 설정 키/hostname 프로퍼티를 공유한다.
    @Value("\${application.noreferrer:false}")
    private val noreferrerEnabled: Boolean = false,
    @Value("\${yona.hostname:localhost}")
    private val hostname: String = "localhost"
) : MarkdownService {
    private val logger = LoggerFactory.getLogger(MarkdownServiceImpl::class.java)

    companion object {
        // yona의 utils/Markdown.java 새니타이저 정책과 동등한 allowlist.
        // 이벤트 핸들러(onclick/onload/onerror 등)와 <script>/<svg> 등은
        // 명시적으로 허용하지 않는 한 OWASP 정책상 자동으로 제거된다.
        private val SANITIZER_POLICY: PolicyFactory = Sanitizers.FORMATTING
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.BLOCKS)
            .and(
                HtmlPolicyBuilder()
                    .allowUrlProtocols("http", "https", "mailto")
                    .allowElements("video", "source", "a", "input", "pre", "br", "hr", "iframe", "ol", "span")
                    // "rel"은 checkReferrer()가 붙인 noreferrer 값이 새니타이저에서 지워지지
                    // 않도록 허용 목록에 추가 — XSS 위험이 없는 안전한 속성.
                    .allowAttributes("href", "name", "target", "rel").onElements("a")
                    .allowAttributes("src", "type", "target").onElements("source")
                    .allowAttributes(
                        "data-setup", "controls", "preload", "type", "autoplay",
                        "responsive", "height", "width", "fluid", "liveui", "src"
                    ).onElements("video")
                    .allowAttributes("type", "disabled", "checked").onElements("input")
                    .allowAttributes("start").onElements("ol")
                    .allowAttributes("width", "height", "src", "frameborder", "allow", "allowfullscreen")
                    .onElements("iframe")
                    .allowAttributes("class", "id", "style", "width", "height").globally()
                    .toFactory()
            )

        // Markdown.java의 imageLink / normalLocalLink 정규식 대응.
        // "!\[text](./path)" (이미지) / "[text](./path)" (일반 링크) 형태의 상대경로 링크만 매칭한다
        // (http:/https:/ftp:/file: 스킴이거나 절대경로는 건드리지 않음 — 원본과 동일).
        private val IMAGE_LINK_PATTERN =
            Regex("""!\[(?<text>[^]]*)]\(/?(?!https:|http:|ftp:|file:)\.\/(?<link>[^)]*)\)""")
        private val NORMAL_LOCAL_LINK_PATTERN =
            Regex("""(?<space>[^!])\[(?<text>[^]]*)]\(/?(?!https:|http:|ftp:|file:)\.\/(?<link>[^)]*)\)""")
    }

    override fun render(body: String): String {
        return render(body, true, null)
    }

    override fun render(body: String, breaks: Boolean): String {
        return render(body, breaks, null)
    }

    override fun render(body: String, breaks: Boolean, project: Project?): String {
        return render(body, breaks, project, null)
    }

    override fun render(body: String, breaks: Boolean, project: Project?, lang: String?): String {
        if (body.isEmpty()) {
            return ""
        }
        val sanitized = renderWithHighlight(body, breaks)
        return autoLinkRenderer.render(sanitized, project, lang)
    }

    // Markdown.java의 renderWithHighlight() 대응. 원본의 캐시 로직을 구조 그대로 포팅했다 —
    // source.hashCode()만 캐시 키로 쓰고 breaks는 키에 포함되지 않으며(동일 source를 breaks만
    // 바꿔 렌더링하면 캐시가 이전 breaks 결과를 돌려줄 수 있음, 원본과 동일한 특성), 캐시 히트
    // 시에도 전체 파이프라인을 다시 계산해 캐시는 갱신하되 반환값은 히트 당시의 예전 캐시 값을
    // 그대로 쓴다(원본에도 TTL이 없어 이 재계산이 사실상 무의미해 보이지만 임의로 "고치지" 않고
    // 구조 그대로 옮겼다).
    private fun renderWithHighlight(source: String, breaks: Boolean): String {
        val sourceHashCode = source.hashCode()
        val cached = MarkdownRenderCache.renderedMarkdown.getIfPresent(sourceHashCode)
        if (cached != null) {
            val sanitized = renderCore(source, breaks)
            MarkdownRenderCache.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized))
            return ZipUtil.decompress(cached)
        }
        val sanitized = renderCore(source, breaks)
        MarkdownRenderCache.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized))
        return sanitized
    }

    private fun renderCore(body: String, breaks: Boolean): String {
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
        )
        val parser = Parser.builder().extensions(extensions).build()
        val document = parser.parse(body)
        val renderer = if (breaks) {
            HtmlRenderer.builder().softbreak("<br />\n").extensions(extensions).build()
        } else {
            HtmlRenderer.builder().extensions(extensions).build()
        }
        val html = renderer.render(document)
        val referrerChecked = checkReferrer(html)
        val issueLinkTransformed = transformIssueLink(referrerChecked)
        return sanitize(issueLinkTransformed)
    }

    // Markdown.java의 checkReferrer() 대응. noreferrer 설정이 켜져 있으면 이 사이트 호스트명으로
    // "시작하지 않는"(legacy `!uri.getHost().startsWith(hostname)`, equals가 아님 — 원본 그대로)
    // 외부 링크의 href에 rel="... noreferrer"를 붙인다. 잘못된 형식의 링크는 legacy와 동일하게
    // 조용히 건너뛴다.
    private fun checkReferrer(source: String): String {
        if (!noreferrerEnabled) {
            return source
        }
        val doc = Jsoup.parse(source)
        for (el in doc.getElementsByAttribute("href")) {
            val href = el.attr("href")
            try {
                val uri = URI(href)
                if (uri.host != null && !uri.host.startsWith(hostname)) {
                    el.attr("rel", "${el.attr("rel")} noreferrer")
                }
            } catch (e: URISyntaxException) {
                // 잘못된 링크는 건너뛴다 — legacy와 동일.
            }
        }
        return doc.body().html()
    }

    // Markdown.java의 transformIssueLink() 대응. 이 사이트로 향하는(상대경로 또는 이 호스트명으로
    // 시작하는) "순수 URL" 링크(commonmark AutolinkExtension이 자동으로 앵커화한, 즉 링크 텍스트가
    // href와 동일한 것)만 이슈 링크 변환 대상으로 삼는다 — 사용자가 `[텍스트](url)`로 직접 텍스트를
    // 지정한 링크는 건드리지 않는다.
    private fun transformIssueLink(source: String): String {
        val doc = Jsoup.parse(source)
        val elements = doc.getElementsByAttribute("href")

        for (el in elements) {
            val href = el.attr("href")
            val linkText = el.text()

            try {
                val uri = URI(href)

                if ((href.startsWith("/") || (uri.host != null && uri.host.startsWith(hostname))) &&
                    linkText == href
                ) {
                    el.attr("rel", "${el.attr("rel")} noreferrer")

                    if (extractIssueLink(el, uri)) break
                }
            } catch (e: URISyntaxException) {
                // 잘못된 링크는 건너뛴다 — legacy와 동일.
            }
        }

        return doc.body().html()
    }

    // Markdown.java의 extractIssueLink() 대응. 이슈 READ 권한이 없으면 true를 반환해 호출부의
    // `break`로 문서 전체 스캔을 중단시킨다 — 이후 이슈 링크는 검사되지 않는 legacy 원본의 동작을
    // 그대로 재현한 것(의도적 최적화가 아니라 원본에 있는 그대로의 동작).
    private fun extractIssueLink(el: Element, uri: URI): Boolean {
        val path = uri.path ?: return false
        val issuePathPattern = Regex("/issue/\\d+")
        if (!issuePathPattern.containsMatchIn(path)) {
            return false
        }

        val segments = path.split("/issue/")
        if (segments.size <= 1) {
            return false
        }

        try {
            val s = segments[0].split("/")
            val owner = s[s.size - 2]
            val projectName = s[s.size - 1]
            val number = segments[1].split("/", "#", "?")[0].toLong()

            val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
                ?: return false
            val issue = issueRepository.findByProjectAndNumber(project, number)
                ?: return false

            if (!accessControl.isAllowed(resolveCurrentUser(), project, issue, Operation.READ)) {
                return true
            }

            var linkText = "#${issue.number}.${issue.title}"
            val fragment = uri.fragment
            if (fragment != null) {
                linkText += "#$fragment"
            }

            el.text("")
            el.prependText(linkText)
            el.addClass("issueLink")
            val stateStr = issue.state.state()
            el.appendElement("span")
                .addClass("issue-state")
                .addClass(stateStr)
                .text(
                    messageSource.getMessage(
                        "issue.state.$stateStr", null, stateStr, LocaleContextHolder.getLocale()
                    ) ?: stateStr
                )
        } catch (e: RuntimeException) {
            logger.warn("Issue link extraction fail: $path", e)
        }

        return false
    }

    private fun resolveCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

override fun renderFileInCodeBrowser(source: String, project: Project): String {
        val defaultBranch = getDefaultBranch(project)
        val imageLinkFiltered = replaceImageLinkPath(project, source, defaultBranch)
        return render(imageLinkFiltered, true, project)
    }

    override fun renderFileInReadme(source: String, project: Project): String {
        val defaultBranch = getDefaultBranch(project)
        val relativeLinksToCodeBrowserPath = replaceContentsLinkToCodeBrowserPath(project, source, defaultBranch)
        return render(relativeLinksToCodeBrowserPath, true, project)
    }

    private fun getDefaultBranch(project: Project): String {
        return try {
            repositoryService.getRepository(project).getDefaultBranch().removePrefix("refs/heads/")
        } catch (e: Exception) {
            "master"
        }
    }

    // Markdown.java의 replaceImageLinkPath() 대응.
    private fun replaceImageLinkPath(project: Project, text: String, defaultBranch: String): String {
        return IMAGE_LINK_PATTERN.replace(text) { m ->
            "![${m.groups["text"]!!.value}](/${project.owner}/${project.name}/files/$defaultBranch/${m.groups["link"]!!.value})"
        }
    }

    // Markdown.java의 replaceContentsLinkToCodeBrowserPath() 대응.
    private fun replaceContentsLinkToCodeBrowserPath(project: Project, text: String, defaultBranch: String): String {
        val imageFiltered = replaceImageLinkPath(project, text, defaultBranch)
        return NORMAL_LOCAL_LINK_PATTERN.replace(imageFiltered) { m ->
            "${m.groups["space"]!!.value}[${m.groups["text"]!!.value}](/${project.owner}/${project.name}/code/$defaultBranch/${m.groups["link"]!!.value})"
        }
    }

    private fun sanitizeInternal(html: String): String {
        return SANITIZER_POLICY.sanitize(html)
    }

    // Markdown.java의 sanitize() 대응.
    override fun sanitize(html: String): String {
        return sanitizeInternal(html)
    }
}
