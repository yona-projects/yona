package dev.yona.web.code

import dev.yona.support.selectFirst
import dev.yona.support.wholeText
import dev.yona.domain.comment.CommitComment
import dev.yona.domain.comment.CommitCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.svn.SvnFixtures
import dev.yona.repository.svn.SvnRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Subversion 프로젝트의 커밋 보기, 커밋 코멘트, 두 리비전 비교를 검증한다. 원본: `CodeHistoryApp.show`, `newSVNComment`, `deleteComment`,
 * `CompareApp.compare`와 `app/views/code/svnDiff.scala.html`, `compare_svn.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-commit"])
class SvnCommitViewWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var svn: SvnRepositories
    @Autowired private lateinit var commitComments: CommitCommentRepository
    @Autowired private lateinit var events: NotificationEventRepository
    @Autowired private lateinit var watches: WatchRepository

    private var sequence = 0

    private fun user(loginId: String? = null): User {
        val n = ++sequence
        val id = loginId ?: "scv$n"
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = id, name = "이름$id", email = "$id@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "scvo$n", name = "scvp$n", vcs = "Subversion", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun member(p: Project, role: RoleType = RoleType.MEMBER, loginId: String? = null) =
        user(loginId).also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(role.id).get())) }

    private fun commit(p: Project, author: String, message: String, files: Map<String, String>) =
        SvnFixtures.commit(svn.directoryOf(p.owner!!, p.name!!), author, message, files.mapValues { it.value.toByteArray() })

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path)).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun comment(p: Project, commitId: String, author: User, contents: String, path: String? = null, line: Int? = null) = commitComments.save(
        CommitComment(project = p, commitId = commitId, contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, path = path, line = line)
    )

    // --- 커밋 보기 ---

    @Test
    fun `커밋 보기는 커밋 정보와 패치를 담는다`() {
        val p = project()
        val dev = member(p, loginId = "svdev")
        commit(p, "svdev", "첫 커밋", mapOf("a.txt" to "line1\n"))
        commit(p, "svdev", "둘째 커밋\n설명 줄", mapOf("a.txt" to "line1\nline2\n"))
        val doc = page("/${p.owner}/${p.name}/commit/2")
        assertTrue(doc.title().contains("Commits @2") || doc.title().contains("@2"), doc.title())
        assertEquals("/svdev", doc.selectFirst("p.commitInfo a.avatar-wrap")!!.attr("href"))
        assertEquals("이름svdev", doc.selectFirst("p.commitInfo strong")!!.text())
        assertEquals("@2", doc.selectFirst("strong.commitId")!!.text())
        assertEquals("둘째 커밋\n설명 줄", doc.selectFirst("pre.commitMsg")!!.wholeText())
        val patch = doc.selectFirst("#commit[data-commit-origin=true]")!!.wholeText()
        assertTrue(patch.contains("+line2"), patch)
        assertEquals(listOf("Files", "Commit"), doc.select(".code-browse-wrap ul.nav-tabs li a").map { it.text() })
        assertNotNull(dev.id)
    }

    @Test
    fun `작성자가 사용자가 아니면 이름만 보인다`() {
        val p = project()
        commit(p, "stranger", "msg", mapOf("a.txt" to "x\n"))
        val doc = page("/${p.owner}/${p.name}/commit/1")
        assertNull(doc.selectFirst("p.commitInfo a.avatar-wrap"))
        assertEquals("stranger", doc.selectFirst("p.commitInfo strong")!!.text())
    }

    @Test
    fun `없는 리비전은 404이다`() {
        val p = project()
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        mockMvc.perform(get("/${p.owner}/${p.name}/commit/9")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/commit/abc")).andExpect(status().isNotFound)
    }

    @Test
    fun `커밋 보기의 스크립트에 부모 리비전과 코드 주소를 넘긴다`() {
        val p = project()
        commit(p, "dev", "a", mapOf("a.txt" to "1\n"))
        commit(p, "dev", "b", mapOf("a.txt" to "2\n"))
        val script = page("/${p.owner}/${p.name}/commit/2").select("script").joinToString("\n") { it.data() }.replace("\\/", "/")
        assertTrue(script.contains("code.SvnDiff"), script)
        assertTrue(Regex("\"sParentCommitId\"\\s*:\\s*\"1\"").containsMatchIn(script), script)
        assertTrue(Regex("\"sCommitId\"\\s*:\\s*\"2\"").containsMatchIn(script), script)
        assertTrue(Regex("\"sCodeURL\"\\s*:\\s*\"/${p.owner}/${p.name}/code\"").containsMatchIn(script), script)
    }

    // --- 코멘트 보기 ---

    @Test
    fun `코멘트는 작성자와 본문과 위치를 담고 삭제 단추는 지울 수 있는 사용자에게만 보인다`() {
        val p = project()
        val writer = member(p, loginId = "scwriter")
        // 프로젝트 구성원은 남의 코멘트도 지울 수 있다. 구성원이 아닌 사용자만 지울 수 없다.
        val reader = user()
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        comment(p, "1", writer, "**굵게** 코멘트", "/a.txt", 3)
        val asWriter = page("/${p.owner}/${p.name}/commit/1", login(writer))
        val item = asWriter.selectFirst("ul.comments li.comment")!!
        assertEquals("/a.txt", item.attr("data-path")); assertEquals("3", item.attr("data-line"))
        assertEquals("/scwriter", item.selectFirst(".comment_author a")!!.attr("href"))
        assertEquals("굵게", item.selectFirst(".comment-body strong")!!.text())
        assertNotNull(item.selectFirst("button[data-toggle=comment-delete]"))
        assertNotNull(asWriter.selectFirst("#toggle-comments"))
        assertNull(page("/${p.owner}/${p.name}/commit/1", login(reader)).selectFirst("button[data-toggle=comment-delete]"))
        assertNull(page("/${p.owner}/${p.name}/commit/1").selectFirst("button[data-toggle=comment-delete]"))
    }

    @Test
    fun `코멘트가 없으면 목록과 토글이 없다`() {
        val p = project()
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        val doc = page("/${p.owner}/${p.name}/commit/1")
        assertNull(doc.selectFirst("ul.comments")); assertNull(doc.selectFirst("#toggle-comments"))
    }

    @Test
    fun `코멘트 입력란은 쓸 수 있는 사용자에게만 보인다`() {
        val p = project(ProjectScope.PRIVATE)
        val writer = member(p)
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        assertNotNull(page("/${p.owner}/${p.name}/commit/1", login(writer)).selectFirst("form#comment-form"))
        mockMvc.perform(get("/${p.owner}/${p.name}/commit/1")).andExpect(status().isForbidden)
    }

    @Test
    fun `구독 단추는 구독 중이면 켜져 있다`() {
        val p = project()
        val watcher = member(p)
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        assertTrue(!page("/${p.owner}/${p.name}/commit/1", login(watcher)).selectFirst("#watch-button")!!.hasClass("active"))
        watches.save(Watch(user = watcher, resourceType = ResourceType.COMMIT, resourceId = "${p.id}:1"))
        assertTrue(page("/${p.owner}/${p.name}/commit/1", login(watcher)).selectFirst("#watch-button")!!.hasClass("active"))
    }

    // --- 코멘트 만들기 ---

    @Test
    fun `코멘트를 만들면 커밋 보기의 그 코멘트로 이동한다`() {
        val p = project()
        val writer = member(p)
        val other = member(p)
        watches.save(Watch(user = other, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        val result = mockMvc.perform(post("/${p.owner}/${p.name}/commit/1/comments").session(login(writer)).param("contents", "새 코멘트").param("path", "/a.txt").param("line", "4").param("side", "B"))
            .andExpect(status().isSeeOther).andReturn()
        val saved = commitComments.findAll().single { it.project?.id == p.id }
        assertEquals("/${p.owner}/${p.name}/commit/1#comment-${saved.id}", result.response.getHeader("Location"))
        assertEquals("새 코멘트", saved.contents); assertEquals(writer.loginId, saved.authorLoginId); assertEquals("1", saved.commitId); assertEquals("/a.txt", saved.path); assertEquals(4, saved.line)
        val event = events.findByResourceTypeAndResourceId(ResourceType.COMMIT_COMMENT, saved.id.toString()).single()
        assertEquals(EventType.NEW_COMMENT, event.eventType)
        assertEquals("새 코멘트", event.newValue)
        assertEquals(setOf(other.loginId), event.receivers.map { it.loginId }.toSet())
    }

    @Test
    fun `내용이 없으면 400이고 쓸 권한이 없으면 403이며 없는 리비전은 404이다`() {
        val p = project(ProjectScope.PRIVATE)
        val writer = member(p)
        val outsider = user()
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        mockMvc.perform(post("/${p.owner}/${p.name}/commit/1/comments").session(login(writer)).param("contents", "")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/${p.owner}/${p.name}/commit/1/comments").session(login(outsider)).param("contents", "x")).andExpect(status().isForbidden)
        mockMvc.perform(post("/${p.owner}/${p.name}/commit/9/comments").session(login(writer)).param("contents", "x")).andExpect(status().isNotFound)
        assertTrue(commitComments.findAll().none { it.project?.id == p.id })
    }

    // --- 코멘트 지우기 ---

    @Test
    fun `없는 코멘트를 지우면 커밋 댓글이 없다고 알린다`() {
        val p = project()
        val writer = member(p)
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        // v1.6 IsAllowedAction: targetType이 code_comment라 "error.notfound.code_comment" 문구이다
        val html = mockMvc.perform(delete("/${p.owner}/${p.name}/commit/1/comments/999999/delete").session(login(writer)))
            .andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(html.contains("Commit-comment does not exist"), html)
    }

    @Test
    fun `작성자는 코멘트를 지우고 커밋 보기로 돌아간다`() {
        val p = project()
        val writer = member(p)
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        val saved = comment(p, "1", writer, "지울 코멘트")
        mockMvc.perform(delete("/${p.owner}/${p.name}/commit/1/comments/${saved.id}/delete").session(login(writer)))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/${p.owner}/${p.name}/commit/1"))
        assertTrue(commitComments.findById(saved.id!!).isEmpty)
    }

    @Test
    fun `지울 권한이 없으면 403이다`() {
        val p = project()
        val writer = member(p)
        val other = user()
        commit(p, "dev", "msg", mapOf("a.txt" to "x\n"))
        val saved = comment(p, "1", writer, "남의 코멘트")
        mockMvc.perform(delete("/${p.owner}/${p.name}/commit/1/comments/${saved.id}/delete").session(login(other))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/${p.owner}/${p.name}/commit/1/comments/${saved.id}/delete")).andExpect(status().isForbidden)
        assertNotNull(commitComments.findById(saved.id!!).orElse(null))
    }

    // --- 비교 ---

    @Test
    fun `두 리비전을 비교하면 패치를 보여 준다`() {
        val p = project()
        commit(p, "dev", "a", mapOf("a.txt" to "1\n"))
        commit(p, "dev", "b", mapOf("a.txt" to "1\n2\n"))
        val doc = page("/${p.owner}/${p.name}/compare/1..2")
        assertEquals("@1..2", doc.selectFirst("strong.commitId")!!.text())
        assertTrue(doc.selectFirst("#commit[data-commit-origin=true]")!!.wholeText().contains("+2"))
        val script = doc.select("script").joinToString("\n") { it.data() }.replace("\\/", "/")
        assertTrue(script.contains("code.SvnDiff") && script.contains("\"sParentCommitId\"") && script.contains("sTplFileURL"), script)
        // v1.6 역방향 라우트는 경로 변수 자리(`${commitId}`)의 중괄호를 인코딩하고 와일드카드(`${path}`)는 그대로 둔다
        assertTrue(script.contains("/code/\$%7BcommitId%7D/\${path}") && script.contains("/rawcode/\$%7BcommitId%7D/\${path}"), script)
    }

    @Test
    fun `변경이 없으면 안내하고 없는 리비전은 404이다`() {
        val p = project()
        commit(p, "dev", "a", mapOf("a.txt" to "1\n"))
        val doc = page("/${p.owner}/${p.name}/compare/1..1")
        assertEquals("No changes", doc.selectFirst("div.alert")!!.text().ifEmpty { "No changes" }.let { doc.selectFirst("div.alert")!!.text() })
        mockMvc.perform(get("/${p.owner}/${p.name}/compare/1..9")).andExpect(status().isNotFound)
    }

    // --- 멘션 후보 ---

    @Test
    fun `멘션 후보는 코멘트를 단 사람과 커밋 작성자 순으로 뒤집혀 나오고 구성원이 뒤따르며 나는 마지막이다`() {
        val p = project()
        val author = member(p, loginId = "mnauthor")
        val commenter = member(p, loginId = "mncommenter")
        val viewer = member(p, loginId = "mnviewer")
        commit(p, "mnauthor", "msg", mapOf("a.txt" to "x\n"))
        comment(p, "1", commenter, "코멘트")
        val body = mockMvc.perform(get("/${p.owner}/${p.name}/mentionListAtCommitDiff?commitId=1&mentionType=user").session(login(viewer))).andExpect(status().isOk).andReturn().response.contentAsString
        val order = Regex("\"loginid\"\\s*:\\s*\"(mn[a-z]+)\"").findAll(body).map { it.groupValues[1] }.toList().ifEmpty { Regex("(mnauthor|mncommenter|mnviewer)").findAll(body).map { it.value }.distinct().toList() }
        // v1.6 addCodeCommenters는 커밋 작성자까지 포함한 목록 전체를 뒤집는다
        assertEquals(listOf("mncommenter", "mnauthor", "mnviewer"), order.distinct(), body)
        assertNotNull(author.id)
    }
}
