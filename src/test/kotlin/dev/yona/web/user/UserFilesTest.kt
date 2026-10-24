package dev.yona.web.user

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 내 파일 목록(`GET /user/files`)을 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:709-722`(`userFiles`), `app/views/user/userFiles.scala.html`,
 * `app/models/Attachment.java:524-537`(`findByUser`), `app/utils/RouteUtil.java:26-62`(`getUrl`).
 */
@AutoConfigureMockMvc
@Transactional
class UserFilesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var attachments: AttachmentRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "uf$n", name = "uf$n", email = "uf$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun attach(owner: User, name: String, at: Long, type: ResourceType = ResourceType.ISSUE_POST, containerId: Long = 0, mime: String = "text/plain", size: Long = 10): Attachment =
        attachments.save(Attachment(name = name, hash = "h$name$at", containerType = type, containerId = containerId, mimeType = mime, size = size, createdDate = Date(at), ownerLoginId = owner.loginId))

    private fun page(session: MockHttpSession, vararg params: Pair<String, String>) =
        Jsoup.parse(mockMvc.perform(get("/user/files").session(session).apply { params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `내가 올린 파일만 최근 순으로 보인다`() {
        val me = newUser(); val other = newUser()
        attach(me, "old.txt", 1_700_000_000_000); attach(me, "new.txt", 1_700_000_100_000); attach(other, "theirs.txt", 1_700_000_200_000)
        val rows = page(login(me)).select(".attachment-file-detail")
        assertEquals(listOf("new.txt", "old.txt"), rows.map { it.selectFirst(".file-name")!!.text().trim() })
    }

    @Test
    fun `내 파일 탭이 활성화된다`() {
        val doc = page(login(newUser()))
        assertEquals("/user/files", doc.selectFirst(".page-wrap > ul.nav-tabs li.active a")!!.attr("href"))
    }

    @Test
    fun `이미지는 미리보기가 있고 그 밖의 파일은 없다`() {
        val me = newUser()
        val img = attach(me, "a.png", 1_700_000_100_000, mime = "image/png"); attach(me, "b.txt", 1_700_000_000_000)
        val rows = page(login(me)).select(".attachment-file-detail")
        assertEquals("/files/${img.id}", rows[0].selectFirst(".file-preview img")!!.attr("src"))
        assertTrue(rows[1].select(".file-preview img").isEmpty())
    }

    @Test
    fun `파일 이름은 열기 링크이고 내려받기 링크와 읽기 쉬운 크기가 있다`() {
        val me = newUser()
        val file = attach(me, "report.pdf", 1_700_000_000_000, size = 1500)
        val row = page(login(me)).selectFirst(".attachment-file-detail")!!
        assertEquals("/files/${file.id}", row.selectFirst(".file-name a")!!.attr("href"))
        assertEquals("/files/${file.id}?action=download", row.selectFirst(".file-download a")!!.attr("href"))
        assertEquals("1.5 kB", row.selectFirst(".file-size")!!.text())
    }

    @Test
    fun `위치는 첨부가 붙은 대상의 주소이다`() {
        val me = newUser()
        val project = projects.save(Project(owner = me.loginId, name = "proj", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        val issue = issues.save(Issue(title = "t", project = project, number = 7, authorId = me.id, authorLoginId = me.loginId, authorName = me.name, createdDate = Date(), updatedDate = Date()))
        attach(me, "issue.txt", 1_700_000_100_000, ResourceType.ISSUE_POST, issue.id!!)
        attach(me, "logo.png", 1_700_000_000_000, ResourceType.MILESTONE, 1)
        val rows = page(login(me)).select(".attachment-file-detail")
        val href = "/${me.loginId}/proj/issue/7"
        assertEquals(href, rows[0].selectFirst(".file-location a")!!.attr("href"))
        assertEquals(href, rows[0].selectFirst(".file-location a")!!.text())
        assertEquals("_blank", rows[0].selectFirst(".file-location a")!!.attr("target"))
        assertTrue(rows[1].select(".file-location a").isEmpty())
    }

    @Test
    fun `filter는 이름에 대소문자 구분 없이 부분 일치하는 파일만 보여 준다`() {
        val me = newUser()
        attach(me, "Design.PNG", 1_700_000_100_000); attach(me, "notes.txt", 1_700_000_000_000)
        val rows = page(login(me), "filter" to "sign").select(".attachment-file-detail")
        assertEquals(listOf("Design.PNG"), rows.map { it.selectFirst(".file-name")!!.text().trim() })
    }

    @Test
    fun `한 쪽에 50개씩 보여 주고 pageNum으로 넘긴다`() {
        val me = newUser()
        (1..55).forEach { attach(me, "f$it.txt", 1_700_000_000_000 + it) }
        val session = login(me)
        val first = page(session); val second = page(session, "pageNum" to "2")
        assertEquals(50, first.select(".attachment-file-detail").size)
        assertEquals(5, second.select(".attachment-file-detail").size)
        assertEquals("f5.txt", second.select(".attachment-file-detail").first()!!.selectFirst(".file-name")!!.text().trim())
        assertTrue(first.html().contains("yobi.Pagination.update(\$(\"#pagination\"), 2)"))
    }

    @Test
    fun `익명 사용자에게는 빈 목록이 보인다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/user/files")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select(".attachment-file-detail").isEmpty())
    }
}
