package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Component

/**
 * yona `models/NotificationEvent.filterReceivers()` 대응.
 * 이벤트가 가리키는 리소스의 프로젝트를 읽을 수 없는 수신자(멘션 등으로 추가됐더라도)와 loginId가 없는 수신자를 제거한다.
 */
@Component
class NotificationReceiverFilter(
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val projectRepository: ProjectRepository,
    private val accessControl: AccessControl
) {
    fun filter(event: NotificationEvent) {
        event.receivers.removeIf { it.loginId.isBlank() }

        val id = event.resourceId.toLongOrNull() ?: return
        val readable = readableBy(event.resourceType, id) ?: return
        event.receivers.removeIf { !readable(it) }
    }

    // v1.6 filterReceivers는 AccessControl.isAllowed(receiver, event.getResource(), READ) — 이벤트 리소스 단위 판정이다.
    // 리소스 단위 규칙(작성자·담당자·공유자 예외)이 프로젝트 READ에는 없어서, 프로젝트만 보면 비공개 프로젝트의 이슈를 공유받은
    // 사용자나 접근권을 잃은 작성자·담당자의 알림이 사라진다. 리소스를 알 수 없는 이벤트는 건드리지 않는다.
    private fun readableBy(type: ResourceType, id: Long): ((User) -> Boolean)? = when (type) {
        ResourceType.ISSUE_POST, ResourceType.ISSUE_STATE, ResourceType.ISSUE_ASSIGNEE, ResourceType.ISSUE_MILESTONE ->
            issueRepository.findById(id).orElse(null)?.let { r -> { u: User -> accessControl.isAllowed(u, r.project, r, Operation.READ) } }
        ResourceType.ISSUE_COMMENT ->
            issueCommentRepository.findById(id).orElse(null)?.let { r -> { u: User -> accessControl.isAllowed(u, r.issue.project, r, Operation.READ) } }
        ResourceType.BOARD_POST ->
            postingRepository.findById(id).orElse(null)?.let { r -> { u: User -> accessControl.isAllowed(u, r.project, r, Operation.READ) } }
        ResourceType.NONISSUE_COMMENT ->
            postingCommentRepository.findById(id).orElse(null)?.let { r -> { u: User -> accessControl.isAllowed(u, r.posting.project, r, Operation.READ) } }
        ResourceType.COMMIT_COMMENT ->
            commitCommentRepository.findById(id).orElse(null)?.let { r -> r.project?.let { p -> { u: User -> accessControl.isAllowed(u, p, r, Operation.READ) } } }
        ResourceType.PULL_REQUEST ->
            pullRequestRepository.findById(id).orElse(null)?.let { r -> { u: User -> accessControl.isAllowed(u, r.toProject, r, Operation.READ) } }
        ResourceType.REVIEW_COMMENT ->
            reviewCommentRepository.findById(id).orElse(null)?.let { r ->
                r.thread?.let { th -> th.pullRequest?.toProject ?: th.project }?.let { p -> { u: User -> accessControl.isAllowed(u, p, r, Operation.READ) } }
            }
        ResourceType.COMMENT_THREAD ->
            commentThreadRepository.findById(id).orElse(null)?.let { r ->
                (r.pullRequest?.toProject ?: r.project)?.let { p -> { u: User -> accessControl.isAllowed(u, p, r, Operation.READ) } }
            }
        ResourceType.PROJECT ->
            projectRepository.findById(id).orElse(null)?.let { p -> { u: User -> accessControl.isAllowed(u, p, Operation.READ) } }
        else -> null
    }
}
