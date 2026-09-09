package com.github.yonaprojects.yona.domain.comment

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

@Service
@Transactional
class CommentServiceImpl(
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val userRepository: UserRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val watchService: WatchService,
    private val accessControl: AccessControl,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    // yona Comment.updateMention() 대응.
    private val mentionService: MentionService
) : CommentService {

    // yona models/User.java의 LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH(문자 클래스 내 "-"의 range
    // 파싱 모호성을 피하려 하이픈을 클래스 끝으로 옮김 — 문자 집합(영숫자/하이픈/슬래시) 자체는
    // 동일) + NotificationEvent.getMentionedUsers()의 매칭 패턴 대응 —
    // owner/project 형식의 그룹 멘션을 포착하려면 '/'를 허용해야 한다.
    private val mentionPattern = Pattern.compile("@[a-zA-Z0-9/-]+([_.][a-z_.A-Z0-9/-]+)*")

    // yona BoardApp/IssueApp의 AddPreviousContent()+getPrevious() 대응
    // — 새 댓글 알림의 oldValue("인용 이전 내용")를 채운다. 첫 댓글이면 원본 게시물/이슈 본문을,
    // 답글이면 같은 부모의 마지막 형제 답글(없으면 부모 댓글 자신)을, 그 외(최상위 새 댓글)면 게시물/
    // 이슈의 마지막 댓글을 인용한다. IssueApp의 numOfComments 불일치 자가복구(가비지 댓글 삭제) 로직은
    // Ebean 캐시 특유의 데이터 정합성 땜질이라 옮기지 않는다 — yona는 매 저장 시 count를 직접 재계산한다.
    private fun resolvePostingPreviousContents(posting: Posting, parentComment: PostingComment?): String {
        val existingComments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        if (existingComments.isEmpty()) {
            return quotePrevious("Original posting", posting.body ?: "", posting.updatedDate, posting.authorLoginId)
        }
        val previous = if (parentComment != null) {
            existingComments.filter { it.parentComment?.id == parentComment.id }.lastOrNull() ?: parentComment
        } else {
            existingComments.last()
        }
        return quotePrevious("Previous comment", previous.contents, previous.createdDate, previous.authorLoginId)
    }

    private fun resolveIssuePreviousContents(issue: Issue, parentComment: IssueComment?): String {
        val existingComments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
        if (existingComments.isEmpty()) {
            return quotePrevious("Original issue", issue.body ?: "", issue.updatedDate, issue.authorLoginId)
        }
        val previous = if (parentComment != null) {
            existingComments.filter { it.parentComment?.id == parentComment.id }.lastOrNull() ?: parentComment
        } else {
            existingComments.last()
        }
        return quotePrevious("Previous comment", previous.contents, previous.createdDate, previous.authorLoginId)
    }

    private fun quotePrevious(title: String, contents: String, date: Instant?, authorLoginId: String?): String {
        return "\n\n<br />\n\n--- $title from @${authorLoginId ?: ""}  ${formatShortDate(date)} ---\n\n<br />\n\n$contents"
    }

    // yona utils/JodaDateUtil.java의 getOptionalShortDate() 대응.
    private fun formatShortDate(date: Instant?): String {
        if (date == null) return ""
        val zone = ZoneId.systemDefault()
        val target = date.atZone(zone)
        val current = Instant.now().atZone(zone)
        val pattern = when {
            target.year != current.year -> "yy.MM.dd 'at' h:mm a"
            target.toLocalDate() != current.toLocalDate() -> "MMM d 'at' h:mm a"
            else -> "'at' h:mm a"
        }
        return target.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    override fun createIssueComment(
        issueId: Long,
        contents: String,
        author: User,
        parentCommentId: Long?
    ): IssueComment {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        
        var parentComment: IssueComment? = null
        if (parentCommentId != null) {
            parentComment = issueCommentRepository.findById(parentCommentId).orElse(null)
        }

        // 저장 전에 미리 계산해야 지금 만드는 이 댓글이 "마지막 댓글"에 섞여 들어가지 않는다.
        val previousContents = resolveIssuePreviousContents(issue, parentComment)

        val comment = IssueComment(
            contents = contents,
            createdDate = Instant.now(),
            authorId = author.id,
            authorLoginId = author.loginId,
            authorName = author.name,
            projectId = issue.project.id,
            issue = issue,
            parentComment = parentComment
        )
        val savedComment = issueCommentRepository.save(comment)

        val mentionedUsers = extractMentionedUsers(contents)
        // yona Comment.save()의 updateMention() 대응.
        mentionService.update(ResourceType.ISSUE_COMMENT, savedComment.id.toString(), mentionedUsers)
        val title = "[${issue.project.name}] 이슈 #${issue.number}에 새 댓글이 등록되었습니다."
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = author.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_COMMENT,
            resourceId = savedComment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            oldValue = previousContents,
            newValue = contents
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == author.id }

        // 멘션된 사용자들 추가
        receivers.addAll(mentionedUsers)
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        return savedComment
    }

    override fun createPostingComment(
        postingId: Long,
        contents: String,
        author: User,
        parentCommentId: Long?
    ): PostingComment {
        val posting = postingRepository.findById(postingId).orElseThrow { IllegalArgumentException("Posting not found") }
        
        var parentComment: PostingComment? = null
        if (parentCommentId != null) {
            parentComment = postingCommentRepository.findById(parentCommentId).orElse(null)
        }

        // 저장 전에 미리 계산해야 지금 만드는 이 댓글이 "마지막 댓글"에 섞여 들어가지 않는다.
        val previousContents = resolvePostingPreviousContents(posting, parentComment)

        val comment = PostingComment(
            contents = contents,
            createdDate = Instant.now(),
            authorId = author.id,
            authorLoginId = author.loginId,
            authorName = author.name,
            projectId = posting.project.id,
            posting = posting,
            parentComment = parentComment
        )
        val savedComment = postingCommentRepository.save(comment)

        // yona AbstractPosting.save()/update()의 numOfComments = computeNumOfComments() 대응.
        posting.numOfComments = postingCommentRepository.countByPostingId(posting.id!!)
        postingRepository.save(posting)

        val mentionedUsers = extractMentionedUsers(contents)
        // yona Comment.save()의 updateMention() 대응.
        mentionService.update(ResourceType.NONISSUE_COMMENT, savedComment.id.toString(), mentionedUsers)
        val title = "[${posting.project.name}] 게시글 #${posting.number}에 새 댓글이 등록되었습니다."
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = author.id,
            created = Instant.now(),
            resourceType = ResourceType.NONISSUE_COMMENT,
            resourceId = savedComment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            oldValue = previousContents,
            newValue = contents
        )

        // 게시글(BOARD_POST) 감시 연동
        val authorUser = posting.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            projectId = posting.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == author.id }

        receivers.addAll(mentionedUsers)
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        return savedComment
    }

    // yona NotificationEvent.getMentionedUsers() 대응. 개별 사용자 멘션뿐
    // 아니라 조직 이름(@orgname → 조직 멤버 전원)과 owner/project 형식(@owner/project → 프로젝트
    // 멤버 전원)도 확장한다. 기존 게스트 계정 제외 정책은 확장된 멤버에도 동일하게 적용한다.
    // 조직/프로젝트 멤버는 엔티티의 in-memory 컬렉션(org.organizationUsers 등) 대신 리포지토리로
    // 직접 조회한다 — 같은 트랜잭션 안에서 방금 저장된 멤버가 부모 엔티티의 이미 초기화된(비어있는)
    // 컬렉션에 반영되지 않는 Hibernate 1차 캐시 함정을 피하기 위함이다.
    override fun extractMentionedUsers(contents: String): Set<User> {
        val users = mutableSetOf<User>()
        val matcher = mentionPattern.matcher(contents)
        while (matcher.find()) {
            val mentionWord = matcher.group().substring(1)

            organizationRepository.findByName(mentionWord).ifPresent { org ->
                organizationUserRepository.findByOrganizationId(org.id!!).forEach { users.add(it.user) }
            }

            if (mentionWord.contains("/")) {
                val lastSlash = mentionWord.lastIndexOf("/")
                val projectName = mentionWord.substring(lastSlash + 1)
                val ownerLoginId = mentionWord.substring(0, lastSlash)
                projectRepository.findByOwnerAndName(ownerLoginId, projectName).ifPresent { project ->
                    projectUserRepository.findByProjectId(project.id!!).forEach { users.add(it.user) }
                }
            }

            val user = userRepository.findByLoginId(mentionWord).orElse(null)
            if (user != null) {
                users.add(user)
            }
        }
        users.removeIf { it.isGuest }
        return users
    }

    override fun updateIssueComment(commentId: Long, contents: String, author: User): IssueComment {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.issue.project, comment, Operation.UPDATE)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        val saved = issueCommentRepository.save(comment)
        // yona Comment.update()의 updateMention() 대응.
        mentionService.update(ResourceType.ISSUE_COMMENT, saved.id.toString(), extractMentionedUsers(contents))
        return saved
    }

    override fun deleteIssueComment(commentId: Long, author: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.issue.project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }
        issueCommentRepository.delete(comment)
    }

    override fun updatePostingComment(commentId: Long, contents: String, author: User): PostingComment {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.posting.project, comment, Operation.UPDATE)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        val saved = postingCommentRepository.save(comment)
        // yona Comment.update()의 updateMention() 대응.
        mentionService.update(ResourceType.NONISSUE_COMMENT, saved.id.toString(), extractMentionedUsers(contents))
        return saved
    }

    override fun deletePostingComment(commentId: Long, author: User) {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.posting.project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }
        val posting = comment.posting
        postingCommentRepository.delete(comment)

        // yona AbstractPosting.save()/update()의 numOfComments = computeNumOfComments() 대응.
        posting.numOfComments = postingCommentRepository.countByPostingId(posting.id!!)
        postingRepository.save(posting)
    }
}