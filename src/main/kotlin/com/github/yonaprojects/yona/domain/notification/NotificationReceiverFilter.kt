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

        val project = projectOf(event) ?: return
        event.receivers.removeIf { !accessControl.isAllowed(it, project, Operation.READ) }
    }

    // 이벤트가 가리키는 리소스가 속한 프로젝트. 프로젝트를 알 수 없는 리소스(조직·사용자 등)는 null.
    private fun projectOf(event: NotificationEvent): Project? {
        val id = event.resourceId.toLongOrNull() ?: return null
        return when (event.resourceType) {
            ResourceType.ISSUE_POST, ResourceType.ISSUE_STATE ->
                issueRepository.findById(id).orElse(null)?.project
            ResourceType.ISSUE_COMMENT ->
                issueCommentRepository.findById(id).orElse(null)?.issue?.project
            ResourceType.BOARD_POST ->
                postingRepository.findById(id).orElse(null)?.project
            ResourceType.NONISSUE_COMMENT ->
                postingCommentRepository.findById(id).orElse(null)?.posting?.project
            ResourceType.COMMIT_COMMENT ->
                commitCommentRepository.findById(id).orElse(null)?.project
            ResourceType.PULL_REQUEST ->
                pullRequestRepository.findById(id).orElse(null)?.toProject
            ResourceType.REVIEW_COMMENT ->
                reviewCommentRepository.findById(id).orElse(null)?.thread?.let { it.pullRequest?.toProject ?: it.project }
            ResourceType.COMMENT_THREAD ->
                commentThreadRepository.findById(id).orElse(null)?.let { it.pullRequest?.toProject ?: it.project }
            ResourceType.PROJECT ->
                projectRepository.findById(id).orElse(null)
            else -> null
        }
    }
}
