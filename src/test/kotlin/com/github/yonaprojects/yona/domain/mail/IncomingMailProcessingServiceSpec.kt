package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository

class IncomingMailProcessingServiceSpec : DescribeSpec({
    val originalEmailRepository = mockk<OriginalEmailRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueService = mockk<IssueService>()
    val commentService = mockk<CommentService>()
    val attachmentService = mockk<AttachmentService>(relaxed = true)
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    val mailService = mockk<MailService>(relaxed = true)
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>(relaxed = true)
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val service = IncomingMailProcessingService(
        originalEmailRepository, userRepository, projectRepository,
        issueRepository, postingRepository, issueService, commentService, attachmentService,
        commentThreadRepository, commitCommentRepository, codeReviewService, mailService,
        issueCommentRepository, postingCommentRepository, reviewCommentRepository,
        accessControl = accessControl,
        inboundBaseAddress = "yona@example.com"
    )

    val sender = User(id = 1L, loginId = "gildong", name = "길동", email = "gildong@example.com")
    val project = Project(id = 10L, name = "hive", owner = "dlab", projectScope = ProjectScope.PUBLIC)

    beforeTest {
        clearMocks(
            originalEmailRepository, userRepository, projectRepository, issueRepository, postingRepository,
            issueService, commentService, attachmentService, commentThreadRepository, commitCommentRepository,
            codeReviewService, mailService, issueCommentRepository, postingCommentRepository, reviewCommentRepository
        )
        every { originalEmailRepository.existsByMessageId(any()) } returns false
        every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(sender)
    }

    fun baseMessage(
        messageId: String = "<msg1@mail.example.com>",
        recipients: List<String> = listOf("yona+dlab/hive@example.com"),
        inReplyTo: String? = null,
        references: String? = null,
        attachments: List<InboundAttachment> = emptyList()
    ) = InboundEmailMessage(
        messageId = messageId,
        subject = "메일로 만든 이슈",
        fromAddress = "gildong@example.com",
        fromName = "길동",
        recipientAddresses = recipients,
        inReplyTo = inReplyTo,
        references = references,
        textBody = "메일 본문 내용",
        attachments = attachments
    )

    describe("IncomingMailProcessingService.process") {
        it("이미 처리된 messageId면 Duplicate를 반환하고 아무 것도 생성하지 않아야 한다") {
            every { originalEmailRepository.existsByMessageId("<dup@mail.example.com>") } returns true

            val result = service.process(baseMessage(messageId = "<dup@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.Duplicate)
            verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
        }

        it("발신자가 등록된 사용자가 아니면 UnknownSender를 반환해야 한다") {
            every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

            val result = service.process(baseMessage().copy(fromAddress = "unknown@example.com"))

            result shouldBe listOf(IncomingMailOutcome.UnknownSender)
        }

        it("수신 주소 중 yona로 향한 것이 없으면 빈 목록을 반환해야 한다") {
            val result = service.process(baseMessage(recipients = listOf("someone-else@example.com")))

            result shouldBe emptyList()
        }

        it("연관 스레드가 없으면 새 이슈를 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
            val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
            every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage())

            result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            val captured = slot<OriginalEmail>()
            verify(exactly = 1) { originalEmailRepository.save(capture(captured)) }
            captured.captured.messageId shouldBe "<msg1@mail.example.com>"
            captured.captured.resourceType shouldBe ResourceType.ISSUE_POST
            captured.captured.resourceId shouldBe "100"
        }

        it("In-Reply-To가 같은 프로젝트의 기존 이슈를 가리키면 새 이슈 대신 댓글을 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

            val originalIssueEmail = OriginalEmail(
                id = 1L, messageId = "<original@mail.example.com>",
                resourceType = ResourceType.ISSUE_POST, resourceId = "50"
            )
            every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)

            val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
            every { issueRepository.findById(50L) } returns Optional.of(existingIssue)

            val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
            every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage(inReplyTo = "<original@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.IssueCommentCreated(200L, 50L))
            verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
        }

        // yona IssueApp.java:1004-1011 newReferComment()의 isResourceCreatable() 거부 분기 대응 [GL-controllers_IssueApp-049]
        // (P2-34) — 비공개 프로젝트에서 발신자가 그 이슈의 작성자/담당자/공유대상도 아니고 프로젝트
        // 멤버도 아니면, 메일 답장으로도 댓글을 달 수 없고 조용히 Rejected를 반환한다(legacy도 forbidden
        // 대신 로그만 남기고 조용히 무시).
        it("비공개 프로젝트에서 이슈 작성자/담당자/공유대상이 아니고 프로젝트 멤버도 아니면 댓글 생성 없이 Rejected를 반환해야 한다") {
            val privateProject = Project(id = 11L, name = "secret", owner = "dlab", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndName("dlab", "secret") } returns Optional.of(privateProject)

            val originalIssueEmail = OriginalEmail(
                id = 2L, messageId = "<original2@mail.example.com>",
                resourceType = ResourceType.ISSUE_POST, resourceId = "51"
            )
            every { originalEmailRepository.findByMessageId("<original2@mail.example.com>") } returns Optional.of(originalIssueEmail)

            val privateIssue = Issue(id = 51L, title = "비공개 이슈", body = "...", project = privateProject, number = 1L, authorId = 999L)
            every { issueRepository.findById(51L) } returns Optional.of(privateIssue)

            val result = service.process(
                baseMessage(recipients = listOf("yona+dlab/secret@example.com"), inReplyTo = "<original2@mail.example.com>")
            )

            result.filterIsInstance<IncomingMailOutcome.Rejected>() shouldHaveSize 1
            verify(exactly = 0) { commentService.createIssueComment(any(), any(), any()) }
        }

        it("References가 같은 프로젝트의 기존 게시글을 가리키면 게시글 댓글을 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

            val originalPostingEmail = OriginalEmail(
                id = 2L, messageId = "<original-post@mail.example.com>",
                resourceType = ResourceType.BOARD_POST, resourceId = "70"
            )
            every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)

            val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
            every { postingRepository.findById(70L) } returns Optional.of(existingPosting)

            val savedComment = PostingComment(id = 300L, contents = "메일 본문 내용", posting = existingPosting)
            every { commentService.createPostingComment(70L, "메일 본문 내용", sender) } returns savedComment
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage(references = "<original-post@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.PostingCommentCreated(300L, 70L))
        }

        it("이슈 생성 권한이 없으면 Rejected를 반환해야 한다") {
            val privateProject = Project(id = 20L, name = "secret", owner = "dlab", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndName("dlab", "secret") } returns Optional.of(privateProject)

            val result = service.process(baseMessage(recipients = listOf("yona+dlab/secret@example.com")))

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        it("프로젝트를 찾을 수 없으면 Rejected를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.empty()

            val result = service.process(baseMessage())

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        it("detail이 owner/project 형식이 아니면(세그먼트 부족) Rejected를 반환해야 한다") {
            val result = service.process(baseMessage(recipients = listOf("yona+dlab@example.com")))

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        describe("첨부파일 저장 (P1-29, yona CreationViaEmail.saveAttachments() 대응)") {
            it("새 이슈 생성 시 메일에 첨부된 파일을 이슈에 연결해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "screenshot.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3))

                service.process(baseMessage(attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "screenshot.png", ResourceType.ISSUE_POST, "100", "gildong")
                }
            }

            it("기존 이슈에 댓글을 생성할 때도 첨부파일을 그 이슈에 연결해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
                every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "log.txt", contentType = "text/plain", bytes = byteArrayOf(9))

                service.process(baseMessage(inReplyTo = "<original@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "log.txt", ResourceType.ISSUE_POST, "50", "gildong")
                }
            }

            it("첨부파일이 없으면 AttachmentService를 호출하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                service.process(baseMessage())

                verify(exactly = 0) { attachmentService.store(any(), any(), any(), any(), any()) }
            }
        }

        describe("HTML 본문 보존 및 cid 인라인 이미지 치환 (P1-47, yona CreationViaEmail.postprocessForHTML/replaceCidWithAttachments 대응)") {
            it("새 이슈 생성 시 HTML 본문의 cid: 참조를 저장된 첨부파일 URL로 치환해 본문을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>사진: <img src=\"cid:image1\"></p>"
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(100L) } returns Optional.of(savedIssue)
                every { issueRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(
                    id = 999L, name = "photo.png", containerType = ResourceType.ISSUE_POST, containerId = "100"
                )
                every {
                    attachmentService.store(any(), "photo.png", ResourceType.ISSUE_POST, "100", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "photo.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3), contentId = "image1"
                )
                val message = baseMessage(attachments = listOf(attachment)).copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<Issue>()
                verify(exactly = 1) { issueRepository.save(capture(bodySlot)) }
                bodySlot.captured.body shouldBe "<p>사진: <img src=\"/files/999\"></p>"
            }

            it("cid 치환도 HtmlCompressor 압축도 실질적인 변화가 없으면 이슈 본문을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>서식 있는 본문</p>"
                val savedIssue = Issue(id = 101L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 2L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(101L) } returns Optional.of(savedIssue)

                val message = baseMessage().copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { issueRepository.save(any()) }
            }
        }

        // yona CreationViaEmail.postprocessForHTML()의 HtmlCompressor 사용부 대응 (P1-61).
        describe("HtmlCompressor를 통한 HTML 본문 압축 (P1-61, yona postprocessForHTML()의 new HtmlCompressor().compress() 대응)") {
            it("cid 첨부가 전혀 없어도 태그 사이 개행을 압축해 이슈 본문을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>제목</p>\n<p>본문</p>"
                val savedIssue = Issue(id = 103L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 7L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(103L) } returns Optional.of(savedIssue)
                every { issueRepository.save(any()) } returnsArgument 0

                val message = baseMessage().copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<Issue>()
                verify(exactly = 1) { issueRepository.save(capture(bodySlot)) }
                bodySlot.captured.body shouldBe "<p>제목</p> <p>본문</p>"
                // 첨부가 없어 attachAttachments()는 빈 맵을 반환하므로 AttachmentService는 호출되지 않아야 한다.
                verify(exactly = 0) { attachmentService.store(any(), any(), any(), any(), any()) }
            }
        }

        describe("리뷰 댓글/커밋 댓글 스레드로의 메일 답장 (P1-30, yona EmailHandler.getThreads() 대응)") {
            it("COMMENT_THREAD(코드리뷰 스레드)를 가리키는 답장이면 그 스레드에 리뷰 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val savedReviewComment = ReviewComment(id = 400L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review@mail.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.ReviewCommentCreated(400L, 60L))
            }

            it("COMMIT_COMMENT를 가리키는 답장이면 같은 커밋/경로/라인에 새 커밋 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)

                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)

                val savedCommitComment = CommitComment(id = 500L, project = project, commitId = "abc123", contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", "메일 본문 내용", "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<commit@mail.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.CommitCommentCreated(500L))
            }
        }

        describe("리뷰 댓글/커밋 댓글 답장의 첨부파일/cid 치환 (P1-59, yona saveReviewComment()의 saveAttachments/replaceCidWithAttachments 대응)") {
            it("코드리뷰 스레드 답장의 첨부파일은 스레드가 아니라 새로 생성된 리뷰 댓글(REVIEW_COMMENT)에 연결돼야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val savedReviewComment = ReviewComment(id = 400L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "diagram.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3))

                service.process(baseMessage(inReplyTo = "<review@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "diagram.png", ResourceType.REVIEW_COMMENT, "400", "gildong")
                }
            }

            it("커밋 댓글 답장의 첨부파일은 새로 생성된 커밋 댓글(COMMIT_COMMENT)에 연결돼야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)

                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)

                val savedCommitComment = CommitComment(id = 500L, project = project, commitId = "abc123", contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", "메일 본문 내용", "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "trace.log", contentType = "text/plain", bytes = byteArrayOf(9))

                service.process(baseMessage(inReplyTo = "<commit@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "trace.log", ResourceType.COMMIT_COMMENT, "500", "gildong")
                }
            }

            it("코드리뷰 스레드 답장의 HTML 본문에 있는 cid: 참조를 저장된 첨부파일 URL로 치환해 리뷰 댓글을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val htmlBody = "<p>스크린샷: <img src=\"cid:shot1\"></p>"
                val savedReviewComment = ReviewComment(id = 401L, contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, any(), null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { reviewCommentRepository.findById(401L) } returns Optional.of(savedReviewComment)
                every { reviewCommentRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(
                    id = 998L, name = "shot.png", containerType = ResourceType.REVIEW_COMMENT, containerId = "401"
                )
                every {
                    attachmentService.store(any(), "shot.png", ResourceType.REVIEW_COMMENT, "401", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "shot.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3), contentId = "shot1"
                )
                val message = baseMessage(inReplyTo = "<review@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<ReviewComment>()
                verify(exactly = 1) { reviewCommentRepository.save(capture(bodySlot)) }
                bodySlot.captured.contents shouldBe "<p>스크린샷: <img src=\"/files/998\"></p>"
            }
        }

        // yona EmailHandler.findResourcesByMessageId()의 OriginalEmail 미스 시 폴백 대응 (P1-60).
        // UI에서 만든 리소스는 OriginalEmail이 없지만, 발신 Message-ID 자체가 결정론적 포맷이라
        // In-Reply-To/References만으로도 답장이 올바른 스레드로 라우팅돼야 한다.
        describe("UI에서 만든 리소스에 대한 답장 라우팅 (P1-60, OriginalEmail 미스 시 결정론적 Message-ID 역파싱 폴백)") {
            it("UI에서 만든 이슈의 알림 메일(OriginalEmail 없음)에 답장하면 그 이슈에 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                // In-Reply-To가 가리키는 Message-ID에 대해 OriginalEmail 레코드가 전혀 없다 — UI 작성.
                every { originalEmailRepository.findByMessageId("<issue_post/70@yona.example.com>") } returns Optional.empty()
                val existingIssue = Issue(id = 70L, title = "UI로 만든 이슈", body = "...", project = project, number = 5L)
                every { issueRepository.findById(70L) } returns Optional.of(existingIssue)
                val savedComment = IssueComment(id = 700L, contents = "메일 본문 내용", issue = existingIssue)
                every { commentService.createIssueComment(70L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<issue_post/70@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCommentCreated(700L, 70L))
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("UI에서 만든 리뷰 댓글의 알림 메일에 답장하면 댓글 자신이 아니라 그 댓글이 속한 스레드에 답글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<review_comment/900@yona.example.com>") } returns Optional.empty()

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                val uiCreatedComment = ReviewComment(id = 900L, contents = "UI로 작성한 댓글", author = UserIdent(sender), thread = thread)
                every { reviewCommentRepository.findById(900L) } returns Optional.of(uiCreatedComment)
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val savedReviewComment = ReviewComment(id = 901L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review_comment/900@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.ReviewCommentCreated(901L, 60L))
            }

            it("Message-ID를 역파싱해도 알 수 없는 리소스 타입이면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<user@yona.example.com>") } returns Optional.empty()
                val savedIssue = Issue(id = 102L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 6L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<user@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(102L, "dlab", "hive"))
            }
        }

        describe("help 자동응답 및 실패 사유 회신 (P1-31, yona EmailHandler.getHelpMessage/reply 대응)") {
            it("수신 주소 detail이 'help'면 도움말 회신을 보내고 다른 처리는 하지 않아야 한다") {
                val result = service.process(baseMessage(recipients = listOf("yona+help@example.com")))

                result shouldBe listOf(IncomingMailOutcome.HelpRequested)
                verify(exactly = 1) {
                    mailService.sendReply(
                        toEmail = "gildong@example.com",
                        toName = "길동",
                        subject = "메일로 만든 이슈",
                        textContent = any(),
                        inReplyToMessageId = "<msg1@mail.example.com>"
                    )
                }
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("처리 중 거부(Rejected)된 대상이 있으면 실패 사유를 요약한 회신을 보내야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "secret") } returns Optional.of(
                    Project(id = 99L, name = "secret", owner = "dlab", projectScope = ProjectScope.PRIVATE)
                )

                service.process(baseMessage(recipients = listOf("yona+dlab/secret@example.com")))

                val captured = slot<String>()
                verify(exactly = 1) {
                    mailService.sendReply(
                        toEmail = "gildong@example.com",
                        toName = "길동",
                        subject = "메일로 만든 이슈",
                        textContent = capture(captured),
                        inReplyToMessageId = "<msg1@mail.example.com>"
                    )
                }
                captured.captured.contains("프로젝트를 찾을 수 없거나 권한이 없습니다") shouldBe true
            }

            it("정상적으로 이슈가 생성되면 회신 메일을 보내지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                service.process(baseMessage())

                verify(exactly = 0) { mailService.sendReply(any(), any(), any(), any(), any()) }
            }
        }

        describe("수신 주소 detail에 리소스 경로 직접 명시(owner/project/resourceType/id) 지원 (P1-32, yona EmailHandler.getResourceFromDetail 대응)") {
            it("detail이 owner/project/issue_post/50 형식이면 In-Reply-To 없이도 그 이슈에 바로 댓글을 달아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
                every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/50@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCommentCreated(200L, 50L))
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("직접 지정한 리소스가 다른 프로젝트 소속이면 무시하고 일반 새 이슈 생성으로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val otherProject = Project(id = 20L, name = "other", owner = "other-owner", projectScope = ProjectScope.PUBLIC)
                val issueInOtherProject = Issue(id = 60L, title = "다른 프로젝트 이슈", body = "...", project = otherProject, number = 1L)
                every { issueRepository.findById(60L) } returns Optional.of(issueInOtherProject)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/60@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("알 수 없는 리소스 타입 세그먼트는 무시하고 일반 새 이슈 생성으로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/unknown_type/99@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }
        }

        // postprocessHtmlBody()의 5개 outcome 분기 각각의 "리소스가 이미 사라졌으면 저장을 건너뛴다"
        // 경로(orElse(null) ?: return)와, IssueComment/PostingComment/CommitComment 분기의 cid 치환+저장
        // 경로를 보강한다. IssueCreated/ReviewCommentCreated의 성공 경로는 기존 테스트에서 이미 커버됨.
        describe("postprocessHtmlBody의 리소스 타입별 나머지 분기 (동시성으로 리소스가 사라진 경우 포함)") {
            it("새 이슈 생성 직후 이슈가 사라지면(동시성) HTML 후처리를 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>본문</p>"
                val savedIssue = Issue(id = 105L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 9L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(105L) } returns Optional.empty()

                val message = baseMessage().copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { issueRepository.save(any()) }
            }

            it("기존 이슈 댓글 답장의 HTML 본문에 있는 cid: 참조를 치환해 이슈 댓글을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)

                val htmlBody = "<p>답장: <img src=\"cid:reply1\"></p>"
                val savedComment = IssueComment(id = 210L, contents = htmlBody, issue = existingIssue)
                every { commentService.createIssueComment(50L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueCommentRepository.findById(210L) } returns Optional.of(savedComment)
                every { issueCommentRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(id = 601L, name = "reply.png", containerType = ResourceType.ISSUE_POST, containerId = "50")
                every {
                    attachmentService.store(any(), "reply.png", ResourceType.ISSUE_POST, "50", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "reply.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "reply1"
                )
                val message = baseMessage(inReplyTo = "<original@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<IssueComment>()
                verify(exactly = 1) { issueCommentRepository.save(capture(bodySlot)) }
                bodySlot.captured.contents shouldBe "<p>답장: <img src=\"/files/601\"></p>"
            }

            it("기존 이슈 댓글 답장 처리 중 그 댓글이 사라지면 HTML 후처리를 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val htmlBody = "<p>답장 본문</p>"
                val savedComment = IssueComment(id = 211L, contents = htmlBody, issue = existingIssue)
                every { commentService.createIssueComment(50L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueCommentRepository.findById(211L) } returns Optional.empty()

                val message = baseMessage(inReplyTo = "<original@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { issueCommentRepository.save(any()) }
            }

            it("게시글 댓글 답장의 HTML 본문에 있는 cid: 참조를 치환해 게시글 댓글을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalPostingEmail = OriginalEmail(
                    id = 2L, messageId = "<original-post@mail.example.com>",
                    resourceType = ResourceType.BOARD_POST, resourceId = "70"
                )
                every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)
                val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
                every { postingRepository.findById(70L) } returns Optional.of(existingPosting)

                val htmlBody = "<p>답장: <img src=\"cid:pimg\"></p>"
                val savedComment = PostingComment(id = 310L, contents = htmlBody, posting = existingPosting)
                every { commentService.createPostingComment(70L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { postingCommentRepository.findById(310L) } returns Optional.of(savedComment)
                every { postingCommentRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(id = 602L, name = "p.png", containerType = ResourceType.BOARD_POST, containerId = "70")
                every {
                    attachmentService.store(any(), "p.png", ResourceType.BOARD_POST, "70", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "p.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "pimg"
                )
                val message = baseMessage(references = "<original-post@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<PostingComment>()
                verify(exactly = 1) { postingCommentRepository.save(capture(bodySlot)) }
                bodySlot.captured.contents shouldBe "<p>답장: <img src=\"/files/602\"></p>"
            }

            it("게시글 댓글 답장 처리 중 그 댓글이 사라지면 HTML 후처리를 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalPostingEmail = OriginalEmail(
                    id = 2L, messageId = "<original-post@mail.example.com>",
                    resourceType = ResourceType.BOARD_POST, resourceId = "70"
                )
                every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)
                val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
                every { postingRepository.findById(70L) } returns Optional.of(existingPosting)
                val htmlBody = "<p>답장 본문</p>"
                val savedComment = PostingComment(id = 311L, contents = htmlBody, posting = existingPosting)
                every { commentService.createPostingComment(70L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { postingCommentRepository.findById(311L) } returns Optional.empty()

                val message = baseMessage(references = "<original-post@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { postingCommentRepository.save(any()) }
            }

            it("커밋 댓글 답장의 HTML 본문에 있는 cid: 참조를 치환해 커밋 댓글을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)
                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)

                val htmlBody = "<p>답장: <img src=\"cid:cimg\"></p>"
                val savedCommitComment = CommitComment(id = 510L, project = project, commitId = "abc123", contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", htmlBody, "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { commitCommentRepository.findById(510L) } returns Optional.of(savedCommitComment)
                every { commitCommentRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(id = 603L, name = "c.png", containerType = ResourceType.COMMIT_COMMENT, containerId = "510")
                every {
                    attachmentService.store(any(), "c.png", ResourceType.COMMIT_COMMENT, "510", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "c.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "cimg"
                )
                val message = baseMessage(inReplyTo = "<commit@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<CommitComment>()
                verify(exactly = 1) { commitCommentRepository.save(capture(bodySlot)) }
                bodySlot.captured.contents shouldBe "<p>답장: <img src=\"/files/603\"></p>"
            }

            it("커밋 댓글 답장 처리 중 새로 생성된 댓글이 사라지면 HTML 후처리를 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)
                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)
                val htmlBody = "<p>답장 본문</p>"
                val savedCommitComment = CommitComment(id = 511L, project = project, commitId = "abc123", contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", htmlBody, "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { commitCommentRepository.findById(511L) } returns Optional.empty()

                val message = baseMessage(inReplyTo = "<commit@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { commitCommentRepository.save(any()) }
            }

            it("코드리뷰 스레드 답장 처리 중 새로 생성된 리뷰 댓글이 사라지면 HTML 후처리를 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)
                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)
                val htmlBody = "<p>답장 본문</p>"
                val savedReviewComment = ReviewComment(id = 402L, contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, htmlBody, null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { reviewCommentRepository.findById(402L) } returns Optional.empty()

                val message = baseMessage(inReplyTo = "<review@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { reviewCommentRepository.save(any()) }
            }

            it("HTML 본문에서 cid가 아닌 href와 매핑되지 않는 cid는 그대로 두고, 매핑되는 cid만 치환해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p><img src=\"cid:img1\"><a href=\"https://example.com\">link</a><a href=\"cid:missing\">no attachment</a></p>"
                val savedIssue = Issue(id = 104L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 8L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(104L) } returns Optional.of(savedIssue)
                every { issueRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(id = 777L, name = "img.png", containerType = ResourceType.ISSUE_POST, containerId = "104")
                every {
                    attachmentService.store(any(), "img.png", ResourceType.ISSUE_POST, "104", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(
                    fileName = "img.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "img1"
                )
                val message = baseMessage(attachments = listOf(attachment)).copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<Issue>()
                verify(exactly = 1) { issueRepository.save(capture(bodySlot)) }
                bodySlot.captured.body shouldBe "<p><img src=\"/files/777\"><a href=\"https://example.com\">link</a><a href=\"cid:missing\">no attachment</a></p>"
            }

            // 프로젝트 읽기 권한 자체가 없으면 processTarget()이 attachAttachments/postprocessHtmlBody
            // 호출 이전에 곧바로 Rejected를 반환하므로, 이 케이스는 그 두 메서드의 else 분기가 아니라
            // "아예 호출되지 않음"을 검증한다(else 분기 자체는 위 동시성 테스트에서 별도로 검증).
            it("프로젝트 읽기 권한이 없어 Rejected가 반환되면 첨부파일 저장도 HTML 후처리도 시도하지 않아야 한다") {
                val privateProject = Project(id = 30L, name = "secret2", owner = "dlab", projectScope = ProjectScope.PRIVATE)
                every { projectRepository.findByOwnerAndName("dlab", "secret2") } returns Optional.of(privateProject)
                val attachment = InboundAttachment(fileName = "ignored.png", contentType = "image/png", bytes = byteArrayOf(1))
                val message = baseMessage(recipients = listOf("yona+dlab/secret2@example.com"), attachments = listOf(attachment))
                    .copy(textBody = "<p>본문</p>", isHtml = true)

                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { attachmentService.store(any(), any(), any(), any(), any()) }
                verify(exactly = 0) { issueRepository.save(any()) }
            }

            it("첨부파일 저장 중 예외가 발생해도 나머지 처리를 계속하고, 실패한 첨부의 cid는 치환하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<img src=\"cid:good\"><img src=\"cid:bad\">"
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(100L) } returns Optional.of(savedIssue)
                every { issueRepository.save(any()) } returnsArgument 0

                val savedAttachment = Attachment(id = 1L, name = "good.png", containerType = ResourceType.ISSUE_POST, containerId = "100")
                every {
                    attachmentService.store(any(), "good.png", ResourceType.ISSUE_POST, "100", "gildong")
                } returns (savedAttachment to true)
                every {
                    attachmentService.store(any(), "bad.png", ResourceType.ISSUE_POST, "100", "gildong")
                } throws RuntimeException("스토리지 오류")

                val attachments = listOf(
                    InboundAttachment(fileName = "good.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "good"),
                    InboundAttachment(fileName = "bad.png", contentType = "image/png", bytes = byteArrayOf(2), contentId = "bad")
                )
                val message = baseMessage(attachments = attachments).copy(textBody = htmlBody, isHtml = true)

                val result = service.process(message)

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
                verify(exactly = 1) { attachmentService.store(any(), "good.png", ResourceType.ISSUE_POST, "100", "gildong") }
                verify(exactly = 1) { attachmentService.store(any(), "bad.png", ResourceType.ISSUE_POST, "100", "gildong") }
                val bodySlot = slot<Issue>()
                verify(exactly = 1) { issueRepository.save(capture(bodySlot)) }
                bodySlot.captured.body shouldBe "<img src=\"/files/1\"><img src=\"cid:bad\">"
            }

            it("게시글 댓글 답장에도 첨부파일이 그 댓글에 연결돼야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalPostingEmail = OriginalEmail(
                    id = 2L, messageId = "<original-post@mail.example.com>",
                    resourceType = ResourceType.BOARD_POST, resourceId = "70"
                )
                every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)
                val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
                every { postingRepository.findById(70L) } returns Optional.of(existingPosting)
                val savedComment = PostingComment(id = 300L, contents = "메일 본문 내용", posting = existingPosting)
                every { commentService.createPostingComment(70L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "notes.txt", contentType = "text/plain", bytes = byteArrayOf(1))

                service.process(baseMessage(references = "<original-post@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "notes.txt", ResourceType.BOARD_POST, "70", "gildong")
                }
            }
        }

        // resolveByDeterministicMessageId()의 나머지 분기: 잘못된 형식/미지원 타입/조회 실패 등
        // OriginalEmail 레코드가 없을 때(UI에서 만든 리소스 X, 순수 미스) 폴백 파싱이 어떻게
        // 실패하는지를 각각 검증한다(P1-60).
        describe("결정론적 Message-ID 역파싱 폴백의 나머지 실패 분기") {
            it("Message-ID에 @가 없으면(형식 오류) 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<malformed>") } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<malformed>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("Message-ID 경로에 슬래시가 없으면(타입만 있음) 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<issue_post@yona.example.com>") } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<issue_post@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            // "milestone"(위 테스트)은 ResourceType.getValue()가 파싱에는 성공하고 이후 when절의
            // else로 빠지는 경우이고, 이 테스트는 ResourceType.getValue() 자체가 IllegalArgumentException을
            // 던지는 try/catch 분기를 검증한다 — 서로 다른 코드 경로다.
            it("Message-ID의 리소스 타입 세그먼트가 어떤 ResourceType과도 일치하지 않으면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<bogus_type/5@yona.example.com>") } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<bogus_type/5@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("OriginalEmail 없이 board_post Message-ID를 역파싱하면 그 게시글에 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<board_post/70@yona.example.com>") } returns Optional.empty()
                val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
                every { postingRepository.findById(70L) } returns Optional.of(existingPosting)
                val savedComment = PostingComment(id = 800L, contents = "메일 본문 내용", posting = existingPosting)
                every { commentService.createPostingComment(70L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<board_post/70@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.PostingCommentCreated(800L, 70L))
            }

            it("OriginalEmail 없이 code_comment Message-ID를 역파싱하면 그 커밋 댓글에 답글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<code_comment/80@yona.example.com>") } returns Optional.empty()
                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)
                val savedCommitComment = CommitComment(id = 850L, project = project, commitId = "abc123", contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", "메일 본문 내용", "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<code_comment/80@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.CommitCommentCreated(850L))
            }

            it("OriginalEmail 없이 comment_thread Message-ID를 역파싱하면 그 스레드에 리뷰 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<comment_thread/60@yona.example.com>") } returns Optional.empty()
                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)
                val savedReviewComment = ReviewComment(id = 950L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<comment_thread/60@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.ReviewCommentCreated(950L, 60L))
            }

            it("역파싱된 리소스 타입이 스레드로 지원하지 않는 타입(milestone)이면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<milestone/5@yona.example.com>") } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<milestone/5@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("review_comment Message-ID의 리소스 ID가 숫자가 아니면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<review_comment/abc@yona.example.com>") } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review_comment/abc@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
                verify(exactly = 0) { reviewCommentRepository.findById(any()) }
            }

            it("review_comment Message-ID가 가리키는 리뷰 댓글이 이미 삭제됐으면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<review_comment/999@yona.example.com>") } returns Optional.empty()
                every { reviewCommentRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review_comment/999@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("review_comment Message-ID가 가리키는 리뷰 댓글에 스레드 정보가 없으면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<review_comment/901@yona.example.com>") } returns Optional.empty()
                val orphanComment = ReviewComment(id = 901L, contents = "UI로 작성한 댓글", author = UserIdent(sender), thread = null)
                every { reviewCommentRepository.findById(901L) } returns Optional.of(orphanComment)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review_comment/901@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }
        }

        // resolveResourceProject()의 나머지 분기(비숫자 resourceId, 미지원 리소스 타입, 리소스를
        // 찾을 수 없음, 리소스는 있지만 project가 비어 있음)를 detail 직접 명시 경로(resolveDirectResource)로
        // 검증한다. 성공 경로(4종 타입 모두 project 조회 성공)는 기존 테스트에서 이미 커버됨.
        describe("resolveResourceProject의 나머지 분기 (detail 직접 명시 경로로 검증, P1-32)") {
            it("직접 명시한 resourceId가 숫자가 아니면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/abc@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 리소스 타입이 스레드로 지원하지 않는 타입(milestone)이면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/milestone/5@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 issue_post 리소스를 찾을 수 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { issueRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/999@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 board_post 리소스를 찾을 수 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { postingRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/board_post/999@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 comment_thread 리소스를 찾을 수 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { commentThreadRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/comment_thread/999@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 comment_thread는 있지만 project 정보가 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val orphanThread = CodeCommentThread(id = 60L, project = null, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(orphanThread)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/comment_thread/60@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 code_comment 리소스를 찾을 수 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { commitCommentRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/code_comment/999@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("직접 명시한 code_comment는 있지만 project 정보가 없으면 무시하고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val orphanCommitComment = CommitComment(
                    id = 80L, project = null, commitId = "abc123", contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(orphanCommitComment)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/code_comment/80@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }
        }

        // 스레드 라우팅(resolveThreads/resolveResourceProject) 시점과 실제 댓글 생성
        // (createComment/createReviewCommentReply/createCommitCommentReply) 시점 사이에 리소스가
        // 사라지는 동시성 상황을 재현한다. 같은 id로 두 번 조회하되 두 번째 조회 결과만 다르게 스텁해
        // createComment 계열 메서드 자신의 null-체크 분기(이미 라우팅된 스레드이므로
        // resolveResourceProject의 동일 분기로는 도달할 수 없는 지점)를 검증한다.
        describe("스레드 생성 시점의 동시성으로 인한 나머지 Rejected 분기") {
            // 이 케이스는 부수적으로 outcome이 Rejected일 때 attachAttachments()/postprocessHtmlBody()가
            // 각각의 when절 else 분기(지원하는 5개 outcome 타입이 아니므로 곧바로 반환)를 타는지도 함께 검증한다.
            // Rejected는 processTarget()의 프로젝트 읽기 권한 체크에서도 발생할 수 있지만 그 경우는
            // attachAttachments/postprocessHtmlBody 호출 이전에 이미 반환되므로 이 두 메서드의 else
            // 분기에 도달시키려면 반드시 createComment/createIssue 내부에서 발생한 Rejected여야 한다.
            it("스레드 라우팅 이후 이슈가 삭제되면 댓글 생성 없이 Rejected를 반환하고, 첨부파일 저장과 HTML 후처리도 시도하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returnsMany listOf(Optional.of(existingIssue), Optional.empty())
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "ignored.png", contentType = "image/png", bytes = byteArrayOf(1))
                val message = baseMessage(inReplyTo = "<original@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = "<p>본문</p>", isHtml = true)

                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { commentService.createIssueComment(any(), any(), any()) }
                verify(exactly = 0) { attachmentService.store(any(), any(), any(), any(), any()) }
                verify(exactly = 0) { issueRepository.save(any()) }
                verify(exactly = 0) { issueCommentRepository.save(any()) }
            }

            it("스레드 라우팅 이후 코드리뷰 스레드가 삭제되면 리뷰 댓글 생성 없이 Rejected를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)
                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returnsMany listOf(Optional.of(thread), Optional.empty())
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review@mail.example.com>"))

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { codeReviewService.createReviewComment(any(), any(), any(), any(), any(), any(), any()) }
            }

            it("코드리뷰 스레드는 남아 있지만 프로젝트 정보만 사라지면 Rejected를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)
                val threadWithProject = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                val threadWithoutProject = CodeCommentThread(id = 60L, project = null, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returnsMany listOf(Optional.of(threadWithProject), Optional.of(threadWithoutProject))
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review@mail.example.com>"))

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
            }

            it("스레드 라우팅 이후 커밋 댓글이 삭제되면 커밋 댓글 생성 없이 Rejected를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)
                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returnsMany listOf(Optional.of(originalCommitComment), Optional.empty())
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<commit@mail.example.com>"))

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { codeReviewService.createCommitComment(any(), any(), any(), any(), any(), any(), any()) }
            }

            it("커밋 댓글은 남아 있지만 프로젝트 정보만 사라지면 Rejected를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)
                val withProject = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                val withoutProject = CommitComment(
                    id = 80L, project = null, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returnsMany listOf(Optional.of(withProject), Optional.of(withoutProject))
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<commit@mail.example.com>"))

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
            }
        }

        describe("process()의 나머지 분기 (수신 주소 파싱/필터링, 스레드 소실, OriginalEmail 저장 여부)") {
            it("수신 주소 형식이 잘못돼(@ 없음) 파싱할 수 없으면 그 주소는 무시하고 빈 목록을 반환해야 한다") {
                val result = service.process(baseMessage(recipients = listOf("not-an-email")))

                result shouldBe emptyList()
            }

            it("수신 주소에 detail(plus 태그)이 전혀 없으면(순수 기준 주소) 무시하고 빈 목록을 반환해야 한다") {
                val result = service.process(baseMessage(recipients = listOf("yona@example.com")))

                result shouldBe emptyList()
            }

            it("In-Reply-To가 가리키는 원본 이메일은 있지만 참조된 이슈가 이미 삭제됐으면 스레드 매칭을 포기하고 새 이슈를 생성해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 5L, messageId = "<deleted@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "999"
                )
                every { originalEmailRepository.findByMessageId("<deleted@mail.example.com>") } returns Optional.of(originalIssueEmail)
                every { issueRepository.findById(999L) } returns Optional.empty()
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<deleted@mail.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("모든 대상이 Rejected면 OriginalEmail을 저장하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.empty()

                service.process(baseMessage())

                verify(exactly = 0) { originalEmailRepository.save(any()) }
            }
        }

        // attachAttachments()의 Content-ID 유무 판정(isNullOrBlank, P1-29) 나머지 분기.
        // contentId가 아예 없는(null) 경우는 기존 테스트에서 이미 커버돼 있어, 여기서는
        // "값은 있지만 비어 있는" 두 가지 케이스(빈 문자열/공백만)를 추가로 검증한다.
        describe("첨부파일 Content-ID 공백 판정의 나머지 분기 (P1-29)") {
            it("Content-ID가 빈 문자열이면 cid 매핑에 포함되지 않아 치환이 일어나지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<img src=\"cid:\">"
                val savedIssue = Issue(id = 106L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 10L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(106L) } returns Optional.of(savedIssue)

                val savedAttachment = Attachment(id = 700L, name = "blank.png", containerType = ResourceType.ISSUE_POST, containerId = "106")
                every {
                    attachmentService.store(any(), "blank.png", ResourceType.ISSUE_POST, "106", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(fileName = "blank.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "")
                val message = baseMessage(attachments = listOf(attachment)).copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 1) { attachmentService.store(any(), "blank.png", ResourceType.ISSUE_POST, "106", "gildong") }
                verify(exactly = 0) { issueRepository.save(any()) }
            }

            it("Content-ID가 공백 문자로만 이뤄져 있으면 cid 매핑에 포함되지 않아 치환이 일어나지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<img src=\"cid:%20%20\">"
                val savedIssue = Issue(id = 108L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 12L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(108L) } returns Optional.of(savedIssue)

                val savedAttachment = Attachment(id = 701L, name = "space.png", containerType = ResourceType.ISSUE_POST, containerId = "108")
                every {
                    attachmentService.store(any(), "space.png", ResourceType.ISSUE_POST, "108", "gildong")
                } returns (savedAttachment to true)

                val attachment = InboundAttachment(fileName = "space.png", contentType = "image/png", bytes = byteArrayOf(1), contentId = "   ")
                val message = baseMessage(attachments = listOf(attachment)).copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 1) { attachmentService.store(any(), "space.png", ResourceType.ISSUE_POST, "108", "gildong") }
                verify(exactly = 0) { issueRepository.save(any()) }
            }
        }

        // postprocessHtmlBody()의 "실질적인 변화가 없으면 저장하지 않는다" 분기(P1-47/P1-61)를
        // IssueCreated 이외 4개 리소스 타입에 대해서도 보강한다. IssueCreated의 동일 분기는
        // 위쪽 "HTML 본문 보존 및 cid 인라인 이미지 치환" describe에서 이미 커버돼 있다.
        describe("postprocessHtmlBody 변화 없음 스킵 분기의 나머지 리소스 타입 (P1-47/P1-61)") {
            it("기존 이슈 댓글 답장의 HTML 본문에 실질적인 변화가 없으면 이슈 댓글을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val htmlBody = "<p>변화 없는 답장</p>"
                val savedComment = IssueComment(id = 212L, contents = htmlBody, issue = existingIssue)
                every { commentService.createIssueComment(50L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueCommentRepository.findById(212L) } returns Optional.of(savedComment)

                val message = baseMessage(inReplyTo = "<original@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { issueCommentRepository.save(any()) }
            }

            it("게시글 댓글 답장의 HTML 본문에 실질적인 변화가 없으면 게시글 댓글을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalPostingEmail = OriginalEmail(
                    id = 2L, messageId = "<original-post@mail.example.com>",
                    resourceType = ResourceType.BOARD_POST, resourceId = "70"
                )
                every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)
                val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
                every { postingRepository.findById(70L) } returns Optional.of(existingPosting)
                val htmlBody = "<p>변화 없는 답장</p>"
                val savedComment = PostingComment(id = 312L, contents = htmlBody, posting = existingPosting)
                every { commentService.createPostingComment(70L, htmlBody, sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { postingCommentRepository.findById(312L) } returns Optional.of(savedComment)

                val message = baseMessage(references = "<original-post@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { postingCommentRepository.save(any()) }
            }

            it("코드리뷰 스레드 답장의 HTML 본문에 실질적인 변화가 없으면 리뷰 댓글을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)
                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)
                val htmlBody = "<p>변화 없는 답장</p>"
                val savedReviewComment = ReviewComment(id = 403L, contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, htmlBody, null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { reviewCommentRepository.findById(403L) } returns Optional.of(savedReviewComment)

                val message = baseMessage(inReplyTo = "<review@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { reviewCommentRepository.save(any()) }
            }

            it("커밋 댓글 답장의 HTML 본문에 실질적인 변화가 없으면 커밋 댓글을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)
                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)
                val htmlBody = "<p>변화 없는 답장</p>"
                val savedCommitComment = CommitComment(id = 512L, project = project, commitId = "abc123", contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", htmlBody, "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { commitCommentRepository.findById(512L) } returns Optional.of(savedCommitComment)

                val message = baseMessage(inReplyTo = "<commit@mail.example.com>").copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { commitCommentRepository.save(any()) }
            }

            it("이슈 본문(body)이 null이면 빈 문자열로 취급해 예외 없이 HTML 후처리를 진행해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 107L, title = "메일로 만든 이슈", body = null, project = project, number = 11L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(107L) } returns Optional.of(savedIssue)

                val message = baseMessage().copy(textBody = "메일 본문", isHtml = true)

                service.process(message)

                // body==null -> ""로 대체된 뒤 압축 결과도 동일해 실질적 변화가 없으므로 저장하지
                // 않아야 한다(핵심 검증 대상은 null-elvis 분기가 예외 없이 타는지 여부).
                verify(exactly = 0) { issueRepository.save(any()) }
            }
        }

        // resolveByDeterministicMessageId()의 나머지 분기(P1-60): 역파싱된 리뷰 댓글이 속한 스레드는
        // 존재하지만 그 스레드의 id 자체가 비어 있는(비정상 상태) 경우.
        describe("결정론적 Message-ID 역파싱 폴백의 나머지 실패 분기 (스레드 id 없음)") {
            it("역파싱된 리뷰 댓글이 속한 스레드의 id가 없으면 폴백하지 않고 새 이슈로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { originalEmailRepository.findByMessageId("<review_comment/902@yona.example.com>") } returns Optional.empty()
                val threadWithoutId = CodeCommentThread(id = null, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                val orphanComment = ReviewComment(id = 902L, contents = "UI로 작성한 댓글", author = UserIdent(sender), thread = threadWithoutId)
                every { reviewCommentRepository.findById(902L) } returns Optional.of(orphanComment)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review_comment/902@yona.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }
        }

        // createComment()/createIssue() 내부 권한 체크(AccessControl)의 "거부" 분기(P2-34/P1-118).
        // 기존 테스트는 모두 PUBLIC 프로젝트의 정상 로그인 사용자라 accessControl.isProjectResourceCreatable()/
        // isIssueCommentCreatable()이 항상 true였다. loginId가 빈 문자열인 사용자는
        // isAllowedToReadProject()의 PUBLIC 분기(게스트가 아니면 loginId를 보지 않고 읽기를 허용)는
        // 통과하지만, isProjectResourceCreatable()/isIssueCommentCreatable()의 최초 가드
        // (user.loginId == "")에서 곧바로 거부돼 "읽기는 되지만 생성/댓글 권한은 없는" 상태를 재현할 수 있다.
        describe("메일로 생성/댓글 작성 시 권한 없음 거부 분기 (P2-34/P1-118)") {
            it("발신자의 loginId가 비어 있으면 새 이슈 생성 권한이 없어 Rejected를 반환해야 한다") {
                val noPermissionSender = User(id = 2L, loginId = "", name = "이름없음", email = "noname@example.com")
                every { userRepository.findByEmail("noname@example.com") } returns Optional.of(noPermissionSender)
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val message = baseMessage().copy(fromAddress = "noname@example.com")
                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("발신자의 loginId가 비어 있으면 기존 이슈에 대한 댓글 작성도 거부돼야 한다") {
                val noPermissionSender = User(id = 3L, loginId = "", name = "이름없음2", email = "noname2@example.com")
                every { userRepository.findByEmail("noname2@example.com") } returns Optional.of(noPermissionSender)
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val existingIssue = Issue(id = 56L, title = "기존 이슈", body = "...", project = project, number = 3L, authorId = 999L)
                every { issueRepository.findById(56L) } returns Optional.of(existingIssue)

                val message = baseMessage(recipients = listOf("yona+dlab/hive/issue_post/56@example.com"))
                    .copy(fromAddress = "noname2@example.com")
                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                verify(exactly = 0) { commentService.createIssueComment(any(), any(), any()) }
            }

            it("[TASK-01] 에러 메시지가 포함된 도움말 메시지 발송 분기를 커버한다") {
                // IncomingMailOutcome.Rejected를 발생시키고, errors.isNotEmpty() 분기와 
                // sampleAddress != null 분기(기본적으로 inboundBaseAddress가 유효하므로)를 커버한다.
                val message = baseMessage(recipients = listOf("yona+invalid@example.com"))
                every { mailService.sendReply(any(), any(), any(), any(), any()) } returns Unit
                
                service.process(message)
                
                // mailService.sendReply 호출 시 errors 문구가 포함되어 있는지 확인
                verify(exactly = 1) { 
                    mailService.sendReply(
                        any(), any(), any(), 
                        match { it.contains("요청을 처리하는 중 다음과 같은 문제가 발생했습니다") }, 
                        any()
                    )
                }
            }

            it("[TASK-02] resolveByDeterministicMessageId에서 유효하지 않은 ResourceType 예외 발생 분기를 커버한다") {
                // resolveByDeterministicMessageId 내에서 IllegalArgumentException 발생 유도
                val message = baseMessage(recipients = listOf("yona+dlab/hive@example.com"))
                    .copy(inReplyTo = "<invalid_type/123@yona.io>")
                
                // 에러 발생 없이 무시되고 새 이슈 생성으로 넘어가야 함
                every { originalEmailRepository.findByMessageId(any()) } returns Optional.empty()
                every { originalEmailRepository.save(any()) } answers { firstArg() }
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns null
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { issueService.createIssue(any(), any(), any(), any(), any()) } answers { firstArg<Issue>().apply { id = 123L } }
                
                service.process(message)
            }

            it("[TASK-03] createComment 도달 불가 분기 및 resolveResourceProject else 분기 커버") {
                // owner/project/project/123 으로 보내면 resolveDirectResource가 ResourceType.PROJECT를 파싱하고
                // resolveResourceProject에서 else -> null 을 리턴하여 스레드로 인식되지 않고 새 이슈 생성으로 넘어간다.
                val message = baseMessage(recipients = listOf("yona+dlab/hive/project/123@example.com"))
                every { originalEmailRepository.findByMessageId(any()) } returns Optional.empty()
                every { originalEmailRepository.save(any()) } answers { firstArg() }
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns null
                every { issueService.createIssue(any(), any(), any(), any(), any()) } answers { firstArg<Issue>().apply { id = 124L } }
                
                service.process(message)
            }

            it("[TASK-04] attachAttachments else 분기 커버") {
                val noPermissionSender = User(id = 3L, loginId = "", name = "이름없음2", email = "noname2@example.com")
                every { userRepository.findByEmail("noname2@example.com") } returns Optional.of(noPermissionSender)
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val existingIssue = Issue(id = 56L, title = "기존 이슈", body = "...", project = project, number = 3L, authorId = 999L)
                every { issueRepository.findById(56L) } returns Optional.of(existingIssue)

                val message = baseMessage(recipients = listOf("yona+dlab/hive/issue_post/56@example.com"))
                    .copy(
                        fromAddress = "noname2@example.com",
                        attachments = listOf(InboundAttachment("test.txt", "text/plain", "abc".toByteArray()))
                    )
                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                // attachAttachments가 emptyMap을 리턴하고 예외 없이 종료된다.
            }
        }
    }
})
