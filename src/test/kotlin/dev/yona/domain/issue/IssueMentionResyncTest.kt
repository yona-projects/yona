package dev.yona.domain.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.MentionRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 이슈를 `update()`로 저장하는 v1.6 경로가 본문 멘션 기록을 다시 맞추는지 검증한다.
 *
 * v1.6 `AbstractPosting.update()`는 댓글 수를 다시 세고 `updateMention()`으로 `mention` 행을 본문 기준으로 맞춘다. 추천, 추천 취소(바뀐 때만),
 * 가중치 조정, 댓글 등록, 수정, 삭제가 모두 이 경로를 지난다. 기대 동작의 근거는 `app/models/Issue.java:195`, `AbstractPosting.java:149-160, 273-277`,
 * `Comment.java:81-98`, `IssueComment.java:137-147`이다.
 */
@Transactional
class IssueMentionResyncTest : AbstractIntegrationTest() {

    @Autowired private lateinit var issueService: IssueService
    @Autowired private lateinit var commentService: IssueCommentService
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var mentions: MentionRepository

    private var sequence = 0

    private fun user(login: String) = users.save(User(loginId = login, name = login, email = "$login@example.com", state = UserState.ACTIVE))

    /** 본문에 `@멘션대상`이 있지만 `mention` 행은 아직 없는 이슈(멘션 대상이 글을 쓴 뒤에 생긴 상황)이다. */
    private fun issueMentioning(loginId: String): Issue {
        val project = projects.save(Project(owner = "mo", name = "mp${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1))
        return issues.save(Issue(project = project, number = 1, title = "제목", body = "확인 @$loginId", state = State.OPEN, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    private fun issueMentionees(issue: Issue) =
        mentions.findAll().filter { it.resourceType == ResourceType.ISSUE_POST && it.resourceId == issue.id.toString() }.mapNotNull { it.user?.loginId }

    @Test
    fun `가중치를 바꾸면 멘션 행을 다시 맞춘다`() {
        val target = "mt${++sequence}"; user(target)
        val issue = issueMentioning(target)
        issueService.adjustWeight(issue, 1)
        assertEquals(listOf(target), issueMentionees(issue))
    }

    @Test
    fun `이슈를 추천하거나 취소하면 멘션 행을 다시 맞춘다`() {
        val target = "mt${++sequence}"; user(target); val voter = user("voter${++sequence}")
        val issue = issueMentioning(target)
        commentService.vote(issue, voter)
        assertEquals(listOf(target), issueMentionees(issue))
        mentions.deleteAll(mentions.findAll().filter { it.resourceId == issue.id.toString() })
        commentService.unvote(issue, voter)
        assertEquals(listOf(target), issueMentionees(issue))
    }

    @Test
    fun `이미 추천한 이슈를 다시 추천하면 아무것도 다시 맞추지 않는다 (추천자가 바뀐 때만 update)`() {
        val target = "mt${++sequence}"; user(target); val voter = user("voter${++sequence}")
        val issue = issueMentioning(target)
        commentService.vote(issue, voter)
        mentions.deleteAll(mentions.findAll().filter { it.resourceId == issue.id.toString() })
        commentService.vote(issue, voter)
        assertEquals(emptyList(), issueMentionees(issue))
    }

    @Test
    fun `댓글을 추천하거나 취소하면 댓글의 멘션 행을 다시 맞춘다`() {
        val target = "mt${++sequence}"; user(target); val voter = user("voter${++sequence}")
        val issue = issueMentioning(target)
        val comment = comments.save(IssueComment(contents = "댓글 @$target", createdDate = Date(), issue = issue, projectId = issue.project!!.id!!))
        commentService.voteComment(comment, voter)
        val rows = { mentions.findAll().filter { it.resourceType == ResourceType.ISSUE_COMMENT && it.resourceId == comment.id.toString() }.mapNotNull { it.user?.loginId } }
        assertEquals(listOf(target), rows())
    }

    @Test
    fun `댓글을 달면 이슈의 멘션 행도 다시 맞춘다`() {
        val target = "mt${++sequence}"; user(target); val author = user("author${++sequence}")
        val issue = issueMentioning(target)
        commentService.add(issue, author, "내용", null, false)
        assertEquals(listOf(target), issueMentionees(issue))
    }

    @Test
    fun `다음 상태로 바꾸면 이슈의 멘션 행을 다시 맞춘다`() {
        val target = "mt${++sequence}"; user(target); val actor = user("actor${++sequence}")
        val issue = issueMentioning(target)
        issueService.toNextState(issue, actor)
        assertEquals(listOf(target), issueMentionees(issue))
    }
}
