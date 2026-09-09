package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.enumeration.EventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.support.HistoryUtil
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Value

@Service
@Transactional(readOnly = true)
class PostingServiceImpl(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val postingCommentRepository: PostingCommentRepository,
    private val watchService: WatchService,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val titleHeadService: TitleHeadService,
    private val commentService: CommentService,
    // yona AbstractPosting.updateMention() 대응.
    private val mentionService: MentionService,
    // yona BoardApp.editPost()의 commitReadmeFile()/unmarkAnotherReadmePostingIfExists() 대응 —
    // BoardViewController.editPost()(form POST 경로)에만 있던 로직을 REST 경로
    // (BoardController.updatePosting(), board/edit.html의 실제 제출 경로)에서도 쓸 수 있도록 서비스
    // 계층으로 옮겨왔다.
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val gitBaseDir: String,
    // IssueServiceImpl.nextIssueNumber()와 동일한 이유(JPQL 벌크 UPDATE가 1차 캐시를 갱신하지 않음)로
    // 채번 직후 project 엔티티를 새로고침하는 데 쓴다.
    private val entityManager: EntityManager
) : PostingService {

    // yona NotificationEvent.afterNewPost/afterResourceDeleted 대응.
    private fun publishNotification(
        posting: Posting,
        actor: User,
        eventType: EventType,
        title: String
    ) {
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = actor.id,
            created = Instant.now(),
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            eventType = eventType,
            newValue = title
        )

        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(actor),
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            projectId = posting.project.id,
            eventType = eventType
        ).toMutableSet()
        // yona NotificationEvent.getReceivers(abstractPosting, except)의 getMentionedUsers(body) 대응.
        // 신규 게시글 본문의 @멘션도 수신자에 포함한다.
        receivers.addAll(commentService.extractMentionedUsers(posting.body ?: ""))
        receivers.removeIf { it.id == actor.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun getPostings(projectId: Long, pageable: Pageable): Page<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProject(project, pageable)
    }

    override fun getNotices(projectId: Long): List<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNotice(project, true)
    }

    override fun getPosting(projectId: Long, number: Long): Posting? {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNumber(project, number)
    }

    @Transactional
    override fun createPosting(projectId: Long, posting: Posting, authorId: Long, explicitNumber: Long?): Posting {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val author = userRepository.findById(authorId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        posting.project = project
        if (explicitNumber != null && explicitNumber > 0) {
            // yona AbstractPosting.saveWithNumber() 대응 — project.lastPostingNumber 카운터는
            // 건드리지 않고 지정된 번호를 그대로 쓴다.
            posting.number = explicitNumber
        } else {
            // IssueServiceImpl.nextIssueNumber()와 같은 근본원인/수정 — 잠금 없이
            // project.lastPostingNumber를 읽고 증가시켜 저장하면 동시 요청 두 개가 같은 번호를 읽어
            // posting(project_id, number) UNIQUE 제약 위반(500)이 날 수 있었다. 증가 UPDATE 문 자체의
            // 행 잠금(모든 RDBMS가 예외 없이 즉시 배타 잠금을 거는 기본 동작)으로 원자적으로 채번한다
            // — ProjectRepository.incrementLastIssueNumber() 주석 참고 (SELECT FOR UPDATE 기반 잠금은
            // H2 AUTO_SERVER 파일 모드에서 실제로 블로킹하지 않으므로 폐기했다).
            projectRepository.incrementLastPostingNumber(project.id!!)
            posting.number = projectRepository.findLastPostingNumber(project.id!!)
            // JPQL 벌크 UPDATE는 1차 캐시를 갱신하지 않는다 — project가 이미 관리 중인 엔티티라면
            // DB의 최신 값으로 즉시 동기화한다(IssueServiceImpl.nextIssueNumber() 참고).
            if (entityManager.contains(project)) {
                entityManager.refresh(project)
            }
        }
        posting.authorId = author.id
        posting.authorLoginId = author.loginId
        posting.authorName = author.name
        posting.createdDate = Instant.now()
        posting.updatedDate = Instant.now()

        val saved = postingRepository.save(posting)

        // yona AbstractPosting.save()의 updateMention() 대응.
        mentionService.update(ResourceType.BOARD_POST, saved.id.toString(), commentService.extractMentionedUsers(saved.body ?: ""))

        // yona AbstractPosting.save()의 TitleHead.saveTitleHeadKeyword() 대응.
        titleHeadService.saveTitleHeadKeyword(project, saved.title)

        val title = "[${project.name}] 새 게시글: ${saved.title}"
        publishNotification(saved, author, EventType.NEW_POSTING, title)

        return saved
    }

    @Transactional
    override fun updatePosting(
        projectId: Long,
        number: Long,
        title: String,
        body: String,
        notice: Boolean,
        readme: Boolean,
        authorId: Long,
        sendNotificationMail: Boolean
    ): Posting {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")

        val originalBody = posting.body
        val originalTitle = posting.title
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): posting.authorId는 타입상 Long?이지만
        // createPosting()이 항상 실제 author.id를 대입하므로 이 서비스를 거쳐 저장된 Posting은 authorId가
        // 결코 null일 수 없다.
        val isAuthoredByUpdater = posting.authorId == authorId
        val updater = userRepository.findById(authorId).orElse(null)

        posting.title = title.trim()
        posting.body = body
        posting.notice = notice
        posting.readme = readme
        posting.updatedDate = Instant.now()

        // yona AbstractPostingApp.editPosting()의 "posting.updatedByAuthorId = UserApp.currentUser().id"
        // 대응 — history 유무와 무관하게 편집이 있을 때마다 항상 갱신된다.
        if (updater != null) {
            posting.updatedByAuthorId = updater.id
            posting.updatedByAuthorLoginId = updater.loginId
            posting.updatedByAuthorName = updater.name
        }

        // yona AbstractPostingApp.editPosting()의 history 갱신 대응.
        if (updater != null && (originalBody ?: "") != body) {
            posting.history = HistoryUtil.appendHistory(
                originalBody = originalBody,
                newBody = body,
                updaterName = updater.name,
                updaterLoginId = updater.loginId,
                updatedDate = posting.updatedDate,
                existingHistory = posting.history
            )
        }

        val saved = postingRepository.save(posting)

        // yona BoardApp.editPost()의 "if (post.readme) { commitReadmeFile(...);
        // unmarkAnotherReadmePostingIfExists(...); }" 대응 — 제출된(새) readme
        // 값이 true면 README.md를 실제로 커밋하고, 같은 프로젝트의 다른 readme 글은 해제한다.
        if (readme && updater != null) {
            try {
                val bare = BareCommit(saved.project, updater, gitBaseDir)
                // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): updatePosting()의 title/body
                // 파라미터가 non-null String이고 바로 위에서 posting.title/body에 대입하므로 saved.title/
                // saved.body는 이 경로에서 결코 null일 수 없다.
                bare.commitTextFile("README.md", saved.body ?: "", saved.title ?: "")
            } catch (e: Exception) {
                // yona commitReadmeFile()과 동일하게 커밋 실패를 게시글 수정 자체의 실패로 보지 않는다.
            }

            postingRepository.findByProjectAndReadme(saved.project, true)
                .filter { it.id != saved.id }
                .forEach {
                    it.readme = false
                    postingRepository.save(it)
                }
        }

        // yona AbstractPosting.update()의 updateMention() 대응.
        // 테스트 커버리지 도달 불가: 위 BareCommit 호출부와 동일한 이유로 saved.body는 null일 수 없다.
        mentionService.update(ResourceType.BOARD_POST, saved.id.toString(), commentService.extractMentionedUsers(saved.body ?: ""))

        // yona AbstractPostingApp.editPosting()의 TitleHead.saveTitleHeadKeyword()/deleteTitleHeadKeyword()
        // 대응. 제목이 안 바뀌었어도 legacy와 동일하게 매 수정마다 무조건 두 호출을 모두 실행한다.
        titleHeadService.saveTitleHeadKeyword(saved.project, saved.title)
        titleHeadService.deleteTitleHeadKeyword(saved.project, originalTitle)

        // yona BoardApp.editPost의 isSelectedToSendNotificationMail() 대응.
        // 본인 글이 아니면 옵션과 무관하게 항상 발송하고, 본인 글이면 체크박스를 선택했을 때만 발송한다.
        if (sendNotificationMail || !isAuthoredByUpdater) {
            if (updater != null) {
                val title2 = "[${saved.project.name}] 게시글 수정: ${saved.title}"
                val notificationEvent = NotificationEvent(
                    title = title2,
                    senderId = updater.id,
                    created = Instant.now(),
                    resourceType = ResourceType.BOARD_POST,
                    resourceId = saved.id.toString(),
                    eventType = EventType.POSTING_BODY_CHANGED,
                    oldValue = originalBody,
                    newValue = saved.body
                )
                val receivers = watchService.findActualWatchers(
                    baseWatchers = setOf(updater),
                    resourceType = ResourceType.BOARD_POST,
                    resourceId = saved.id.toString(),
                    projectId = saved.project.id,
                    eventType = notificationEvent.eventType
                ).toMutableSet()
                receivers.removeIf { it.id == updater.id }
                notificationEvent.receivers = receivers

                notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
            }
        }

        return saved
    }

    @Transactional
    override fun deletePosting(projectId: Long, number: Long, authorId: Long) {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")
        val actor = userRepository.findById(authorId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        // yona NotificationEvent.afterResourceDeleted(): 실제 삭제 전에 알림을 발행한다.
        val title = "[${posting.project.name}] 게시글 삭제: ${posting.title}"
        publishNotification(posting, actor, EventType.RESOURCE_DELETED, title)

        deletePostingCascade(posting)
    }

    // yona Project.delete()의 posting 삭제 루프(posting.delete()) 대응. PostingComment.posting
    // FK가 nullable=false라 반드시 먼저 삭제해야 postingRepository.delete(posting)가 FK 제약 위반 없이
    // 성공한다.
    override fun deletePostingCascade(posting: Posting) {
        // 연관된 댓글의 첨부파일도 일괄 삭제
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.NONISSUE_COMMENT, comment.id.toString())
        }

        attachmentService.deleteAll(ResourceType.BOARD_POST, posting.id.toString())
        // yona ResourcePersistAdapter.postDelete() 대응.
        watchService.deleteAll(ResourceType.BOARD_POST, posting.id.toString())
        // yona AbstractPosting.delete()의 TitleHead.deleteTitleHeadKeyword() 대응.
        titleHeadService.deleteTitleHeadKeyword(posting.project, posting.title)
        // 답글(parentComment)이 원 댓글보다 항상 나중에 생성되므로, 생성일 역순으로 지우면
        // 답글이 부모보다 먼저 삭제돼 자기참조 FK(parent_comment_id) 위반을 피할 수 있다.
        postingCommentRepository.deleteAll(comments.asReversed())
        postingRepository.delete(posting)
    }
}
