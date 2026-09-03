package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

@Component
class AutoLinkRenderer(
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val repositoryService: RepositoryService,
    private val messageSource: MessageSource
) {
    companion object {
        private const val PATH_PATTERN_STR = "[a-zA-Z0-9-_.가-힣/]+"
        private const val ISSUE_PATTERN_STR = "\\d+"
        private const val SHA_PATTERN_STR = "[a-f0-9]{7,40}"

        private val PATH_WITH_ISSUE_PATTERN = Pattern.compile("@?($PATH_PATTERN_STR)#($ISSUE_PATTERN_STR)")
        private val ISSUE_PATTERN = Pattern.compile("#($ISSUE_PATTERN_STR)")

        private val PATH_WITH_SHA_PATTERN = Pattern.compile("($PATH_PATTERN_STR)@?($SHA_PATTERN_STR)")
        private val SHA_PATTERN = Pattern.compile("@?($SHA_PATTERN_STR)")

        private val LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN = Pattern.compile("@($PATH_PATTERN_STR)")

        private val IGNORE_TAGNAME = arrayOf("CODE", "A")

        private val WORD_PATTERN = Pattern.compile("\\w")
    }

    private class Link {
        val href: String?
        val className: String?
        val displayName: String?

        constructor() {
            this.href = null
            this.className = null
            this.displayName = null
        }

        constructor(href: String, displayName: String) {
            this.href = href
            this.className = null
            this.displayName = displayName
        }

        constructor(href: String, className: String, displayName: String) {
            this.href = href
            this.className = className
            this.displayName = displayName
        }

        override fun toString(): String {
            val h = href ?: ""
            val c = className ?: ""
            val d = displayName ?: ""
            return "<a href='$h' class='$c'>$d</a>"
        }

        fun isValid(): Boolean {
            return href != null
        }
    }

    private fun interface ToLink {
        fun toLink(matcher: Matcher): Link
    }

    fun render(body: String, currentProject: Project?, lang: String? = null): String {
        var resultHtml = body

        // 1. Path with Issue
        resultHtml = parse(resultHtml, PATH_WITH_ISSUE_PATTERN) { matcher ->
            val path = matcher.group(1)
            val issueNumber = matcher.group(2)
            val project = getProjectFromPath(path, currentProject)
            toValidIssueLink(path, project, issueNumber)
        }

        // 2. Issue only
        resultHtml = parse(resultHtml, ISSUE_PATTERN) { matcher ->
            toValidIssueLink("", currentProject, matcher.group(1))
        }

        // 3. Path with SHA
        resultHtml = parse(resultHtml, PATH_WITH_SHA_PATTERN) { matcher ->
            val path = matcher.group(1)
            val sha = matcher.group(2)
            val project = getProjectFromPath(path, currentProject)
            toValidSHALink(path, project, sha)
        }

        // 4. SHA only
        resultHtml = parse(resultHtml, SHA_PATTERN) { matcher ->
            toValidSHALink(currentProject, matcher.group(1))
        }

        // 5. User / Org / Project Link
        resultHtml = parse(resultHtml, LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN) { matcher ->
            val path = matcher.group(1)
            val slashIndex = path.indexOf("/")
            if (slashIndex > -1) {
                toValidProjectLink(path.substring(0, slashIndex), path.substring(slashIndex + 1))
            } else {
                toValidUserLink(path, lang)
            }
        }

        return resultHtml
    }

    private fun parse(html: String, pattern: Pattern, toLink: ToLink): String {
        val doc = Jsoup.parseBodyFragment(html)
        val settings = doc.outputSettings()
        settings.prettyPrint(false)

        val elements = doc.getElementsMatchingOwnText(pattern)
        for (el in elements) {
            if (isIgnoreElement(el)) {
                continue
            }
            val textNodeList = el.textNodes()
            for (node in textNodeList) {
                val nodeText = node.toString()
                val converted = convertLink(nodeText, pattern, toLink)
                if (converted != nodeText) {
                    node.text("")
                    node.after(converted)
                }
            }
        }
        return doc.body().html()
    }

    private fun convertLink(text: String, pattern: Pattern, toLink: ToLink): String {
        val matcher = pattern.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            if (isWrappedNonCharacter(text, matcher)) {
                continue
            }
            val link = toLink.toLink(matcher)
            if (link.isValid()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(link.toString()))
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun getProjectFromPath(path: String, currentProject: Project?): Project? {
        val slashIndex = path.indexOf("/")
        return if (slashIndex > -1) {
            projectRepository.findByOwnerAndName(path.substring(0, slashIndex), path.substring(slashIndex + 1)).orElse(null)
        } else {
            if (currentProject != null) {
                projectRepository.findByOwnerAndName(path, currentProject.name).orElse(null)
            } else {
                null
            }
        }
    }

    private fun toValidIssueLink(prefix: String, project: Project?, issueNumber: String): Link {
        if (project != null) {
            val num = issueNumber.toLongOrNull() ?: return Link()
            val issue = issueRepository.findByProjectAndNumber(project, num)
            if (issue != null) {
                val locale = LocaleContextHolder.getLocale()
                val stateStr = issue.state.state()
                val stateLocalized = messageSource.getMessage("issue.state.$stateStr", null, stateStr, locale)
                var linkText = "#$issueNumber.${issue.title}"
                if (prefix.isNotEmpty()) {
                    linkText = prefix + linkText
                }
                linkText += "<span class='issue-state ${stateStr.lowercase()}'>$stateLocalized</span>"
                val url = "/${project.owner}/${project.name}/issue/${issue.number}"
                return Link(url, "issueLink", linkText)
            }
        }
        return Link()
    }

    private fun toValidSHALink(project: Project?, sha: String): Link {
        return toValidSHALink("", project, sha)
    }

    private fun toValidSHALink(prefix: String, project: Project?, sha: String): Link {
        if (project != null) {
            try {
                // yona utils/AutoLinkRenderer.java:275 "!project.isCodeAvailable() || !project.isGit()"
                // 대응 (P2-35). isCodeAvailable()(코드브라우저 메뉴 활성 여부, legacy
                // `menuSetting == null || menuSetting.code` == yona `project.isCodeEnabled`)이 꺼져
                // 있으면 GIT이어도 커밋 SHA를 링크로 바꾸지 않는다.
                val vcs = project.vcs?.uppercase() ?: "GIT"
                if (!project.isCodeEnabled || vcs != "GIT") {
                    return Link()
                }
                val repo = repositoryService.getRepository(project)
                val commit = repo.getCommit(sha)
                if (commit != null) {
                    val url = "/${project.owner}/${project.name}/commit/${commit.getId()}"
                    return if (prefix.isEmpty()) {
                        Link(url, commit.getShortId())
                    } else {
                        Link(url, "$prefix@${commit.getShortId()}")
                    }
                }
            } catch (e: Exception) {
                return Link()
            }
        }
        return Link()
    }

    private fun toValidUserLink(userId: String, lang: String? = null): Link {
        val userOpt = userRepository.findByLoginId(userId)
        val orgOpt = organizationRepository.findByName(userId)

        if (orgOpt.isPresent) {
            val org = orgOpt.get()
            return Link("/org/${org.name}", "<span class='org-link'>@${org.name}</span>")
        }

        if (userOpt.isPresent) {
            val user = userOpt.get()
            if (user.id == null || user.loginId == "anonymous") {
                return Link()
            }
            val avatarImage = if (user.avatarUrl == "/assets/images/default-avatar-128.png") {
                ""
            } else {
                "<img src='${user.avatarUrl}' class='avatar-wrap smaller no-margin-no-padding vertical-top' alt='@${user.name} ${user.loginId}'> "
            }
            // yona AutoLinkRenderer.java:322-327 대응 (P1-140) — lang이 명시적으로 주어지면(다이제스트
            // 메일 배치 스레드처럼 HTTP 요청 컨텍스트가 없어 LocaleContextHolder가 수신자의 언어를 알 수
            // 없는 경우) 그 값을 그대로 쓰고, 없을 때만(일반 요청 처리 스레드) 현재 요청의 로케일로 대체한다.
            val effectiveLang = if (lang.isNullOrBlank()) LocaleContextHolder.getLocale().language else lang
            val userName = user.getPureNameOnly(effectiveLang)
            val escapeContent = StringEscapeUtils.escapeHtml4("$avatarImage${user.name} ${user.loginId}")

            return Link(
                "/user/${user.loginId}",
                "no-text-decoration user-link",
                "<span data-toggle='popover' data-placement='top' data-trigger='hover' data-html='true' data-content=\"$escapeContent\">@$userName</span>"
            )
        }
        return Link()
    }

    private fun toValidProjectLink(ownerName: String, projectName: String): Link {
        val projectOpt = projectRepository.findByOwnerAndName(ownerName, projectName)
        if (projectOpt.isPresent) {
            val project = projectOpt.get()
            return Link("/$ownerName/$projectName", "<span class='project-link'>@${project.owner}/${project.name}</span>")
        }
        return Link()
    }

    private fun isIgnoreElement(el: Element): Boolean {
        return IGNORE_TAGNAME.contains(el.tagName().uppercase())
    }

    private fun isWrappedNonCharacter(body: String, matcher: Matcher): Boolean {
        val start = matcher.start()
        val end = matcher.end()
        val hasLeftWord = start != 0 && WORD_PATTERN.matcher(body.substring(start - 1, start)).find()
        val hasRightWord = end != body.length && WORD_PATTERN.matcher(body.substring(end, end + 1)).find()
        return hasLeftWord || hasRightWord
    }
}
