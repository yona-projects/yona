package dev.yona.markdown

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException

/**
 * 렌더링한 HTML 속 이슈 주소 링크의 문구를 `#번호.제목`과 상태 배지로 바꾼다.
 * v1.6 `utils.Markdown.transformIssueLink`, `extractIssueLink`에 해당한다.
 *
 * 대상은 `/`로 시작하는 링크, 또는 호스트가 이 사이트이고 링크 문구가 주소와 같은 링크이다. 대상 링크에는 모두
 * `rel`에 `noreferrer`가 붙는다. 주소가 `.../이슈/번호` 꼴이고 읽을 수 있는 이슈이면 문구를 바꾸고 `issueLink`
 * 클래스와 `issue-state` 배지를 더한다.
 *
 * v1.6의 동작을 그대로 따른다. 읽을 권한이 없는 이슈 링크를 만나면 나머지 링크는 처리하지 않는다
 * ([refactoring-backlog.md] R-15).
 */
@Component
class IssueLinkTransformer(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val hostname: SiteHostname,
    private val messages: MessageSource
) {

    /** [html]의 이슈 링크를 변환한다. */
    fun transform(html: String): String {
        val host = hostname.name()
        val body = HtmlDocuments.parse(html).body()
        for (element in body.getElementsByAttribute("href")) {
            val href = element.attr("href")
            val linkText = element.text()
            try {
                val uri = URI(href)
                if (href.startsWith("/") || (uri.host != null && uri.host.startsWith(host) && linkText == href)) {
                    element.attr("rel", element.attr("rel") + " noreferrer")
                    if (extractIssueLink(element, uri)) break
                }
            } catch (e: URISyntaxException) {
                // 잘못된 링크는 건너뛴다.
            }
        }
        return body.html()
    }

    /** 이슈 링크이면 문구를 바꾼다. 읽을 수 없는 이슈이면 `true`를 돌려 반복을 멈추게 한다. */
    private fun extractIssueLink(element: Element, uri: URI): Boolean {
        val path = uri.path ?: return false
        if (!ISSUE_PATH.containsMatchIn(path)) return false
        try {
            val segments = path.split("/issue/")
            if (segments.size > 1) {
                val owners = segments[0].split("/")
                val owner = owners[owners.size - 2]
                val projectName = owners[owners.size - 1]
                val number = segments[1].toLong()

                val project = projectService.findByOwnerAndProjectName(owner, projectName)!!
                val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number)!!

                if (!accessControl.isAllowed(currentUserProvider.get().user, issue.asResource(), Operation.READ)) return true

                var text = "#${issue.number}.${issue.title}"
                uri.fragment?.let { text += "#$it" }

                val state = issue.state
                val key = "issue.state.${state?.state}"
                val stateLabel = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key
                element.text("")
                element.prependText(text)
                element.addClass("issueLink")
                element.appendElement("span")
                    .addClass("issue-state")
                    .addClass(state?.state?.lowercase().orEmpty())
                    .text(stateLabel)
            }
        } catch (e: RuntimeException) {
            // 이슈를 찾지 못했거나 번호가 잘못된 링크는 바꾸지 않고 다음 링크로 넘어간다.
        }
        return false
    }

    private companion object {
        val ISSUE_PATH = Regex("/issue/\\d+")
    }
}
