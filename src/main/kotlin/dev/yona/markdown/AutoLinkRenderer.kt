package dev.yona.markdown

import dev.yona.util.LegacyText
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import org.eclipse.jgit.revwalk.RevWalk
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 렌더링한 HTML 속의 참조를 링크로 바꾼다. v1.6 `utils.AutoLinkRenderer`에 해당한다.
 *
 * | 참조 | 변환 |
 * |---|---|
 * | `소유자/프로젝트#번호`, `소유자#번호`, `#번호` | 이슈 링크(`issueLink` 클래스, 상태 배지 포함) |
 * | `소유자/프로젝트@SHA`, `소유자@SHA`, `@SHA`, `SHA` | 짧은 SHA로 보이는 커밋 링크 |
 * | `@사용자`, `@조직` | 사용자 링크(소개 팝오버), 조직 링크 |
 * | `@소유자/프로젝트` | 프로젝트 링크 |
 *
 * 대상이 실제로 있을 때만 바꾸고, `code`와 `a` 요소 안의 글, 단어 문자에 붙어 있는 참조는 건드리지 않는다.
 * 변환은 위 표의 순서대로 차례로 수행하며 매번 HTML을 다시 해석한다.
 *
 * v1.6의 동작을 그대로 따른다. 다른 프로젝트의 이슈를 참조하면 링크 문구에 접두어가 두 번 붙고, 링크 문구에
 * `$`나 `\`가 들어 있으면 치환 문자열 규칙에 걸리는 점이 그렇다([refactoring-backlog.md] R-15).
 */
@Component
class AutoLinkRenderer(
    private val projectService: ProjectService,
    private val menuSettings: ProjectMenuSettingRepository,
    private val issues: IssueRepository,
    private val users: UserRepository,
    private val organizations: OrganizationRepository,
    private val repositories: RepositoryService,
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider,
    private val messages: MessageSource
) {

    private data class Link(val href: String?, val className: String?, val displayName: String?) {
        /** v1.6 `Link.toString`: 값이 없으면 빈 문자열로 쓴다. */
        override fun toString(): String = "<a href='${href.orEmpty()}' class='${className.orEmpty()}'>${displayName.orEmpty()}</a>"
    }

    private fun interface ToLink {
        /** 일치한 참조를 링크로 바꾼다. 바꿀 수 없으면 `null`이다. */
        fun toLink(matcher: Matcher): Link?
    }

    /**
     * [body]의 참조를 링크로 바꿔 돌려준다.
     *
     * @param project 글이 속한 프로젝트
     * @param lang 보는 사람의 언어 코드. 비어 있으면 현재 사용자의 언어를 쓴다
     */
    fun render(body: String, project: Project, lang: String?): String {
        var html = body
        html = parse(html, PATH_WITH_ISSUE_PATTERN) { m ->
            val path = m.group(1)
            issueLink(path, projectFromPath(path, project), m.group(2))
        }
        html = parse(html, ISSUE_PATTERN) { m -> issueLink("", project, m.group(1)) }
        html = parse(html, PATH_WITH_SHA_PATTERN) { m ->
            val path = m.group(1)
            shaLink(path, projectFromPath(path, project), m.group(2))
        }
        html = parse(html, SHA_PATTERN) { m -> shaLink("", project, m.group(1)) }
        html = parse(html, LOGIN_ID_PATTERN) { m ->
            val path = m.group(1)
            val slash = path.indexOf('/')
            if (slash > -1) projectLink(path.substring(0, slash), path.substring(slash + 1)) else userLink(path, lang)
        }
        return html
    }

    private fun parse(body: String, pattern: Pattern, toLink: ToLink): String {
        val doc = Jsoup.parse(body)
        doc.outputSettings().prettyPrint(false)
        doc.getElementsMatchingOwnText(pattern).forEach { element ->
            if (isIgnoreElement(element)) return@forEach
            element.textNodes().forEach { node ->
                val result = convertLink(node.toString(), pattern, toLink)
                node.text("")
                node.after(result)
            }
        }
        return doc.body().html()
    }

    private fun convertLink(text: String, pattern: Pattern, toLink: ToLink): String {
        val matcher = pattern.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            if (isWrappedNonCharacter(text, matcher)) continue
            val link = toLink.toLink(matcher)
            // v1.6은 링크 HTML을 치환 문자열 규칙 그대로 넘겨 `$`나 `\`가 든 제목에서 예외가 난다(R-105). 오류는 v2가 처리하므로 글자 그대로 넣는다.
            if (link != null) matcher.appendReplacement(sb, Matcher.quoteReplacement(link.toString()))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun projectFromPath(path: String, project: Project): Project? {
        val slash = path.indexOf('/')
        // 소유자와 같은 이름의 프로젝트는 이름을 생략할 수 있다 (GitHub의 참조 방식).
        return if (slash > -1) projectService.findByOwnerAndProjectName(path.substring(0, slash), path.substring(slash + 1))
        else projectService.findByOwnerAndProjectName(path, project.name.orEmpty())
    }

    private fun issueLink(prefix: String, project: Project?, issueNumber: String): Link? {
        project ?: return null
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), issueNumber.toLong()) ?: return null
        var linkText = "#$issueNumber.${issue.title}"
        // v1.6는 접두어가 있을 때 링크 문구에 접두어와 문구를 한 번 더 붙여 문구가 두 번 나왔다(R-15-1). 접두어는 반환할 때 한 번만 붙인다
        val state = issue.state
        val label = state?.let { messages.getMessage("issue.state.${it.state}", null, "issue.state.${it.state}", LocaleContextHolder.getLocale()) }
        linkText += "<span class='issue-state ${state?.state?.lowercase()}'>$label</span>"
        return Link(issueUrl(issue.project ?: project, requireNotNull(issue.number)), "issueLink", prefix + linkText)
    }

    private fun shaLink(prefix: String, project: Project?, sha: String): Link? {
        project ?: return null
        val codeAvailable = menuSettings.findFirstByProjectId(requireNotNull(project.id))?.code != false
        if (!codeAvailable || project.vcs != RepositoryService.VCS_GIT) return null
        return try {
            repositories.openRepository(project).use { repository ->
                val id = repository.resolve(sha) ?: return null
                RevWalk(repository).use { walk ->
                    val commit = walk.parseCommit(id)
                    val shortId = commit.abbreviate(SHORT_ID_LENGTH).name()
                    val href = "${projectUrl(project)}/commit/${commit.name}"
                    Link(href, null, if (prefix.isEmpty()) shortId else "$prefix@$shortId")
                }
            }
        } catch (e: java.io.IOException) {
            null
        }
    }

    private fun userLink(loginId: String, lang: String?): Link? {
        val org = organizations.findByNameIgnoreCase(loginId)
        if (org != null) return Link("/organizations/${segment(org.name)}", null, "<span class='org-link'>@${org.name}</span>")

        val user = users.findByLoginId(loginId) ?: return null
        val avatarUrl = avatars.avatarUrl(user, AVATAR_SIZE)
        val avatarImage = if (avatarUrl == AvatarService.DEFAULT_AVATAR_URL) "" else
            "<img src='$avatarUrl' class='avatar-wrap smaller no-margin-no-padding vertical-top' alt='@${user.name} ${user.loginId}'> "
        val userName = pureName(user, lang)
        val content = escapeHtml(avatarImage + user.name + " " + user.loginId)
        return Link(
            "/${segment(user.loginId)}", "no-text-decoration user-link",
            "<span data-bs-toggle='popover' data-bs-placement='top' data-bs-trigger='hover' data-bs-html='true' data-bs-content=\"$content\">@$userName</span>"
        )
    }

    private fun projectLink(ownerName: String, projectName: String): Link? {
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName) ?: return null
        return Link(projectUrl(project), null, "<span class='project-link'>@${project.owner}/${project.name}</span>")
    }

    /**
     * 사용자 이름에서 부서 부분을 뗀 이름이다 (v1.6 `User.getPureNameOnly`). [lang]이 비어 있으면 현재 사용자의 언어(없으면 `ko-KR`)로
     * 판단하고 이름을 자른 경우에만 다듬는다(`trim`).
     */
    private fun pureName(user: User, lang: String?): String {
        val english = user.englishName
        if (lang.isNullOrBlank()) {
            val viewerLang = currentUserProvider.get().user?.lang ?: DEFAULT_LANG
            if ((english != null && LegacyText.isNotBlank(english)) && user.lang != null && viewerLang.startsWith("en")) return english
            // v1.6 `getPureNameOnly()`는 이름을 자를 때만 trim한다. 언어를 받는 `getPureNameOnly(lang)`는 trim하지 않는다
            val name = user.name.orEmpty()
            val cut = cutAtDepartment(name)
            return if (cut.length != name.length) cut.trim() else cut
        }
        if ((english != null && LegacyText.isNotBlank(english)) && user.lang != null && lang.startsWith("en")) return english
        return cutAtDepartment(user.name.orEmpty())
    }

    private fun cutAtDepartment(name: String): String {
        var result = name
        for (splitter in listOf("[", "(")) {
            if (result.contains(splitter)) result = name.substring(0, name.indexOf(splitter))
        }
        return result
    }

    private fun issueUrl(project: Project, number: Long) = "${projectUrl(project)}/issue/$number"

    private fun projectUrl(project: Project) = "/${segment(project.owner)}/${segment(project.name)}"

    private fun segment(value: String?) = UriUtils.encodePathSegment(value.orEmpty(), Charsets.UTF_8)

    private fun escapeHtml(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun isIgnoreElement(element: Element) = element.tagName().uppercase() in IGNORE_TAG_NAMES

    /** 일치한 부분의 앞이나 뒤가 단어 문자(영문, 숫자, 밑줄)이면 참조가 아니라고 본다. */
    private fun isWrappedNonCharacter(body: String, matcher: Matcher): Boolean =
        (matcher.start() != 0 && WORD.matcher(body.substring(matcher.start() - 1, matcher.start())).find()) ||
            (matcher.end() != body.length && WORD.matcher(body.substring(matcher.end(), matcher.end() + 1)).find())

    private companion object {
        const val PATH = "[a-zA-Z0-9-_.가-힣/]+"
        const val ISSUE = "\\d+"
        const val SHA = "[a-f0-9]{7,40}"
        const val SHORT_ID_LENGTH = 7
        const val AVATAR_SIZE = 64
        const val DEFAULT_LANG = "ko-KR"

        val PATH_WITH_ISSUE_PATTERN: Pattern = Pattern.compile("@?($PATH)#($ISSUE)")
        val ISSUE_PATTERN: Pattern = Pattern.compile("#($ISSUE)")
        val PATH_WITH_SHA_PATTERN: Pattern = Pattern.compile("($PATH)@?($SHA)")
        val SHA_PATTERN: Pattern = Pattern.compile("@?($SHA)")
        val LOGIN_ID_PATTERN: Pattern = Pattern.compile("@($PATH)")
        val WORD: Pattern = Pattern.compile("\\w")
        val IGNORE_TAG_NAMES = setOf("CODE", "A")
    }
}
