package dev.yona.web.board

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
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.notification.MentionRepository
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.board.PostingComment
import dev.yona.repository.RepositoryService
import dev.yona.support.GitTestSupport
import org.springframework.http.MediaType
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게시글 댓글(등록, 수정, 삭제)과 게시판 JSON API(라벨, 본문과 댓글 갱신)를 검증한다.
 *
 * 원본: `app/controllers/BoardApp.java:260-340`, `app/controllers/api/BoardApi.java:42-63, 128-160, 199-240`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-board"])
class BoardCommentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var comments: PostingCommentRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var mentions: MentionRepository
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(login: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = login ?: "bc$n", name = "bc$n", email = "bc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "co$n", name = "cp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private fun post(project: Project, author: User?, body: String = "본문"): Posting {
        project.lastPostingNumber = 1; projects.save(project)
        return postings.save(
            Posting(
                project = project, number = 1, title = "제목", body = body, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name, notice = false, readme = false,
                numOfComments = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
    }

    private fun comment(post: Posting, author: User, contents: String = "댓글", parent: PostingComment? = null): PostingComment {
        val c = PostingComment(contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, posting = post, projectId = post.project!!.id!!, parentComment = parent)
        entityManager.persist(c); entityManager.flush()
        post.numOfComments = (post.numOfComments ?: 0) + 1; postings.save(post)
        return c
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun send(project: Project, session: MockHttpSession?, path: String, vararg params: Pair<String, String>) =
        mockMvc.perform(multipart("${base(project)}/post/1/$path").apply { session?.let { session(it) } }.apply { params.forEach { (k, v) -> param(k, v) } })

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, session: MockHttpSession?, body: String?) =
        mockMvc.perform(builder.apply { session?.let { session(it) } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 등록 ---

    @Test
    fun `댓글을 쓰면 저장되고 댓글 주소로 돌아간다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val location = send(project, login(member), "comment", "contents" to "첫 댓글").andExpect(status().isSeeOther).andReturn().response.getHeader("Location")!!
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single()
        assertEquals("${base(project)}/post/1#comment-${saved.id}", location)
        assertEquals("첫 댓글", saved.contents); assertEquals(member.loginId, saved.authorLoginId); assertEquals(project.id, saved.projectId)
        val reloaded = postings.findById(p.id!!).get()
        assertEquals(1, reloaded.numOfComments); assertTrue(reloaded.updatedDate!!.after(Date(1_700_000_000_000L)))
    }

    @Test
    fun `저장되는 댓글 수는 두 번째 댓글부터 실제보다 하나 적다 (v1_6 결함, 지우거나 글을 고치면 바로잡힌다)`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val session = login(member)
        val stored = mutableListOf<Int?>()
        repeat(4) { n ->
            send(project, session, "comment", "contents" to "댓글 ${n + 1}").andExpect(status().isSeeOther)
            entityManager.flush(); entityManager.clear()
            stored += postings.findById(p.id!!).get().numOfComments
        }
        assertEquals(listOf<Int?>(1, 1, 2, 3), stored)
        val last = comments.findAll().maxByOrNull { it.id!! }!!
        mockMvc.perform(delete("${base(project)}/post/1/comment/${last.id}/delete").session(session)).andExpect(status().is3xxRedirection)
        entityManager.flush(); entityManager.clear()
        assertEquals(3, postings.findById(p.id!!).get().numOfComments)
    }

    @Test
    fun `이전 내용을 활동에 싣는다`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        val session = login(member)
        send(project, session, "comment", "contents" to "첫째").andExpect(status().isSeeOther)
        send(project, session, "comment", "contents" to "둘째").andExpect(status().isSeeOther)
        val list = events.stream(dev.yona.domain.board.PostingCommentActivity::class.java).toList()
        assertTrue(list[0].previousContents!!.contains("Original posting from @${member.loginId}") && list[0].previousContents!!.endsWith("본문"))
        assertTrue(list[1].previousContents!!.contains("Previous comment from @${member.loginId}") && list[1].previousContents!!.endsWith("첫째"))
        assertTrue(list.all { it.created })
    }

    @Test
    fun `한 줄 댓글은 부모 댓글을 가리킨다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val parent = comment(p, member, "부모")
        send(project, login(member), "comment", "contents" to "답", "parentCommentId" to parent.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(parent.id, comments.findAll().single { it.contents == "답" }.parentComment?.id)
    }

    @Test
    fun `비어 있는 댓글은 400이다`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        send(project, login(member), "comment", "contents" to "").andExpect(status().isBadRequest)
        assertEquals(0, comments.findAll().size)
    }

    @Test
    fun `쓸 수 없는 사용자는 403이다`() {
        val secret = newProject(ProjectScope.PRIVATE); post(secret, null)
        send(secret, login(newUser()), "comment", "contents" to "x").andExpect(status().isForbidden)
        val project = newProject(); post(project, null)
        send(project, null, "comment", "contents" to "x").andExpect(status().isForbidden)
    }

    @Test
    fun `멘션을 기록하고 임시 첨부를 옮긴다`() {
        val project = newProject(); val member = join(newUser(), project); val target = newUser("bc-target")
        post(project, member)
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h-${System.nanoTime()}", containerType = ResourceType.USER, containerId = member.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = member.loginId))
        send(project, login(member), "comment", "contents" to "확인 @bc-target", "temporaryUploadFiles" to temp.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single()
        assertEquals(listOf(target.loginId), mentions.findAll().filter { it.resourceType == ResourceType.NONISSUE_COMMENT && it.resourceId == saved.id.toString() }.map { it.user?.loginId })
        assertEquals(ResourceType.NONISSUE_COMMENT, attachments.findById(temp.id!!).get().containerType)
    }

    @Test
    fun `임시 첨부를 일부 옮기지 못하면 안내 메시지를 한 번 남긴다 (v1_6 moveOnlySelected 불일치의 flash)`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        send(project, login(member), "comment", "contents" to "댓글", "temporaryUploadFiles" to "99999999").andExpect(status().isSeeOther)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("title"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("description"))
    }

    // --- 수정 ---

    @Test
    fun `작성자는 댓글을 고친다`() {
        val project = newProject(); val member = join(newUser(), project)
        val c = comment(post(project, member), member, "처음")
        send(project, login(member), "comment/${c.id}", "id" to c.id.toString(), "contents" to "고침").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals("고침", comments.findById(c.id!!).get().contents); assertEquals(1, comments.findAll().size)
    }

    @Test
    fun `수정 활동은 알림 메일을 고르거나 작성자가 아닐 때만 발행한다`() {
        val project = newProject(); val member = join(newUser(), project); val other = join(newUser(), project)
        val c = comment(post(project, member), member)
        send(project, login(member), "comment/${c.id}", "id" to c.id.toString(), "contents" to "a").andExpect(status().isSeeOther)
        assertEquals(0, events.stream(dev.yona.domain.board.PostingCommentActivity::class.java).count())
        send(project, login(member), "comment/${c.id}", "id" to c.id.toString(), "contents" to "b", "notificationMail" to "yes").andExpect(status().isSeeOther)
        send(project, login(other), "comment/${c.id}", "id" to c.id.toString(), "contents" to "c").andExpect(status().isSeeOther)
        assertEquals(listOf(false, false), events.stream(dev.yona.domain.board.PostingCommentActivity::class.java).map { it.created }.toList())
    }

    @Test
    fun `남의 댓글은 고칠 수 없다`() {
        val project = newProject(); val member = join(newUser(), project)
        val c = comment(post(project, member), member, "원문")
        send(project, login(newUser()), "comment/${c.id}", "id" to c.id.toString(), "contents" to "탈취").andExpect(status().isForbidden)
        assertEquals("원문", comments.findById(c.id!!).get().contents)
    }

    // --- 삭제 ---

    @Test
    fun `댓글을 지우면 글의 댓글 수가 줄고 한 줄 댓글은 일반 댓글이 된다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val parent = comment(p, member, "부모"); val child = comment(p, member, "자식", parent = parent)
        mockMvc.perform(delete("${base(project)}/post/1/comment/${parent.id}/delete").session(login(member))).andExpect(status().isSeeOther).andExpect(header().string("Location", "${base(project)}/post/1"))
        entityManager.flush(); entityManager.clear()
        assertNull(comments.findById(parent.id!!).orElse(null))
        assertNull(comments.findById(child.id!!).get().parentComment)
        assertEquals(1, postings.findById(p.id!!).get().numOfComments)
    }

    @Test
    fun `Ajax로 지우면 204이고 권한이 없으면 403, 없는 댓글은 404이다`() {
        val project = newProject(); val member = join(newUser(), project)
        val c = comment(post(project, member), member)
        mockMvc.perform(delete("${base(project)}/post/1/comment/${c.id}/delete").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(delete("${base(project)}/post/1/comment/999999/delete").session(login(member))).andExpect(status().isNotFound)
        mockMvc.perform(delete("${base(project)}/post/1/comment/${c.id}/delete").session(login(member)).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent).andExpect(header().string("Location", "${base(project)}/post/1"))
    }

    // --- 라벨 API ---

    @Test
    fun `글의 라벨을 바꾼다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        val y = labels.save(IssueLabel(project = project, category = category, name = "y", color = "#000"))
        json(post(api(project, "postlabel/1")), login(member), "[\"${x.id}\",\"${y.id}\"]").andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(project.owner)).andExpect(jsonPath("$.labels").value(2))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf(x.id, y.id), postings.findById(p.id!!).get().labels.map { it.id }.toSet())
        json(post(api(project, "postlabel/1")), login(member), "[]").andExpect(jsonPath("$.labels").value(0))
        entityManager.flush(); entityManager.clear()
        assertTrue(postings.findById(p.id!!).get().labels.isEmpty())
    }

    @Test
    fun `라벨 API의 오류`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        mockMvc.perform(post(api(project, "postlabel/1")).session(login(member)).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        json(post(api(project, "postlabel/1")), login(newUser()), "[]").andExpect(status().isForbidden)
        json(post(api(project, "postlabel/9")), login(member), "[]").andExpect(status().isNotFound)
    }

    // --- 본문 갱신 ---

    @Test
    fun `본문을 갱신한다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member, "- [ ] 할 일")
        json(patch(api(project, "posts/1/content")), login(member), """{"content":"- [x] 할 일","original":"- [ ] 할 일"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("- [x] 할 일")).andExpect(jsonPath("$.type").value("BOARD_POST")).andExpect(jsonPath("$.number").value(1)).andExpect(jsonPath("$.owner").value(project.owner))
        entityManager.flush(); entityManager.clear()
        assertEquals("- [x] 할 일", postings.findById(p.id!!).get().body)
    }

    @Test
    fun `본문 갱신의 충돌과 오류`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member, "현재")
        json(patch(api(project, "posts/1/content")), login(member), """{"content":"내 것","original":"옛날"}""").andExpect(status().isConflict).andExpect(jsonPath("$.storedContent").value("현재"))
        json(patch(api(project, "posts/1/content")), null, """{"content":"a","original":"b"}""").andExpect(status().isUnauthorized)
        json(patch(api(project, "posts/1/content")), login(newUser()), """{"content":"a","original":"현재"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Forbidden request"))
        mockMvc.perform(patch(api(project, "posts/1/content")).session(login(member)).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    // --- 댓글 갱신 ---

    @Test
    fun `댓글 내용을 갱신한다`() {
        val project = newProject(); val member = join(newUser(), project)
        val c = comment(post(project, member), member, "처음")
        json(patch("${base(project)}/post/1/comment/${c.id}"), login(member), """{"content":"고침","original":"처음"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.id").value(c.id)).andExpect(jsonPath("$.result.contents").value("고침")).andExpect(jsonPath("$.result.author.loginId").value(member.loginId))
        entityManager.flush(); entityManager.clear()
        assertEquals("고침", comments.findById(c.id!!).get().contents)
    }

    @Test
    fun `댓글 갱신은 권한을 충돌보다 먼저 본다`() {
        val project = newProject(); val member = join(newUser(), project)
        val c = comment(post(project, member), member, "현재")
        val url = "${base(project)}/post/1/comment/${c.id}"
        json(patch(url), login(newUser()), """{"content":"x","original":"옛날"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Forbidden request"))
        json(patch(url), login(member), """{"content":"x","original":"옛날"}""").andExpect(status().isConflict).andExpect(jsonPath("$.storedContent").value("현재"))
        json(patch(url), null, """{"content":"x","original":"현재"}""").andExpect(status().isUnauthorized)
    }
}
