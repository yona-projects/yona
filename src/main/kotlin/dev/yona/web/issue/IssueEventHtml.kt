package dev.yona.web.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.TemplateHelper
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/**
 * 이슈 타임라인의 이벤트 한 줄(`li.event`)을 HTML로 만든다. v1.6 `issue/partial_event_timeline.scala.html`에 해당한다.
 * v1.6은 번역 메시지의 인자로 HTML 조각(사용자 링크, 마일스톤 링크 등)을 넘기므로 같은 방식으로 문자열을 만든다.
 * 사용자가 쓴 값(이름, 제목, 라벨 이름과 색)은 모두 이스케이프한다.
 */
@Component
class IssueEventHtml(
    private val users: UserRepository,
    private val milestones: MilestoneRepository,
    private val pullRequests: PullRequestRepository,
    private val labelService: IssueLabelService,
    private val avatars: AvatarService,
    private val helper: TemplateHelper,
    private val messages: MessageSource
) {

    /**
     * 이벤트를 HTML 한 줄로 만든다. 본문 변경 이벤트는 화면에 보이지 않으므로 `null`이다.
     *
     * @param previous 타임라인에서 바로 앞이 이벤트이면 그 이벤트(같은 종류가 이어질 때 제목을 생략한다)
     */
    fun render(event: IssueEvent, project: Project, previous: IssueEvent?, viewer: User?): String? {
        if (event.eventType == EventType.ISSUE_BODY_CHANGED) return null
        val lang = viewer?.lang
        val sender = event.senderLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        val senderLink = link(sender?.loginId.orEmpty(), sender?.let { UserDisplay.of(it, lang) } ?: unknownName(), true)
        val senderPure = link(sender?.loginId.orEmpty(), sender?.let { UserDisplay.pureName(it, lang) } ?: unknownName(), true)
        val newValue = event.newValue
        val oldValue = event.oldValue
        val body = when (event.eventType) {
            EventType.ISSUE_STATE_CHANGED ->
                "<span class=\"state ${esc(newValue)}\">${message("issue.state.$newValue")}</span>\n" + message("issue.event.$newValue", senderLink)
            EventType.ISSUE_ASSIGNEE_CHANGED -> {
                val key = when {
                    newValue != null && newValue == sender?.loginId -> "issue.event.assignedToMe"
                    newValue != null -> "issue.event.assigned"
                    else -> "issue.event.unassigned"
                }
                val assigned = newValue?.let { users.findByLoginIdIgnoreCase(it) }
                "<span class=\"state changed\">${message("issue.state.assigned")}</span>\n" +
                    message(key, senderLink, link(newValue, assigned?.let { UserDisplay.of(it, lang) } ?: unknownName(), true))
            }
            EventType.ISSUE_MILESTONE_CHANGED ->
                "<span class=\"state milestone-changed\">${message("issue.update.milestone.id")}</span>\n" +
                    message("issue.event.milestone.changed", senderLink, milestoneLink(newValue, project))
            EventType.ISSUE_REFERRED_FROM_COMMIT ->
                "<span class=\"state changed\">${message("issue.event.referred.title")}</span>\n" +
                    message("issue.event.referred", senderLink, "<strong>${message("code.commits")} <a href=\"/${esc(project.owner)}/${esc(project.name)}/commit/${esc(newValue)}\" class=\"link\">@${esc(newValue)}</a></strong>")
            EventType.ISSUE_MOVED -> {
                val (owner, name) = (oldValue.orEmpty().split("/") + listOf("", "")).take(2)
                "<span class=\"state changed\">${message("issue.event.moved.title")}</span>\n" +
                    message("issue.event.moved", senderLink, "<strong><a href=\"/${esc(owner)}/${esc(name)}\" class=\"link\">${esc(owner)}/${esc(name)}</a></strong>")
            }
            EventType.ISSUE_REFERRED_FROM_PULL_REQUEST -> {
                val pull = newValue?.toLongOrNull()?.let { pullRequests.findById(it).orElse(null) }
                val target = pull?.toProject
                "<span class=\"state changed\">${message("issue.event.referred.title")}</span>\n" +
                    message(
                        "issue.event.referred", senderLink,
                        "<strong>${message("pullRequest")}\n-${pull?.number} <a href=\"/${esc(target?.owner)}/${esc(target?.name)}/pullRequest/${pull?.number}\" class=\"link\">${esc(pull?.title)}</a></strong>"
                    )
            }
            EventType.ISSUE_SHARER_CHANGED -> sharerChanged(event, previous, senderPure, lang)
            EventType.ISSUE_LABEL_CHANGED -> labelChanged(event, previous, senderPure, project)
            else -> "${esc(newValue)} by $senderLink"
        }
        return "<li class=\"event\" id=\"event-${event.id}\">\n$body\n" +
            "<span class=\"date\"><a href=\"#event-${event.id}\">${esc(helper.agoOrDateString(event.created))}</a></span>\n</li>"
    }

    private fun sharerChanged(event: IssueEvent, previous: IssueEvent?, senderPure: String, lang: String?): String {
        val adding = isAdding(event)
        val same = previous != null && previous.eventType == event.eventType && ((adding && isAdding(previous)) || (isDeleting(event) && isDeleting(previous)))
        val target = (if (adding) event.newValue else event.oldValue)
        val user = target?.let { users.findByLoginIdIgnoreCase(it) }
        val targetLink = link(target, user?.let { UserDisplay.pureName(it, lang) } ?: unknownName(), true)
        return if (adding) {
            (if (same) "<span class=\"state\"></span>" else "<span class=\"state sharer-added\">${message("issue.sharer")}</span>") + "\n" +
                message("issue.event.sharer.added", senderPure, targetLink)
        } else {
            (if (same) "<span class=\"state\"></span>" else "<span class=\"state sharer-deleted\">${message("issue.event.sharer.deleted.title")}</span>") + "\n" +
                message("issue.event.sharer.deleted", senderPure, targetLink)
        }
    }

    private fun labelChanged(event: IssueEvent, previous: IssueEvent?, senderPure: String, project: Project): String {
        val adding = isAdding(event)
        val same = previous != null && previous.eventType == event.eventType && ((adding && isAdding(previous)) || (isDeleting(event) && isDeleting(previous)))
        return if (adding) {
            (if (same) "<span class=\"state\"></span>" else "<span class=\"state label-added\">${message("issue.event.label.added.title")}</span>") + "\n" +
                message("issue.event.label.added", senderPure, labelBox(event.newValue, project))
        } else {
            (if (same) "<span class=\"state\"></span>" else "<span class=\"state label-deleted\">${message("issue.event.label.deleted.title")}</span>") + "\n" +
                message("issue.event.label.deleted", senderPure, labelBox(event.oldValue, project))
        }
    }

    private fun isAdding(event: IssueEvent) = event.oldValue.isNullOrBlank() && !event.newValue.isNullOrBlank()

    private fun isDeleting(event: IssueEvent) = event.newValue.isNullOrBlank() && !event.oldValue.isNullOrBlank()

    /** `분류 - 이름 #색` 형태의 값을 색 상자로 바꾼다. 형식이 다르거나 라벨이 지워졌으면 이름(또는 값)만 보인다. */
    private fun labelBox(value: String?, project: Project): String {
        val text = value.orEmpty()
        val parts = text.split(" - ")
        if (parts.size != 2) return esc(text)
        val category = parts[0].trim()
        val name = parts[1].split(" #")[0].trim()
        val label = labelService.labels(project).firstOrNull { it.category?.name == category && it.name == name }
            ?: return esc(name)
        return "<div class='label issue-label' style='background-color: ${esc(label.color)}'>${esc(name)}</div>"
    }

    private fun milestoneLink(value: String?, project: Project): String {
        val milestone = value?.toLongOrNull()?.let { milestones.findById(it).orElse(null) }
        return if (milestone == null || milestone.id == NO_MILESTONE) {
            "<span class=\"bold\">${message("common.none")}</span>"
        } else {
            "<span class=\"bold font-blue\"><a href=\"/${esc(project.owner)}/${esc(project.name)}/milestone/${milestone.id}\" data-toggle=\"tooltip\" data-placement=\"bottom\" title=\"${message("milestone")}\">${esc(milestone.title)}</a></span>"
        }
    }

    /** 사용자 링크(아바타 + 이름)를 만든다. 로그인 ID가 없으면 `Anonymous`이다. */
    private fun link(loginId: String?, name: String, showAvatar: Boolean): String {
        if (loginId == null) return "Anonymous"
        val user = users.findByLoginIdIgnoreCase(loginId)
        val avatar = if (showAvatar) {
            "<a href=\"/${esc(loginId)}\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"${esc(name)}\"><img src=\"${esc(user?.let { avatars.avatarUrl(it, AVATAR_SIZE) } ?: "")}\" class=\"avatar-wrap small\"></a>\n"
        } else ""
        return avatar + "<a href=\"/${esc(loginId)}\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"${esc(loginId)}\">\n<strong>${esc(name)}</strong></a>"
    }

    private fun unknownName(): String = message("user.notExists.name")

    private fun message(key: String, vararg args: String): String =
        messages.getMessage(key, args.takeIf { it.isNotEmpty() }, key, LocaleContextHolder.getLocale()) ?: key

    private fun esc(value: String?): String = HtmlUtils.htmlEscape(value.orEmpty())

    private companion object {
        const val AVATAR_SIZE = 32
        const val NO_MILESTONE = -1L
    }
}
