package com.github.yonaprojects.yona.domain.comment

import com.github.yonaprojects.yona.AbstractIntegrationTest
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
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.enumeration.EventType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class CommentServiceExtraSpec @Autowired constructor(
    private val commentService: CommentService,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    init {
        describe("CommentService 추가 커버리지 테스트") {
            beforeEach {
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                postingCommentRepository.deleteAll()
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("답글 생성 시 지정한 parentCommentId의 부모 댓글이 다른 자식이 없고 자기 자신만 있을 때") {
                val author = userRepository.save(User(loginId = "exta1", name = "작성자"))
                val replier = userRepository.save(User(loginId = "replier1", name = "답글러"))
                val project = projectRepository.save(Project(name = "p1", owner = "exta1", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(Issue(title = "t1", project = project, authorId = author.id))
                
                val parentComment = commentService.createIssueComment(issue.id!!, "parent", author, null)
                val reply = commentService.createIssueComment(issue.id!!, "reply", replier, parentComment.id)
                
                val event = notificationEventRepository.findAll().maxByOrNull { it.id!! }
                event?.oldValue shouldNotBe null
                event?.oldValue shouldContain "parent"
            }
            
            it("게시글에 두번째 최상위 댓글 달기") {
                val author = userRepository.save(User(loginId = "exta2", name = "작성자"))
                val author2 = userRepository.save(User(loginId = "exta22", name = "작성자2"))
                val project = projectRepository.save(Project(name = "p2", owner = "exta2", projectScope = ProjectScope.PUBLIC))
                val posting = postingRepository.save(Posting(title = "t2", project = project, number = 1L, authorId = author.id))
                
                commentService.createPostingComment(posting.id!!, "first", author, null)
                commentService.createPostingComment(posting.id!!, "second", author2, null)
                
                val event = notificationEventRepository.findAll().maxByOrNull { it.id!! }
                event?.oldValue shouldNotBe null
                event?.oldValue shouldContain "first"
            }
            
            it("게시글 답글 생성 시 지정한 parentCommentId의 부모 댓글이 다른 자식이 없고 자기 자신만 있을 때") {
                val author = userRepository.save(User(loginId = "exta3", name = "작성자"))
                val replier = userRepository.save(User(loginId = "replier3", name = "답글러"))
                val project = projectRepository.save(Project(name = "p3", owner = "exta3", projectScope = ProjectScope.PUBLIC))
                val posting = postingRepository.save(Posting(title = "t3", project = project, number = 1L, authorId = author.id))
                
                val parentComment = commentService.createPostingComment(posting.id!!, "parent", author, null)
                val reply = commentService.createPostingComment(posting.id!!, "reply", replier, parentComment.id)
                
                val event = notificationEventRepository.findAll().maxByOrNull { it.id!! }
                event?.oldValue shouldNotBe null
                event?.oldValue shouldContain "parent"
            }
            
            it("게시글 답글 업데이트") {
                val author = userRepository.save(User(loginId = "exta4", name = "작성자"))
                val project = projectRepository.save(Project(name = "p4", owner = "exta4"))
                val posting = postingRepository.save(Posting(title = "t4", project = project, number = 1L))
                
                val c = commentService.createPostingComment(posting.id!!, "old", author, null)
                val updated = commentService.updatePostingComment(c.id!!, "new", author)
                updated.contents shouldBe "new"
            }
            
            it("게시글 수정 권한이 없을 경우") {
                val author = userRepository.save(User(loginId = "exta5", name = "작성자"))
                val stranger = userRepository.save(User(loginId = "stranger", name = "외부인"))
                val project = projectRepository.save(Project(name = "p5", owner = "exta5"))
                val posting = postingRepository.save(Posting(title = "t5", project = project, number = 1L))
                
                val c = commentService.createPostingComment(posting.id!!, "old", author, null)
                shouldThrow<IllegalArgumentException> {
                    commentService.updatePostingComment(c.id!!, "new", stranger)
                }
            }
            
            it("게시글 삭제 권한이 없을 경우") {
                val author = userRepository.save(User(loginId = "exta6", name = "작성자"))
                val stranger = userRepository.save(User(loginId = "stranger2", name = "외부인"))
                val project = projectRepository.save(Project(name = "p6", owner = "exta6"))
                val posting = postingRepository.save(Posting(title = "t6", project = project, number = 1L))
                
                val c = commentService.createPostingComment(posting.id!!, "old", author, null)
                shouldThrow<IllegalArgumentException> {
                    commentService.deletePostingComment(c.id!!, stranger)
                }
            }
            
            it("존재하지 않는 이슈에 댓글 작성 시 예외") {
                val author = userRepository.save(User(loginId = "exta7", name = "작성자"))
                shouldThrow<IllegalArgumentException> {
                    commentService.createIssueComment(9999L, "content", author, null)
                }
            }
            
            it("존재하지 않는 게시글에 댓글 작성 시 예외") {
                val author = userRepository.save(User(loginId = "exta8", name = "작성자"))
                shouldThrow<IllegalArgumentException> {
                    commentService.createPostingComment(9999L, "content", author, null)
                }
            }
            
            it("이슈 댓글 삭제 (권한 없는 경우 / 있는 경우)") {
                val author = userRepository.save(User(loginId = "exta9", name = "작성자"))
                val stranger = userRepository.save(User(loginId = "stranger3", name = "외부인"))
                val project = projectRepository.save(Project(name = "p9", owner = "exta9"))
                val issue = issueRepository.save(Issue(title = "t9", project = project, authorId = author.id))
                
                val c = commentService.createIssueComment(issue.id!!, "old", author, null)
                shouldThrow<IllegalArgumentException> {
                    commentService.deleteIssueComment(c.id!!, stranger)
                }
                commentService.deleteIssueComment(c.id!!, author)
                issueCommentRepository.findById(c.id!!).isPresent shouldBe false
            }
            
            it("멘션에 게스트 계정이 포함된 경우 제외되어야 한다") {
                // 2026-08-25: 기존엔 issue 작성자 본인이 직접 댓글을 달아 notificationEvent를 통해
                // 검증하려 했으나, createIssueComment의 receivers.removeIf { it.id == author.id }가
                // 댓글쓴이 본인을 항상 제거하기 때문에(baseWatchers가 issue 작성자=댓글쓴이 자기 자신뿐)
                // 멘션이 게스트뿐이면 receivers가 완전히 비어 notificationEventRecorder.record()가
                // null을 반환해(receivers.isEmpty() 분기) 이벤트 자체가 생성되지 않아 검증이 불가능했다
                // — extractMentionedUsers()를 직접 호출해 게스트 제외 로직만 순수하게 검증한다.
                userRepository.save(User(loginId = "guest1", name = "게스트", isGuest = true))

                val mentioned = commentService.extractMentionedUsers("@guest1 님")

                mentioned.any { it.loginId == "guest1" } shouldBe false
            }
            
            it("멘션된 owner/project에서 프로젝트가 존재하지 않는 경우 무시되어야 한다") {
                val author = userRepository.save(User(loginId = "exta11", name = "작성자"))
                val project = projectRepository.save(Project(name = "p11", owner = "exta11"))
                val issue = issueRepository.save(Issue(title = "t11", project = project, authorId = author.id))
                
                commentService.createIssueComment(issue.id!!, "@nonexist/project 님", author, null)
            }
            
            it("이슈 이전 내용 인용 시 날짜 형식이 올바르게 처리되어야 한다 (formatShortDate)") {
                val author = userRepository.save(User(loginId = "exta12", name = "작성자"))
                val replier = userRepository.save(User(loginId = "replier12", name = "답글러"))
                val project = projectRepository.save(Project(name = "p12", owner = "exta12", projectScope = ProjectScope.PUBLIC))
                // updatedDate가 null인 이슈 강제 세팅
                val issue = Issue(title = "t12", project = project, authorId = author.id, authorLoginId = author.loginId, body = "body")
                issue.updatedDate = null
                val savedIssue = issueRepository.save(issue)
                
                commentService.createIssueComment(savedIssue.id!!, "first", replier, null)
                
                val event = notificationEventRepository.findAll().maxByOrNull { it.id!! }
                event?.oldValue shouldNotBe null
                event?.oldValue shouldContain "--- Original issue from @exta12   ---"
            }
        }
    }
}
