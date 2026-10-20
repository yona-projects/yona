package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.issue.IssueSharer
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// v1.6 NotificationEvent.getMandatoryReceivers()/getReceivers()는 글 작성자·담당자·공유자·답글 대상 같은 "암묵 수신자"를
// 감시(Watch) 여부와 무관하게 수신자로 넣었다. next는 호출부마다 baseWatchers를 좁게(작성자만/발신자만/빈 집합) 넘겨
// 감시하지 않은 담당자·공유자·부모 댓글 작성자가 알림을 받지 못했다.
@Transactional
class NotificationImplicitReceiversIntegrationSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val commentService: CommentService,
    private val postingService: PostingService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository
) : AbstractIntegrationTest() {

    private fun user(login: String) = userRepository.save(User(loginId = login, name = login, email = "$login@yona.io"))

    private fun receiversOf(type: EventType): List<String> =
        notificationEventRepository.findAll().filter { it.eventType == type }
            .flatMap { event -> event.receivers.map { it.loginId } }

    init {
        describe("암묵 수신자") {
            lateinit var author: User
            lateinit var assignee: User
            lateinit var sharer: User
            lateinit var actor: User
            lateinit var outsider: User
            lateinit var project: Project
            lateinit var issue: Issue

            beforeEach {
                notificationEventRepository.deleteAll()
                author = user("imp-author")
                assignee = user("imp-assignee")
                sharer = user("imp-sharer")
                actor = user("imp-actor")
                outsider = user("imp-outsider")
                project = projectRepository.save(Project(name = "imp-proj", owner = author.loginId, projectScope = ProjectScope.PUBLIC))
                issue = issueRepository.save(
                    Issue(
                        title = "이슈", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now(), assignee = Assignee(user = assignee, project = project)
                    )
                )
                issue.sharers.add(IssueSharer(loginId = sharer.loginId, user = sharer, issue = issue))
                issue = issueRepository.save(issue)
            }

            it("이슈 상태가 바뀌면 작성자·담당자·공유자가 (감시하지 않아도) 수신자이고 변경한 사람은 제외된다") {
                issueService.changeState(issue.id!!, State.CLOSED, actor.loginId)

                val receivers = receiversOf(EventType.ISSUE_STATE_CHANGED)
                receivers shouldContain "imp-author"
                receivers shouldContain "imp-assignee"
                receivers shouldContain "imp-sharer"
                receivers shouldNotContain "imp-actor"
                receivers shouldNotContain "imp-outsider"
            }

            it("담당자가 바뀌면 새 담당자와 이전 담당자 모두 수신자이다") {
                val newAssignee = user("imp-assignee2")
                issueService.changeAssignee(issue.id!!, newAssignee, actor.loginId)

                val receivers = receiversOf(EventType.ISSUE_ASSIGNEE_CHANGED)
                receivers shouldContain "imp-assignee2"
                receivers shouldContain "imp-assignee"
                receivers shouldContain "imp-author"
                receivers shouldNotContain "imp-actor"
            }

            it("담당자를 지정해 새 이슈를 만들면 담당자가 수신자이다") {
                val created = issueService.createIssue(
                    Issue(title = "새 이슈", body = "내용", project = project), author = author, assigneeUser = assignee,
                    explicitNumber = 99L
                )

                val receivers = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.NEW_ISSUE && it.resourceId == created.id.toString() }
                    .flatMap { e -> e.receivers.map { it.loginId } }
                receivers shouldContain "imp-assignee"
                receivers shouldNotContain "imp-author"
            }

            it("이슈에 댓글이 달리면 작성자와 담당자가 수신자이고 댓글 작성자는 제외된다") {
                commentService.createIssueComment(issue.id!!, "댓글입니다", actor)

                val receivers = receiversOf(EventType.NEW_COMMENT)
                receivers shouldContain "imp-author"
                receivers shouldContain "imp-assignee"
                receivers shouldNotContain "imp-actor"
            }

            it("답글이 달리면 부모 댓글 작성자가 수신자이다") {
                val commenter = user("imp-commenter")
                val parent = commentService.createIssueComment(issue.id!!, "부모 댓글", commenter)

                val reply = commentService.createIssueComment(issue.id!!, "답글", actor, parent.id)

                val receivers = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.NEW_COMMENT && it.resourceId == reply.id.toString() }
                    .flatMap { e -> e.receivers.map { it.loginId } }
                receivers shouldContain "imp-commenter"
            }

            // v1.6 getMandatoryReceivers(): UserProjectNotification에서 해당 이벤트를 허용(allowed=true)한 사용자를 수신자에 더하고,
            // 허용하지 않은(allowed=false) 사용자는 작성자·담당자여도 뺀다.
            it("이벤트를 옵트인한 사용자는 감시·관계가 없어도 수신자이다") {
                val optIn = user("imp-optin")
                userProjectNotificationRepository.save(
                    UserProjectNotification(user = optIn, project = project, notificationType = EventType.NEW_COMMENT, allowed = true)
                )

                commentService.createIssueComment(issue.id!!, "댓글", actor)

                receiversOf(EventType.NEW_COMMENT) shouldContain "imp-optin"
            }

            it("이벤트를 거부(allowed=false)한 작성자는 상태 변경 알림을 받지 않는다") {
                userProjectNotificationRepository.save(
                    UserProjectNotification(user = author, project = project, notificationType = EventType.ISSUE_STATE_CHANGED, allowed = false)
                )

                issueService.changeState(issue.id!!, State.CLOSED, actor.loginId)

                val receivers = receiversOf(EventType.ISSUE_STATE_CHANGED)
                receivers shouldNotContain "imp-author"
                receivers shouldContain "imp-assignee"
            }

            // v1.6 getReceiversForIssueBodyChanged(): getNewMentionedUsers(oldBody, newBody) — 새로 멘션된 사용자만 추가한다.
            it("이슈 본문 수정으로 새로 멘션된 사용자는 수신자이고 이미 멘션돼 있던 사용자는 아니다") {
                user("imp-oldmention")
                user("imp-newmention")
                issue.body = "본문 @imp-oldmention"
                issue = issueRepository.save(issue)

                issueService.updateIssue(issue.id!!, "이슈", "본문 @imp-oldmention @imp-newmention", actor, assignee, null, null)

                val receivers = receiversOf(EventType.ISSUE_BODY_CHANGED)
                receivers shouldContain "imp-newmention"
                receivers shouldNotContain "imp-oldmention"
            }

            // v1.6 filterInactiveUsers()는 state != ACTIVE만 뺐다. v1.6에서 게스트(isGuest)·사이트 관리자(SiteAdmin)는 state가 ACTIVE였으므로
            // next(GUEST/SITE_ADMIN을 state로 표현)에서는 LOCKED/DELETED만 빼야 같은 동작이다.
            it("잠긴 담당자(LOCKED)는 제외되고 게스트 공유자(GUEST)는 댓글 알림 수신자에 남는다") {
                assignee.state = UserState.LOCKED
                userRepository.save(assignee)
                sharer.state = UserState.GUEST
                userRepository.save(sharer)

                commentService.createIssueComment(issue.id!!, "댓글", actor)

                val receivers = receiversOf(EventType.NEW_COMMENT)
                receivers shouldContain "imp-author"
                receivers shouldNotContain "imp-assignee"
                receivers shouldContain "imp-sharer"
            }

            it("다른 사람이 게시글을 수정하면 게시글 작성자가 수신자이다") {
                val posting = postingService.createPosting(
                    project.id!!, Posting(title = "게시글", body = "본문", project = project), author.id!!
                )

                postingService.updatePosting(project.id!!, posting.number!!, "게시글", "수정된 본문", false, false, actor.id!!)

                receiversOf(EventType.POSTING_BODY_CHANGED) shouldContain "imp-author"
            }
        }
    }
}
