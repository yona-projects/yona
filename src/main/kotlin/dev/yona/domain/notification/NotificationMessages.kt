package dev.yona.domain.notification

import dev.yona.util.UrlPaths
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.descrKey
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.util.SiteUrls
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/**
 * 알림 이벤트를 메일에 실을 문구와 이동 주소로 바꾼다. v1.6 `NotificationEvent.getMessage`, `getPlainMessage`, `getUrlToView`
 * (`app/models/NotificationEvent.java:123-273, 535-575`)와 `utils.RouteUtil.getUrl`에 해당한다.
 *
 * 문구는 받는 사람의 언어로 만든다. 이벤트 종류마다 정해진 메시지 키를 쓰며, 새 글이나 댓글처럼 본문 자체가 문구인 것도 있다.
 * 코드 리뷰 댓글과 커밋 이벤트는 5단계의 뒷 단위에서 더한다. 아직 모르는 종류는 v1.6처럼 경고를 남기고 종류 설명을 돌려준다.
 */
@Service
class NotificationMessages(
    private val properties: dev.yona.config.YonaProperties,
    private val messages: MessageSource,
    private val milestones: MilestoneRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val organizations: OrganizationRepository,
    private val reviewComments: dev.yona.domain.comment.ReviewCommentRepository,
    private val resourceUrls: dev.yona.domain.misc.ResourceUrls,
    private val diffs: dev.yona.repository.diff.DiffService,
    private val siteUrls: SiteUrls
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 메일 HTML 본문에 들어갈 문구이다. 마크다운 문법이 들어 있어 이어서 렌더링한다(본문 변경은 이미 HTML이다).
     * 만들 수 없으면(예: 지워진 사용자) `null`이다.
     */
    @Transactional(readOnly = true)
    fun message(event: NotificationEvent, lang: String): String? {
        val locale = Locale.forLanguageTag(lang)
        val old = event.oldValue
        val new = event.newValue
        fun text(key: String, vararg args: Any?): String = messages.getMessage(key, arrayOf<Any?>(*args).map { it ?: "null" }.toTypedArray<Any>(), locale)
        return when (val type = requireNotNull(event.eventType)) {
            EventType.ISSUE_STATE_CHANGED ->
                if (new == State.CLOSED.state) text("notification.issue.closed") else text("notification.issue.reopened")
            EventType.ISSUE_ASSIGNEE_CHANGED ->
                if (new == null) text("notification.issue.unassigned") else text("notification.issue.assigned", new)
            EventType.ISSUE_MILESTONE_CHANGED -> {
                val milestone = new?.toLongOrNull()?.let { milestones.findById(it).orElse(null) }
                // v1.6은 "마일스톤 없음" 문구를 받는 사람의 언어가 아니라 Lang.defaultLang()(application.langs의 첫 언어)로 만든다.
                text("notification.milestone.changed", milestone?.title ?: messages.getMessage("issue.noMilestone", null, dev.yona.config.SupportedLocales.of(properties.langs).first()))
            }
            EventType.NEW_ISSUE, EventType.NEW_POSTING, EventType.NEW_PULL_REQUEST, EventType.NEW_COMMIT, EventType.COMMENT_UPDATED -> new
            EventType.NEW_COMMENT -> "$new$old"
            EventType.ISSUE_BODY_CHANGED, EventType.POSTING_BODY_CHANGED -> MailDiff.html(old, new)
            EventType.MEMBER_ENROLL_REQUEST -> enroll(new, "notification.member.enroll") { key -> text(key) }
            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST -> enroll(new, "notification.organization.member.enroll") { key -> text(key) }
            EventType.ISSUE_MOVED -> text("notification.type.issue.moved", old, new)
            EventType.ISSUE_SHARER_CHANGED ->
                if (!new.isNullOrBlank()) users.findByLoginId(new)?.let { text("notification.issue.sharer.added", UserDisplay.of(it, it.lang)) }
                else if (!old.isNullOrBlank()) text("notification.issue.sharer.deleted")
                else null
            EventType.NEW_REVIEW_COMMENT -> try {
                event.resourceId?.toLongOrNull()?.let { reviewComments.findById(it).orElse(null) }?.let { commentedCode(it, locale) } ?: new
            } catch (e: Exception) {
                log.error("Failed to generate a notification message for a review comment", e)
                new
            }
            EventType.REVIEW_THREAD_STATE_CHANGED ->
                if (new == dev.yona.domain.enumeration.ThreadState.CLOSED.name) text("notification.reviewthread.closed") else text("notification.reviewthread.reopened")
            EventType.PULL_REQUEST_STATE_CHANGED ->
                if (State.OPEN.state == new) text("notification.pullrequest.reopened") else text("notification.pullrequest.$new")
            EventType.PULL_REQUEST_COMMIT_CHANGED -> new
            EventType.PULL_REQUEST_MERGED -> text("notification.type.pullrequest.merged.$new") + "\n" + (old ?: "")
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> {
                val sender = event.senderId?.let { users.findById(it).orElse(null) }?.loginId
                if (new == "DONE") text("notification.pullrequest.reviewed", sender) else text("notification.pullrequest.unreviewed", sender)
            }
            EventType.RESOURCE_DELETED -> new?.let { users.findByLoginId(it) }?.let { text("notification.resource.deleted", UserDisplay.of(it, it.lang)) }
            else -> {
                log.warn("알 수 없는 알림 이벤트 종류이다: {}. NotificationMessages.message를 확인한다", type)
                // v1.6는 이 분기에서 요청 문맥이 없는 `Messages.get(key)`를 써서 받는 사람이 아니라 서버 기본 언어로 만든다
                messages.getMessage(type.descrKey, null, Locale.getDefault())
            }
        }
    }

    /**
     * 코드 범위 스레드의 첫 댓글이면 댓글이 달린 줄까지의 차이(diff) 조각과 댓글을 함께 보여 주는 문구를 만든다. 첫 댓글이 아니거나 파일 단위 스레드이거나
     * 차이를 만들 수 없으면 댓글 내용만 돌려준다 (v1.6 `buildCommentedCodeMessage`).
     */
    private fun commentedCode(comment: dev.yona.domain.comment.ReviewComment, locale: Locale): String? {
        val thread = comment.thread
        val contents = comment.contents
        if (thread !is dev.yona.domain.comment.CodeCommentThread) return contents
        if (thread.reviewComments.minByOrNull { it.createdDate ?: java.util.Date(0) }?.id != comment.id) return contents
        val project = thread.project ?: return contents
        val commitId = thread.commitId ?: return contents
        val files = (if (thread.prevCommitId == null) diffs.commitDiff(project, commitId) else diffs.diff(project, thread.prevCommitId!!, commitId)) ?: return contents
        for (file in files) {
            val onFile = when (thread.endSide) {
                dev.yona.domain.enumeration.Side.B -> file.pathB == thread.path
                dev.yona.domain.enumeration.Side.A -> file.pathA == thread.path
                null -> true
            }
            if (!onFile) continue
            val message = StringBuilder(messages.getMessage("notification.reviewthread.inTheFile", arrayOf<Any>(thread.path.orEmpty()), locale)).append('\n')
            file.interestLine = thread.endLine
            file.interestSide = thread.endSide
            val hunks = file.hunks() ?: return message.append(contents).toString()
            message.append("```diff\n")
            for (hunk in hunks) {
                message.append("> @@ -${hunk.beginA + 1}, ${hunk.endA - hunk.beginA} +${hunk.beginB + 1}, ${hunk.endB - hunk.beginB} @@\n")
                for (line in hunk.lines) {
                    message.append("> ").append(
                        when (line.kind) {
                            dev.yona.repository.diff.DiffLineType.CONTEXT -> " "
                            dev.yona.repository.diff.DiffLineType.ADD -> "+"
                            dev.yona.repository.diff.DiffLineType.REMOVE -> "-"
                        }
                    ).append(line.content).append('\n')
                    val ends = (thread.endSide == dev.yona.domain.enumeration.Side.A && thread.endLine == line.numA) || (thread.endSide == dev.yona.domain.enumeration.Side.B && thread.endLine == line.numB)
                    if (ends) message.append("```\n").append("\n").append(contents).append("\n\n").append("```diff\n")
                }
            }
            return message.append("```\n").toString()
        }
        return contents
    }

    /** 메일 일반 텍스트 본문에 들어갈 문구이다. 본문 변경은 `---`, `+++` 접두어 형식이다. */
    @Transactional(readOnly = true)
    fun plainMessage(event: NotificationEvent, lang: String): String? = when (event.eventType) {
        EventType.ISSUE_BODY_CHANGED, EventType.POSTING_BODY_CHANGED -> MailDiff.plain(event.oldValue, event.newValue)
        else -> message(event, lang)?.replace("\n\n<br />\n", "\n\n")
    }

    private fun enroll(state: String?, prefix: String, text: (String) -> String): String = when (state) {
        RequestState.REQUEST.name -> text("$prefix.request")
        RequestState.ACCEPT.name -> text("$prefix.accept")
        else -> text("$prefix.cancel")
    }

    /**
     * 메일의 "보기" 링크가 가리킬 화면 주소이다(컨텍스트 경로 포함, 호스트 제외). 대상을 찾을 수 없으면 `null`이다.
     */
    @Transactional(readOnly = true)
    fun urlToView(event: NotificationEvent): String? {
        val resourceId = event.resourceId ?: return null
        return when (event.eventType) {
            // v1.6 역방향 라우트는 경로 변수를 퍼센트 인코딩한다
            EventType.NEW_COMMIT -> projectOf(event)?.let { siteUrls.path("${UrlPaths.projectPath(it.owner, it.name)}/commits") }
            EventType.MEMBER_ENROLL_REQUEST -> projectOf(event)?.let { siteUrls.path("${UrlPaths.projectPath(it.owner, it.name)}/members") }
            EventType.MEMBER_ENROLL_ACCEPT -> projectOf(event)?.let { siteUrls.path(UrlPaths.projectPath(it.owner, it.name)) }
            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST -> organization(resourceId)?.let { siteUrls.path("/organizations/${UrlPaths.encode(it.name.orEmpty())}/members") }
            EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> organization(resourceId)?.let { siteUrls.path("/organizations/${UrlPaths.encode(it.name.orEmpty())}") }
            else -> resourceUrls.urlOf(event.resourceType, resourceId)?.let { siteUrls.path(it) }
        }
    }

    private fun projectOf(event: NotificationEvent): Project? =
        if (event.resourceType == ResourceType.PROJECT) event.resourceId?.toLongOrNull()?.let { projects.findById(it).orElse(null) } else null

    private fun organization(id: String) = id.toLongOrNull()?.let { organizations.findById(it).orElse(null) }
}
