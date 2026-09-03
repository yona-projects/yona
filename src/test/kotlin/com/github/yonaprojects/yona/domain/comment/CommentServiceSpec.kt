package com.github.yonaprojects.yona.domain.comment

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
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
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class CommentServiceSpec @Autowired constructor(
    private val commentService: CommentService,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository
) : AbstractIntegrationTest() {

    init {
        describe("CommentService 댓글 및 멘션 연동 테스트") {
            beforeEach {
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                postingCommentRepository.deleteAll()
                postingRepository.deleteAll()
                organizationUserRepository.deleteAll()
                organizationRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            it("멘션이 포함된 댓글을 작성하면 댓글이 저장되고 멘션된 사용자가 알림 수신자에 포함되어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "usera", name = "작성자", email = "usera@yona.io")
                )
                val targetUser = userRepository.save(
                    User(loginId = "userb", name = "수신자", email = "userb@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "comment-project", owner = "tester")
                )

                val issue = Issue(
                    title = "멘션 테스트용 이슈",
                    body = "이슈 본문",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val commentContents = "이 문제를 @userb 님께서 검토해 주시겠습니까?"

                // When
                val savedComment = commentService.createIssueComment(
                    issueId = savedIssue.id!!,
                    contents = commentContents,
                    author = author
                )

                // Then
                savedComment.id shouldNotBe null
                savedComment.contents shouldBe commentContents
                savedComment.issue.id shouldBe savedIssue.id

                // 알림 이벤트 및 수신자 멘션 검증
                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.NEW_COMMENT
                event.resourceType shouldBe ResourceType.ISSUE_COMMENT
                event.newValue shouldBe commentContents

                // userb가 수신자로 정상 등록되었는지 확인
                event.receivers.size shouldBe 1
                event.receivers.first().loginId shouldBe "userb"
            }

            // yona NotificationEvent.getMentionedUsers()의 findOrganizationMembers() 대응 (P1-126).
            it("조직 이름을 멘션하면 조직 멤버 전원이 알림 수신자에 포함되어야 한다") {
                val author = userRepository.save(User(loginId = "usera2", name = "작성자2", email = "usera2@yona.io"))
                val orgMember1 = userRepository.save(User(loginId = "orgmember1", name = "조직원1", email = "orgmember1@yona.io"))
                val orgMember2 = userRepository.save(User(loginId = "orgmember2", name = "조직원2", email = "orgmember2@yona.io"))
                val role = roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER"))
                val org = organizationRepository.save(Organization(name = "team-org"))
                organizationUserRepository.save(OrganizationUser(user = orgMember1, organization = org, role = role))
                organizationUserRepository.save(OrganizationUser(user = orgMember2, organization = org, role = role))

                val project = projectRepository.save(Project(name = "org-mention-project", owner = "usera2"))
                val issue = issueRepository.save(
                    Issue(
                        title = "조직 멘션 테스트", body = "본문", project = project,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now(), state = State.OPEN
                    )
                )

                val savedComment = commentService.createIssueComment(
                    issueId = issue.id!!,
                    contents = "@team-org 검토 부탁드립니다.",
                    author = author
                )

                savedComment.id shouldNotBe null
                val event = notificationEventRepository.findAll().first()
                val receiverLoginIds = event.receivers.map { it.loginId }.toSet()
                receiverLoginIds shouldBe setOf("orgmember1", "orgmember2")
            }

            // yona NotificationEvent.getMentionedUsers()의 findProjectMembers() 대응 (P1-126).
            it("owner/project 형식으로 멘션하면 해당 프로젝트 멤버 전원이 알림 수신자에 포함되어야 한다") {
                val author = userRepository.save(User(loginId = "usera3", name = "작성자3", email = "usera3@yona.io"))
                val projMember = userRepository.save(User(loginId = "projmember", name = "프로젝트원", email = "projmember@yona.io"))
                val role = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))

                val mentionedProject = projectRepository.save(Project(name = "target-project", owner = "someowner"))
                projectUserRepository.save(ProjectUser(project = mentionedProject, user = projMember, role = role))

                val hostProject = projectRepository.save(Project(name = "host-project", owner = "usera3"))
                val issue = issueRepository.save(
                    Issue(
                        title = "프로젝트 멘션 테스트", body = "본문", project = hostProject,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now(), state = State.OPEN
                    )
                )

                val savedComment = commentService.createIssueComment(
                    issueId = issue.id!!,
                    contents = "@someowner/target-project 확인해주세요.",
                    author = author
                )

                savedComment.id shouldNotBe null
                val event = notificationEventRepository.findAll().first()
                val receiverLoginIds = event.receivers.map { it.loginId }.toSet()
                receiverLoginIds shouldBe setOf("projmember")
            }

            it("게시글 댓글을 작성/삭제하면 posting.numOfComments가 실제 댓글 수와 일치해야 한다 (P1-19)") {
                val author = userRepository.save(User(loginId = "boardwriter", name = "글쓴이", email = "boardwriter@yona.io"))
                val commenter = userRepository.save(User(loginId = "boardcommenter", name = "댓글러", email = "boardcommenter@yona.io"))
                val project = projectRepository.save(Project(name = "comment-count-project", owner = "boardwriter"))
                val posting = postingRepository.save(
                    Posting(title = "댓글수 테스트", body = "본문", project = project, number = 1L)
                )
                posting.numOfComments shouldBe 0

                val comment1 = commentService.createPostingComment(posting.id!!, "댓글1", commenter, null)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 1

                commentService.createPostingComment(posting.id!!, "댓글2", commenter, null)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 2

                commentService.deletePostingComment(comment1.id!!, commenter)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 1
            }

            // yona IssueApp.java:1020-1057 AddPreviousContent()+getPrevious() 대응 (P2-17) — 새 댓글 [GL-controllers_IssueApp-050]
            // 알림의 oldValue("인용 이전 내용")가 항상 null로 방치되던 것을 채운다.
            it("이슈의 첫 댓글이면 알림 oldValue에 원본 이슈 본문이 인용되어야 한다 (P2-17)") {
                val author = userRepository.save(User(loginId = "quoteauthor", name = "작성자", email = "quoteauthor@yona.io"))
                val commenter = userRepository.save(User(loginId = "quotecommenter", name = "댓글러", email = "quotecommenter@yona.io"))
                val project = projectRepository.save(Project(name = "quote-project", owner = "quoteauthor", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(
                        title = "인용 테스트 이슈", body = "이슈 원본 본문", project = project,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name
                    )
                )

                commentService.createIssueComment(issue.id!!, "첫 댓글", commenter, null)

                val event = notificationEventRepository.findAll().first { it.eventType == EventType.NEW_COMMENT }
                event.oldValue shouldNotBe null
                event.oldValue!!.shouldContain("Original issue")
                event.oldValue!!.shouldContain("이슈 원본 본문")
            }

            it("이슈에 두 번째 최상위 댓글을 달면 알림 oldValue에 첫 번째 댓글이 인용되어야 한다 (P2-17)") {
                val author = userRepository.save(User(loginId = "quoteauthor2", name = "작성자", email = "quoteauthor2@yona.io"))
                val commenter = userRepository.save(User(loginId = "quotecommenter2", name = "댓글러", email = "quotecommenter2@yona.io"))
                // 두 댓글을 다른 사람이 달아야 한다 — 같은 발신자의 30초 이내 연속 NEW_COMMENT는
                // NotificationEventRecorder의 초안 병합(P1-27)에 걸려 이 테스트가 검증하려는
                // 두 번째 댓글의 oldValue 자체가 첫 번째 이벤트 것으로 덮여쓰이게 된다.
                val secondCommenter = userRepository.save(User(loginId = "quotecommenter2b", name = "댓글러2", email = "quotecommenter2b@yona.io"))
                val project = projectRepository.save(Project(name = "quote-project2", owner = "quoteauthor2", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(
                        title = "인용 테스트 이슈2", body = "본문", project = project,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name
                    )
                )

                commentService.createIssueComment(issue.id!!, "첫 번째 댓글 내용", commenter, null)
                commentService.createIssueComment(issue.id!!, "두 번째 댓글", secondCommenter, null)

                val event = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.NEW_COMMENT }
                    .maxBy { it.id!! }
                event.oldValue!!.shouldContain("Previous comment")
                event.oldValue!!.shouldContain("첫 번째 댓글 내용")
            }

            it("답글이고 형제 답글이 없으면 알림 oldValue에 부모 댓글이 인용되어야 한다 (P2-17)") {
                val author = userRepository.save(User(loginId = "quoteauthor3", name = "작성자", email = "quoteauthor3@yona.io"))
                val commenter = userRepository.save(User(loginId = "quotecommenter3", name = "댓글러", email = "quotecommenter3@yona.io"))
                // 부모 댓글과 답글을 다른 사람이 달아야 한다 — 같은 발신자의 30초 이내 연속 NEW_COMMENT는
                // NotificationEventRecorder의 초안 병합(P1-27)에 걸려 답글의 oldValue가 부모 댓글 생성
                // 이벤트의 것으로 덮여쓰이게 된다.
                val replier = userRepository.save(User(loginId = "quotereplier3", name = "답글러", email = "quotereplier3@yona.io"))
                val project = projectRepository.save(Project(name = "quote-project3", owner = "quoteauthor3", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(
                        title = "인용 테스트 이슈3", body = "본문", project = project,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name
                    )
                )
                val parent = commentService.createIssueComment(issue.id!!, "부모 댓글 내용", commenter, null)

                commentService.createIssueComment(issue.id!!, "답글", replier, parent.id)

                val event = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.NEW_COMMENT }
                    .maxBy { it.id!! }
                event.oldValue!!.shouldContain("Previous comment")
                event.oldValue!!.shouldContain("부모 댓글 내용")
            }

            it("게시글의 첫 댓글이면 알림 oldValue에 원본 게시글 본문이 인용되어야 한다 (P2-17)") {
                val author = userRepository.save(User(loginId = "quoteauthor4", name = "작성자", email = "quoteauthor4@yona.io"))
                val commenter = userRepository.save(User(loginId = "quotecommenter4", name = "댓글러", email = "quotecommenter4@yona.io"))
                val project = projectRepository.save(Project(name = "quote-project4", owner = "quoteauthor4", projectScope = ProjectScope.PUBLIC))
                val posting = postingRepository.save(
                    Posting(
                        title = "인용 테스트 게시글", body = "게시글 원본 본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name
                    )
                )

                commentService.createPostingComment(posting.id!!, "첫 댓글", commenter, null)

                val event = notificationEventRepository.findAll().first { it.eventType == EventType.NEW_COMMENT }
                event.oldValue!!.shouldContain("Original posting")
                event.oldValue!!.shouldContain("게시글 원본 본문")
            }

            it("작성자도 매니저도 아닌 일반 프로젝트 멤버가 이슈 댓글을 수정할 수 있어야 한다 (P1-90, yona AccessControl.java:280-282 UPDATE는 isMemberOf만 있으면 허용)") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "issuewriter", name = "작성자", email = "issuewriter@yona.io"))
                val otherMember = userRepository.save(User(loginId = "othermember", name = "다른멤버", email = "othermember@yona.io"))
                val project = projectRepository.save(Project(name = "member-update-project", owner = "issuewriter"))
                // AccessControl.isMemberOf()는 DB 조회가 아니라 User.projectUsers 엔티티 컬렉션을 직접 읽는다.
                // projectUserRepository.save()만으로는 이미 메모리에 들고 있는 otherMember 인스턴스의
                // projectUsers가 갱신되지 않으므로(재조회 전까지 지연 컬렉션이 초기화되지 않음) 양쪽에 반영한다.
                val projectUser = projectUserRepository.save(ProjectUser(project = project, user = otherMember, role = roleMember))
                otherMember.projectUsers.add(projectUser)

                val issue = issueRepository.save(
                    Issue(title = "이슈", body = "본문", project = project, authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
                )
                val comment = issueCommentRepository.save(
                    IssueComment(contents = "원본 댓글", issue = issue, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                val updated = commentService.updateIssueComment(comment.id!!, "다른 멤버가 수정한 댓글", otherMember)

                updated.contents shouldBe "다른 멤버가 수정한 댓글"
            }

            it("프로젝트 멤버가 아닌 사용자는 이슈 댓글을 수정할 수 없어야 한다") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "issuewriter2", name = "작성자", email = "issuewriter2@yona.io"))
                val stranger = userRepository.save(User(loginId = "stranger", name = "외부인", email = "stranger@yona.io"))
                val project = projectRepository.save(Project(name = "non-member-project", owner = "issuewriter2"))

                val issue = issueRepository.save(
                    Issue(title = "이슈", body = "본문", project = project, authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
                )
                val comment = issueCommentRepository.save(
                    IssueComment(contents = "원본 댓글", issue = issue, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                shouldThrow<IllegalArgumentException> {
                    commentService.updateIssueComment(comment.id!!, "외부인이 시도한 수정", stranger)
                }
            }

            it("작성자도 매니저도 아닌 일반 프로젝트 멤버가 게시글 댓글을 삭제할 수 있어야 한다 (P1-90)") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "postwriter", name = "글쓴이", email = "postwriter@yona.io"))
                val otherMember = userRepository.save(User(loginId = "othermember2", name = "다른멤버2", email = "othermember2@yona.io"))
                val project = projectRepository.save(Project(name = "member-delete-project", owner = "postwriter"))
                val projectUser = projectUserRepository.save(ProjectUser(project = project, user = otherMember, role = roleMember))
                otherMember.projectUsers.add(projectUser)

                val posting = postingRepository.save(Posting(title = "글", body = "본문", project = project, number = 1L))
                val comment = postingCommentRepository.save(
                    PostingComment(contents = "댓글", posting = posting, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                commentService.deletePostingComment(comment.id!!, otherMember)

                postingCommentRepository.findById(comment.id!!).isPresent shouldBe false
            }
        }
    }
}
