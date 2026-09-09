package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 메일로 들어온 답장/신규 이메일을 실제 리소스(이슈/댓글/리뷰스레드 등)로 라우팅·생성하는 핵심
 * 로직. IMAP 연결(IncomingMailPoller)과 분리되어 있어, 실제 메일 서버 없이도 단위테스트로 검증
 * 가능하다.
 *
 * Reply-To 헤더 자체가 이미 리소스 상세 주소(owner/project/<resourceType>/<resourceId>,
 * resolveDirectResource() 참고)로 설정되므로 수신 주소만으로도 스레드를 찾을 수 있고, 여기에
 * 더해 In-Reply-To/References 기반 라우팅(resolveThreads())도 OriginalEmail 미스 시 발신
 * Message-ID의 결정론적 포맷(computeMessageId() 참고)을 직접 역파싱하는 폴백을 갖춰 UI에서 만든
 * 리소스(OriginalEmail이 없는)에 대한 답장도 매칭한다.
 *
 * 한 이메일이 여러 프로젝트로 발송된 경우, OriginalEmail은 최초 성공 리소스 1건만 기록한다.
 */
@Service
class IncomingMailProcessingService(
    private val originalEmailRepository: OriginalEmailRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueService: IssueService,
    private val commentService: CommentService,
    private val attachmentService: AttachmentService,
    private val commentThreadRepository: CommentThreadRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val codeReviewService: CodeReviewService,
    private val mailService: MailService,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val accessControl: AccessControl,
    @Value("\${yona.mailbox.imap.address:}")
    private val inboundBaseAddress: String
) {
    private val logger = LoggerFactory.getLogger(IncomingMailProcessingService::class.java)
    private val htmlCompressor = HtmlCompressor()

    private data class ResolvedThread(
        val resourceType: ResourceType,
        val resourceId: String,
        val projectId: Long?
    )

    @Transactional
    fun process(message: InboundEmailMessage): List<IncomingMailOutcome> {
        if (originalEmailRepository.existsByMessageId(message.messageId)) {
            return listOf(IncomingMailOutcome.Duplicate)
        }

        val sender = userRepository.findByEmail(message.fromAddress).orElse(null)
            ?: return listOf(IncomingMailOutcome.UnknownSender)

        val targets = message.recipientAddresses
            .mapNotNull { runCatching { EmailAddressDetail.of(it) }.getOrNull() }
            .filter { it.isToYona(inboundBaseAddress) && it.detail.isNotBlank() }

        if (targets.isEmpty()) {
            return emptyList()
        }

        val threads = resolveThreads(message) + targets.mapNotNull { resolveDirectResource(it) }
        val outcomes = targets.map { target -> processTarget(target, threads, sender, message) }

        outcomes.firstOrNull { it.isCreated() }?.let { saveOriginalEmail(message.messageId, it) }
        replyWithErrorsIfAny(outcomes, sender, message)

        return outcomes
    }

    // yona EmailHandler.handle()의 "errors.size() > 0이면 도움말+사유를 회신" 분기 대응
    private fun replyWithErrorsIfAny(outcomes: List<IncomingMailOutcome>, sender: User, message: InboundEmailMessage) {
        val reasons = outcomes.filterIsInstance<IncomingMailOutcome.Rejected>().map { it.reason }
        if (reasons.isEmpty()) return

        mailService.sendReply(
            toEmail = sender.email,
            toName = sender.name,
            subject = message.subject,
            textContent = buildHelpMessage(sender.name, reasons),
            inReplyToMessageId = message.messageId
        )
    }

    // yona EmailHandler.getHelpMessage() 대응. i18n 메시지 번들 대신 이 저장소의
    // 다른 사용자 안내문과 마찬가지로 한국어 고정 문구로 단순화했다.
    private fun buildHelpMessage(username: String, errors: List<String> = emptyList()): String {
        val lines = mutableListOf("안녕하세요 ${username}님,")
        if (errors.isNotEmpty()) {
            lines += ""
            lines += "요청을 처리하는 중 다음과 같은 문제가 발생했습니다:"
            errors.forEach { lines += "- $it" }
        }
        lines += ""
        lines += "메일로 이슈를 등록하거나 댓글을 달려면 프로젝트 주소로 보내주세요."
        val sampleAddress = runCatching {
            EmailAddressDetail.of(inboundBaseAddress).let { EmailAddressDetail(it.user, "owner/project", it.domain).toString() }
        }.getOrNull()
        if (sampleAddress != null) {
            lines += "예) $sampleAddress"
        }
        lines += ""
        lines += "감사합니다."
        return lines.joinToString("\n")
    }

    private fun IncomingMailOutcome.isCreated(): Boolean = this is IncomingMailOutcome.IssueCreated ||
        this is IncomingMailOutcome.IssueCommentCreated ||
        this is IncomingMailOutcome.PostingCommentCreated ||
        this is IncomingMailOutcome.ReviewCommentCreated ||
        this is IncomingMailOutcome.CommitCommentCreated

    private fun processTarget(
        target: EmailAddressDetail,
        threads: List<ResolvedThread>,
        sender: User,
        message: InboundEmailMessage
    ): IncomingMailOutcome {
        // yona EmailHandler.getProjects()의 detail=="help" 분기 대응
        if (target.detail.equals("help", ignoreCase = true)) {
            mailService.sendReply(
                toEmail = sender.email,
                toName = sender.name,
                subject = message.subject,
                textContent = buildHelpMessage(sender.name),
                inReplyToMessageId = message.messageId
            )
            return IncomingMailOutcome.HelpRequested
        }

        val segments = target.detail.split("/")
        if (segments.size < 2) {
            return IncomingMailOutcome.Rejected("잘못된 수신 주소 형식: $target")
        }
        val owner = segments[0]
        val projectName = segments[1]

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
        if (project == null || !accessControl.isAllowedToReadProject(sender, project)) {
            return IncomingMailOutcome.Rejected("프로젝트를 찾을 수 없거나 권한이 없습니다: $owner/$projectName")
        }

        val thread = threads.firstOrNull { it.projectId == project.id }
        val outcome = if (thread != null) {
            createComment(thread, sender, message.textBody)
        } else {
            createIssue(project, owner, projectName, sender, message)
        }
        val cidAttachments = attachAttachments(outcome, message.attachments, sender)

        // yona CreationViaEmail.postprocessForHTML() 대응. cid: 첨부가 없어도
        // HtmlCompressor 압축은 시도해야 하므로(yona도 항상 postprocessForHTML을 호출) HTML이면 무조건 진입 —
        // 실제로 바뀐 게 없으면 postprocessHtmlBody 내부에서 저장을 건너뛴다.
        if (message.isHtml) {
            postprocessHtmlBody(outcome, cidAttachments)
        }

        return outcome
    }

    // yona CreationViaEmail.saveAttachments() 대응. Content-ID가 있는 첨부파일은
    // cid → Attachment 매핑으로 반환해, HTML 본문의 cid: 참조 치환에 사용한다.
    private fun attachAttachments(outcome: IncomingMailOutcome, attachments: List<InboundAttachment>, sender: User): Map<String, Attachment> {
        if (attachments.isEmpty()) return emptyMap()
        val (resourceType, resourceId) = when (outcome) {
            is IncomingMailOutcome.IssueCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.IssueCommentCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.PostingCommentCreated -> ResourceType.BOARD_POST to outcome.postingId.toString()
            // yona saveReviewComment()의 saveAttachments(content.attachments, comment.asResource()) 대응.
            // 첨부는 스레드가 아니라 댓글 자신에 붙는다(comment.asResource() == REVIEW_COMMENT+comment.id).
            is IncomingMailOutcome.ReviewCommentCreated -> ResourceType.REVIEW_COMMENT to outcome.commentId.toString()
            is IncomingMailOutcome.CommitCommentCreated -> ResourceType.COMMIT_COMMENT to outcome.commentId.toString()
            else -> return emptyMap()
        }
        val cidMap = mutableMapOf<String, Attachment>()
        for (attachment in attachments) {
            try {
                val (saved, _) = attachmentService.store(
                    attachment.bytes.inputStream(),
                    attachment.fileName,
                    resourceType,
                    resourceId,
                    sender.loginId
                )
                if (!attachment.contentId.isNullOrBlank()) {
                    cidMap[attachment.contentId] = saved
                }
            } catch (e: Exception) {
                logger.warn("메일 첨부파일 저장 실패: fileName=${attachment.fileName}", e)
            }
        }
        return cidMap
    }

    // yona CreationViaEmail.postprocessForHTML() 대응. 이미 생성된 리소스의 본문에서
    // cid: 참조를 실제 저장된 첨부파일 URL로 치환하고 HtmlCompressor로 압축해 갱신한다. Issue/Posting
    // 본문은 Markdown 기준이지만 렌더링 시점에 항상 OWASP sanitizer를 거치므로(MarkdownServiceImpl
    // 참고) 원본 HTML을 그대로 저장해도 안전하다.
    private fun postprocessHtmlBody(outcome: IncomingMailOutcome, cidAttachments: Map<String, Attachment>) {
        when (outcome) {
            is IncomingMailOutcome.IssueCreated -> {
                val issue = issueRepository.findById(outcome.issueId).orElse(null) ?: return
                val processed = postprocessForHtml(issue.body ?: "", cidAttachments) ?: return
                issue.body = processed
                issueRepository.save(issue)
            }
            is IncomingMailOutcome.IssueCommentCreated -> {
                val comment = issueCommentRepository.findById(outcome.commentId).orElse(null) ?: return
                val processed = postprocessForHtml(comment.contents, cidAttachments) ?: return
                comment.contents = processed
                issueCommentRepository.save(comment)
            }
            is IncomingMailOutcome.PostingCommentCreated -> {
                val comment = postingCommentRepository.findById(outcome.commentId).orElse(null) ?: return
                val processed = postprocessForHtml(comment.contents, cidAttachments) ?: return
                comment.contents = processed
                postingCommentRepository.save(comment)
            }
            is IncomingMailOutcome.ReviewCommentCreated -> {
                val comment = reviewCommentRepository.findById(outcome.commentId).orElse(null) ?: return
                val processed = postprocessForHtml(comment.contents, cidAttachments) ?: return
                comment.contents = processed
                reviewCommentRepository.save(comment)
            }
            is IncomingMailOutcome.CommitCommentCreated -> {
                val comment = commitCommentRepository.findById(outcome.commentId).orElse(null) ?: return
                val processed = postprocessForHtml(comment.contents, cidAttachments) ?: return
                comment.contents = processed
                commitCommentRepository.save(comment)
            }
            else -> return
        }
    }

    // yona postprocessForHTML()의 "1. cid 치환 2. HtmlCompressor로 태그 사이 개행 제거" 순서 그대로.
    // 결과가 원본과 동일하면(치환도 압축도 실질적 변화 없음) null을 반환해 불필요한 저장을 막는다.
    private fun postprocessForHtml(html: String, cidAttachments: Map<String, Attachment>): String? {
        val cidReplaced = replaceCidWithAttachments(html, cidAttachments) ?: html
        val compressed = htmlCompressor.compress(cidReplaced)
        return if (compressed == html) null else compressed
    }

    // cid: 참조가 하나도 치환되지 않으면 null을 반환한다.
    private fun replaceCidWithAttachments(html: String, attachments: Map<String, Attachment>): String? {
        val doc = Jsoup.parse(html)
        var replacedAny = false

        for (attrName in listOf("src", "href")) {
            for (tag in doc.select("*[$attrName]")) {
                val uri = tag.attr(attrName).trim()
                if (!uri.startsWith("cid:", ignoreCase = true)) continue

                val cid = uri.substring("cid:".length)
                val attachment = attachments[cid] ?: continue

                tag.attr(attrName, "/files/${attachment.id}")
                replacedAny = true
            }
        }

        if (!replacedAny) return null
        return doc.body().html()
    }

    private fun resolveThreads(message: InboundEmailMessage): List<ResolvedThread> {
        val messageIds = (MessageIdParser.parse(message.inReplyTo) + MessageIdParser.parse(message.references)).toSet()

        return messageIds.mapNotNull { id ->
            val originalEmail = originalEmailRepository.findByMessageId(id).orElse(null)
            val (resourceType, resourceId) = if (originalEmail != null) {
                originalEmail.resourceType to originalEmail.resourceId
            } else {
                resolveByDeterministicMessageId(id) ?: return@mapNotNull null
            }
            resolveResourceProject(resourceType, resourceId)?.let { projectId ->
                ResolvedThread(resourceType, resourceId, projectId)
            }
        }
    }

    // yona EmailHandler.findResourcesByMessageId()의 OriginalEmail 미스 시 폴백(IMAPMessageUtil.
    // getIdLeftFromMessageId() + Resource.findByPath()), 그리고 getThreads()의
    // "case REVIEW_COMMENT: threads.add(resource.getContainer())" 리다이렉트 대응.
    // yona도 발신(outbound) 시점에는 OriginalEmail을 쓰지 않는다(CreationViaEmail.java 3곳에서만,
    // 전부 수신 메일 처리 시점에 기록) — 대신 발신 Message-ID 자체가 Resource.getMessageId()의
    // 결정론적 "<type/id@host>" 포맷(yona computeMessageId()와 동일 포맷)이라 역파싱만으로 UI에서
    // 만든 리소스(OriginalEmail이 없는)에 대한 첫 답장도 매칭할 수 있다.
    private fun resolveByDeterministicMessageId(messageId: String): Pair<ResourceType, String>? {
        val start = messageId.indexOf('<')
        val at = messageId.indexOf('@')
        if (start < 0 || at < 0 || at <= start) return null
        val path = messageId.substring(start + 1, at).trim().removePrefix("/")
        val segments = path.split("/")
        if (segments.size < 2) return null
        val resourceType = try {
            ResourceType.getValue(segments[0])
        } catch (e: IllegalArgumentException) {
            return null
        }
        val resourceId = segments[1]

        if (resourceType == ResourceType.REVIEW_COMMENT) {
            val threadId = resourceId.toLongOrNull()
                ?.let { reviewCommentRepository.findById(it).orElse(null) }
                ?.thread?.id
                ?: return null
            return ResourceType.COMMENT_THREAD to threadId.toString()
        }

        return when (resourceType) {
            ResourceType.COMMENT_THREAD, ResourceType.ISSUE_POST, ResourceType.BOARD_POST,
            // yona에는 없는 커밋 댓글 전용 모델이지만 첨부/원본메일 저장에서
            // 이미 REVIEW_COMMENT/COMMIT_COMMENT를 동급으로 다루고 있어 폴백 대상에도 동일하게 포함한다.
            ResourceType.COMMIT_COMMENT -> resourceType to resourceId
            else -> null
        }
    }

    // yona EmailHandler.getResourceFromDetail() 대응. detail이
    // "owner/project/<resourceType>/<resourceId>" 형식(resourceType은 ResourceType.getValue()가
    // 받는 전체 문자열, 예: issue_post)이면 In-Reply-To/References 없이도 그 리소스를 바로 스레드로 취급한다.
    private fun resolveDirectResource(target: EmailAddressDetail): ResolvedThread? {
        val segments = target.detail.split("/")
        if (segments.size < 4) return null

        val resourceType = try {
            ResourceType.getValue(segments[2])
        } catch (e: IllegalArgumentException) {
            return null
        }
        val resourceId = segments[3]

        val projectId = resolveResourceProject(resourceType, resourceId) ?: return null
        return ResolvedThread(resourceType, resourceId, projectId)
    }

    private fun resolveResourceProject(resourceType: ResourceType, resourceId: String): Long? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (resourceType) {
            ResourceType.ISSUE_POST -> issueRepository.findById(id).orElse(null)?.project?.id
            ResourceType.BOARD_POST -> postingRepository.findById(id).orElse(null)?.project?.id
            ResourceType.COMMENT_THREAD -> commentThreadRepository.findById(id).orElse(null)?.project?.id
            ResourceType.COMMIT_COMMENT -> commitCommentRepository.findById(id).orElse(null)?.project?.id
            else -> {
                logger.debug("아직 지원하지 않는 스레드 리소스 타입이라 스킵: $resourceType")
                null
            }
        }
    }

    private fun createComment(thread: ResolvedThread, sender: User, body: String): IncomingMailOutcome {
        return when (thread.resourceType) {
            // yona IssueApp.java:1004-1011 newReferComment() 대응. isResourceCreatable()의
            // ISSUE_COMMENT 케이스로 판단해, 발신자가 프로젝트 READ 권한이 없어도 그 이슈의
            // 작성자/담당자/공유대상이면 메일 답장으로 댓글을 달 수 있다(legacy와 동일하게 거부 시
            // 조용히 Rejected로 회신 — 메일 인바운드 발신자 이메일 노출 방지).
            ResourceType.ISSUE_POST -> {
                val issueId = thread.resourceId.toLong()
                val issue = issueRepository.findById(issueId).orElse(null)
                    ?: return IncomingMailOutcome.Rejected("이슈를 찾을 수 없습니다: $issueId")
                if (!accessControl.isIssueCommentCreatable(sender, issue.project, issue)) {
                    return IncomingMailOutcome.Rejected("댓글 작성 권한이 없습니다: $issueId")
                }
                val comment = commentService.createIssueComment(issueId, body, sender)
                IncomingMailOutcome.IssueCommentCreated(comment.id!!, issueId)
            }
            ResourceType.BOARD_POST -> {
                val comment = commentService.createPostingComment(thread.resourceId.toLong(), body, sender)
                IncomingMailOutcome.PostingCommentCreated(comment.id!!, thread.resourceId.toLong())
            }
            ResourceType.COMMENT_THREAD -> createReviewCommentReply(thread, sender, body)
            ResourceType.COMMIT_COMMENT -> createCommitCommentReply(thread, sender, body)
            else -> IncomingMailOutcome.Rejected("지원하지 않는 스레드 타입: ${thread.resourceType}")
        }
    }

    // yona EmailHandler.getThreads()의 COMMENT_THREAD 분기(CreationViaEmail.saveReviewComment) 대응
    private fun createReviewCommentReply(thread: ResolvedThread, sender: User, body: String): IncomingMailOutcome {
        val threadId = thread.resourceId.toLong()
        val commentThread = commentThreadRepository.findById(threadId).orElse(null)
            ?: return IncomingMailOutcome.Rejected("코드리뷰 스레드를 찾을 수 없습니다: $threadId")
        val project = commentThread.project
            ?: return IncomingMailOutcome.Rejected("코드리뷰 스레드에 프로젝트 정보가 없습니다: $threadId")

        val comment = codeReviewService.createReviewComment(project, null, null, body, null, threadId, sender)
        return IncomingMailOutcome.ReviewCommentCreated(comment.id!!, threadId)
    }

    // yona EmailHandler.getThreads()의 REVIEW_COMMENT->컨테이너 분기(커밋 댓글 부분) 대응.
    // yona는 커밋 댓글에 별도 스레드 개념이 없어, 같은 커밋/경로/라인에 새 댓글을 추가하는 것으로 답장을 표현한다.
    private fun createCommitCommentReply(thread: ResolvedThread, sender: User, body: String): IncomingMailOutcome {
        val originalId = thread.resourceId.toLong()
        val original = commitCommentRepository.findById(originalId).orElse(null)
            ?: return IncomingMailOutcome.Rejected("커밋 댓글을 찾을 수 없습니다: $originalId")
        val project = original.project
            ?: return IncomingMailOutcome.Rejected("커밋 댓글에 프로젝트 정보가 없습니다: $originalId")

        val comment = codeReviewService.createCommitComment(
            project, original.commitId, body, original.path, original.line, original.side, sender
        )
        return IncomingMailOutcome.CommitCommentCreated(comment.id!!)
    }

    private fun createIssue(
        project: Project,
        owner: String,
        projectName: String,
        sender: User,
        message: InboundEmailMessage
    ): IncomingMailOutcome {
        if (!accessControl.isProjectResourceCreatable(sender, project, ResourceType.ISSUE_POST)) {
            return IncomingMailOutcome.Rejected("이슈 생성 권한이 없습니다: $owner/$projectName")
        }

        val issue = Issue(title = message.subject, body = message.textBody, project = project)
        val saved = issueService.createIssue(issue, sender, null, null, null)
        return IncomingMailOutcome.IssueCreated(saved.id!!, owner, projectName)
    }

    private fun saveOriginalEmail(messageId: String, outcome: IncomingMailOutcome) {
        val (resourceType, resourceId) = when (outcome) {
            is IncomingMailOutcome.IssueCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.IssueCommentCreated -> ResourceType.ISSUE_COMMENT to outcome.commentId.toString()
            is IncomingMailOutcome.PostingCommentCreated -> ResourceType.NONISSUE_COMMENT to outcome.commentId.toString()
            is IncomingMailOutcome.ReviewCommentCreated -> ResourceType.COMMENT_THREAD to outcome.threadId.toString()
            is IncomingMailOutcome.CommitCommentCreated -> ResourceType.COMMIT_COMMENT to outcome.commentId.toString()
            else -> return
        }
        originalEmailRepository.save(OriginalEmail(messageId = messageId, resourceType = resourceType, resourceId = resourceId))
    }
}
