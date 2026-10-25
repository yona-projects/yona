package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코드 브라우저 화면과 JSON, 원본, 내려받기를 검증한다.
 *
 * 원본: `app/controllers/CodeApp.java`, `app/views/code/view.scala.html`, `partial_view_folder.scala.html`, `partial_view_file.scala.html`, `nohead.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CodeBrowserWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "cw$n", name = "cw$n", email = "cw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC, memberOnly: Boolean = false, vcs: String = "GIT"): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "cwo$n", name = "cwp$n", vcs = vcs, projectScope = scope, isCodeAccessibleMemberOnly = memberOnly))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    private fun member(p: Project, role: RoleType = RoleType.MEMBER): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(role.id).get())) }

    /** `%25` 같은 인코딩을 다시 인코딩하지 않도록 [java.net.URI]로 요청한다. */
    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path)).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun filled(): Project = project().also {
        GitTestSupport.pushCommits(repositories, it,
            TestCommit("처음", mapOf("README.md" to "# 제목".toByteArray(), "src/App.kt" to "fun main() {}\n".toByteArray(), "src/util/Tool.kt" to "object Tool\n".toByteArray(), "logo.png" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13), "data.bin" to byteArrayOf(0, 1, 2, 0, 3))))
    }

    @Test
    fun `코드 주소는 기본 브랜치의 코드 화면으로 보낸다`() {
        val p = filled()
        mockMvc.perform(get("/${p.owner}/${p.name}/code")).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/${p.owner}/${p.name}/code/master/"))
    }

    @Test
    fun `비어 있는 저장소는 처음 올리는 방법을 구성원에게만 보여 준다`() {
        val p = project(); val m = member(p, RoleType.MANAGER)
        val doc = page("/${p.owner}/${p.name}/code", login(m))
        assertNotNull(doc.selectFirst(".alert.alert-block"))
        val commands = doc.select("pre code").map { it.text() }
        assertTrue(commands.first().startsWith("git clone http://"), commands.first())
        assertTrue(commands.first().contains("${m.loginId}@"), commands.first())
        assertTrue(commands.first().contains("git push origin master"))
        val anonymous = page("/${p.owner}/${p.name}/code")
        assertNotNull(anonymous.selectFirst(".alert.alert-block")); assertTrue(anonymous.select("pre code").isEmpty())
    }

    @Test
    fun `폴더 화면은 폴더를 앞세운 목록과 탭, 브랜치 선택, 내려받기를 보여 준다`() {
        val p = filled()
        val doc = page("/${p.owner}/${p.name}/code/master/")
        assertEquals(listOf("Files", "Commit", "Branches"), doc.select(".code-browse-wrap ul.nav-tabs li a").map { it.text() })
        assertEquals(listOf("refs/heads/master"), doc.select("select#branches option").map { it.text() })
        assertEquals("/${p.owner}/${p.name}/code/master/", doc.selectFirst("select#branches option")!!.attr("value"))
        assertEquals(listOf("src", "README.md", "data.bin", "logo.png"), doc.select(".listitem .filename a").map { it.text() })
        val folderLink = doc.selectFirst(".listitem a[data-type=folder]")!!
        assertEquals("/${p.owner}/${p.name}/code/master/src", folderLink.attr("href"))
        assertEquals("dynatree-ico-cf", folderLink.className()); assertEquals("src", folderLink.attr("data-targetPath"))
        assertEquals("처음", doc.selectFirst(".listitem .commitMsg a:not(.avatar-wrap)")!!.text())
        assertEquals("/${p.owner}/${p.name}/code/master/download", doc.selectFirst(".code-browse-header a.ybtn")!!.attr("href"))
        assertNull(doc.selectFirst("#new-file-link"))
    }

    @Test
    fun `로그인하면 새 파일 링크가 나온다`() {
        val p = filled(); val m = member(p)
        val doc = page("/${p.owner}/${p.name}/code/master/src", login(m))
        assertEquals("/${p.owner}/${p.name}/postform?path=src/&branch=master", doc.selectFirst("#new-file-link")!!.attr("href"))
    }

    @Test
    fun `하위 폴더 화면은 상위 폴더 목록과 경로를 함께 보여 준다`() {
        val p = filled()
        val doc = page("/${p.owner}/${p.name}/code/master/src/util")
        assertEquals(listOf("src", "util"), doc.select("#breadcrumbs a").drop(1).map { it.text() })
        assertEquals("/${p.owner}/${p.name}/code/master/src/util", doc.select("#breadcrumbs a").last()!!.attr("href"))
        assertEquals(3, doc.select(".list-wrap[data-type=folder]").size)
    }

    @Test
    fun `텍스트 파일은 코드로, 마크다운은 렌더링해 보여 준다`() {
        val p = filled()
        val code = page("/${p.owner}/${p.name}/code/master/src/App.kt")
        assertEquals("fun main() {}\n", code.selectFirst("#codeVal.hidden")!!.wholeText())
        assertEquals("text/plain", code.selectFirst("pre#showCode")!!.attr("data-mimeType"))
        assertEquals("UNIX", code.selectFirst("#fileInfo > span:last-child")!!.text())
        assertEquals("/${p.owner}/${p.name}/rawcode/${rev(code)}/src/App.kt", code.select(".file-header .pull-right a").first()!!.attr("href"))
        val md = page("/${p.owner}/${p.name}/code/master/README.md")
        assertTrue(md.selectFirst("#codeVal.markdown-wrap h1")!!.text().startsWith("제목"))
    }

    private fun rev(doc: Document) = doc.selectFirst("#revisionNo a")!!.attr("href").substringAfter("/commit/").substringBefore('?')

    @Test
    fun `이미지와 이진 파일은 각자 방식으로 보여 준다`() {
        val p = filled()
        val image = page("/${p.owner}/${p.name}/code/master/logo.png")
        assertNotNull(image.selectFirst("#showImage img"))
        val binary = page("/${p.owner}/${p.name}/code/master/data.bin")
        assertEquals("data.bin", binary.selectFirst("#showFile .filename")!!.text())
        assertNotNull(binary.selectFirst("#showFile a.filehref"))
        assertNull(binary.selectFirst(".file-header a[target=_blank]:contains(Raw)"))
    }

    @Test
    fun `없는 경로나 브랜치는 코드를 찾을 수 없다고 답한다`() {
        val p = filled()
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/nope")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/code/nobranch/")).andExpect(status().isNotFound)
    }

    @Test
    fun `브랜치 이름에 슬래시가 있어도 두 번 인코딩한 주소로 연다`() {
        val p = filled()
        GitTestSupport.pushCommits(repositories, p, TestCommit("기능", mapOf("only.txt" to "x".toByteArray())), branch = "feature/x")
        val doc = page("/${p.owner}/${p.name}/code/feature%252Fx/")
        assertTrue(doc.select(".listitem .filename a").any { it.text() == "only.txt" })
        assertTrue(doc.select("select#branches option").any { it.attr("value") == "/${p.owner}/${p.name}/code/feature%252Fx/" })
    }

    @Test
    fun `메타데이터 JSON을 돌려준다`() {
        val p = filled()
        mockMvc.perform(get("/${p.owner}/${p.name}/code/!")).andExpect(status().isOk).andExpect(jsonPath("$.type").value("folder")).andExpect(jsonPath("$.data.src.type").value("folder"))
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/!/src/App.kt")).andExpect(status().isOk).andExpect(jsonPath("$.type").value("file")).andExpect(jsonPath("$.data").value("fun main() {}\n"))
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/!")).andExpect(status().isOk).andExpect(jsonPath("$.data['README.md'].msg").value("처음"))
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/!/missing")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/code/!/src")).andExpect(status().isOk).andExpect(jsonPath("$.data['util/Tool.kt']").doesNotExist()).andExpect(jsonPath("$.data.util.type").value("folder"))
    }

    @Test
    fun `브랜치를 zip으로 내려받는다`() {
        val p = filled()
        val result = mockMvc.perform(get("/${p.owner}/${p.name}/code/master/download")).andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=${p.name}-master.zip")).andReturn().response
        val names = ZipInputStream(ByteArrayInputStream(result.contentAsByteArray)).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue("src/App.kt" in names, names.toString())
        mockMvc.perform(get("/${p.owner}/${p.name}/code/nobranch/download")).andExpect(status().isNotFound)
    }

    @Test
    fun `원본, 이미지, 열기 주소는 파일을 종류에 맞게 돌려준다`() {
        val p = filled()
        val raw = mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/master/src/App.kt")).andExpect(status().isOk).andReturn().response
        assertTrue(raw.contentType!!.startsWith("text/plain"), raw.contentType); assertEquals("fun main() {}\n", raw.contentAsString)
        mockMvc.perform(get("/${p.owner}/${p.name}/image/master/logo.png")).andExpect(status().isOk).andExpect(header().string("Content-Type", "image/png"))
        mockMvc.perform(get("/${p.owner}/${p.name}/files/master/logo.png")).andExpect(status().isOk).andExpect(header().string("Content-Type", "image/png"))
        mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/master/missing.txt")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/files/master/src")).andExpect(status().isNotFound)
    }

    @Test
    fun `코드를 구성원에게만 여는 프로젝트는 비구성원에게 막힌다`() {
        val p = project(memberOnly = true)
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to "a".toByteArray())))
        val m = member(p); val outsider = user()
        mockMvc.perform(get("/${p.owner}/${p.name}/code").session(login(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/!").session(login(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${p.owner}/${p.name}/code/master/").session(login(m))).andExpect(status().isOk)
    }

    @Test
    fun `Git도 Subversion도 아닌 프로젝트는 지원하지 않는다`() {
        val p = project(vcs = "Mercurial")
        mockMvc.perform(get("/${p.owner}/${p.name}/code")).andExpect(status().isNotImplemented)
    }
}
