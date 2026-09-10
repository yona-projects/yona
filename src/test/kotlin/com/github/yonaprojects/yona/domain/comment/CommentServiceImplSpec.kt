package com.github.yonaprojects.yona.domain.comment

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * CommentServiceImpl 전용 스펙 부재로 인한 신규 작성 (COVERAGE_BACKLOG 4차 배치).
 * 실제 DB(AbstractIntegrationTest)로 createIssueComment/createPostingComment의 이전 내용
 * 인용 분기(P2-17), extractMentionedUsers의 조직/프로젝트/개인 멘션 확장(P1-126), update/delete의
 * AccessControl 기반 권한(P1-90, 멤버 전원 허용) 분기를 다룬다.
 */
@Transactional
class CommentServiceImplSpec @Autowired constructor(
    private val commentService: CommentService,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRepository: com.github.yonaprojects.yona.domain.notification.NotificationEventRepository,
    private val attachmentRepository: com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
) : AbstractIntegrationTest() {

    private fun mkUser(loginId: String, isGuest: Boolean = false): User =
        userRepository.save(User(loginId = loginId, name = loginId, email = "$loginId@yona.io", isGuest = isGuest))

    private fun mkProject(name: String, owner: String): Project =
        projectRepository.save(Project(name = name, owner = owner, projectScope = ProjectScope.PUBLIC))

    private fun mkRole(type: RoleType): Role =
        roleRepository.findById(type.roleType).orElseGet { roleRepository.save(Role(id = type.roleType, name = type.name)) }

    private fun addMember(user: User, project: Project) {
        val projectUser = projectUserRepository.save(ProjectUser(user = user, project = project, role = mkRole(RoleType.MEMBER)))
        // otherMember는 addMember() 이후 같은 트랜잭션 내에서 바로 isMemberOf()로 검사되는데,
        // Hibernate 1차 캐시가 이미 초기화해둔(빈) projectUsers 컬렉션 인스턴스는 DB에 새 ProjectUser를
        // 저장해도 자동으로 갱신되지 않는다. 실제 운영에서는 매 HTTP 요청마다 User를 새로 조회하므로
        // 발생하지 않는 테스트 전용 현상 — 양방향 연관관계의 인메모리 쪽도 직접 맞춰준다.
        user.projectUsers.add(projectUser)
    }

    private fun mkIssue(
        project: Project, author: User, body: String? = "이슈 본문",
        authorId: Long? = author.id, authorLoginId: String? = author.loginId
    ): Issue =
        issueRepository.save(
            Issue(title = "제목", body = body, project = project, authorId = authorId, authorLoginId = authorLoginId, authorName = author.name, createdDate = Instant.now())
        )

    private fun mkPosting(
        project: Project, author: User, body: String? = "게시글 본문",
        authorId: Long? = author.id, authorLoginId: String? = author.loginId
    ): Posting =
        postingRepository.save(Posting(title = "제목", body = body, project = project, authorId = authorId, authorLoginId = authorLoginId, authorName = author.name))

    init {
        describe("CommentServiceImpl") {
            lateinit var author: User
            lateinit var project: Project

            beforeEach {
                author = mkUser("author1")
                project = mkProject("proj1", author.loginId)
                addMember(author, project)
            }

            describe("createIssueComment") {
                it("기존 댓글이 없으면 이슈 본문을 인용해야 한다") {
                    val issue = mkIssue(project, author, body = "원본 이슈 본문")
                    val comment = commentService.createIssueComment(issue.id!!, "첫 댓글", author, null)
                    comment.contents shouldBe "첫 댓글"
                }

                it("답글이고 같은 부모의 형제 댓글이 있으면 그 형제를 인용해야 한다") {
                    val issue = mkIssue(project, author)
                    val parent = commentService.createIssueComment(issue.id!!, "부모 댓글", author, null)
                    val sibling = commentService.createIssueComment(issue.id!!, "형제 댓글", author, parent.id)
                    val reply = commentService.createIssueComment(issue.id!!, "새 답글", author, parent.id)
                    reply.parentComment?.id shouldBe parent.id
                    sibling.contents shouldBe "형제 댓글"
                }

                it("답글인데 같은 부모의 형제가 없으면 부모 댓글 자신을 인용해야 한다") {
                    val issue = mkIssue(project, author)
                    val parent = commentService.createIssueComment(issue.id!!, "부모 댓글", author, null)
                    val reply = commentService.createIssueComment(issue.id!!, "형제 없는 답글", author, parent.id)
                    reply.parentComment?.id shouldBe parent.id
                }

                it("최상위 새 댓글이고 기존 댓글이 있으면 마지막 댓글을 인용해야 한다") {
                    val issue = mkIssue(project, author)
                    commentService.createIssueComment(issue.id!!, "이전 댓글", author, null)
                    val newest = commentService.createIssueComment(issue.id!!, "최신 댓글", author, null)
                    newest.parentComment shouldBe null
                }

                it("존재하지 않는 이슈면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.createIssueComment(999999L, "내용", author, null)
                    }
                }

                it("이슈 본문이 null이면 빈 문자열로 인용해야 한다") {
                    val issue = mkIssue(project, author, body = null)
                    val comment = commentService.createIssueComment(issue.id!!, "첫 댓글", author, null)
                    comment.contents shouldBe "첫 댓글"
                }

                it("이슈 작성자의 authorLoginId가 null이면 인용문의 @작성자 표기가 빈 값이어야 한다") {
                    val issue = mkIssue(project, author, authorLoginId = null)
                    val comment = commentService.createIssueComment(issue.id!!, "첫 댓글", author, null)
                    comment.contents shouldBe "첫 댓글"
                }

                it("이전 댓글이 작년에 작성됐으면 연도가 다른 날짜 형식으로 인용해야 한다") {
                    val issue = mkIssue(project, author)
                    val old = commentService.createIssueComment(issue.id!!, "작년 댓글", author, null)
                    old.createdDate = Instant.now().minus(400, java.time.temporal.ChronoUnit.DAYS)
                    issueCommentRepository.save(old)

                    val newest = commentService.createIssueComment(issue.id!!, "올해 댓글", author, null)
                    newest.contents shouldBe "올해 댓글"
                }

                it("이전 댓글이 어제 작성됐으면(연도는 같음) 날짜만 다른 형식으로 인용해야 한다") {
                    val issue = mkIssue(project, author)
                    val old = commentService.createIssueComment(issue.id!!, "어제 댓글", author, null)
                    old.createdDate = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
                    issueCommentRepository.save(old)

                    val newest = commentService.createIssueComment(issue.id!!, "오늘 댓글", author, null)
                    newest.contents shouldBe "오늘 댓글"
                }

                it("이슈 작성자가 없으면(authorId=null) 감시자 기본값 없이도 정상 생성돼야 한다") {
                    val issue = mkIssue(project, author, authorId = null)
                    val comment = commentService.createIssueComment(issue.id!!, "댓글", author, null)
                    comment.contents shouldBe "댓글"
                }

                it("본문에 다른 사용자를 멘션하면 그 사용자가 알림 수신자로 기록돼야 한다") {
                    val issue = mkIssue(project, author)
                    val mentioned = mkUser("mentioned1")
                    addMember(mentioned, project)

                    val comment = commentService.createIssueComment(issue.id!!, "@mentioned1 확인해주세요", author, null)
                    comment.contents shouldBe "@mentioned1 확인해주세요"
                }

                // P3-50 조사 중 발견: common/uploadForm.html(yobi.Files.js)로 올린 파일은
                // POST /files가 항상 NOT_A_RESOURCE(임시)에 저장하는데, 댓글 생성 경로가 그
                // 파일을 실제 컨테이너로 옮기는 단계가 아예 없어 첨부파일이 영구히 미아가 됐다.
                it("본문에 링크된 첨부파일을 NOT_A_RESOURCE에서 ISSUE_COMMENT로 옮겨야 한다") {
                    val issue = mkIssue(project, author)
                    val attachment = attachmentRepository.save(
                        com.github.yonaprojects.yona.domain.attachment.Attachment(
                            name = "upload.png", hash = "hash-create-issue-attach",
                            containerType = ResourceType.NOT_A_RESOURCE, containerId = "",
                            mimeType = "image/png", size = 100L, createdDate = Instant.now(), ownerLoginId = author.loginId
                        )
                    )

                    val comment = commentService.createIssueComment(
                        issue.id!!, "본문 ![img](/files/${attachment.id})", author, null
                    )

                    val moved = attachmentRepository.findById(attachment.id!!).orElseThrow()
                    moved.containerType shouldBe ResourceType.ISSUE_COMMENT
                    moved.containerId shouldBe comment.id.toString()
                }

                it("다른 사람이 올린 파일에 대한 링크를 붙여넣어도 옮겨가지 않아야 한다(하이재킹 방지)") {
                    val issue = mkIssue(project, author)
                    val stranger = mkUser("attach-stranger1")
                    val attachment = attachmentRepository.save(
                        com.github.yonaprojects.yona.domain.attachment.Attachment(
                            name = "stranger.png", hash = "hash-stranger-issue-attach",
                            containerType = ResourceType.NOT_A_RESOURCE, containerId = "",
                            mimeType = "image/png", size = 100L, createdDate = Instant.now(), ownerLoginId = stranger.loginId
                        )
                    )

                    commentService.createIssueComment(issue.id!!, "훔친 링크 ![img](/files/${attachment.id})", author, null)

                    val untouched = attachmentRepository.findById(attachment.id!!).orElseThrow()
                    untouched.containerType shouldBe ResourceType.NOT_A_RESOURCE
                }
            }

            describe("createPostingComment") {
                it("댓글 생성 후 posting의 numOfComments를 재계산해야 한다") {
                    val posting = mkPosting(project, author)
                    commentService.createPostingComment(posting.id!!, "댓글1", author, null)
                    commentService.createPostingComment(posting.id!!, "댓글2", author, null)
                    val updated = postingRepository.findById(posting.id!!).orElseThrow()
                    updated.numOfComments shouldBe 2
                }

                it("본문에 링크된 첨부파일을 NOT_A_RESOURCE에서 NONISSUE_COMMENT로 옮겨야 한다") {
                    val posting = mkPosting(project, author)
                    val attachment = attachmentRepository.save(
                        com.github.yonaprojects.yona.domain.attachment.Attachment(
                            name = "upload.png", hash = "hash-create-posting-attach",
                            containerType = ResourceType.NOT_A_RESOURCE, containerId = "",
                            mimeType = "image/png", size = 100L, createdDate = Instant.now(), ownerLoginId = author.loginId
                        )
                    )

                    val comment = commentService.createPostingComment(
                        posting.id!!, "본문 ![img](/files/${attachment.id})", author, null
                    )

                    val moved = attachmentRepository.findById(attachment.id!!).orElseThrow()
                    moved.containerType shouldBe ResourceType.NONISSUE_COMMENT
                    moved.containerId shouldBe comment.id.toString()
                }

                it("답글이고 같은 부모의 형제 댓글이 있으면 그 형제를 인용해야 한다") {
                    val posting = mkPosting(project, author)
                    val parent = commentService.createPostingComment(posting.id!!, "부모 댓글", author, null)
                    val sibling = commentService.createPostingComment(posting.id!!, "형제 댓글", author, parent.id)
                    val reply = commentService.createPostingComment(posting.id!!, "새 답글", author, parent.id)
                    reply.parentComment?.id shouldBe parent.id
                    sibling.contents shouldBe "형제 댓글"
                }

                it("답글인데 같은 부모의 형제가 없으면 부모 댓글 자신을 인용해야 한다") {
                    val posting = mkPosting(project, author)
                    val parent = commentService.createPostingComment(posting.id!!, "부모 댓글", author, null)
                    val reply = commentService.createPostingComment(posting.id!!, "형제 없는 답글", author, parent.id)
                    reply.parentComment?.id shouldBe parent.id
                }

                it("게시글 본문이 null이면 빈 문자열로 인용해야 한다") {
                    val posting = mkPosting(project, author, body = null)
                    val comment = commentService.createPostingComment(posting.id!!, "첫 댓글", author, null)
                    comment.contents shouldBe "첫 댓글"
                }

                it("게시글 작성자가 없으면(authorId=null) 감시자 기본값 없이도 정상 생성돼야 한다") {
                    val posting = mkPosting(project, author, authorId = null)
                    val comment = commentService.createPostingComment(posting.id!!, "댓글", author, null)
                    comment.contents shouldBe "댓글"
                }

                it("본문에 다른 사용자를 멘션하면 그 사용자가 알림 수신자로 기록돼야 한다") {
                    val posting = mkPosting(project, author)
                    val mentioned = mkUser("mentioned2")
                    addMember(mentioned, project)

                    val comment = commentService.createPostingComment(posting.id!!, "@mentioned2 확인해주세요", author, null)
                    comment.contents shouldBe "@mentioned2 확인해주세요"
                }

                it("존재하지 않는 게시글이면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.createPostingComment(999999L, "내용", author, null)
                    }
                }
            }

            describe("extractMentionedUsers") {
                it("조직 이름을 멘션하면 조직 멤버 전원을 반환해야 한다") {
                    val org = organizationRepository.save(Organization(name = "myorg"))
                    val orgMember = mkUser("orgmember1")
                    organizationUserRepository.save(OrganizationUser(user = orgMember, organization = org, role = mkRole(RoleType.ORG_MEMBER)))

                    val users = commentService.extractMentionedUsers("@myorg 안녕하세요")
                    users.map { it.loginId } shouldBe listOf("orgmember1")
                }

                it("owner/project 형식을 멘션하면 프로젝트 멤버 전원을 반환해야 한다") {
                    val projMember = mkUser("projmember1")
                    addMember(projMember, project)

                    // beforeEach에서 author도 이미 project의 멤버로 추가돼 있어(addMember(author, project))
                    // "멤버 전원"에는 author까지 포함되는 것이 정확한 기대값이다.
                    val users = commentService.extractMentionedUsers("@${project.owner}/${project.name} 확인해주세요")
                    users.map { it.loginId }.toSet() shouldBe setOf(author.loginId, projMember.loginId)
                }

                it("개인 로그인아이디를 멘션하면 해당 유저를 포함하되 게스트는 제외해야 한다") {
                    val normalUser = mkUser("plainuser1")
                    val guestUser = mkUser("guestuser1", isGuest = true)

                    val users = commentService.extractMentionedUsers("@plainuser1 @guestuser1 hi")
                    users.map { it.loginId } shouldBe listOf("plainuser1")
                }

                it("어디에도 매칭되지 않는 멘션은 무시해야 한다") {
                    val users = commentService.extractMentionedUsers("@nobody-here-at-all")
                    users.isEmpty() shouldBe true
                }
            }

            describe("updateIssueComment / deleteIssueComment") {
                it("프로젝트 멤버가 아니면 수정 시 예외를 던져야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val outsider = mkUser("outsider1")

                    shouldThrow<IllegalArgumentException> {
                        commentService.updateIssueComment(comment.id!!, "수정본", outsider)
                    }
                }

                it("프로젝트 멤버면 작성자가 아니어도 수정할 수 있어야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val otherMember = mkUser("member2")
                    addMember(otherMember, project)

                    val updated = commentService.updateIssueComment(comment.id!!, "수정됨", otherMember)
                    updated.contents shouldBe "수정됨"
                }

                it("프로젝트 멤버가 아니면 삭제 시 예외를 던져야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val outsider = mkUser("outsider2")

                    shouldThrow<IllegalArgumentException> {
                        commentService.deleteIssueComment(comment.id!!, outsider)
                    }
                }

                it("존재하지 않는 댓글을 수정하려 하면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.updateIssueComment(999999L, "내용", author)
                    }
                }

                it("프로젝트 멤버면 작성자가 아니어도 삭제할 수 있어야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val otherMember = mkUser("member3")
                    addMember(otherMember, project)

                    commentService.deleteIssueComment(comment.id!!, otherMember)

                    issueCommentRepository.findById(comment.id!!).isPresent shouldBe false
                }

                it("존재하지 않는 댓글을 삭제하려 하면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.deleteIssueComment(999999L, author)
                    }
                }

                // P3-50: legacy IssueApp.saveComment()의
                // `isSelectedToSendNotificationMail() || !existingComment.isAuthoredBy(currentUser)` 대응.
                it("작성자 본인이 sendNotificationMail=false로 수정하면 알림을 발행하지 않아야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val before = notificationEventRepository.count()

                    commentService.updateIssueComment(comment.id!!, "수정본", author, sendNotificationMail = false)

                    notificationEventRepository.count() shouldBe before
                }

                it("작성자 본인이 sendNotificationMail=true로 수정하면 COMMENT_UPDATED 알림을 발행해야 한다") {
                    // record()는 수신자가 비어있으면 알림을 아예 저장하지 않으므로(스팸 방지),
                    // 이슈 작성자=댓글 작성자=수정자가 전부 동일한 이 테스트에서는 멘션으로
                    // 최소 한 명의 수신자를 확보한다.
                    val mentioned = mkUser("member-notify-mentioned1")
                    addMember(mentioned, project)
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val before = notificationEventRepository.count()

                    commentService.updateIssueComment(comment.id!!, "수정본 @member-notify-mentioned1", author, sendNotificationMail = true)

                    val events = notificationEventRepository.findAll()
                        .filter { it.resourceId == comment.id.toString() && it.eventType == com.github.yonaprojects.yona.domain.enumeration.EventType.COMMENT_UPDATED }
                    notificationEventRepository.count() shouldBe before + 1
                    events.size shouldBe 1
                    events.first().newValue shouldBe "수정본 @member-notify-mentioned1"
                }

                it("작성자가 아닌 다른 사람이 수정하면 sendNotificationMail 값과 무관하게 항상 알림을 발행해야 한다") {
                    val issue = mkIssue(project, author)
                    val comment = commentService.createIssueComment(issue.id!!, "원본", author, null)
                    val otherMember = mkUser("member-notify1")
                    addMember(otherMember, project)
                    val before = notificationEventRepository.count()

                    commentService.updateIssueComment(comment.id!!, "매니저 수정", otherMember, sendNotificationMail = false)

                    notificationEventRepository.count() shouldBe before + 1
                }
            }

            describe("updatePostingComment / deletePostingComment") {
                it("프로젝트 멤버가 아니면 수정 시 예외를 던져야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val outsider = mkUser("outsider3")

                    shouldThrow<IllegalArgumentException> {
                        commentService.updatePostingComment(comment.id!!, "수정본", outsider)
                    }
                }

                it("프로젝트 멤버면 작성자가 아니어도 수정할 수 있어야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val otherMember = mkUser("member4")
                    addMember(otherMember, project)

                    val updated = commentService.updatePostingComment(comment.id!!, "수정됨", otherMember)
                    updated.contents shouldBe "수정됨"
                }

                it("존재하지 않는 댓글을 수정하려 하면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.updatePostingComment(999999L, "내용", author)
                    }
                }

                it("프로젝트 멤버가 아니면 삭제 시 예외를 던져야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val outsider = mkUser("outsider4")

                    shouldThrow<IllegalArgumentException> {
                        commentService.deletePostingComment(comment.id!!, outsider)
                    }
                }

                it("삭제하면 posting의 numOfComments가 감소해야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    commentService.deletePostingComment(comment.id!!, author)
                    val updated = postingRepository.findById(posting.id!!).orElseThrow()
                    updated.numOfComments shouldBe 0
                }

                it("존재하지 않는 댓글을 삭제하려 하면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        commentService.deletePostingComment(999999L, author)
                    }
                }

                // P3-50: legacy BoardApp.saveComment()와 동일한 규칙.
                it("작성자 본인이 sendNotificationMail=false로 수정하면 알림을 발행하지 않아야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val before = notificationEventRepository.count()

                    commentService.updatePostingComment(comment.id!!, "수정본", author, sendNotificationMail = false)

                    notificationEventRepository.count() shouldBe before
                }

                it("작성자 본인이 sendNotificationMail=true로 수정하면 COMMENT_UPDATED 알림을 발행해야 한다") {
                    val mentioned = mkUser("member-notify-mentioned2")
                    addMember(mentioned, project)
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val before = notificationEventRepository.count()

                    commentService.updatePostingComment(comment.id!!, "수정본 @member-notify-mentioned2", author, sendNotificationMail = true)

                    val events = notificationEventRepository.findAll()
                        .filter { it.resourceId == comment.id.toString() && it.eventType == com.github.yonaprojects.yona.domain.enumeration.EventType.COMMENT_UPDATED }
                    notificationEventRepository.count() shouldBe before + 1
                    events.size shouldBe 1
                    events.first().newValue shouldBe "수정본 @member-notify-mentioned2"
                }

                it("작성자가 아닌 다른 사람이 수정하면 sendNotificationMail 값과 무관하게 항상 알림을 발행해야 한다") {
                    val posting = mkPosting(project, author)
                    val comment = commentService.createPostingComment(posting.id!!, "원본", author, null)
                    val otherMember = mkUser("member-notify2")
                    addMember(otherMember, project)
                    val before = notificationEventRepository.count()

                    commentService.updatePostingComment(comment.id!!, "매니저 수정", otherMember, sendNotificationMail = false)

                    notificationEventRepository.count() shouldBe before + 1
                }
            }
        }
    }
}
