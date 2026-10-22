package dev.yona.domain.notification

import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.descr
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueRepository
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
 * 풀 리퀘스트와 코드 리뷰, 커밋 이벤트는 5단계에서 더한다. 아직 모르는 종류는 v1.6처럼 경고를 남기고 종류 설명을 돌려준다.
 */
@Service
class NotificationMessages(
    private val messages: MessageSource,
    private val milestones: MilestoneRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val organizations: OrganizationRepository,
    private val issues: IssueRepository,
    private val issueComments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
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
                // v1.6은 "마일스톤 없음" 문구를 받는 사람의 언어가 아니라 서버 기본 언어로 만든다.
                text("notification.milestone.changed", milestone?.title ?: messages.getMessage("issue.noMilestone", null, Locale.getDefault()))
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
            EventType.RESOURCE_DELETED -> new?.let { users.findByLoginId(it) }?.let { text("notification.resource.deleted", UserDisplay.of(it, it.lang)) }
            else -> {
                log.warn("알 수 없는 알림 이벤트 종류이다: {}. NotificationMessages.message를 확인한다", type)
                type.descr
            }
        }
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
            EventType.MEMBER_ENROLL_REQUEST -> projectOf(event)?.let { siteUrls.path("/${it.owner}/${it.name}/members") }
            EventType.MEMBER_ENROLL_ACCEPT -> projectOf(event)?.let { siteUrls.path("/${it.owner}/${it.name}") }
            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST -> organization(resourceId)?.let { siteUrls.path("/organizations/${it.name}/members") }
            EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> organization(resourceId)?.let { siteUrls.path("/organizations/${it.name}") }
            else -> resourceUrl(event.resourceType, resourceId)?.let { siteUrls.path(it) }
        }
    }

    private fun projectOf(event: NotificationEvent): Project? =
        if (event.resourceType == ResourceType.PROJECT) event.resourceId?.toLongOrNull()?.let { projects.findById(it).orElse(null) } else null

    private fun organization(id: String) = id.toLongOrNull()?.let { organizations.findById(it).orElse(null) }

    /** v1.6 `RouteUtil.getUrl(resourceType, resourceId)`이다. 지원하지 않는 종류와 없는 대상은 `null`이다. */
    private fun resourceUrl(type: ResourceType?, resourceId: String): String? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (type) {
            ResourceType.ISSUE_POST -> issues.findById(id).orElse(null)?.let { "/${it.project?.owner}/${it.project?.name}/issue/${it.number}" }
            ResourceType.ISSUE_COMMENT -> issueComments.findById(id).orElse(null)?.let { c ->
                c.issue?.let { "/${it.project?.owner}/${it.project?.name}/issue/${it.number}#comment-${c.id}" }
            }
            ResourceType.BOARD_POST -> postings.findById(id).orElse(null)?.let { "/${it.project?.owner}/${it.project?.name}/post/${it.number}" }
            ResourceType.NONISSUE_COMMENT -> postingComments.findById(id).orElse(null)?.let { c ->
                c.posting?.let { "/${it.project?.owner}/${it.project?.name}/post/${it.number}#comment-${c.id}" }
            }
            ResourceType.PROJECT -> projects.findById(id).orElse(null)?.let { "/${it.owner}/${it.name}" }
            ResourceType.USER_AVATAR -> users.findById(id).orElse(null)?.let { "/${it.loginId}" }
            else -> null
        }
    }
}
