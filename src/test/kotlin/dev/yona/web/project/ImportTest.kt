package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부 Git 저장소 가져오기(`/_import`)를 검증한다. 가져올 저장소는 같은 서버의 다른 프로젝트 저장소를 `file:` 주소로 가리킨다.
 *
 * 원본: `app/controllers/ImportApp.java`, `app/views/project/importing.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser", "yona.import-allow-local-sources=true"])
class ImportTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "im$n", name = "이름im$n", email = "im$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 가져올 원본 저장소이다. 커밋 두 개와 브랜치 두 개가 있다. */
    private fun source(): String {
        val n = ++sequence
        val p = projects.save(Project(owner = "imo$n", name = "imp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        GitTestSupport.pushCommits(repositories, p, TestCommit("하나", mapOf("a.txt" to "a".toByteArray())), TestCommit("둘", mapOf("b.txt" to "b".toByteArray())))
        GitTestSupport.appendCommits(repositories, p, TestCommit("기능", mapOf("c.txt" to "c".toByteArray())), branch = "feature")
        return repositories.openGitRepository(p).use { it.directory.toURI().toString() }
    }

    private fun send(session: MockHttpSession, owner: String, name: String, url: String, extra: Map<String, String> = emptyMap()) =
        post("/_import").session(session).param("owner", owner).param("name", name).param("url", url).param("projectScope", "PUBLIC").param("vcs", extra["vcs"] ?: "GIT")
            .param("code", "true").param("issue", "true").apply { extra.filterKeys { it != "vcs" }.forEach { (k, v) -> param(k, v) } }

    @Test
    fun `가져오기 폼은 로그인이 필요하고 소유자를 미리 고를 수 있다`() {
        mockMvc.perform(get("/_import")).andExpect(status().is3xxRedirection)
        val me = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        organizationUsers.save(OrganizationUser(user = me, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        val doc = Jsoup.parse(mockMvc.perform(get("/_import").param("owner", org.name!!).session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("/_import", doc.selectFirst("form#importGit")!!.attr("action"))
        assertEquals(listOf(me.loginId, org.name), doc.select("select#project-owner option").map { it.attr("value") })
        assertEquals(org.name, doc.selectFirst("select#project-owner option[selected]")!!.attr("value"))
        // 저장소 종류는 가져올 수 있는 종류(Git, Mercurial)를 고르는 선택 상자이고 기본은 Git이다(v1.6은 Git으로 고정한 숨은 입력이었다)
        assertEquals(listOf("GIT", "Mercurial"), doc.select("select#vcs option").map { it.attr("value") })
        assertEquals("GIT", doc.selectFirst("select#vcs option[selected]")!!.attr("value"))
        assertEquals(7, doc.select("input[type=checkbox][name][value=true]").size)
    }

    @Test
    fun `저장소를 가져와 프로젝트를 만들고 만든 사람을 매니저로 둔다`() {
        val me = user(); val url = source()
        mockMvc.perform(send(login(me), me.loginId!!, "imported", url, mapOf("overview" to "설명"))).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${me.loginId}/imported"))
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "imported")!!
        assertEquals("GIT", project.vcs); assertEquals("설명", project.overview); assertEquals(ProjectScope.PUBLIC, project.projectScope); assertNotNull(project.createdDate)
        assertEquals(setOf("refs/heads/feature", "refs/heads/master"), repositories.refNames(project).toSet())
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, project.id!!, RoleType.MANAGER.id))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(1L, project.id!!, RoleType.SITEMANAGER.id))
        val menu = menuSettings.findFirstByProjectId(project.id!!)!!
        assertEquals(listOf(true, true, false, false, false, false, false), listOf(menu.code, menu.issue, menu.pullRequest, menu.review, menu.milestone, menu.board, menu.wiki))
    }

    @Test
    fun `위키를 켜서 가져오면 위키 메뉴가 켜지고 보내지 않으면 꺼진다 (E-F3, 폼의 위키 체크박스 값이 버려졌다)`() {
        val me = user(); val session = login(me)
        mockMvc.perform(send(session, me.loginId!!, "withwiki", source(), mapOf("wiki" to "true"))).andExpect(status().isSeeOther)
        assertEquals(true, menuSettings.findFirstByProjectId(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "withwiki")!!.id!!)!!.wiki)
        mockMvc.perform(send(session, me.loginId!!, "nowiki", source())).andExpect(status().isSeeOther)
        assertEquals(false, menuSettings.findFirstByProjectId(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "nowiki")!!.id!!)!!.wiki)
    }

    @Test
    fun `실패해서 다시 그릴 때 위키 체크박스도 고른 값을 따른다 (E-F3)`() {
        val me = user(); val session = login(me)
        val html = mockMvc.perform(send(session, me.loginId!!, "wikiredraw", "  ", mapOf("wiki" to "true"))).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(org.jsoup.Jsoup.parse(html).selectFirst("input#menuSettingWiki")!!.hasAttr("checked"))
        val html2 = mockMvc.perform(send(session, me.loginId!!, "wikiredraw2", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(!org.jsoup.Jsoup.parse(html2).selectFirst("input#menuSettingWiki")!!.hasAttr("checked"))
    }

    @Test
    fun `주소 앞뒤에 공백이 있어도 검증과 복제에 같은 주소를 써서 가져온다 (C072)`() {
        val me = user(); val url = source()
        mockMvc.perform(send(login(me), me.loginId!!, "spaced", "  $url  ")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${me.loginId}/spaced"))
        assertNotNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "spaced"))
    }

    @Test
    fun `조직 소유로 가져오면 조직에 연결되고 만든 사람은 매니저가 되지 않는다`() {
        val me = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        organizationUsers.save(OrganizationUser(user = me, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        mockMvc.perform(send(login(me), org.name!!, "orgimport", source())).andExpect(status().isSeeOther)
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(org.name!!, "orgimport")!!
        assertEquals(org.id, project.organization?.id)
        assertTrue(!projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, project.id!!, RoleType.MANAGER.id))
    }

    @Test
    fun `관리자가 아닌 조직이면 403이고 다른 사용자 소유이면 폼에 오류가 보인다`() {
        val me = user(); val other = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        mockMvc.perform(send(login(me), org.name!!, "x", source())).andExpect(status().isForbidden)
        val doc = Jsoup.parse(mockMvc.perform(send(login(me), other.loginId!!, "x", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(doc.html().contains("project.owner.invalidate") || doc.html().contains("owner"))
    }

    @Test
    fun `실패해서 다시 그릴 때는 고른 메뉴만 켜져 있다 (R-46-4)`() {
        val me = user(); val session = login(me)
        // 코드와 이슈만 고르고 주소를 비워 실패시킨다
        val html = mockMvc.perform(send(session, me.loginId!!, "menusonly", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        val doc = org.jsoup.Jsoup.parse(html)
        fun checked(id: String) = doc.selectFirst("input#$id")!!.hasAttr("checked")
        assertTrue(checked("menuSettingCode") && checked("menuSettingIssue"))
        assertEquals(listOf(false, false, false, false), listOf("menuSettingPullRequest", "menuSettingReview", "menuSettingMilestone", "menuSettingBoard").map { checked(it) })
    }

    @Test
    fun `이름에는 문제가 없고 주소만 비었으면 이름 안내를 더하지 않는다 (R-46-5)`() {
        val me = user(); val session = login(me)
        fun errorsOf(html: String) = html.substringAfter("\"htError\": ").substringBefore("\n")
        val blank = errorsOf(mockMvc.perform(send(session, me.loginId!!, "goodname", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        // v1.6은 오류가 하나라도 있으면 이름 규칙 안내(project.name.alert)를 이름에 더 붙였다
        assertTrue(blank.contains("\"url\"") && !blank.contains("\"name\""), blank)
        val badName = errorsOf(mockMvc.perform(send(session, me.loginId!!, "bad name!", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(badName.contains("\"name\""), badName)
    }

    @Test
    fun `같은 이름이 있거나 주소가 비었거나 이름 규칙을 어기면 400이고 이름 안내가 더해진다`() {
        val me = user(); val session = login(me)
        mockMvc.perform(send(session, me.loginId!!, "taken", source())).andExpect(status().isSeeOther)
        val duplicate = mockMvc.perform(send(session, me.loginId!!, "TAKEN", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(duplicate.contains("htError") || duplicate.contains("\"name\""), duplicate)
        val blank = mockMvc.perform(send(session, me.loginId!!, "newone", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(blank.contains("\"url\""), blank)
        mockMvc.perform(send(session, me.loginId!!, "bad name!", source())).andExpect(status().isBadRequest)
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "newone"))
    }

    @Test
    fun `저장소가 아닌 주소는 주소 오류이고 복제하다 만 디렉터리를 남기지 않는다`() {
        val me = user()
        val response = mockMvc.perform(send(login(me), me.loginId!!, "broken", "file:///no/such/repository")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(response.contains("\"url\""), response)
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "broken"))
        assertTrue(!java.io.File("build/tmp/it-code-browser/repo/git/${me.loginId}/broken.git").exists())
    }

    @Test
    fun `로그인하지 않으면 만들 수 없다`() {
        mockMvc.perform(post("/_import").param("owner", "x").param("name", "y").param("url", "z")).andExpect(status().isForbidden)
    }

    // --- Mercurial 가져오기(X-7 7b) ---

    @Autowired private lateinit var hgRepositories: dev.yona.repository.hg.HgRepositories

    /** 가져올 Mercurial 원본 저장소의 경로이다. 커밋 둘과 이름표 `main`이 있다. */
    private fun hgSource(): String {
        org.junit.jupiter.api.Assumptions.assumeTrue(dev.yona.support.HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val dir = hgRepositories.directoryOf("imo$n", "hgsrc$n")
        // 이전 실행이 남긴 디렉터리가 있으면 지운다(번호가 실행마다 같다)
        dir.deleteRecursively(); dev.yona.support.HgTestSupport.init(dir)
        dev.yona.support.HgTestSupport.commit(dir, "하나", mapOf("a.txt" to "a\n"))
        dev.yona.support.HgTestSupport.commit(dir, "둘", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900")
        dev.yona.support.HgTestSupport.bookmark(dir, "main")
        return dir.canonicalPath
    }

    @Test
    fun `종류를 Mercurial로 고르면 Mercurial 저장소를 가져와 같은 종류의 프로젝트를 만든다`() {
        val me = user(); val url = hgSource()
        mockMvc.perform(send(login(me), me.loginId!!, "hgimported", url, mapOf("vcs" to "Mercurial"))).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${me.loginId}/hgimported"))
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "hgimported")!!
        assertEquals("Mercurial", project.vcs)
        assertEquals(listOf("refs/heads/main"), repositories.refNames(project))
        assertTrue(dev.yona.support.HgTestSupport.verifies(hgRepositories.directoryOf(me.loginId!!, "hgimported")))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, project.id!!, RoleType.MANAGER.id))
    }

    @Test
    fun `Mercurial 저장소가 아닌 주소는 주소 오류이고 만들다 만 디렉터리를 남기지 않는다`() {
        val me = user()
        val response = mockMvc.perform(send(login(me), me.loginId!!, "hgbroken", "/no/such/hg/repository", mapOf("vcs" to "Mercurial"))).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(response.contains("\"url\""), response)
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "hgbroken"))
        assertTrue(!hgRepositories.directoryOf(me.loginId!!, "hgbroken").exists())
    }

    @Test
    fun `가져올 수 없는 종류나 모르는 값은 종류 오류이다`() {
        val me = user(); val url = source()
        for (vcs in listOf("Subversion", "Bazaar")) {
            val response = mockMvc.perform(send(login(me), me.loginId!!, "badvcs", url, mapOf("vcs" to vcs))).andExpect(status().isBadRequest).andReturn().response.contentAsString
            assertTrue(response.contains("\"vcs\""), response)
        }
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "badvcs"))
    }

    @Test
    fun `종류를 주지 않으면 v1_6처럼 Git으로 가져온다`() {
        val me = user(); val url = source()
        mockMvc.perform(post("/_import").session(login(me)).param("owner", me.loginId!!).param("name", "novcs").param("url", url).param("projectScope", "PUBLIC").param("code", "true"))
            .andExpect(status().isSeeOther)
        assertEquals("GIT", projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "novcs")!!.vcs)
    }

    @Test
    fun `Mercurial을 고르면 오류 문구와 폼 문구가 Git이 아니라 Mercurial을 말한다`() {
        val me = user()
        // 주소 오류와 빈 주소 오류
        val wrong = mockMvc.perform(send(login(me), me.loginId!!, "hgmsg1", "/no/such/hg/repository", mapOf("vcs" to "Mercurial"))).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(wrong.contains("Mercurial") && !wrong.contains("Git 저장소 URL이"), wrong)
        val empty = mockMvc.perform(send(login(me), me.loginId!!, "hgmsg2", "", mapOf("vcs" to "Mercurial"))).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(empty.contains("Mercurial"), empty)
        // 다시 그린 폼의 제목과 주소 칸 이름, 선택된 종류
        val doc = Jsoup.parse(mockMvc.perform(send(login(me), me.loginId!!, "hgmsg3", "", mapOf("vcs" to "Mercurial"))).andReturn().response.contentAsString)
        assertTrue(doc.selectFirst("form#importGit legend")!!.text().contains("Mercurial"), doc.selectFirst("form#importGit legend")!!.text())
        assertTrue(doc.selectFirst("label[for=url]")!!.text().contains("Mercurial"))
        assertEquals("Mercurial", doc.selectFirst("select#vcs option[selected]")!!.attr("value"))
        // Git은 그대로이다
        val git = Jsoup.parse(mockMvc.perform(get("/_import").session(login(me))).andReturn().response.contentAsString)
        assertTrue(git.selectFirst("form#importGit legend")!!.text().contains("Git"))
    }
}
