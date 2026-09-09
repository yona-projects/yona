package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.support.DiffUtil
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.DiffLineType
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * yona `models/NotificationEvent.java`의 `getMessage(Lang)`/`getPlainMessage(Lang)` +
 * `buildCommentedCodeMessage()` 대응. Play의 `Messages.get(lang, key, args...)`를
 * Spring `MessageSource.getMessage(key, args, defaultMessage, locale)`로 옮기되, yona의
 * `NotificationEvent.oldValue`/`newValue`가 legacy와 다르게 저장하는 이벤트 타입들
 * (ISSUE_STATE_CHANGED/ISSUE_MILESTONE_CHANGED/ISSUE_LABEL_CHANGED/RESOURCE_DELETED/
 * PULL_REQUEST_MERGED 등)은 실제 yona 데이터에 맞춰 분기를 조정했다 — 각 분기 위에 근거를 남긴다.
 */
@Component
class NotificationMessageResolver(
    private val messageSource: MessageSource,
    private val userRepository: UserRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val repositoryService: RepositoryService
) {
    private val logger = LoggerFactory.getLogger(NotificationMessageResolver::class.java)

    fun getMessage(event: NotificationEvent, locale: Locale): String {
        val newValue = event.newValue
        val oldValue = event.oldValue

        return when (event.eventType) {
            // yona는 State.toString()(대문자 enum 이름, 예: "CLOSED")을 저장한다. legacy는
            // State.state()(소문자 코드워드)를 비교했다.
            EventType.ISSUE_STATE_CHANGED ->
                if (newValue == "CLOSED") msg("notification.issue.closed", locale)
                else msg("notification.issue.reopened", locale)

            EventType.ISSUE_ASSIGNEE_CHANGED ->
                if (newValue.isNullOrBlank()) msg("notification.issue.unassigned", locale)
                else msg("notification.issue.assigned", locale, newValue)

            // yona는 마일스톤 ID가 아니라 제목 문자열을 그대로 저장한다(IssueServiceImpl).
            EventType.ISSUE_MILESTONE_CHANGED ->
                if (newValue.isNullOrBlank()) msg("notification.milestone.changed", locale, msg("issue.noMilestone", Locale.getDefault()))
                else msg("notification.milestone.changed", locale, newValue)

            EventType.NEW_ISSUE, EventType.NEW_POSTING, EventType.NEW_PULL_REQUEST,
            EventType.NEW_COMMIT, EventType.COMMENT_UPDATED ->
                newValue.orEmpty()

            // legacy: newValue + oldValue. oldValue(comment.previousContents, "인용 이전 내용")는
            // CommentServiceImpl.resolvePostingPreviousContents/resolveIssuePreviousContents가
            // 채운다 — 최초 댓글이면 원본 게시물/이슈 본문, 아니면 형제 답글/부모 댓글/마지막 댓글을 인용.
            EventType.NEW_COMMENT -> newValue.orEmpty() + oldValue.orEmpty()

            EventType.ISSUE_BODY_CHANGED, EventType.POSTING_BODY_CHANGED ->
                DiffUtil.getDiffText(oldValue, newValue)

            EventType.NEW_REVIEW_COMMENT -> resolveReviewCommentMessage(event, locale)

            // yona는 State.toString()(대문자)을 저장한다.
            EventType.PULL_REQUEST_STATE_CHANGED ->
                if (newValue == "OPEN") msg("notification.pullrequest.reopened", locale)
                else msg("notification.pullrequest." + newValue.orEmpty().lowercase(), locale, newValue.orEmpty())

            EventType.PULL_REQUEST_COMMIT_CHANGED -> newValue.orEmpty()

            // yona는 newValue에 "conflict"/"resolved" 코드워드가 아니라 표시용 제목을 저장한다
            // (PullRequestMergeEventListener). 키가 없으면 MessageSource의 default 인자로
            // 원본 값을 그대로 보여준다.
            EventType.PULL_REQUEST_MERGED ->
                msg("notification.type.pullrequest.merged." + newValue.orEmpty(), locale, newValue.orEmpty()) +
                    "\n" + oldValue.orEmpty()

            // yona에서는 REQUEST/ACCEPT가 서로 다른 EventType으로 분리돼 있어(MEMBER_ENROLL_REQUEST의
            // newValue는 "REQUEST"/"CANCEL"만 쓰인다) ACCEPT 분기는 사실상 도달하지 않지만, legacy의
            // 3분기 구조를 그대로 남겨 방어적으로 처리한다.
            EventType.MEMBER_ENROLL_REQUEST ->
                when (newValue) {
                    "REQUEST" -> msg("notification.member.enroll.request", locale)
                    "ACCEPT" -> msg("notification.member.enroll.accept", locale)
                    else -> msg("notification.member.enroll.cancel", locale)
                }

            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST ->
                when (newValue) {
                    "REQUEST" -> msg("notification.organization.member.enroll.request", locale)
                    "ACCEPT" -> msg("notification.organization.member.enroll.accept", locale)
                    else -> msg("notification.organization.member.enroll.cancel", locale)
                }

            // legacy의 getMessage() switch는 MEMBER_ENROLL_ACCEPT/ORGANIZATION_MEMBER_ENROLL_ACCEPT를
            // 다루지 않아 default 분기(EventType.getDescr() == Messages.get(messageKey), lang 인자 없음
            // = 항상 사이트 기본 언어)로 빠진다. 요청 locale을 무시하고 기본 로케일을 쓰는 것까지
            // legacy 동작 그대로 재현한다.
            EventType.MEMBER_ENROLL_ACCEPT, EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT ->
                msg("notification.member.enroll.accept", Locale.getDefault())

            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> {
                val senderLoginId = event.senderId?.let { userRepository.findById(it).orElse(null)?.loginId } ?: ""
                if (newValue == "DONE") msg("notification.pullrequest.reviewed", locale, senderLoginId)
                else msg("notification.pullrequest.unreviewed", locale, senderLoginId)
            }

            EventType.REVIEW_THREAD_STATE_CHANGED ->
                if (newValue == "CLOSED") msg("notification.reviewthread.closed", locale)
                else msg("notification.reviewthread.reopened", locale)

            EventType.ISSUE_MOVED -> msg("notification.type.issue.moved", locale, oldValue.orEmpty(), newValue.orEmpty())

            // yona는 loginId를 저장한다(legacy와 동일).
            EventType.ISSUE_SHARER_CHANGED ->
                if (!newValue.isNullOrBlank()) {
                    val user = userRepository.findByLoginId(newValue).orElse(null)
                    msg("notification.issue.sharer.added", locale, user?.name ?: newValue)
                } else if (!oldValue.isNullOrBlank()) {
                    msg("notification.issue.sharer.deleted", locale)
                } else {
                    ""
                }

            // yona는 loginId가 아니라 변경된 라벨 이름 목록(콤마 구분)을 저장한다(IssueServiceImpl).
            EventType.ISSUE_LABEL_CHANGED ->
                if (!newValue.isNullOrBlank()) msg("notification.issue.label.added", locale, newValue)
                else if (!oldValue.isNullOrBlank()) msg("notification.issue.label.deleted", locale)
                else ""

            // yona는 삭제한 사용자의 loginId가 아니라 삭제된 리소스의 표시 제목을 저장한다
            // (PostingServiceImpl 등). loginId로 먼저 찾아보고 없으면 원본 값을 그대로 보여준다.
            EventType.RESOURCE_DELETED -> {
                val user = newValue?.let { userRepository.findByLoginId(it).orElse(null) }
                msg("notification.resource.deleted", locale, user?.name ?: newValue.orEmpty())
            }

            else -> {
                logger.warn("Unknown event message: eventType={}, event={}", event.eventType, event.id)
                msg(event.eventType.messageKey, Locale.getDefault())
            }
        }
    }

    fun getPlainMessage(event: NotificationEvent, locale: Locale): String {
        return when (event.eventType) {
            EventType.ISSUE_BODY_CHANGED, EventType.POSTING_BODY_CHANGED ->
                DiffUtil.getDiffPlainText(event.oldValue, event.newValue)
            else -> getMessage(event, locale).replace(Regex("\n\n<br />\n"), "\n\n")
        }
    }

    fun getMessage(merged: MergedNotificationEvent, locale: Locale): String =
        merged.messageSources.joinToString("\n\n---\n\n") { getMessage(it, locale) }

    fun getPlainMessage(merged: MergedNotificationEvent, locale: Locale): String =
        merged.messageSources.joinToString("\n\n---\n\n") { getPlainMessage(it, locale) }

    @Suppress("UNCHECKED_CAST")
    private fun msg(key: String, locale: Locale, vararg args: Any?): String {
        return messageSource.getMessage(key, args as Array<Any>, key, locale) ?: key
    }

    // yona NotificationEvent.buildCommentedCodeMessage() 대응. 코드 리뷰 댓글이 스레드의 첫 댓글이면
    // 커밋 diff의 해당 hunk를 인용구로 붙이고, 아니면 댓글 내용만 반환한다.
    private fun resolveReviewCommentMessage(event: NotificationEvent, locale: Locale): String {
        val reviewCommentId = event.resourceId.toLongOrNull() ?: return event.newValue.orEmpty()
        val reviewComment = reviewCommentRepository.findById(reviewCommentId).orElse(null)
            ?: return event.newValue.orEmpty()

        return try {
            buildCommentedCodeMessage(reviewComment, locale) ?: event.newValue.orEmpty()
        } catch (e: Exception) {
            logger.error("Failed to generate a notification message for a review comment", e)
            event.newValue.orEmpty()
        }
    }

    private fun buildCommentedCodeMessage(
        reviewComment: ReviewComment,
        locale: Locale
    ): String? {
        val thread = reviewComment.thread
        if (thread == null || thread.getFirstReviewComment() != reviewComment || thread !is CodeCommentThread) {
            return reviewComment.contents
        }

        val project = thread.project ?: return reviewComment.contents
        val codeRange = thread.codeRange

        val repo = try {
            repositoryService.getRepository(project)
        } catch (e: Exception) {
            logger.error("Failed to get the repository", e)
            return reviewComment.contents
        }

        val commitId = thread.commitId ?: return reviewComment.contents
        val diffs = (if (thread.prevCommitId.isBlank()) {
            repo.getDiff(commitId)
        } else {
            repo.getDiff(thread.prevCommitId, commitId)
        }).filterIsInstance<FileDiff>()

        for (diff in diffs) {
            if (!codeRange.isFor(diff)) continue

            val message = StringBuilder()
            message.append(msg("notification.reviewthread.inTheFile", locale, codeRange.path))
            message.append("\n")

            diff.interestLine = codeRange.endLine
            diff.interestSide = codeRange.endSide

            val hunks = diff.getHunks()
            if (hunks != null) {
                message.append("```diff\n")
                for (hunk in hunks) {
                    message.append(
                        "> @@ -%d, %d +%d, %d @@\n".format(
                            hunk.beginA + 1, hunk.endA - hunk.beginA,
                            hunk.beginB + 1, hunk.endB - hunk.beginB
                        )
                    )
                    for (line in hunk.lines) {
                        message.append("> ")
                        when (line.kind) {
                            DiffLineType.CONTEXT -> message.append(" ")
                            DiffLineType.ADD -> message.append("+")
                            DiffLineType.REMOVE -> message.append("-")
                        }
                        message.append(line.content).append("\n")
                        if (codeRange.endsWith(line)) {
                            message.append("```\n")
                            message.append("\n").append(reviewComment.contents).append("\n\n")
                            message.append("```diff\n")
                        }
                    }
                }
                message.append("```\n")
            } else {
                message.append(reviewComment.contents)
            }

            return message.toString()
        }

        return reviewComment.contents
    }
}
