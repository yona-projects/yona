package dev.yona.domain.project

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.CommitComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.SimpleCommentThread
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.Milestone
import dev.yona.domain.misc.FavoriteIssue
import dev.yona.domain.misc.FavoriteProject
import dev.yona.domain.misc.RecentIssue
import dev.yona.domain.notification.UserProjectNotification
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestCommit
import dev.yona.domain.pullrequest.PullRequestEvent
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 삭제를 검증한다. 프로젝트와, 프로젝트에 딸린 모든 데이터를 지운다.
 *
 * 원본: `app/models/Project.java:695-790` (`delete`, `deletePullRequests`, `deleteCommentThreads`, `deleteOriginal`),
 * `app/models/PullRequest.java:831-849`, `app/models/CommentThread.java:236-240`.
 */
@Transactional
class ProjectDeletionServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ProjectDeletionService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(): User = users.save(User(loginId = "pd${++sequence}", name = "pd$sequence", email = "pd$sequence@example.com", state = UserState.ACTIVE))

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun project(original: Project? = null): Project =
        persist(Project(owner = "pdo${++sequence}", name = "pd$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC, originalProject = original))

    private fun count(table: String, where: String = "1 = 1"): Long {
        em.flush()
        return (em.createNativeQuery("select count(*) from $table where $where").singleResult as Number).toLong()
    }

    private fun exec(sql: String) {
        em.createNativeQuery(sql).executeUpdate()
    }

    /** 삭제 대상 프로젝트에 모든 종류의 하위 데이터를 만든다. */
    private fun populate(p: Project, other: Project, member: User): Map<String, Long> {
        val pid = p.id!!
        persist(ProjectMenuSetting(project = p, code = true))
        persist(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        persist(PushedBranch(pushedDate = Date(), name = "dev", project = p))
        persist(ProjectTransfer(sender = member, destination = "someone", project = p, requested = Date(), confirmKey = "k", accepted = false))
        persist(UserProjectNotification(user = member, project = p, notificationType = EventType.NEW_ISSUE, allowed = true))
        persist(FavoriteProject(user = member, project = p, owner = p.owner, projectName = p.name))
        persist(RecentProject(userId = member.id, owner = p.owner, project = p, projectName = p.name))
        persist(TitleHead(project = p, headKeyword = "kw", frequency = 1))
        persist(CommitComment(project = p, path = "a", line = 1, contents = "c", createdDate = Date(), authorId = member.id))
        member.enrolledProjects.add(p)
        val label = persist(Label(category = "os", name = "linux${++sequence}"))
        exec("insert into project_label (project_id, label_id) values ($pid, ${label.id})")

        val webhook = persist(Webhook(project = p, payloadUrl = "http://x", createdAt = Date(), gitPush = true))
        persist(WebhookThread(webhook = webhook, resourceType = ResourceType.ISSUE_POST, resourceId = "1", threadId = "t", createdAt = Date()))

        val category = persist(IssueLabelCategory(project = p, name = "cat"))
        val issueLabel = persist(IssueLabel(category = category, color = "red", name = "bug", project = p))
        val assignee = persist(Assignee(user = member, project = p))
        val milestone = persist(Milestone(title = "m", state = State.OPEN, project = p))
        val issue = persist(Issue(project = p, number = 1, title = "t", state = State.OPEN, milestone = milestone, assignee = assignee, authorId = member.id))
        issue.labels.add(issueLabel)
        issue.voters.add(member)
        val comment = persist(IssueComment(contents = "c", createdDate = Date(), issue = issue, projectId = pid))
        comment.voters.add(member)
        persist(IssueEvent(created = Date(), issue = issue, eventType = EventType.NEW_COMMENT))
        persist(IssueSharer(loginId = member.loginId, user = member, issue = issue))
        persist(FavoriteIssue(user = member, issue = issue))
        persist(RecentIssue(user = member, issue = issue, title = "t", url = "/", createdDate = Date()))
        val child = persist(Issue(project = p, number = 2, title = "child", state = State.OPEN, parent = issue))

        val posting = persist(Posting(project = p, number = 1, title = "post", body = "b", createdDate = Date()))
        posting.labels.add(issueLabel)
        persist(PostingComment(contents = "c", createdDate = Date(), posting = posting, projectId = pid))

        // 프로젝트의 코멘트 스레드(리뷰)
        val thread = persist(SimpleCommentThread().apply { project = p; state = ThreadState.OPEN; createdDate = Date() })
        thread.codeAuthors.add(member)
        persist(ReviewComment(contents = "r", createdDate = Date(), thread = thread))

        // 이 프로젝트가 보내고 받은 풀 리퀘스트
        val sent = persist(PullRequest(title = "sent", fromProject = p, toProject = other, contributor = member, created = Date(), state = State.OPEN, number = 1))
        val received = persist(PullRequest(title = "recv", fromProject = other, toProject = p, contributor = member, created = Date(), state = State.OPEN, number = 1))
        received.reviewers.add(member)
        persist(PullRequestCommit(pullRequest = sent, commitId = "abc", created = Date(), state = PullRequestCommitState.CURRENT))
        persist(PullRequestEvent(senderLoginId = member.loginId, pullRequest = received, eventType = EventType.NEW_PULL_REQUEST, created = Date()))
        val prThread = persist(SimpleCommentThread().apply { pullRequest = sent; project = other; state = ThreadState.OPEN; createdDate = Date() })
        persist(ReviewComment(contents = "pr", createdDate = Date(), thread = prThread))
        // 풀 리퀘스트를 가리키는 다른 이슈의 이벤트
        val referring = persist(Issue(project = other, number = 9, title = "ref", state = State.OPEN))
        persist(IssueEvent(created = Date(), senderLoginId = member.loginId, issue = referring, eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST, newValue = sent.id.toString()))
        assertNotNull(child.id)
        em.flush()
        return emptyMap()
    }

    private val tablesWithProjectId = listOf(
        "assignee", "comment_thread", "commit_comment", "favorite_project", "issue", "issue_label", "issue_label_category", "milestone",
        "posting", "project_label", "project_menu_setting", "project_pushed_branch", "project_transfer", "project_user",
        "recent_project", "title_head", "user_project_notification", "webhook"
    )

    @Test
    fun `프로젝트와 딸린 모든 데이터를 지운다`() {
        val p = project()
        val other = project()
        val member = user()
        populate(p, other, member)
        val pid = p.id!!

        service.delete(p)
        em.flush()
        em.clear()

        assertEquals(0, count("project", "id = $pid"))
        tablesWithProjectId.forEach { assertEquals(0, count(it, "project_id = $pid"), "$it 에 남은 데이터가 있다") }
        assertEquals(0, count("user_enrolled_project", "project_id = $pid"))
        assertEquals(0, count("pull_request", "from_project_id = $pid or to_project_id = $pid"))
        assertEquals(0, count("webhook_thread"))
        assertEquals(0, count("issue_comment", "project_id = $pid"))
        assertEquals(0, count("posting_comment", "project_id = $pid"))
        assertEquals(0, count("pull_request_commit"))
        assertEquals(0, count("pull_request_event"))
        assertEquals(0, count("pull_request_reviewers"))
        assertEquals(0, count("review_comment"))
        assertEquals(0, count("comment_thread_n4user"))
        assertEquals(0, count("issue_issue_label"))
        assertEquals(0, count("posting_issue_label"))
        assertEquals(0, count("issue_voter"))
        assertEquals(0, count("issue_comment_voter"))
        assertEquals(0, count("issue_sharer"))
        assertEquals(0, count("favorite_issue"))
        assertEquals(0, count("recent_issue"))
        // 풀 리퀘스트를 가리키던 다른 프로젝트 이슈의 이벤트도 지운다.
        assertEquals(0, count("issue_event", "event_type = 'ISSUE_REFERRED_FROM_PULL_REQUEST'"))
    }

    @Test
    fun `이슈, 게시글, 댓글의 첨부와 알림 이벤트와 프로젝트 로고를 지운다 (R-18)`() {
        val p = project(); val other = project(); val member = user()
        val pid = p.id!!
        val issue = persist(Issue(project = p, number = 1, title = "t", state = State.OPEN, authorId = member.id))
        val comment = persist(IssueComment(contents = "c", createdDate = Date(), issue = issue, projectId = pid))
        val posting = persist(Posting(project = p, number = 1, title = "post", body = "b", createdDate = Date()))
        val postingComment = persist(PostingComment(contents = "c", createdDate = Date(), posting = posting, projectId = pid))
        em.flush()
        fun attach(type: ResourceType, id: Long) = persist(
            dev.yona.domain.misc.Attachment(name = "f$type", hash = "h-$type-$id-${++sequence}", containerType = type, containerId = id, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = member.loginId)
        )
        fun event(type: ResourceType, id: Long) = persist(
            dev.yona.domain.notification.NotificationEvent(title = "e", senderId = member.id, created = Date(), resourceType = type, resourceId = id.toString(), eventType = EventType.NEW_COMMENT)
        )
        val targets = listOf(
            ResourceType.ISSUE_POST to issue.id!!, ResourceType.ISSUE_COMMENT to comment.id!!,
            ResourceType.BOARD_POST to posting.id!!, ResourceType.NONISSUE_COMMENT to postingComment.id!!
        )
        targets.forEach { (type, id) -> attach(type, id); event(type, id) }
        attach(ResourceType.PROJECT, pid)
        val untouched = attach(ResourceType.ISSUE_POST, 99_999L)
        em.flush()

        service.delete(p)
        em.flush(); em.clear()

        // v1.6 Project.delete는 issue.delete()와 posting.delete()를 불러 이슈, 게시글, 댓글의 첨부와 알림 이벤트를 지운다. 프로젝트 로고는 v1.6이 지우지 않았으나 지운다(R-18).
        targets.forEach { (type, id) ->
            assertEquals(0, count("attachment", "container_type = '${type.name}' and container_id = $id"), "$type 첨부")
            assertEquals(0, count("notification_event", "resource_type = '${type.name}' and resource_id = '$id'"), "$type 알림 이벤트")
        }
        assertEquals(0, count("attachment", "container_type = 'PROJECT' and container_id = $pid"))
        assertEquals(1, count("attachment", "id = ${untouched.id}"))
        assertNotNull(other.id)
    }

    @Test
    fun `프로젝트와 풀 리퀘스트의 구독 기록도 지운다 (v1_6 ResourcePersistAdapter_postDelete)`() {
        val p = project(); val member = user()
        populate(p, project(), member)
        val pid = p.id!!
        val prIds = em.createNativeQuery("select id from pull_request where from_project_id = $pid or to_project_id = $pid").resultList.map { (it as Number).toLong() }
        exec("insert into watch (user_id, resource_type, resource_id) values (${member.id}, 'PROJECT', '$pid')")
        exec("insert into unwatch (user_id, resource_type, resource_id) values (${member.id}, 'PROJECT', '$pid')")
        prIds.forEach { exec("insert into watch (user_id, resource_type, resource_id) values (${member.id}, 'PULL_REQUEST', '$it')") }
        assertEquals(1 + prIds.size.toLong(), count("watch"))

        service.delete(p)
        em.flush(); em.clear()
        assertEquals(0, count("watch", "resource_type in ('PROJECT', 'PULL_REQUEST')"))
        assertEquals(0, count("unwatch", "resource_type = 'PROJECT'"))
    }

    @Test
    fun `다른 프로젝트의 데이터는 건드리지 않는다`() {
        val p = project()
        val control = project()
        val member = user()
        populate(p, project(), member)
        populate(control, project(), member)

        service.delete(p)
        em.flush()

        val cid = control.id!!
        assertEquals(1, count("project", "id = $cid"))
        tablesWithProjectId.forEach { assertTrue(count(it, "project_id = $cid") > 0, "$it 의 다른 프로젝트 데이터가 지워졌다") }
    }

    @Test
    fun `포크가 있으면 포크의 원본 연결을 끊고 포크가 보내고 받은 풀 리퀘스트를 지운다`() {
        val original = project()
        val fork = project(original)
        val other = project()
        val member = user()
        persist(PullRequest(title = "fork-sent", fromProject = fork, toProject = other, contributor = member, created = Date(), state = State.OPEN, number = 1))
        persist(PullRequest(title = "fork-recv", fromProject = other, toProject = fork, contributor = member, created = Date(), state = State.OPEN, number = 2))
        em.flush()

        service.delete(original)
        em.flush()
        em.clear()

        assertEquals(1, count("project", "id = ${fork.id}"))
        assertNull(em.find(Project::class.java, fork.id)!!.originalProject)
        assertEquals(0, count("pull_request", "from_project_id = ${fork.id} or to_project_id = ${fork.id}"))
    }

    @Test
    fun `이슈의 부모 연결은 끊고 다른 프로젝트의 이슈는 남긴다`() {
        val p = project()
        val other = project()
        val parent = persist(Issue(project = p, number = 1, title = "parent", state = State.OPEN))
        val child = persist(Issue(project = other, number = 1, title = "child", state = State.OPEN, parent = parent))
        em.flush()

        service.delete(p)
        em.flush()
        em.clear()

        assertNull(em.find(Issue::class.java, child.id)!!.parent)
    }

    @Test
    fun `처리하는 테이블이 프로젝트를 따라가는 모든 테이블을 덮는다`() {
        // v1.6 외래 키 그래프에서 프로젝트로부터 닿는 테이블이 모두 삭제 대상 목록에 있어야 한다.
        val reachable = reachableFromProject()
        val covered = ProjectDeletionService.DELETED_TABLES
        // DB가 삭제를 따라 지워 주거나(CASCADE, SET NULL) 연결만 끊는 테이블은 목록에 없어도 된다.
        val missing = reachable - covered
        assertEquals(emptySet(), missing, "삭제 대상 목록에 없는 테이블: $missing")
    }

    private fun reachableFromProject(): Set<String> {
        val edges = javaClass.getResourceAsStream("/schema/v16-foreign-keys.tsv")!!.bufferedReader().readLines()
            .map { it.split('\t') }.filter { it.size >= 6 }
        val children = edges.groupBy({ it[3] }, { it[0] })
        val seen = linkedSetOf<String>()
        fun visit(table: String) {
            children[table].orEmpty().filter { it != table }.forEach { if (seen.add(it)) visit(it) }
        }
        visit("project")
        return seen
    }
}
