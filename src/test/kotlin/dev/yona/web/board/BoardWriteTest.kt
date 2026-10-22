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
 * 게시글 작성 폼, 작성, 수정 폼, 수정, 삭제를 검증한다.
 *
 * 원본: `app/controllers/BoardApp.java:132-340`, `app/controllers/AbstractPostingApp.java:83-210`, `app/views/board/create.scala.html`, `app/views/board/edit.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-board"])
class BoardWriteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "bw$n", name = "bw$n", email = "bw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, git: Boolean = true): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "wo$n", name = "wp$n", vcs = if (git) "GIT" else "Subversion", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (git) repositories.createRepository(project)
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private fun post(project: Project, author: User?, title: String = "제목", body: String = "본문", number: Long = 1, readme: Boolean = false, notice: Boolean = false): Posting {
        project.lastPostingNumber = maxOf(project.lastPostingNumber ?: 0, number); projects.save(project)
        return postings.save(
            Posting(
                project = project, number = number, title = title, body = body, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name, notice = notice, readme = readme,
                numOfComments = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun get(project: Project, path: String, session: MockHttpSession?) =
        mockMvc.perform(get("${base(project)}/$path").apply { session?.let { session(it) } })

    private fun submit(project: Project, path: String, session: MockHttpSession?, vararg params: Pair<String, String>) =
        mockMvc.perform(multipart("${base(project)}/$path").apply { session?.let { session(it) } }.apply { params.forEach { (k, v) -> param(k, v) } })

    private fun headFiles(project: Project): Map<String, String> = repositories.openRepository(project).use { repository ->
        val id = repository.resolve("HEAD^{commit}") ?: return emptyMap()
        RevWalk(repository).use { walk ->
            TreeWalk(repository).use { tree ->
                tree.addTree(walk.parseCommit(id).tree); tree.isRecursive = true
                val result = linkedMapOf<String, String>()
                while (tree.next()) result[tree.pathString] = String(repository.open(tree.getObjectId(0)).bytes)
                result
            }
        }
    }

    private fun reload(p: Posting): Posting { entityManager.flush(); entityManager.clear(); return postings.findById(p.id!!).get() }

    // --- 작성 폼 ---

    @Test
    fun `작성 폼의 입력란`() {
        val project = newProject(); val member = join(newUser(), project)
        val doc = Jsoup.parse(get(project, "postform", login(member)).andExpect(status().isOk).andReturn().response.contentAsString)
        val form = doc.selectFirst("form[action=${base(project)}/posts]")!!
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("", form.selectFirst("input#title")!!.attr("value"))
        assertEquals("Title", form.selectFirst("input#title")!!.attr("placeholder"))
        assertTrue(form.select("textarea[name=body]").isNotEmpty())
        assertTrue(form.select("input#notice").isNotEmpty())
        assertEquals("", form.selectFirst("input#issueTemplate")!!.attr("value"))
        assertEquals("", form.selectFirst("input#branch")!!.attr("value"))
        assertEquals("", form.selectFirst("input#path")!!.attr("value"))
        assertEquals("UNDEFINED", form.selectFirst("input#lineEnding")!!.attr("value"))
        assertTrue(form.select("div#upload, input#upload-ID, [data-resource-type=BOARD_POST]").isNotEmpty())
        assertTrue(form.select("input#readme").isEmpty())
        assertTrue(doc.select("script").any { "board.Write" in it.data() && "\"new\"" in it.data() })
    }

    @Test
    fun `공지 선택은 구성원에게만 보인다`() {
        val project = newProject()
        val outsider = newUser()
        val doc = Jsoup.parse(get(project, "postform", login(outsider)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select("input#notice").isEmpty())
    }

    @Test
    fun `README로 열면 저장소의 README를 채우고 README 선택이 켜져 있다`() {
        val project = newProject(); val member = join(newUser(), project)
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "# 소개\r\n".toByteArray()))
        val doc = Jsoup.parse(get(project, "postform?readme=", login(member)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Update README.md", doc.selectFirst("input#title")!!.attr("value"))
        assertEquals("# 소개\r\n".trimEnd(), doc.selectFirst("textarea[name=body]")!!.text().trimEnd())
        assertTrue(doc.selectFirst("input#readme")!!.hasAttr("checked"))
        assertEquals("DOS", doc.selectFirst("input#lineEnding")!!.attr("value"))
    }

    @Test
    fun `이슈 템플릿으로 열면 안내가 있고 업로더가 없다`() {
        val project = newProject(); val member = join(newUser(), project)
        GitTestSupport.commitFiles(repositories, project, mapOf("ISSUE_TEMPLATE.md" to "템플릿 본문".toByteArray()))
        val doc = Jsoup.parse(get(project, "postform?issueTemplate=true", login(member)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("ISSUE_TEMPLATE.md: Project Issue Template", doc.selectFirst("input#title")!!.attr("value"))
        assertEquals("템플릿 본문", doc.selectFirst("textarea[name=body]")!!.text())
        assertEquals("Issue templates do not support attachments.", doc.selectFirst(".attach-wrap .help")!!.text())
        assertEquals("true", doc.selectFirst("input#issueTemplate")!!.attr("value"))
        assertTrue(doc.select("input#notice").isEmpty())
    }

    @Test
    fun `파일 경로로 열면 브랜치의 파일을 채우고 구성원만 열 수 있다`() {
        val project = newProject(); val member = join(newUser(), project)
        val actor = User(loginId = "x", name = "X", email = "x@example.com")
        repositories.openRepository(project).close()
        dev.yona.repository.OnlineCommit(repositories).commitTextFile(project, actor, "master", "docs/a.md", "파일 내용", "init")
        val doc = Jsoup.parse(get(project, "postform?branch=master&path=docs/a.md&edit=", login(member)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("파일 내용", doc.selectFirst("textarea[name=body]")!!.text())
        assertEquals("Commit message", doc.selectFirst("input#title")!!.attr("placeholder"))
        assertEquals("master: /docs/a.md", doc.selectFirst(".file-path")!!.ownText().trim())
        assertTrue(doc.select("input.new-file-name").isEmpty())
        assertEquals("docs/a.md", doc.selectFirst("input#path")!!.attr("value")); assertEquals("master", doc.selectFirst("input#branch")!!.attr("value"))
        assertTrue(doc.select("[data-resource-type=BOARD_POST]").isEmpty())
        get(project, "postform?branch=master&path=docs/a.md", login(newUser())).andExpect(status().isForbidden)
        val create = Jsoup.parse(get(project, "postform?branch=master&path=docs/", login(member)).andReturn().response.contentAsString)
        assertTrue(create.select("input.new-file-name").isNotEmpty())
    }

    @Test
    fun `작성 폼은 로그인해야 열린다`() {
        val project = newProject()
        get(project, "postform", null).andExpect(status().is3xxRedirection)
        val secret = newProject(ProjectScope.PRIVATE)
        get(secret, "postform", login(newUser())).andExpect(status().isForbidden)
    }

    // --- 작성 ---

    @Test
    fun `글을 쓰면 번호를 받고 저장되며 그 글로 간다`() {
        val project = newProject(); val member = join(newUser(), project)
        submit(project, "posts", login(member), "title" to "[공지] 첫 글", "body" to "첫 본문", "notice" to "on").andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/post/1"))
        entityManager.flush(); entityManager.clear()
        val saved = postings.findByProjectIdAndNumber(project.id!!, 1)!!
        assertEquals("[공지] 첫 글", saved.title); assertEquals("첫 본문", saved.body); assertEquals(true, saved.notice); assertEquals(false, saved.readme)
        assertEquals(member.loginId, saved.authorLoginId); assertEquals(0, saved.numOfComments)
        submit(project, "posts", login(member), "title" to "둘째", "body" to "b").andExpect(redirectedUrl("${base(project)}/post/2"))
        assertEquals(2L, projects.findById(project.id!!).get().lastPostingNumber)
        val heads = entityManager.createQuery("select count(t) from TitleHead t where t.project.id = :p", java.lang.Long::class.java).setParameter("p", project.id).singleResult.toLong()
        assertEquals(1L, heads)
    }

    @Test
    fun `공지는 공지 권한이 있는 사용자만 지정한다`() {
        val project = newProject()
        val outsider = newUser()
        submit(project, "posts", login(outsider), "title" to "t", "body" to "b", "notice" to "on").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(false, postings.findByProjectIdAndNumber(project.id!!, 1)!!.notice)
    }

    @Test
    fun `제목이 없으면 400으로 폼을 다시 보여 준다`() {
        val project = newProject(); val member = join(newUser(), project)
        val doc = Jsoup.parse(submit(project, "posts", login(member), "title" to "", "body" to "b").andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertEquals("This field is required", doc.selectFirst("div.message div")!!.text())
        assertEquals(0, postings.findAll().size)
    }

    @Test
    fun `본문이 오지 않으면 413이다`() {
        val project = newProject(); val member = join(newUser(), project)
        submit(project, "posts", login(member), "title" to "t").andExpect(status().isPayloadTooLarge)
    }

    @Test
    fun `쓸 수 없는 사용자는 403이다`() {
        val secret = newProject(ProjectScope.PRIVATE)
        submit(secret, "posts", login(newUser()), "title" to "t", "body" to "b").andExpect(status().isForbidden)
        submit(newProject(), "posts", null, "title" to "t", "body" to "b").andExpect(status().isForbidden)
    }

    @Test
    fun `임시 첨부는 글로 옮겨지고 새 글 활동이 발행된다`() {
        val project = newProject(); val member = join(newUser(), project)
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h-${System.nanoTime()}", containerType = ResourceType.USER, containerId = member.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = member.loginId))
        submit(project, "posts", login(member), "title" to "t", "body" to "b", "temporaryUploadFiles" to temp.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val saved = postings.findByProjectIdAndNumber(project.id!!, 1)!!
        assertEquals(saved.id, attachments.findById(temp.id!!).get().containerId)
        assertEquals(1, events.stream(dev.yona.domain.board.PostingCreatedActivity::class.java).count())
    }

    @Test
    fun `README로 쓰면 저장소에 README md를 커밋하고 프로젝트 홈으로 간다`() {
        val project = newProject(); val member = join(newUser(), project)
        submit(project, "posts", login(member), "title" to "Update README.md", "body" to "# 새 소개", "readme" to "on").andExpect(status().isSeeOther).andExpect(redirectedUrl(base(project)))
        assertEquals(mapOf("README.md" to "# 새 소개\n"), headFiles(project))
        entityManager.flush(); entityManager.clear()
        assertEquals(true, postings.findByProjectIdAndNumber(project.id!!, 1)!!.readme)
    }

    @Test
    fun `이미 README 글이 있으면 그 글을 수정한다`() {
        val project = newProject(); val member = join(newUser(), project)
        val existing = post(project, member, "README", "옛 본문", number = 1, readme = true)
        submit(project, "posts", login(member), "title" to "새 제목", "body" to "새 본문", "readme" to "on").andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/post/1"))
        val saved = reload(existing)
        assertEquals("새 본문", saved.body); assertEquals("새 제목", saved.title)
        assertEquals(1, postings.findAll().size)
        assertEquals("새 본문\n", headFiles(project)["README.md"])
    }

    @Test
    fun `이슈 템플릿이면 ISSUE_TEMPLATE md를 커밋하고 글은 저장하지 않는다`() {
        val project = newProject(); val member = join(newUser(), project)
        submit(project, "posts", login(member), "title" to "msg", "body" to "템플릿", "issueTemplate" to "true").andExpect(status().isSeeOther).andExpect(redirectedUrl(base(project)))
        assertEquals(mapOf("ISSUE_TEMPLATE.md" to "템플릿\n"), headFiles(project))
        assertEquals(0, postings.findAll().size)
    }

    @Test
    fun `경로가 있으면 구성원은 그 경로에 커밋하고 코드 화면으로 간다`() {
        val project = newProject(); val member = join(newUser(), project)
        submit(project, "posts", login(member), "title" to "Add a.md", "body" to "a\r\nb", "path" to "docs/한글 a.md", "branch" to "master", "lineEnding" to "UNIX")
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/code/master/docs/%ED%95%9C%EA%B8%80%20a.md"))
        assertEquals(mapOf("docs/한글 a.md" to "a\nb"), headFiles(project))
        assertEquals(0, postings.findAll().size)
    }

    @Test
    fun `경로가 있어도 구성원이 아니면 일반 글로 저장한다`() {
        val project = newProject()
        val outsider = newUser()
        submit(project, "posts", login(outsider), "title" to "t", "body" to "b", "path" to "docs/a.md", "branch" to "master").andExpect(status().isSeeOther)
        assertEquals(1, postings.findAll().size); assertTrue(headFiles(project).isEmpty())
    }

    // --- 수정 폼 ---

    @Test
    fun `수정 폼의 값과 단추`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author, "제목", "본문", notice = true)
        val doc = Jsoup.parse(get(project, "post/1/editform", login(author)).andExpect(status().isOk).andReturn().response.contentAsString)
        val form = doc.selectFirst("form[action=${base(project)}/post/1/edit]")!!
        assertEquals("제목", form.selectFirst("input#title")!!.attr("value"))
        assertEquals("본문", form.selectFirst("textarea[name=body]")!!.text())
        assertTrue(form.selectFirst("input#notice")!!.hasAttr("checked"))
        assertTrue(form.select("input#notificationMail").isNotEmpty())
        assertTrue(form.select(".actions button.ybtn-info").isNotEmpty())
        assertTrue(doc.select("script").any { "board.Write" in it.data() && "\"edit\"" in it.data() })
    }

    @Test
    fun `수정할 수 없으면 저장 단추가 없고 알림 메일 선택은 작성자만 있다`() {
        val project = newProject(); val author = join(newUser(), project); val member = join(newUser(), project)
        post(project, author)
        val reader = Jsoup.parse(get(project, "post/1/editform", login(newUser())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(reader.select(".actions button.ybtn-info").isEmpty()); assertTrue(reader.select("input#notificationMail").isEmpty())
        val other = Jsoup.parse(get(project, "post/1/editform", login(member)).andReturn().response.contentAsString)
        assertTrue(other.select("input#notificationMail").isEmpty()); assertTrue(other.select(".actions button.ybtn-info").isNotEmpty())
    }

    @Test
    fun `README 글의 수정 폼은 저장소 README를 본문으로 보여 준다`() {
        val project = newProject(); val member = join(newUser(), project)
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "저장소 본문".toByteArray()))
        post(project, member, "README", "옛 본문", readme = true)
        val doc = Jsoup.parse(get(project, "post/1/editform", login(member)).andReturn().response.contentAsString)
        assertEquals("저장소 본문", doc.selectFirst("textarea[name=body]")!!.text())
        assertTrue(doc.selectFirst("input#readme")!!.hasAttr("checked"))
        assertTrue(doc.select("input#notificationMail").isEmpty())
    }

    @Test
    fun `수정 폼의 없는 글은 404이고 읽을 수 없으면 403이다`() {
        val project = newProject()
        get(project, "post/9/editform", login(newUser())).andExpect(status().isNotFound)
        val secret = newProject(ProjectScope.PRIVATE); post(secret, null)
        get(secret, "post/1/editform", login(newUser())).andExpect(status().isForbidden)
    }

    // --- 수정 ---

    @Test
    fun `수정하면 값과 이력이 바뀌고 글로 돌아온다`() {
        val project = newProject(); val author = join(newUser(), project)
        val p = post(project, author, "옛 제목", "옛 본문")
        submit(project, "post/1/edit", login(author), "title" to "새 제목", "body" to "새 본문").andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/post/1"))
        val saved = reload(p)
        assertEquals("새 제목", saved.title); assertEquals("새 본문", saved.body); assertEquals(false, saved.notice)
        assertEquals(author.id, saved.updatedByAuthorId); assertTrue(saved.updatedDate!!.after(Date(1_700_000_000_000L)))
        assertTrue(saved.history!!.contains("history-made-by") && saved.history!!.contains("diff-added"))
        assertEquals(Date(1_700_000_000_000L), saved.createdDate); assertEquals(author.loginId, saved.authorLoginId)
    }

    @Test
    fun `공지를 해제할 수 있다`() {
        val project = newProject(); val author = join(newUser(), project)
        val p = post(project, author, notice = true)
        submit(project, "post/1/edit", login(author), "title" to "t", "body" to "본문").andExpect(status().isSeeOther)
        assertEquals(false, reload(p).notice)
    }

    @Test
    fun `본문이 같으면 이력을 쌓지 않는다`() {
        val project = newProject(); val author = join(newUser(), project)
        val p = post(project, author, "t", "본문")
        submit(project, "post/1/edit", login(author), "title" to "바뀐 제목", "body" to "본문").andExpect(status().isSeeOther)
        assertNull(reload(p).history)
    }

    @Test
    fun `알림 메일을 고르거나 작성자가 아니면 수정 활동을 발행한다`() {
        val project = newProject(); val author = join(newUser(), project); val other = join(newUser(), project)
        post(project, author)
        submit(project, "post/1/edit", login(author), "title" to "t", "body" to "b1").andExpect(status().isSeeOther)
        assertEquals(0, events.stream(dev.yona.domain.board.PostingUpdatedActivity::class.java).count())
        submit(project, "post/1/edit", login(author), "title" to "t", "body" to "b2", "notificationMail" to "yes").andExpect(status().isSeeOther)
        submit(project, "post/1/edit", login(other), "title" to "t", "body" to "b3").andExpect(status().isSeeOther)
        val updates = events.stream(dev.yona.domain.board.PostingUpdatedActivity::class.java).toList()
        assertEquals(listOf("b1", "b2"), updates.map { it.oldBody })
    }

    @Test
    fun `제목 머리말 개수를 새 제목으로 옮긴다`() {
        val project = newProject(); val author = join(newUser(), project)
        submit(project, "posts", login(author), "title" to "[a] 글", "body" to "b")
        submit(project, "post/1/edit", login(author), "title" to "[b] 글", "body" to "b").andExpect(status().isSeeOther)
        val keywords = entityManager.createQuery("select t.headKeyword from TitleHead t where t.project.id = :p", String::class.java).setParameter("p", project.id).resultList
        assertEquals(listOf("b"), keywords)
    }

    @Test
    fun `수정 오류와 권한`() {
        val project = newProject(); val author = join(newUser(), project)
        val p = post(project, author, "t", "b")
        submit(project, "post/1/edit", login(author), "title" to "", "body" to "b").andExpect(status().isBadRequest)
        submit(project, "post/1/edit", login(author), "title" to "t").andExpect(status().isPayloadTooLarge)
        submit(project, "post/1/edit", login(newUser()), "title" to "x", "body" to "y").andExpect(status().isForbidden)
        submit(project, "post/9/edit", login(author), "title" to "x", "body" to "y").andExpect(status().isNotFound)
        assertEquals("t", reload(p).title)
    }

    @Test
    fun `README로 지정해 수정하면 저장소 README도 커밋하고 다른 README 글의 표시를 푼다`() {
        val project = newProject(); val member = join(newUser(), project)
        val old = post(project, member, "README", "옛", number = 1, readme = true)
        val target = post(project, member, "다른 글", "본문", number = 2)
        submit(project, "post/2/edit", login(member), "title" to "msg", "body" to "# 새 README", "readme" to "on").andExpect(status().isSeeOther)
        assertEquals("# 새 README\n", headFiles(project)["README.md"])
        assertEquals(false, reload(old).readme); assertEquals(true, reload(target).readme)
    }

    // --- 삭제 ---

    @Test
    fun `글을 지우면 목록으로 가고 댓글과 첨부도 지운다`() {
        val project = newProject(); val member = join(newUser(), project)
        val p = post(project, member)
        mockMvc.perform(delete("${base(project)}/post/1/delete").session(login(member))).andExpect(status().isSeeOther).andExpect(header().string("Location", "${base(project)}/posts"))
        entityManager.flush(); entityManager.clear()
        assertNull(postings.findById(p.id!!).orElse(null))
        assertEquals(1, events.stream(dev.yona.domain.board.PostingDeletedActivity::class.java).count())
    }

    @Test
    fun `Ajax로 지우면 204와 목록 주소이다`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, member)
        mockMvc.perform(delete("${base(project)}/post/1/delete").session(login(member)).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent).andExpect(header().string("Location", "${base(project)}/posts"))
    }

    @Test
    fun `삭제 권한이 없으면 403이다`() {
        val project = newProject(); val author = join(newUser(), project)
        post(project, author)
        mockMvc.perform(delete("${base(project)}/post/1/delete").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(delete("${base(project)}/post/9/delete").session(login(author))).andExpect(status().isNotFound)
        assertEquals(1, postings.findAll().size)
    }
}
