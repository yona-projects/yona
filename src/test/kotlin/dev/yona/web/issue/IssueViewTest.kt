package dev.yona.web.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.FavoriteIssue
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 이슈 보기 화면(`GET /:user/:project/issue/:number`)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:248-309`, `app/views/issue/{view,partial_comments,partial_comment,partial_event_timeline,
 * partial_voters,partial_voter_list,partial_show_selected_label,partial_index_comments,partial_index_comment,partial_view_childIssueList}.scala.html`,
 * `app/views/common/{commentForm,commentUpdateForm,childComments,child_commentForm,commentDeleteModal,partial_history}.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "iv$n", name = name ?: "iv$n", email = "iv$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, milestone: Boolean = true): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = milestone, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun issue(project: Project, number: Long = 1, author: User? = null, title: String = "이슈 제목", body: String? = "본문 **굵게**", state: State = State.OPEN, draft: Boolean = false,
                      assignee: User? = null, milestone: Milestone? = null, parent: Issue? = null, history: String? = null, dueDate: Date? = null, issueLabels: Set<IssueLabel> = emptySet()): Issue =
        issues.save(
            Issue(
                project = project, number = number, title = title, body = body, state = state, isDraft = draft, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name,
                assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, parent = parent, history = history, dueDate = dueDate,
                labels = issueLabels.toMutableSet(), numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L + number), updatedDate = Date(1_700_000_100_000L)
            )
        )

    private fun comment(issue: Issue, author: User, contents: String = "댓글", parent: IssueComment? = null, at: Long = 1_700_000_200_000L): IssueComment {
        val c = IssueComment(contents = contents, createdDate = Date(at), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = issue.project!!.id!!, parentComment = parent)
        entityManager.persist(c); entityManager.flush()
        return c
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun view(project: Project, number: Long = 1, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/$number").apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun listShortcut(project: Project, number: Long, referer: String?): String {
        val html = mockMvc.perform(get("${base(project)}/issue/$number").apply { referer?.let { header("Referer", it) } }).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"L\":\\s*\"([^\"]*)\"").find(html.replace("\\/", "/").replace("\\u0026", "&"))!!.groupValues[1]
    }

    @Test
    fun `목록으로 가는 단축키는 Referer가 없으면 열린 이슈 목록이다`() {
        val project = newProject()
        issue(project, 1, state = State.CLOSED)
        // v1.6 view.scala.html urlToIssues: Referer가 없으면 이슈 상태와 관계없이 state=open
        assertEquals("${base(project)}/issues?state=open", listShortcut(project, 1, null))
    }

    @Test
    fun `목록으로 가는 단축키는 같은 목록에서 왔을 때만 Referer를 쓴다`() {
        val project = newProject()
        issue(project, 1, state = State.CLOSED)
        val list = "${base(project)}/issues?state=open&filter=abc"
        assertEquals(list, listShortcut(project, 1, list))
        // 경로만 같고 호스트가 다른 곳에서 오면 v1.6 resourceEquals가 아니라고 보고 기본 주소를 쓴다
        assertEquals("${base(project)}/issues?state=closed", listShortcut(project, 1, "http://other.example.org${base(project)}/issues?state=open"))
        assertEquals("${base(project)}/issues?state=closed", listShortcut(project, 1, "${base(project)}/boards"))
    }

    // --- 머리와 본문 ---

    @Test
    fun `제목과 머리 영역`() {
        val project = newProject()
        val author = newUser("글쓴이")
        issue(project, author = author)
        val doc = view(project)
        assertEquals("이슈 제목", doc.title())
        val header = doc.selectFirst(".board-header.issue")!!
        assertEquals("Open", header.selectFirst(".badge.badge-issue-open")!!.text())
        assertEquals("1", header.selectFirst(".board-id")!!.text())
        assertTrue(header.selectFirst(".title")!!.text().contains("이슈 제목"))
        assertTrue(header.select(".subtask-mark, .draft").isEmpty())
        assertTrue(header.select(".favorite-issue i.star").isNotEmpty())
        assertFalse(header.select(".favorite-issue i").first()!!.hasClass("starred"))
    }

    @Test
    fun `내 이름을 찾는 스크립트가 올바른 자바스크립트로 그려진다`() {
        val project = newProject()
        val author = newUser("글쓴이")
        issue(project, author = author)
        val script = view(project).select("script").map { it.data() }.first { ".comment-body:contains(" in it }
        // 타임리프가 `/*[[ ]]*/ ""` 뒤의 `+ "')"`를 지우면 선택자가 깨진다.
        assertEquals("""        var viewerName = "User exists not";""", script.lines().first { "var viewerName" in it })
        assertTrue(script.contains("""$(".comment-body:contains('" + viewerName + "')")"""))
    }

    @Test
    fun `하위 이슈와 초안 표시`() {
        val project = newProject()
        val author = newUser()
        val parent = issue(project, 1, author)
        issue(project, 2, author, parent = parent)
        issue(project, 3, author, draft = true, state = State.DRAFT)
        assertEquals("subtask", view(project, 2).selectFirst(".board-header .subtask-mark")!!.text())
        val draft = view(project, 3, login(author))
        assertEquals("#Draft", draft.selectFirst(".board-header .board-id")!!.text())
        assertEquals("Draft", draft.selectFirst(".board-header .badge")!!.text().ifEmpty { "Draft" })
        assertTrue(draft.select(".board-header .draft").isNotEmpty())
    }

    @Test
    fun `즐겨찾기한 이슈는 별이 채워진다`() {
        val project = newProject()
        val me = newUser(); val i = issue(project, author = me)
        entityManager.persist(FavoriteIssue(user = me, issue = i)); entityManager.flush()
        assertTrue(view(project, session = login(me)).selectFirst(".favorite-issue i")!!.hasClass("starred"))
    }

    @Test
    fun `작성자와 본문`() {
        val project = newProject()
        val author = newUser("글쓴이")
        val i = issue(project, author = author)
        val doc = view(project)
        val info = doc.selectFirst(".author-info a.usf-group")!!
        assertEquals("/${author.loginId}", info.attr("href"))
        assertEquals("글쓴이", info.selectFirst(".name")!!.text())
        assertEquals("@${author.loginId}", info.selectFirst(".loginid")!!.text())
        assertEquals("<strong>굵게</strong>", doc.selectFirst("#issue-body-1 .content.markdown-wrap p strong")!!.outerHtml())
        assertEquals("false", doc.selectFirst("#issue-body-1 .content")!!.attr("data-allowed-update"))
        assertTrue(doc.select("#issue-body-1 .tasklist").isNotEmpty())
        assertEquals("본문 **굵게**", doc.selectFirst("#issue-1 textarea")!!.wholeText())
        assertEquals("/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/1/content", doc.selectFirst("#issue-1 form")!!.attr("action"))
        assertTrue(doc.select("#attachments[data-attachments]").isNotEmpty())
        assertEquals(i.id.toString(), doc.selectFirst(".favorite-issue")!!.attr("data-issue-id"))
    }

    @Test
    fun `본문이 비어 있으면 빈 칸이다`() {
        val project = newProject()
        issue(project, body = "")
        assertTrue(view(project).select(".content.empty-content").isNotEmpty())
    }

    @Test
    fun `수정 권한이 있으면 본문의 갱신 허용 표시가 true이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project)
        assertEquals("true", view(project, session = login(member)).selectFirst("#issue-body-1 .content")!!.attr("data-allowed-update"))
    }

    @Test
    fun `변경 이력은 로그인하면 대화상자로, 익명이면 로그인 링크로 보인다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, history = "<b>이력</b>")
        val anonymous = view(project)
        assertTrue(anonymous.select(".posting-history a[data-toggle=modal][href*=login]").isNotEmpty() || anonymous.select(".posting-history a[href*=redirectUrl]").isNotEmpty())
        assertTrue(anonymous.select("#-yona-posting-history").isEmpty())
        val logged = view(project, session = login(member))
        assertTrue(logged.select(".posting-history a[href=#-yona-posting-history]").isNotEmpty())
        assertTrue(logged.select("#-yona-posting-history .modal-body b").isNotEmpty())
        assertTrue(view(newProject().also { issue(it) }.let { it }).select(".posting-history").isEmpty())
    }

    // --- 동작 단추 ---

    @Test
    fun `구독 단추는 구독 권한이 있을 때 보이고 구독 상태를 나타낸다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        assertTrue(view(project).select("#watch-button").isEmpty())
        val before = view(project, session = login(member)).selectFirst("#watch-button")!!
        assertEquals("false", before.attr("data-watching")); assertEquals("Subscribe", before.text())
        watches.save(Watch(user = member, resourceType = ResourceType.ISSUE_POST, resourceId = i.id.toString()))
        val after = view(project, session = login(member)).selectFirst("#watch-button")!!
        assertEquals("true", after.attr("data-watching")); assertTrue(after.hasClass("ybtn-watching")); assertEquals("Unsubscribe from this issue", after.text())
    }

    @Test
    fun `수정 권한이 있으면 공유와 가중치 단추와 수정 삭제 아이콘이 보인다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        val doc = view(project, session = login(member))
        assertTrue(doc.select("#issue-share-button").isNotEmpty())
        assertTrue(doc.select("#upvote-issue-weight, #down-vote-issue-weight, .weight-number").size == 3)
        assertEquals("0", doc.selectFirst(".weight-number")!!.text())
        assertTrue(doc.select(".act-row button[onclick*=editform]").isNotEmpty())
        assertTrue(doc.select(".act-row a[href=#deleteConfirm]").isNotEmpty())
        val anonymous = view(project)
        assertTrue(anonymous.select("#issue-share-button, a[href=#deleteConfirm]").isEmpty())
        assertTrue(anonymous.select(".act-row a[href\$=editform] button").isNotEmpty())
    }

    @Test
    fun `다른 사람의 댓글이 있으면 지울 수 없다고 알린다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = newUser()
        val i = issue(project, author = author)
        comment(i, other)
        val doc = view(project, session = login(author))
        assertTrue(doc.select(".act-row a[href=#deleteConfirm]").isEmpty())
        assertTrue(doc.select(".act-row button.disabled[data-content]").isNotEmpty())
    }

    @Test
    fun `삭제 확인 대화상자`() {
        val project = newProject()
        issue(project)
        val button = view(project).selectFirst("#deleteConfirm button[data-request-method=delete]")!!
        assertEquals("${base(project)}/issue/1/delete", button.attr("data-request-uri"))
    }

    // --- 추천 ---

    @Test
    fun `추천 단추는 댓글을 쓸 수 있으면 링크이고 아니면 로그인 안내이다`() {
        val project = newProject()
        val me = join(newUser(), project)
        issue(project)
        val link = view(project, session = login(me)).selectFirst("#vote a")!!
        assertEquals("${base(project)}/issue/1/vote", link.attr("href"))
        assertEquals("post", link.attr("data-request-method"))
        assertTrue(view(project).select("#vote span.ybtn-disabled[data-login=required]").isNotEmpty())
    }

    @Test
    fun `추천한 사람이 있으면 아바타 목록과 더 보기 대화상자를 보인다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        val voters = (1..5).map { newUser("추천$it") }
        i.voters.addAll(voters); issues.save(i); entityManager.flush()
        val doc = view(project, session = login(me))
        assertTrue(doc.select("#vote.voter-exists").isNotEmpty())
        assertEquals(3, doc.select(".voter-list li").size - doc.select(".voter-list li[data-toggle=tooltip]").size)
        assertEquals("and 2 others", doc.selectFirst(".voter-list li[data-toggle=tooltip] a")!!.text().trim())
        assertEquals(5, doc.select("#voters .modal-body li").size)
    }

    // --- 공유자, 하위 이슈 ---

    @Test
    fun `공유자는 수정 권한이 있으면 입력 상자, 없으면 이름 목록이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val shared = newUser("공유받음")
        val i = issue(project)
        entityManager.persist(IssueSharer(loginId = shared.loginId, user = shared, issue = i)); entityManager.flush()
        val editable = view(project, session = login(member))
        assertEquals(shared.loginId, editable.selectFirst("input#issueSharer")!!.attr("value"))
        assertEquals("1", editable.selectFirst(".issue-sharer-count")!!.text())
        val readonly = view(project)
        assertEquals(listOf("공유받음"), readonly.select("#sharer-list .sharer-item .name").map { it.text() })
        assertTrue(view(newProject().also { issue(it) }.let { it }).select(".sharer-list.hideFromDisplayOnly").isNotEmpty())
    }

    @Test
    fun `부모 이슈는 하위 이슈 목록을 보인다`() {
        val project = newProject()
        val parent = issue(project, 1, title = "부모")
        issue(project, 2, title = "열린 자식", parent = parent)
        issue(project, 3, title = "닫힌 자식", parent = parent, state = State.CLOSED)
        val doc = view(project, 1)
        assertEquals("#1 부모", doc.selectFirst(".subtasks .parent-issue a")!!.text().trim())
        assertEquals(listOf("#2", "#3"), doc.select(".subtasks .child-issue .subtask-number").map { it.text() })
        assertTrue(doc.select(".subtasks .parent-issue .upload-progress.red-outline").isNotEmpty())
        assertEquals(listOf("#2", "#3"), view(project, 2).select(".subtasks .child-issue .subtask-number").map { it.text() })
    }

    // --- 댓글과 타임라인 ---

    @Test
    fun `댓글이 시간 순서로 보이고 개수가 나온다`() {
        val project = newProject()
        val author = newUser(); val other = newUser("댓글쓴이")
        val i = issue(project, author = author)
        comment(i, other, "첫 댓글", at = 1_700_000_200_000L)
        comment(i, author, "둘째 댓글", at = 1_700_000_300_000L)
        val doc = view(project)
        assertEquals("2", doc.selectFirst("#timeline .comment-header .num")!!.text())
        assertEquals(listOf("첫 댓글", "둘째 댓글"), doc.select("#timeline .timeline-list li.comment .comment-body p").map { it.text() })
        val first = doc.selectFirst("#timeline li.comment")!!
        assertEquals("comment-${first.id().removePrefix("comment-")}", first.id())
        assertEquals("댓글쓴이", first.selectFirst(".comment_author strong")!!.text())
    }

    @Test
    fun `내 댓글은 author 표시가 붙는다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        comment(i, me)
        assertTrue(view(project, session = login(me)).select("#timeline li.comment.author").isNotEmpty())
    }

    @Test
    fun `댓글의 수정과 삭제 단추는 권한에 따라 보인다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val other = newUser()
        val i = issue(project)
        val mine = comment(i, me, "내 것"); val theirs = comment(i, other, "남의 것")
        val doc = view(project, session = login(me))
        assertTrue(doc.select("#comment-${mine.id} button[data-toggle=comment-delete]").isNotEmpty())
        assertEquals("${base(project)}/issue/1/comment/${mine.id}/delete", doc.selectFirst("#comment-${mine.id} button[data-toggle=comment-delete]")!!.attr("data-request-uri"))
        assertTrue(doc.select("#comment-${mine.id} button[data-toggle=comment-edit]").isNotEmpty())
        assertTrue(doc.select("#comment-${theirs.id} button[data-toggle=comment-delete]").isNotEmpty())
        assertTrue(view(project).select("button[data-toggle=comment-delete]").isEmpty())
    }

    @Test
    fun `댓글 수정 폼에는 현재 내용과 첨부 영역이 있다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        val c = comment(i, me, "고칠 내용")
        val form = view(project, session = login(me)).selectFirst("#comment-editform-${c.id} form")!!
        assertEquals("${base(project)}/issue/1/comments/${c.id}", form.attr("action"))
        assertEquals("고칠 내용", form.selectFirst("textarea[name=contents]")!!.text())
        assertEquals("ISSUE_COMMENT", form.selectFirst("div#upload-${c.id}")!!.attr("data-resourceType"), form.select("div#upload-${c.id}").outerHtml())
    }

    @Test
    fun `댓글의 추천 단추`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        val c = comment(i, newUser())
        val doc = view(project, session = login(me))
        assertEquals("${base(project)}/issue/1/comment/${c.id}/vote", doc.selectFirst("#comment-${c.id} button[data-request-type=comment-vote]")!!.attr("data-request-uri"))
        assertTrue(view(project).select("#comment-${c.id} i.vote-heart-disable-hover").isNotEmpty())
    }

    @Test
    fun `한 줄 댓글은 부모 댓글 아래에 보이고 목록에는 따로 나오지 않는다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        val parent = comment(i, me, "부모 댓글")
        val child = comment(i, me, "한 줄 댓글", parent = parent, at = 1_700_000_300_000L)
        val doc = view(project, session = login(me))
        assertEquals(1, doc.select(".span-left-pane #timeline ul.comments > li.comment").size)
        assertEquals("한 줄 댓글", doc.selectFirst("#comment-${parent.id} .child-comments .one-line-comment p")!!.text())
        assertTrue(doc.select("#comment-${parent.id} div#comment-${child.id}").isNotEmpty())
        val form = doc.selectFirst("#comment-${parent.id} .child-comment-input-form form")!!
        assertEquals("${base(project)}/issue/1/comments", form.attr("action"))
        assertEquals(parent.id.toString(), form.selectFirst("input[name=parentCommentId]")!!.attr("value"))
    }

    @Test
    fun `이벤트가 댓글 사이에 시간 순서로 나온다`() {
        val project = newProject()
        val me = join(newUser("나"), project)
        val i = issue(project)
        comment(i, me, "댓글")
        entityManager.persist(IssueEvent(created = Date(1_700_000_250_000L), senderLoginId = me.loginId, issue = i, eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "open", newValue = "closed"))
        entityManager.flush()
        val doc = view(project, session = login(me))
        val items = doc.select(".span-left-pane #timeline ul.comments > li")
        assertEquals(2, items.size)
        assertTrue(items[0].hasClass("comment")); assertTrue(items[1].hasClass("event"))
        assertEquals("Closed", items[1].selectFirst(".state.closed")!!.text())
        assertTrue(items[1].text().contains("나"))
    }

    @Test
    fun `담당자 마일스톤 라벨 이벤트의 모양`() {
        val project = newProject()
        val me = join(newUser("나"), project); val other = newUser("그분")
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false))
        labels.save(IssueLabel(project = project, category = category, name = "버그", color = "#f00"))
        val i = issue(project)
        fun event(type: EventType, old: String?, new: String?, at: Long) =
            entityManager.persist(IssueEvent(created = Date(at), senderLoginId = me.loginId, issue = i, eventType = type, oldValue = old, newValue = new))
        event(EventType.ISSUE_ASSIGNEE_CHANGED, null, other.loginId, 1_700_000_300_000L)
        event(EventType.ISSUE_MILESTONE_CHANGED, "-1", milestone.id.toString(), 1_700_000_400_000L)
        event(EventType.ISSUE_LABEL_CHANGED, "", "종류 - 버그 #f00", 1_700_000_500_000L)
        entityManager.flush()
        val doc = view(project, session = login(me))
        assertEquals("Assigned", doc.select("#timeline li.event .state.changed").first()!!.text())
        assertTrue(doc.select("#timeline li.event a[href=/${other.loginId}]").isNotEmpty())
        assertEquals("${base(project)}/milestone/${milestone.id}", doc.selectFirst("#timeline li.event a[href*=/milestone/]")!!.attr("href"))
        assertEquals("버그", doc.selectFirst("#timeline li.event .issue-label")!!.text())
        assertTrue(doc.selectFirst("#timeline li.event .issue-label")!!.attr("style").contains("#f00"))
    }

    @Test
    fun `본문 변경 이벤트는 보이지 않는다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        entityManager.persist(IssueEvent(created = Date(), senderLoginId = me.loginId, issue = i, eventType = EventType.ISSUE_BODY_CHANGED, oldValue = "a", newValue = "b")); entityManager.flush()
        assertTrue(view(project, session = login(me)).select("#timeline li.event").isEmpty())
    }

    @Test
    fun `이벤트만 있는 이슈도 색인의 댓글 목록 상자를 그린다 (v1_6 comments + events가 0보다 크면)`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        assertTrue(view(project, session = login(me)).select(".issue-info #timeline ul.comments").isEmpty())
        entityManager.persist(IssueEvent(created = Date(), senderLoginId = me.loginId, issue = i, eventType = EventType.ISSUE_BODY_CHANGED, oldValue = "a", newValue = "b")); entityManager.flush()
        val box = view(project, session = login(me)).selectFirst(".issue-info #timeline ul.comments")!!
        assertTrue(box.select("li").isEmpty())
    }

    @Test
    fun `댓글 입력 폼은 댓글을 쓸 수 있으면 폼이고 아니면 비활성 상자이다`() {
        val project = newProject()
        val me = join(newUser(), project)
        issue(project)
        val form = view(project, session = login(me)).selectFirst("form#comment-form")!!
        assertEquals("${base(project)}/issue/1/comments", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertTrue(form.select("textarea[name=contents]").isNotEmpty())
        assertTrue(form.select("#upload[data-resource-type=ISSUE_COMMENT]").isNotEmpty())
        val anonymous = view(project)
        assertTrue(anonymous.select("form#comment-form").isEmpty())
        assertTrue(anonymous.select(".write-comment-box textarea.disabled").isNotEmpty())
    }

    @Test
    fun `초안에는 댓글 영역이 없다`() {
        val project = newProject()
        val me = join(newUser(), project)
        issue(project, draft = true, state = State.DRAFT, author = me)
        assertTrue(view(project, session = login(me)).select(".span-left-pane #comments, form#comment-form").isEmpty())
    }

    // --- 오른쪽 창 ---

    @Test
    fun `오른쪽 창은 수정 권한에 따라 입력 또는 읽기 전용이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val assignee = newUser("담당자")
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "버그", color = "#f00"))
        val i = issue(project, assignee = assignee, milestone = milestone, issueLabels = setOf(label), dueDate = Date(System.currentTimeMillis() + 3 * 86_400_000L))
        val editable = view(project, session = login(member)).selectFirst("form#issueUpdateForm")!!
        assertEquals("${base(project)}/issues", editable.attr("action"))
        assertEquals(i.id.toString(), editable.selectFirst("input[name='issues[0].id']")!!.attr("value"))
        assertEquals(assignee.loginId, editable.selectFirst("input#assignee[name=assigneeLoginId]")!!.attr("value"))
        assertEquals(milestone.id.toString(), editable.selectFirst("select#milestone[name='milestone.id'] option[selected]")!!.attr("value"))
        assertTrue(editable.select("input[name=dueDate][data-toggle=calendar]").isNotEmpty())
        assertTrue(editable.selectFirst("select#labelIds option[value=${label.id}]")!!.hasAttr("selected"))

        val readonly = view(project).selectFirst("form#issueUpdateForm")!!
        assertEquals("담당자", readonly.selectFirst("a.usf-group .name")!!.text())
        assertEquals("v1", readonly.select("a[href*=/milestone/]").text().trim())
        assertEquals(1, readonly.select("a.issue-label.static").size)
        assertTrue(readonly.select("input#assignee, select#milestone, select#labelIds").isEmpty())
    }

    @Test
    fun `담당자와 마일스톤이 없으면 안내 문구이다`() {
        val project = newProject()
        issue(project)
        val form = view(project).selectFirst("form#issueUpdateForm")!!
        assertTrue(form.text().contains("No assignee"))
        assertTrue(form.text().contains("No milestone") || form.select("a[href\$=newMilestoneForm]").isNotEmpty())
    }

    @Test
    fun `마일스톤 메뉴가 꺼져 있으면 마일스톤 칸이 없다`() {
        val project = newProject(milestone = false)
        issue(project)
        assertTrue(view(project).select("form#issueUpdateForm a[href*=milestone], form#issueUpdateForm select#milestone").isEmpty())
    }

    @Test
    fun `오른쪽 창 아래에는 댓글 목록 색인이 있다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        val c = comment(i, me, "색인 댓글")
        val index = view(project, session = login(me)).selectFirst(".issue-info #timeline li.index-comment")!!
        assertEquals("#comment-${c.id}", index.attr("data-location"))
        assertEquals("색인 댓글", index.selectFirst(".comment-body a")!!.text())
    }

    @Test
    fun `보이지 않는 입력값과 스크립트`() {
        val project = newProject()
        val i = issue(project)
        val doc = view(project)
        assertEquals("a50e7a35bfe3bb4fa8c6b3d4d8f5c4e9c1e0f2b8".length, doc.selectFirst("#issueBodyChecksum")!!.attr("value").length)
        assertEquals("0", doc.selectFirst("#numOfComments")!!.attr("value"))
        assertEquals(i.updatedDate!!.time.toString(), doc.selectFirst("#issueUpdateDate")!!.attr("value"))
        assertTrue(doc.select("script").any { "issue.View" in it.data() && "\"nextState\"" in it.data() && "${base(project)}/issue/1/nextstate" in it.data().replace("\\/", "/") })
        assertTrue(doc.select("#helpKeys").isNotEmpty())
    }

    // --- 접근 ---

    @Test
    fun `없는 이슈는 404 화면이고 JSON을 원하면 안내 JSON이다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/issue/99")).andExpect(status().isNotFound).andExpect(header().string("Vary", "Accept"))
        mockMvc.perform(get("${base(project)}/issue/99").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.title").value(99)).andExpect(jsonPath("$.body").value("Issue does not exist"))
    }

    @Test
    fun `비공개 프로젝트의 이슈는 비구성원에게 막힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        issue(project)
        mockMvc.perform(get("${base(project)}/issue/1").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `초안은 쓴 사람만 볼 수 있다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = join(newUser(), project)
        issue(project, author = author, draft = true, state = State.DRAFT)
        mockMvc.perform(get("${base(project)}/issue/1").session(login(other))).andExpect(status().isForbidden)
        mockMvc.perform(get("${base(project)}/issue/1")).andExpect(status().isForbidden)
        view(project, session = login(author))
    }

    @Test
    fun `JSON을 원하면 이슈 요약 JSON이다`() {
        val project = newProject()
        issue(project, body = "가".repeat(300))
        val result = mockMvc.perform(get("${base(project)}/issue/1").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.title").value("이슈 제목")).andExpect(jsonPath("$.state").value("OPEN"))
            .andExpect(jsonPath("$.link").value("${base(project)}/issue/1")).andExpect(jsonPath("$.createdDate").exists()).andReturn()
        val body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("body").stringValue()
        assertEquals(200, body.length); assertTrue(body.endsWith("..."))
    }

    @Test
    fun `보면 방문 기록이 남는다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        view(project, session = login(me))
        assertEquals(1, entityManager.createQuery("select r from RecentIssue r where r.user.id = :u and r.issue.id = :i").setParameter("u", me.id).setParameter("i", i.id).resultList.size)
        assertEquals(1, entityManager.createQuery("select r from RecentProject r where r.userId = :u").setParameter("u", me.id).resultList.size)
        view(project, session = login(me))
        assertEquals(1, entityManager.createQuery("select r from RecentIssue r where r.user.id = :u").setParameter("u", me.id).resultList.size)
    }

    @Test
    fun `타임라인 조각은 댓글과 이벤트 목록만 돌려준다`() {
        val project = newProject()
        val me = join(newUser(), project)
        val i = issue(project)
        comment(i, me, "조각 댓글")
        val doc = Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/1/timeline")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("조각 댓글", doc.selectFirst("ul.comments li.comment .comment-body p")!!.text())
        assertTrue(doc.select("nav, footer, .project-menu-outer").isEmpty())
    }

    @Test
    fun `추천자 대화상자는 현재 사용자를 뺀 목록이고 나만 추천했으면 없다 (v1_6 동작)`() {
        val project = newProject()
        val me = join(newUser("나"), project)
        val other = newUser("다른사람")
        val i = issue(project)
        i.voters.addAll(listOf(me, other)); issues.save(i); entityManager.flush()
        val doc = view(project, session = login(me))
        assertEquals(listOf("다른사람"), doc.select("#voters .modal-body li .name").map { it.text() })
        i.voters.remove(other); issues.save(i); entityManager.flush(); entityManager.clear()
        val onlyMe = view(project, session = login(me))
        assertTrue(onlyMe.select("#voters").isEmpty())
        assertTrue(onlyMe.select("#vote.voter-exists .voter-list li").isNotEmpty())
    }
}
