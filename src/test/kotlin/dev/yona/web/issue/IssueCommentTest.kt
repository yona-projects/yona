package dev.yona.web.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueCommentActivity
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.notification.MentionRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 댓글 등록, 수정, 삭제와 추천(이슈, 댓글)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:955-1140`, `app/controllers/AbstractPostingApp.java:52-82`, `app/controllers/VoteApp.java`, `app/models/Comment.java`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class IssueCommentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var mentions: MentionRepository
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(login: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = login ?: "ic$n", name = "ic$n", email = "ic$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun issue(project: Project, author: User? = null): Issue = issues.save(
        Issue(
            project = project, number = 1, title = "제목", body = "이슈 본문", state = State.OPEN, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name,
            numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
        )
    )

    private fun comment(issue: Issue, author: User, contents: String = "댓글", parent: IssueComment? = null): IssueComment {
        val c = IssueComment(contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = issue.project!!.id!!, parentComment = parent)
        entityManager.persist(c); entityManager.flush()
        return c
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun post(project: Project, session: MockHttpSession?, path: String, vararg params: Pair<String, String>) =
        mockMvc.perform(multipart("${base(project)}/issue/1/$path").apply { session?.let { session(it) } }.apply { params.forEach { (k, v) -> param(k, v) } })

    // --- 등록 ---

    @Test
    fun `댓글을 쓰면 저장되고 댓글 주소로 돌아간다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val location = post(project, login(member), "comments", "contents" to "첫 댓글").andExpect(status().isSeeOther).andReturn().response.getHeader("Location")!!
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single { it.issue?.id == i.id }
        assertEquals("${base(project)}/issue/1#comment-${saved.id}", location)
        assertEquals("첫 댓글", saved.contents); assertEquals(member.loginId, saved.authorLoginId); assertEquals(project.id, saved.projectId)
        val reloaded = issues.findById(i.id!!).get()
        assertEquals(1, reloaded.numOfComments)
        assertTrue(reloaded.updatedDate!!.after(Date(1_700_000_000_000L)))
    }

    @Test
    fun `공개 프로젝트에서는 로그인한 누구나 댓글을 쓸 수 있다`() {
        val project = newProject()
        issue(project)
        post(project, login(newUser()), "comments", "contents" to "외부인").andExpect(status().isSeeOther)
    }

    @Test
    fun `비어 있는 댓글은 400이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project)
        post(project, login(member), "comments", "contents" to "").andExpect(status().isBadRequest).andExpect(content().string(org.hamcrest.Matchers.containsString("Comment should not be empty.")))
        assertEquals(0, comments.findAll().size)
    }

    @Test
    fun `쓸 수 없는 사용자는 403이다`() {
        val project = newProject(ProjectScope.PRIVATE)
        issue(project)
        post(project, login(newUser()), "comments", "contents" to "x").andExpect(status().isForbidden)
        post(project, null, "comments", "contents" to "x").andExpect(status().isForbidden)
    }

    @Test
    fun `멘션을 기록하고 활동을 발행한다`() {
        val project = newProject()
        val member = join(newUser(), project); val mentioned = newUser("cm-target")
        issue(project)
        post(project, login(member), "comments", "contents" to "확인 @cm-target").andExpect(status().isSeeOther)
        val saved = comments.findAll().single()
        assertEquals(listOf(mentioned.loginId), mentions.findAll().filter { it.resourceType == ResourceType.ISSUE_COMMENT && it.resourceId == saved.id.toString() }.map { it.user?.loginId })
        val activity = events.stream(IssueCommentActivity::class.java).toList().single()
        assertEquals(saved.id, activity.comment.id); assertEquals(true, activity.created)
    }

    @Test
    fun `첫 댓글은 원래 이슈를, 다음 댓글은 이전 댓글을 이전 내용으로 남긴다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project)
        val session = login(member)
        post(project, session, "comments", "contents" to "첫째").andExpect(status().isSeeOther)
        post(project, session, "comments", "contents" to "둘째").andExpect(status().isSeeOther)
        val list = events.stream(IssueCommentActivity::class.java).toList()
        assertTrue(list[0].previousContents!!.contains("Original issue from @${list[0].comment.issue!!.authorLoginId}"), list[0].previousContents)
        assertTrue(list[0].previousContents!!.endsWith("이슈 본문"))
        assertTrue(list[1].previousContents!!.contains("Previous comment from @${member.loginId}") && list[1].previousContents!!.endsWith("첫째"))
    }

    @Test
    fun `한 줄 댓글은 부모 댓글을 가리킨다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val parent = comment(i, member, "부모")
        i.numOfComments = 1; issues.save(i)
        post(project, login(member), "comments", "contents" to "답", "parentCommentId" to parent.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val child = comments.findAll().single { it.contents == "답" }
        assertEquals(parent.id, child.parentComment?.id)
    }

    @Test
    fun `상태 전환을 함께 보내면 댓글을 쓰고 상태를 바꾼다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        post(project, login(member), "comments", "contents" to "닫습니다", "withStateTransition" to "closed").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.CLOSED, issues.findById(i.id!!).get().state)
        val types = entityManager.createQuery("select e.eventType from IssueEvent e where e.issue.id = :id", EventType::class.java).setParameter("id", i.id).resultList
        assertEquals(listOf(EventType.ISSUE_STATE_CHANGED), types)
    }

    @Test
    fun `댓글로 하위 이슈를 다시 열어도 닫힌 상위 이슈는 열지 않는다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val parent = issue(project)
        parent.state = State.CLOSED; issues.save(parent)
        val child = issues.save(
            Issue(
                project = project, number = 2, title = "하위", body = "b", state = State.CLOSED, numOfComments = 0, weight = 0, parent = parent,
                createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
        // v1.6 newComment는 Issue.toNextState만 부르고 상위 이슈를 열지 않는다 (nextState 액션만 연다).
        mockMvc.perform(multipart("${base(project)}/issue/2/comments").session(login(member)).param("contents", "다시 엽니다").param("withStateTransition", "open"))
            .andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.OPEN, issues.findById(child.id!!).get().state)
        assertEquals(State.CLOSED, issues.findById(parent.id!!).get().state)
    }

    @Test
    fun `임시 첨부는 댓글로 옮겨진다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project)
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h-${System.nanoTime()}", containerType = ResourceType.USER, containerId = member.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = member.loginId))
        post(project, login(member), "comments", "contents" to "첨부", "temporaryUploadFiles" to temp.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val moved = attachments.findById(temp.id!!).get()
        assertEquals(ResourceType.ISSUE_COMMENT, moved.containerType); assertEquals(comments.findAll().single().id, moved.containerId)
    }

    // --- 수정 ---

    @Test
    fun `작성자는 댓글을 고칠 수 있다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val c = comment(i, member, "처음")
        post(project, login(member), "comments/${c.id}", "id" to c.id.toString(), "contents" to "고침").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals("고침", comments.findById(c.id!!).get().contents)
        assertEquals(1, comments.findAll().size)
    }

    @Test
    fun `다른 구성원이 고치면 알림 활동을 발행한다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = join(newUser(), project)
        val c = comment(issue(project), author)
        post(project, login(other), "comments/${c.id}", "id" to c.id.toString(), "contents" to "고침").andExpect(status().isSeeOther)
        val activity = events.stream(IssueCommentActivity::class.java).toList().single()
        assertEquals(false, activity.created); assertEquals(other.id, activity.actor.id)
    }

    @Test
    fun `작성자가 알림 메일을 고르지 않으면 수정 활동을 발행하지 않는다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val c = comment(issue(project), member)
        post(project, login(member), "comments/${c.id}", "id" to c.id.toString(), "contents" to "고침", "notificationMail" to "yes").andExpect(status().isSeeOther)
        assertEquals(1, events.stream(IssueCommentActivity::class.java).count())
        post(project, login(member), "comments/${c.id}", "id" to c.id.toString(), "contents" to "또 고침").andExpect(status().isSeeOther)
        assertEquals(1, events.stream(IssueCommentActivity::class.java).count())
    }

    @Test
    fun `남의 댓글은 고칠 수 없다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = newUser()
        val c = comment(issue(project), author, "원문")
        post(project, login(other), "comments/${c.id}", "id" to c.id.toString(), "contents" to "탈취").andExpect(status().isForbidden)
        entityManager.flush(); entityManager.clear()
        assertEquals("원문", comments.findById(c.id!!).get().contents)
    }

    // --- 삭제 ---

    @Test
    fun `작성자는 댓글을 지울 수 있고 이슈의 댓글 수가 줄어든다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val c = comment(i, member)
        i.numOfComments = 1; issues.save(i); entityManager.flush()
        mockMvc.perform(delete("${base(project)}/issue/1/comment/${c.id}/delete").session(login(member))).andExpect(status().isSeeOther).andExpect(header().string("Location", "${base(project)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        assertNull(comments.findById(c.id!!).orElse(null))
        assertEquals(0, issues.findById(i.id!!).get().numOfComments)
    }

    @Test
    fun `Ajax로 지우면 204와 이슈 주소를 알린다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val c = comment(issue(project), member)
        mockMvc.perform(delete("${base(project)}/issue/1/comment/${c.id}/delete").session(login(member)).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent).andExpect(header().string("Location", "${base(project)}/issue/1"))
    }

    @Test
    fun `남의 댓글은 지울 수 없지만 구성원은 지울 수 있다`() {
        val project = newProject()
        val author = join(newUser(), project); val member = join(newUser(), project); val outsider = newUser()
        val c = comment(issue(project), author)
        mockMvc.perform(delete("${base(project)}/issue/1/comment/${c.id}/delete").session(login(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(delete("${base(project)}/issue/1/comment/${c.id}/delete").session(login(member))).andExpect(status().isSeeOther)
    }

    @Test
    fun `부모 댓글을 지우면 한 줄 댓글은 일반 댓글이 된다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val parent = comment(i, member, "부모"); val child = comment(i, member, "자식", parent = parent)
        mockMvc.perform(delete("${base(project)}/issue/1/comment/${parent.id}/delete").session(login(member))).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertNull(comments.findById(child.id!!).get().parentComment)
    }

    @Test
    fun `없는 댓글은 404이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project)
        mockMvc.perform(delete("${base(project)}/issue/1/comment/999999/delete").session(login(member))).andExpect(status().isNotFound)
    }

    // --- 추천 ---

    @Test
    fun `이슈를 추천하고 취소한다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val session = login(member)
        post(project, session, "vote").andExpect(status().isSeeOther).andExpect(header().string("Location", "${base(project)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf(member.id), issues.findById(i.id!!).get().voters.map { it.id }.toSet())
        post(project, session, "vote").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(1, issues.findById(i.id!!).get().voters.size)
        post(project, session, "unvote").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(0, issues.findById(i.id!!).get().voters.size)
    }

    @Test
    fun `추천은 로그인해야 한다`() {
        val project = newProject()
        issue(project)
        post(project, null, "vote").andExpect(status().is3xxRedirection)
    }

    @Test
    fun `댓글을 추천하고 취소한다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val c = comment(issue(project), newUser())
        val session = login(member)
        post(project, session, "comment/${c.id}/vote").andExpect(status().isSeeOther).andExpect(header().string("Location", "${base(project)}/issue/1#comment-${c.id}"))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf(member.id), comments.findById(c.id!!).get().voters.map { it.id }.toSet())
        post(project, session, "comment/${c.id}/unvote").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(0, comments.findById(c.id!!).get().voters.size)
    }

    @Test
    fun `추천하지 않은 댓글의 취소와 없는 댓글의 추천은 404이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val c = comment(issue(project), newUser())
        val session = login(member)
        post(project, session, "comment/${c.id}/unvote").andExpect(status().isNotFound).andExpect(content().string("issue.comment.error.have.not.voted"))
        post(project, session, "comment/999999/vote").andExpect(status().isNotFound).andExpect(content().string("issue.comment.error.vote"))
        post(project, session, "comment/999999/unvote").andExpect(status().isNotFound).andExpect(content().string("issue.comment.error.unvote"))
        assertNotNull(c.id)
    }
}
