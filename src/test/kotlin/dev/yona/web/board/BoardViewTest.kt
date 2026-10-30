package dev.yona.web.board

import dev.yona.support.selectFirst
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
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
 * 게시글 보기(`GET /:user/:project/post/:number`)를 검증한다.
 *
 * 원본: `app/controllers/BoardApp.java:151-182`, `app/views/board/view.scala.html`, `app/views/board/partial_comments.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-board"])
class BoardViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var avatars: dev.yona.security.AvatarService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "bv$n", name = "bv$n", email = "bv$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "vo$n", name = "vp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private fun post(project: Project, author: User?, title: String = "제목", body: String = "본문", number: Long = 1, history: String? = null, readme: Boolean = false, labelSet: Set<IssueLabel> = emptySet()): Posting {
        project.lastPostingNumber = maxOf(project.lastPostingNumber ?: 0, number); projects.save(project)
        return postings.save(
            Posting(
                project = project, number = number, title = title, body = body, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name, notice = false, readme = readme,
                numOfComments = 0, history = history, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L), labels = labelSet.toMutableSet()
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

    private fun doc(project: Project, number: Long = 1, session: MockHttpSession? = null): org.jsoup.nodes.Document =
        Jsoup.parse(mockMvc.perform(get("${base(project)}/post/$number").apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `머리와 작성자와 본문을 보여 준다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author, "[긴급] 제목", "**굵게**")
        val doc = doc(project)
        assertTrue(doc.title().contains("제목"), doc.title())
        assertEquals("#1", doc.selectFirst(".board-header .board-id")!!.text())
        assertTrue(doc.selectFirst(".board-header .title")!!.text().contains("[긴급] 제목"))
        assertEquals("/${author.loginId}", doc.selectFirst(".author-info a.usf-group")!!.attr("href"))
        assertEquals(author.name, doc.selectFirst(".author-info .name")!!.text())
        assertEquals("@${author.loginId}", doc.selectFirst(".author-info .loginid")!!.text().trim())
        assertEquals("굵게", doc.selectFirst(".markdown-wrap strong")!!.text())
        assertEquals("false", doc.selectFirst(".markdown-wrap")!!.attr("data-allowed-update"))
    }

    @Test
    fun `내 이름을 찾는 스크립트가 올바른 자바스크립트로 그려진다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author)
        val script = doc(project, session = login(author)).select("script").map { it.data() }.first { ".comment-body:contains(" in it }
        // v1.6 `$(".comment-body:contains('@getPureNameOnly')")`와 같이 이름을 그 자리에 찍는다
        assertTrue(script.contains("""$(".comment-body:contains('${author.name}')")"""), script.lines().first { "comment-body:contains" in it })
        assertTrue(!script.contains("viewerName"))
    }

    @Test
    fun `작성자가 없으면 작성자 없음이다`() {
        val project = newProject()
        post(project, null)
        assertEquals("No author", doc(project).selectFirst(".author-info .name")!!.text())
    }

    @Test
    fun `본문이 비면 빈 내용 표시이다`() {
        val project = newProject()
        post(project, null, body = "")
        val doc = doc(project)
        assertTrue(doc.select(".content.empty-content").isNotEmpty())
        assertTrue(doc.select("#post-body-1").isEmpty())
    }

    @Test
    fun `체크리스트 갱신 폼은 본문을 숨겨 둔다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author, body = "- [ ] a")
        val doc = doc(project, session = login(author))
        assertEquals("/${project.owner}/${project.name}/post/1/content", doc.selectFirst("#post-1 form")!!.attr("action"))
        assertEquals("- [ ] a", doc.selectFirst("#post-1 textarea")!!.text())
        assertEquals("true", doc.selectFirst("#post-body-1 .markdown-wrap")!!.attr("data-allowed-update"))
        assertEquals("false", doc(project, session = login(newUser())).selectFirst("#post-body-1 .markdown-wrap")!!.attr("data-allowed-update"))
    }

    @Test
    fun `변경 이력은 익명에게는 로그인 링크이고 로그인한 사용자에게는 대화상자이다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author, history = "<div class='history-made-by'>x</div>")
        val anonymous = doc(project)
        assertTrue(anonymous.selectFirst(".posting-history a")!!.attr("href").startsWith("/users/loginform?redirectUrl=${base(project)}/post/1"))
        assertTrue(anonymous.select("#-yona-posting-history").isEmpty())
        val member = doc(project, session = login(author))
        assertEquals("#-yona-posting-history", member.selectFirst(".posting-history a")!!.attr("href"))
        assertEquals("x", member.selectFirst("#-yona-posting-history .history-made-by")!!.text())
        post(project, author, number = 2)
        assertTrue(doc(project, 2).select(".posting-history").isEmpty())
    }

    @Test
    fun `수정 권한이 있으면 편집과 삭제 단추가 있고 없으면 원본 보기이다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author)
        val owner = doc(project, session = login(author))
        assertEquals("window.location = '${base(project)}/post/1/editform'", owner.selectFirst(".board-actrow button[title=Edit]")!!.attr("onclick"))
        assertTrue(owner.select("a[href=#deleteConfirm]").isNotEmpty())
        assertEquals("${base(project)}/post/1/delete", owner.selectFirst("#deleteConfirm button[data-request-method=delete]")!!.attr("data-request-uri"))
        val other = doc(project, session = login(newUser()))
        assertEquals("${base(project)}/post/1/editform", other.selectFirst(".board-actrow a[href$=editform]")!!.attr("href"))
        assertTrue(other.select("a[href=#deleteConfirm]").isEmpty())
    }

    @Test
    fun `구독 단추는 구독 여부에 따라 바뀐다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        val session = login(member)
        assertEquals("Watch", doc(project, session = session).selectFirst("#watch-button")!!.text().trim())
        mockMvc.perform(post("/watch").session(session).queryParam("resource.type", "board_post").queryParam("resource.id", p.id.toString())).andExpect(status().isOk)
        val watching = doc(project, session = session).selectFirst("#watch-button")!!
        assertEquals("Stop watching", watching.text().trim()); assertEquals("true", watching.attr("data-watching")); assertTrue(watching.hasClass("ybtn-watching"))
        assertTrue(doc(project).select("#watch-button").isEmpty())
    }

    @Test
    fun `댓글을 보여 주고 작성자 댓글 표시와 한 줄 댓글을 묶는다`() {
        val project = newProject(); val author = join(newUser(), project); val other = join(newUser(), project)
        val p = post(project, author)
        val parent = comment(p, other, "부모 **댓글**")
        comment(p, author, "한 줄", parent = parent)
        val doc = doc(project, session = login(author))
        assertEquals("2", doc.selectFirst(".comment-header .num")!!.text())
        val item = doc.selectFirst("ul.comments > li.comment")!!
        assertEquals("comment-${parent.id}", item.id())
        assertFalse(item.hasClass("author"))
        assertEquals("댓글", item.selectFirst(".comment-body strong")!!.text())
        assertEquals("한 줄", doc.selectFirst(".one-line-comment .contents")!!.text().substringBefore(" - ").trim())
        assertEquals(1, doc.select("ul.comments > li.comment").size)
        assertTrue(doc.select("button[data-toggle=comment-edit]").isNotEmpty())
        assertTrue(doc.select("#comment-${parent.id} button[data-toggle=comment-delete]").isEmpty(), "남의 댓글에는 구성원에게도 삭제 단추가 없다 (X-36, D-48)")
        assertEquals("${base(project)}/post/1/comment/${parent.id}/delete", doc(project, session = login(other)).selectFirst("#comment-${parent.id} button[data-toggle=comment-delete]")!!.attr("data-request-uri"))
    }

    @Test
    fun `작성자 아바타는 32 크기이고 댓글 아바타는 64 크기이다 (v1_6 avatarUrl 인자)`() {
        val before = avatars.gravatarReachable
        avatars.gravatarReachable = true
        try {
            val project = newProject(); val author = join(newUser(), project)
            val p = post(project, author)
            comment(p, author, "댓글")
            val doc = doc(project)
            assertTrue(doc.selectFirst(".author-info .avatar-wrap img")!!.attr("src").contains("s=32&"), doc.select(".author-info").html())
            assertTrue(doc.selectFirst("ul.comments .comment-avatar img")!!.attr("src").contains("s=64&"), doc.select("ul.comments").html())
        } finally { avatars.gravatarReachable = before }
    }

    @Test
    fun `대댓글 폼은 게시글 댓글 등록 주소로 보내고 작성자 이름은 댓글에 저장된 이름이다`() {
        // v1.6: BoardApp.newComment는 POST /:user/:project/post/:number/comment (이슈와 달리 단수)이다
        val project = newProject(); val author = join(newUser(), project); val other = join(newUser(), project)
        val p = post(project, author)
        val saved = other.name
        comment(p, other, "부모")
        other.name = "바뀐 이름"; users.save(other)
        val doc = doc(project, session = login(author))
        assertEquals("${base(project)}/post/1/comment", doc.selectFirst(".child-comment-input-form form")!!.attr("action"))
        assertEquals(saved, doc.selectFirst("ul.comments > li.comment .comment_author strong")!!.text())
    }

    @Test
    fun `댓글 입력 폼은 쓸 수 있는 사용자에게만 열린다`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        assertEquals("${base(project)}/post/1/comment", doc(project, session = login(member)).selectFirst("form#comment-form")!!.attr("action"))
        val anonymous = doc(project)
        assertTrue(anonymous.select("form#comment-form").isEmpty())
        assertTrue(anonymous.select("textarea.comment.disabled").isNotEmpty())
    }

    @Test
    fun `라벨은 수정할 수 있으면 선택기이고 아니면 목록이다`() {
        val project = newProject(); val author = join(newUser(), project)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        labels.save(IssueLabel(project = project, category = category, name = "y", color = "#000"))
        post(project, author, labelSet = setOf(x))
        val editor = doc(project, session = login(author))
        assertTrue(editor.selectFirst(".board-labels select#labelIds option[value=${x.id}]")!!.hasAttr("selected"))
        val viewer = doc(project, session = login(newUser()))
        assertTrue(viewer.select(".board-labels select#labelIds").isEmpty())
        assertEquals("x", viewer.selectFirst(".board-labels .badge")!!.text())
        val none = newProject(); post(none, null)
        assertTrue(doc(none).select(".board-labels select, .board-labels .issue-label").isEmpty())
    }

    @Test
    fun `쓰기 단추는 게시판 메뉴가 켜져 있을 때만 있다`() {
        val project = newProject()
        post(project, null)
        assertEquals("${base(project)}/postform", doc(project).selectFirst(".board-labels a.ybtn-success")!!.attr("href"))
        menuSettings.findAll().filter { it.project?.id == project.id }.forEach { it.board = false; menuSettings.save(it) }
        entityManager.flush(); entityManager.clear()
        assertTrue(doc(project).select(".board-labels a.ybtn-success").isEmpty())
    }

    @Test
    fun `README 글은 저장소의 README를 본문으로 보여 준다`() {
        val project = newProject()
        repositories.createRepository(project)
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "# 저장소 안내".toByteArray()))
        post(project, null, "README", "옛 본문", readme = true)
        assertTrue(doc(project).selectFirst(".markdown-wrap h1")!!.text().startsWith("저장소 안내"))
        val empty = newProject(); repositories.createRepository(empty); post(empty, null, "README", "옛 본문", readme = true)
        assertTrue(doc(empty).select(".content.empty-content").isNotEmpty())
    }

    @Test
    fun `JSON을 받으면 JSON으로 답한다`() {
        val project = newProject(); val author = join(newUser(), project)
        val p = post(project, author, "제목", "본문")
        comment(p, author, "댓글 본문")
        mockMvc.perform(get("${base(project)}/post/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("제목")).andExpect(jsonPath("$.type").value("BOARD_POST")).andExpect(jsonPath("$.author").value(author.loginId))
            .andExpect(jsonPath("$.authorName").value(author.name)).andExpect(jsonPath("$.created_at").value(1_700_000_000_000L)).andExpect(jsonPath("$.body").value("본문"))
            .andExpect(jsonPath("$.comments[0].body").value("댓글 본문")).andExpect(jsonPath("$.comments[0].type").value("NONISSUE_COMMENT")).andExpect(jsonPath("$.comments[0].authorId").value(author.loginId))
            .andExpect(jsonPath("$.attachments").isArray)
    }

    @Test
    fun `없는 글은 404이고 읽을 수 없으면 403이다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/post/9")).andExpect(status().isNotFound)
        val secret = newProject(ProjectScope.PRIVATE); post(secret, null)
        mockMvc.perform(get("${base(secret)}/post/1").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `로그인한 사용자가 보면 최근 본 글에 남는다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        doc(project, session = login(member))
        val recent = entityManager.createQuery("select r from RecentIssue r where r.user.id = :u", dev.yona.domain.misc.RecentIssue::class.java).setParameter("u", member.id).resultList
        assertEquals(listOf(p.id), recent.map { it.postingId }); assertEquals("${base(project)}/post/1", recent.single().url)
    }
}
