package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.watch.WatchService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class CodeReviewServiceImpl(
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val commitCommentRepository: CommitCommentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val projectUserRepository: ProjectUserRepository,
    private val attachmentService: AttachmentService,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val commentService: CommentService,
    private val watchService: WatchService,
    private val pullRequestService: PullRequestService,
    private val accessControl: AccessControl
) : CodeReviewService {

    private val logger = LoggerFactory.getLogger(CodeReviewServiceImpl::class.java)

    override fun createReviewComment(
        project: Project,
        pullRequest: PullRequest?,
        commitId: String?,
        contents: String,
        codeRange: CodeRange?,
        threadId: Long?,
        currentUser: User
    ): ReviewComment {
        val userIdent = UserIdent(currentUser)
        val comment = ReviewComment(
            contents = contents,
            createdDate = Instant.now(),
            author = userIdent
        )

        val targetThread: CommentThread
        if (threadId == null) {
            if (codeRange != null && codeRange.startLine != null) {
                val thread = CodeCommentThread()
                if (commitId != null) {
                    thread.commitId = commitId
                } else if (pullRequest != null) {
                    thread.commitId = pullRequest.mergedCommitIdTo
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom ?: ""
                }

                thread.commitId?.let { cid ->
                    try {
                        val commit = repositoryService.getRepository(project).getCommit(cid)
                        val codeAuthor = commit?.getAuthor()
                        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): RepositoryService의
                        // userResolver는 userRepository.findByEmail() 결과만 반환하므로 codeAuthor가
                        // non-null이면 항상 실제 영속 User(id 항상 존재)다 — codeAuthor.id != null은
                        // 항상 참이라 이 조건의 null 분기는 성립할 수 없다.
                        if (codeAuthor != null && codeAuthor.id != null && !codeAuthor.isGuest) {
                            thread.codeAuthors.add(codeAuthor)
                        }
                    } catch (e: Exception) {
                        // VCS에서 커밋 작성자 로딩 실패 시 예외를 조용히 처리하거나 로깅
                    }
                }

                thread.codeRange = codeRange
                targetThread = thread
            } else {
                val thread = NonRangedCodeCommentThread()
                if (commitId != null) {
                    thread.commitId = commitId
                } else if (pullRequest != null) {
                    thread.commitId = pullRequest.mergedCommitIdTo
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom ?: ""
                }
                targetThread = thread
            }

            targetThread.project = project
            targetThread.state = CommentThread.ThreadState.OPEN
            targetThread.createdDate = comment.createdDate
            targetThread.author = userIdent
            targetThread.pullRequest = pullRequest

            commentThreadRepository.save(targetThread)
        } else {
            targetThread = commentThreadRepository.findById(threadId)
                .orElseThrow { IllegalArgumentException("CommentThread not found for id: $threadId") }
        }

        comment.thread = targetThread
        targetThread.reviewComments.add(comment)

        val savedComment = reviewCommentRepository.save(comment)

        if (pullRequest != null) {
            pullRequestRepository.save(pullRequest)
        }

        publishNewReviewCommentNotification(project, targetThread, savedComment, currentUser)

        return savedComment
    }

    // yona NotificationEvent.forNewComment(sender, pullRequest, newComment)(PR 위) /
    // forNewCommitComment(project, comment, commitId, author)(PR 밖 커밋 위) 대응. 새 댓글이든
    // 기존 스레드에 대한 답글이든 legacy는 구분 없이 항상 이 알림을 발행한다(PullRequestApp.newComment/
    // CodeHistoryApp.newCommitComment 모두 스레드 신규/기존 분기 이후 조건 없이 호출).
    private fun publishNewReviewCommentNotification(project: Project, thread: CommentThread, comment: ReviewComment, sender: User) {
        val mentioned = commentService.extractMentionedUsers(comment.contents)
        val receivers = mutableSetOf<User>()
        receivers.addAll(mentioned)

        val pullRequest = thread.pullRequest
        val title: String
        if (pullRequest != null) {
            title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}에 새 리뷰 댓글이 등록되었습니다."
            receivers.addAll(
                watchService.findActualWatchers(
                    baseWatchers = emptySet(),
                    resourceType = ResourceType.PULL_REQUEST,
                    resourceId = pullRequest.id.toString(),
                    projectId = pullRequest.toProject.id,
                    eventType = EventType.NEW_REVIEW_COMMENT
                )
            )
        } else {
            val commitId = thread.commitId
            title = "[${project.name}] 커밋 리뷰에 새 댓글이 등록되었습니다."
            if (commitId != null) {
                receivers.addAll(getCommitWatchers(project, commitId, EventType.NEW_REVIEW_COMMENT))
            }
        }
        receivers.removeIf { it.id == sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.REVIEW_COMMENT,
            resourceId = comment.id.toString(),
            eventType = EventType.NEW_REVIEW_COMMENT,
            newValue = comment.contents,
            receivers = receivers
        )
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): resourceId가 매번 새로 생성되는
        // comment.id라 NotificationEventRecorder의 draft-window 병합 대상이 될 "같은 resourceId의
        // 이전 이벤트"가 결코 존재할 수 없어 record()가 null을 반환하는 경로에 도달 불가.
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    // yona playRepository/Commit.java의 getWatchers(project)/asResource(project) 대응. PR 밖(순수 커밋
    // 위) 리뷰/커밋 댓글·스레드 상태변경 알림의 "커밋 단위 감시자"를 계산한다. 기본 감시자는 (1) 커밋
    // 작성자(게스트 제외), (2) 이미 이 커밋에 댓글을 남긴 모든 사용자(git이면 ReviewComment 스레드,
    // svn 계열이면 CommitComment)이고, 여기에 Watch 엔티티로 이 커밋(resourceType=COMMIT,
    // resourceId="{projectId}:{commitId}" — legacy Commit.asResource()와 동일한 합성 키)을 명시적으로
    // 감시 중인 사용자까지 findActualWatchers()로 합산한다.
    private fun getCommitWatchers(project: Project, commitId: String, eventType: EventType): Set<User> {
        val baseWatchers = mutableSetOf<User>()

        try {
            val author = repositoryService.getRepository(project).getCommit(commitId)?.getAuthor()
            // 테스트 커버리지 도달 불가: createReviewComment의 codeAuthor 로딩과 동일한 이유
            // (userResolver는 실제 영속 User만 반환)로 author.id != null은 항상 참이다.
            if (author != null && author.id != null && !author.isGuest) {
                baseWatchers.add(author)
            }
        } catch (e: Exception) {
            // VCS에서 커밋 작성자 조회 실패 시 조용히 무시(createReviewComment의 codeAuthor 조회와 동일한 방어)
        }

        commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, commitId)
            .flatMap { it.reviewComments }
            .mapNotNull { it.author?.id }
            .forEach { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }

        commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
            .mapNotNull { it.author?.id }
            .forEach { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }

        return watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.COMMIT,
            resourceId = "${project.id}:$commitId",
            projectId = project.id,
            eventType = eventType
        )
    }

    override fun deleteReviewComment(commentId: Long, currentUser: User) {
        val comment = reviewCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("ReviewComment not found for id: $commentId") }

        val thread = comment.thread ?: throw IllegalStateException("Comment has no associated thread.")
        // 테스트 커버리지 도달 불가: thread.id는 @GeneratedValue(IDENTITY)라 영속화된(findById로
        // 조회 가능한) 스레드는 id가 결코 null일 수 없다.
        val threadId = thread.id ?: throw IllegalStateException("Thread has no id.")
        // yona AccessControl.java:205-301 isProjectResourceAllowed() 대응 (P1-116). 작성자 또는 [GL-utils_AccessControl-009]
        // 프로젝트 role==MANAGER로만 좁게 검사하던 것을, 사이트매니저/조직관리자 우회까지 포함하는
        // AccessControl.isAllowed(user, project, reviewComment, Operation)로 교체.
        val project = thread.project ?: thread.pullRequest?.toProject
            ?: throw IllegalStateException("Comment has no associated project.")

        if (!accessControl.isAllowed(currentUser, project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }

        thread.removeComment(comment)
        reviewCommentRepository.delete(comment)

        val remainingComments = reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(threadId)
        if (remainingComments.isEmpty()) {
            commentThreadRepository.delete(thread)
        }
    }

    override fun createCommitComment(
        project: Project,
        commitId: String,
        contents: String,
        path: String?,
        line: Int?,
        side: CodeRange.Side?,
        currentUser: User
    ): CommitComment {
        val userIdent = UserIdent(currentUser)
        val commitComment = CommitComment(
            project = project,
            commitId = commitId,
            contents = contents,
            path = path,
            line = line,
            side = side,
            createdDate = Instant.now(),
            author = userIdent
        )
        val saved = commitCommentRepository.save(commitComment)
        attachmentService.moveAll(
            ResourceType.USER,
            currentUser.id.toString(),
            ResourceType.COMMIT_COMMENT,
            saved.id.toString()
        )

        publishNewCommitCommentNotification(project, saved, currentUser)

        return saved
    }

    // yona NotificationEvent.forNewSVNCommitComment(project, codeComment, author) 대응.
    // legacy는 이벤트 타입을 NEW_REVIEW_COMMENT가 아니라 NEW_COMMENT로 남긴다(CommitComment는
    // ReviewComment와 다른 모델이라 별도 분기).
    private fun publishNewCommitCommentNotification(project: Project, comment: CommitComment, sender: User) {
        val mentioned = commentService.extractMentionedUsers(comment.contents)
        val receivers = mutableSetOf<User>()
        receivers.addAll(mentioned)
        receivers.addAll(getCommitWatchers(project, comment.commitId, EventType.NEW_COMMENT))
        receivers.removeIf { it.id == sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = "[${project.name}] 커밋 리뷰에 새 댓글이 등록되었습니다.",
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.COMMIT_COMMENT,
            resourceId = comment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            newValue = comment.contents,
            receivers = receivers
        )
        // 테스트 커버리지 도달 불가: publishNewReviewCommentNotification과 동일한 이유
        // (resourceId=comment.id가 매번 새로 생성돼 draft-window 병합 대상이 존재할 수 없음).
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun deleteCommitComment(commentId: Long, currentUser: User) {
        val comment = commitCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("CommitComment not found for id: $commentId") }

        // yona AccessControl.java:205-301 isProjectResourceAllowed() 대응 (P1-116). deleteReviewComment와 [GL-utils_AccessControl-009]
        // 동일하게 사이트매니저/조직관리자 우회를 포함하는 AccessControl.isAllowed()로 교체.
        val project = comment.project ?: throw IllegalStateException("Comment has no associated project.")
        if (!accessControl.isAllowed(currentUser, project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }

        commitCommentRepository.delete(comment)
    }

    

    override fun updateThreadState(
        threadId: Long,
        state: CommentThread.ThreadState,
        currentUser: User
    ): CommentThread {
        val thread = commentThreadRepository.findById(threadId)
            .orElseThrow { IllegalArgumentException("CommentThread not found for id: $threadId") }
        val oldState = thread.state
        if (oldState == state) {
            return thread
        }

        thread.state = state
        val saved = commentThreadRepository.save(thread)

        // yona CommentThreadApp.java:66-70의 try/catch(알림 발행 실패해도 상태변경은 항상 커밋)
        // 대응 (P1-79). 클래스 레벨 @Transactional 하에서 이 호출이 예외를 던지면 메서드 밖으로
        // 전파돼 트랜잭션 전체(방금 저장한 상태변경까지)가 롤백되므로, 반드시 여기서 잡아야 한다.
        try {
            publishThreadStateChangedNotification(saved, oldState, currentUser)
        } catch (e: Exception) {
            logger.warn("Failed to send a notification for thread state change: ${e.message}", e)
        }

        return saved
    }

    // yona NotificationEvent.afterStateChanged(CommentThread.ThreadState, CommentThread) 대응.
    private fun publishThreadStateChangedNotification(thread: CommentThread, oldState: CommentThread.ThreadState, sender: User) {
        val pullRequest = thread.pullRequest
        val title: String
        val watchers: Set<User>
        if (pullRequest != null) {
            title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}의 리뷰 스레드 상태가 변경되었습니다."
            watchers = watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = ResourceType.PULL_REQUEST,
                resourceId = pullRequest.id.toString(),
                projectId = pullRequest.toProject.id,
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED
            )
        } else {
            val project = thread.project ?: return
            val commitId = thread.commitId ?: return
            title = "[${project.name}] 리뷰 스레드 상태가 변경되었습니다."
            watchers = getCommitWatchers(project, commitId, EventType.REVIEW_THREAD_STATE_CHANGED)
        }

        val receivers = watchers.filterTo(mutableSetOf()) { it.id != sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.COMMENT_THREAD,
            resourceId = thread.id.toString(),
            eventType = EventType.REVIEW_THREAD_STATE_CHANGED,
            oldValue = oldState.name,
            newValue = thread.state.name,
            receivers = receivers
        )
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    // yona ReviewApp.java(유일한 진입점) → PullRequest.addReviewer()/removeReviewer()(유일한 모델
    // 메서드)와 달리 yona는 REST 표면이 두 벌(PullRequestController/ReviewApiController)이라 서비스도
    // 각각 독립 구현돼 있었다(P1-49 완료 로그에 이미 기록된 기술부채). PullRequestServiceSpec의 "최소
    // 리뷰어 수 미달 시 머지 실패" 테스트가 PullRequestService.addReviewer에 의존하고 있어 그쪽을
    // 유일한 구현으로 남기고 이쪽은 위임만 한다(P1-62).
    override fun addReviewer(pullRequestId: Long, reviewerId: Long) {
        val reviewer = userRepository.findById(reviewerId)
            .orElseThrow { IllegalArgumentException("User not found") }
        pullRequestService.addReviewer(pullRequestId, reviewer)
    }

    override fun removeReviewer(pullRequestId: Long, reviewerId: Long) {
        val reviewer = userRepository.findById(reviewerId)
            .orElseThrow { IllegalArgumentException("User not found") }
        pullRequestService.removeReviewer(pullRequestId, reviewer)
    }

    // yona CodeCommentThread.isOutdated() 대응 (P1-20)
    override fun isThreadOutdated(threadId: Long): Boolean {
        val thread = commentThreadRepository.findById(threadId).orElse(null) as? CodeCommentThread ?: return false
        return computeOutdated(thread)
    }

    private fun computeOutdated(thread: CodeCommentThread): Boolean {
        if (thread.codeRange.startLine == null || thread.commitId.isNullOrEmpty()) {
            return false
        }
        if (!thread.isOnPullRequest()) {
            return false
        }
        val pullRequest = thread.pullRequest!!
        if (pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return false
        }

        val commitId = thread.commitId!!

        if (thread.isCommitComment()) {
            return pullRequestCommitRepository
                .findFirstByPullRequestAndCommitIdOrderByCreatedDesc(pullRequest, commitId) == null
        }

        var path = thread.codeRange.path ?: ""
        if (path.startsWith("/")) {
            path = path.substring(1)
        }

        val repository = repositoryService.getRepository(pullRequest.toProject)

        return try {
            val unchangedFromMergeBase = noChangesBetween(
                repository, pullRequest.mergedCommitIdFrom!!, thread.prevCommitId, path
            )
            if (!unchangedFromMergeBase) {
                true
            } else {
                !noChangesBetween(repository, pullRequest.mergedCommitIdTo!!, commitId, path)
            }
        } catch (e: Exception) {
            // yona MissingObjectException 처리와 동일 — git 객체 조회 실패 시 outdated로 간주(안전한 기본값)
            true
        }
    }

    private fun noChangesBetween(repository: PlayRepository, revA: String, revB: String, path: String): Boolean {
        val blobA = repository.getBlobId(revA, path)
        val blobB = repository.getBlobId(revB, path)
        return blobA == blobB
    }
}
