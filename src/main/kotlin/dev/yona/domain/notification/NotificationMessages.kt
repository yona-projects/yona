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
                // v1.6은 "마일스톤 없음" 문구를 받는 사람의 언어가 아니라 Lang.defaultLang()(서버 JVM 기본 로캘)로 만들었다. 받는 사람의 언어로 만든다(R-32).
                text("notification.milestone.changed", milestone?.title ?: messages.getMessage("issue.noMilestone", null, locale))
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
            EventType.PULL_REQUEST_ASSIGNEE_CHANGED ->
                if (new == null) text("notification.pullrequest.unassigned") else text("notification.pullrequest.assigned", new)
            EventType.PULL_REQUEST_REVIEW_REQUESTED -> text("notification.pullrequest.review.requested", new)
            EventType.PULL_REQUEST_MERGED -> text("notification.type.pullrequest.merged.$new") + "\n" + (old ?: "")
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> {
                val sender = event.senderId?.let { users.findById(it).orElse(null) }?.loginId
                if (new == "DONE") text("notification.pullrequest.reviewed", sender) else text("notification.pullrequest.unreviewed", sender)
            }
            // 추가 항목 X-12(D-35): 리뷰 판정 제출과 해제, 규칙 안내
            EventType.PULL_REQUEST_REVIEW_SUBMITTED -> {
                val sender = event.senderId?.let { users.findById(it).orElse(null) }?.loginId
                // 승인은 v1.6 리뷰 완료 알림 문구를 그대로 쓴다(선생님 지시 2026-10-29). 변경 요청과 댓글은 옛 문구가 없어 판정별 문구를 쓴다
                val head = if (old == "APPROVED") text("notification.pullrequest.reviewed", sender) else text("pullRequest.event.message.review.${old.orEmpty().lowercase()}", sender)
                head + (new?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
            }
            EventType.PULL_REQUEST_REVIEW_DISMISSED -> {
                val sender = event.senderId?.let { users.findById(it).orElse(null) }?.loginId
                text("pullRequest.event.message.review.dismissed", sender, old) + (new?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
            }
            EventType.PULL_REQUEST_REVIEW_RULE_NOTICE -> text("pullRequest.review.rule.notice")
            EventType.RESOURCE_DELETED -> new?.let { users.findByLoginId(it) }?.let { text("notification.resource.deleted", UserDisplay.of(it, it.lang)) }
            else -> {
                log.warn("알 수 없는 알림 이벤트 종류이다: {}. NotificationMessages.message를 확인한다", type)
                // v1.6는 이 분기에서 `eventType.getDescr()`(= `Messages.get(key)`)를 써서 받는 사람이 아니라 지금 요청의 언어로 만든다
                // (요청 밖이면 서버 기본 언어이다). 공식 v1.16.0 홈 화면에서 Accept-Language 영어 요청에 영어 문구가 나옴을 확인했다
                messages.getMessage(type.descrKey, null, org.springframework.context.i18n.LocaleContextHolder.getLocale())
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
            // v1.6은 추가하거나 지운 파일에는 바뀐 곳 목록이 없어 파일 이름 줄 뒤에 댓글만 나왔다. 파일 전체를 +/- 줄로 만든 가상 헝크를 쓴다(R-44-1)
            val hunks = file.hunks() ?: wholeFileHunks(file, thread.endLine ?: 0) ?: return message.append(contents).toString()
            message.append("```diff\n")
            for (hunk in hunks) {
                // 쉼표 뒤에 공백이 없는 통합 diff 머리줄이다. v1.6은 쉼표 뒤에 공백을 넣었다(R-44-2)
                val startA = if (hunk.endA == hunk.beginA) hunk.beginA else hunk.beginA + 1
                val startB = if (hunk.endB == hunk.beginB) hunk.beginB else hunk.beginB + 1
                message.append("> @@ -$startA,${hunk.endA - hunk.beginA} +$startB,${hunk.endB - hunk.beginB} @@\n")
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

    /**
     * 추가하거나 지운 파일의 전체 내용을 `+` 또는 `-` 줄로 만든 가상 헝크이다. 텍스트가 아니면 `null`이다. 메일이 너무 길어지지 않도록 댓글이 달린 줄의
     * 앞 [WHOLE_FILE_BEFORE]줄과 뒤 [WHOLE_FILE_AFTER]줄까지만 담는다.
     */
    private fun wholeFileHunks(file: dev.yona.repository.diff.FileDiff, interest: Int): dev.yona.repository.diff.Hunks? {
        val added = file.changeType == org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD
        val deleted = file.changeType == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE
        val text = (if (added) file.b else if (deleted) file.a else null) ?: return null
        if (if (added) file.isBinaryB else file.isBinaryA) return null
        val from = maxOf(0, interest - WHOLE_FILE_BEFORE)
        val to = minOf(text.size(), interest + WHOLE_FILE_AFTER + 1)
        val hunk = if (added) dev.yona.repository.diff.Hunk(0, 0, from, to) else dev.yona.repository.diff.Hunk(from, to, 0, 0)
        for (i in from until to) {
            hunk.lines.add(
                if (added) dev.yona.repository.diff.DiffLine(file, dev.yona.repository.diff.DiffLineType.ADD, null, i, text.getString(i))
                else dev.yona.repository.diff.DiffLine(file, dev.yona.repository.diff.DiffLineType.REMOVE, i, null, text.getString(i))
            )
        }
        return dev.yona.repository.diff.Hunks().also { it.add(hunk) }
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

    private companion object {
        /** 추가하거나 지운 파일의 알림에 댓글이 달린 줄 앞으로 담는 줄 수이다. */
        const val WHOLE_FILE_BEFORE = 20
        /** 추가하거나 지운 파일의 알림에 댓글이 달린 줄 뒤로 담는 줄 수이다. */
        const val WHOLE_FILE_AFTER = 3
    }
}
