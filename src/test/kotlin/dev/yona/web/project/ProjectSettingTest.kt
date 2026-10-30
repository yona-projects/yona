package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.misc.FavoriteProject
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
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
import org.eclipse.jgit.lib.Constants
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 설정 폼(`GET /:user/:project/settingform`)과 저장(`POST /:user/:project/setting`)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:160-166, 235-330`, `app/views/project/setting.scala.html`,
 * `app/views/project/partial_settingmenu.scala.html`, `app/models/Project.java:236-242, 676-700`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-setting"])
class ProjectSettingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var favorites: FavoriteProjectRepository
    @Autowired private lateinit var attachments: AttachmentService

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-project-setting").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "ps$n", name = "ps$n", email = "ps$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(
        vcs: String = "GIT", organization: Organization? = null, menu: ProjectMenuSetting.() -> Unit = {}, configure: Project.() -> Unit = {}
    ): Project {
        val n = ++sequence
        val project = projects.save(
            Project(owner = "owner$n", name = "proj$n", vcs = vcs, projectScope = ProjectScope.PUBLIC, organization = organization, overview = "설명 $n")
                .apply(configure)
        )
        menuSettings.save(
            ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true)
                .apply(menu)
        )
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun manager(project: Project): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun settingForm(project: Project, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/settingform").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun save(
        project: Project, session: MockHttpSession?, vararg params: Pair<String, String>, logo: MockMultipartFile? = null
    ): MvcResult {
        val request = multipart("/${project.owner}/${project.name}/setting")
        session?.let { request.session(it) }
        logo?.let { request.file(it) }
        request.param("id", project.id.toString())
        params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn()
    }

    private fun reload(project: Project): Project {
        projects.flush()
        return projects.findById(project.id!!).get()
    }

    private val allMenus = arrayOf("code" to "true", "issue" to "true", "pullRequest" to "true", "review" to "true", "milestone" to "true", "board" to "true")

    // --- 접근 ---

    @Test
    fun `설정 폼은 관리자만 열 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/settingform").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/settingform")).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/settingform").session(login(manager(project)))).andExpect(status().isOk)
    }

    @Test
    fun `설정 저장도 관리자만 할 수 있다`() {
        val project = newProject()
        assertEquals(403, save(project, login(newUser()), "name" to "hacked").response.status)
        assertEquals("proj${sequence}".takeIf { false } ?: project.name, reload(project).name)
    }

    // --- 폼 ---

    @Test
    fun `폼 제목과 기본 입력란`() {
        val project = newProject()
        val page = settingForm(project, login(manager(project)))
        assertEquals("Project settings - ${project.owner}/${project.name}", page.title())
        val form = page.selectFirst("form#saveSetting")!!
        assertEquals("/${project.owner}/${project.name}/setting", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals(project.id.toString(), form.selectFirst("input[type=hidden][name=id]")?.attr("value"))
        assertEquals("0", form.selectFirst("input[type=hidden][name=watchingCount]")?.attr("value"))
        assertEquals(project.name, form.selectFirst("#project-name")?.attr("value"))
        assertEquals("250", form.selectFirst("#project-name")?.attr("maxlength"))
        assertEquals(project.overview, form.selectFirst("#project-desc")?.text())
        assertTrue(form.selectFirst(".logo-wrap")!!.attr("style").contains("/assets/images/project_default_logo.png"))
        assertEquals("File upload", form.selectFirst(".fake-file-wrap")?.text()?.trim())
        assertNotNull(form.selectFirst("input#logoPath[type=file][name=logoPath]"))
        assertEquals("Save", form.selectFirst("button#save")?.text())
    }

    @Test
    fun `공개 범위 라디오는 현재 값을 고르고 그룹 공개는 조직 프로젝트에만 있다`() {
        val plain = newProject()
        val page = settingForm(plain, login(manager(plain)))
        assertEquals(listOf("PUBLIC", "PRIVATE"), page.select("input[name=projectScope]").map { it.attr("value") })
        assertTrue(page.selectFirst("input#public")!!.hasAttr("checked"))

        val org = organizations.save(Organization(name = "setorg${++sequence}"))
        val grouped = newProject(organization = org) { }
        val groupedPage = settingForm(grouped, login(manager(grouped)))
        assertEquals(listOf("PUBLIC", "PROTECTED", "PRIVATE"), groupedPage.select("input[name=projectScope]").map { it.attr("value") })
    }

    @Test
    fun `이슈 템플릿 링크와 리뷰어 수와 기본 브랜치는 Git 프로젝트에만 있다`() {
        val git = newProject()
        val page = settingForm(git, login(manager(git)))
        assertEquals("/${git.owner}/${git.name}/postform?issueTemplate=true", page.selectFirst("a[target=_blank].ybtn")?.attr("href"))
        assertNotNull(page.selectFirst("#reviewerCountSettingPanel"))
        assertNotNull(page.selectFirst("select#project-default-branch"))

        val svn = newProject(vcs = "Subversion")
        val svnPage = settingForm(svn, login(manager(svn)))
        assertNull(svnPage.selectFirst("a[target=_blank].ybtn"))
        assertNull(svnPage.selectFirst("#reviewerCountSettingPanel"))
        assertNull(svnPage.selectFirst("select#project-default-branch"))
        assertNull(svnPage.selectFirst("input#menuSettingPullRequest"))
    }

    @Test
    fun `코드 접근 제한 라디오는 현재 값을 따른다`() {
        val project = newProject { }
        val on = newProject(configure = { isCodeAccessibleMemberOnly = true })
        assertTrue(settingForm(on, login(manager(on))).selectFirst("#codeAccessibleMemberOnly")!!.hasAttr("checked"))
        assertTrue(settingForm(project, login(manager(project))).selectFirst("#codeAccessibleAnyone")!!.hasAttr("checked"))
    }

    @Test
    fun `리뷰어 수 설정 패널은 코드 메뉴가 꺼져 있으면 숨긴다`() {
        val project = newProject(menu = { code = false })
        val page = settingForm(project, login(manager(project)))
        assertTrue(page.selectFirst("#reviewerCountSettingPanel")!!.attr("style").contains("display:none"))
        assertTrue(page.selectFirst("#defaultBranceSettingPanel")!!.attr("style").contains("display:none"))
    }

    @Test
    fun `기본 브랜치 선택지는 브랜치만 보여 주고 현재 기본 브랜치를 고른다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray()))
        repositories.openRepository(project).use { repository ->
            repository.updateRef("refs/heads/dev").apply { setNewObjectId(repository.resolve(Constants.HEAD)) }.update()
            repository.updateRef("refs/tags/v1").apply { setNewObjectId(repository.resolve(Constants.HEAD)) }.update()
        }
        val options = settingForm(project, login(manager(project))).select("select#project-default-branch option")
        assertEquals(listOf("refs/heads/dev", "refs/heads/master"), options.map { it.attr("value") }.sorted())
        assertEquals("refs/heads/master", options.first { it.hasAttr("selected") }.attr("value"))
    }

    @Test
    fun `메뉴 체크박스는 현재 설정을 따른다`() {
        val project = newProject(menu = { issue = false; board = false })
        val page = settingForm(project, login(manager(project)))
        assertTrue(page.selectFirst("#menuSettingCode")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("#menuSettingIssue")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("#menuSettingBoard")!!.hasAttr("checked"))
        assertEquals("true", page.selectFirst("#menuSettingCode")!!.attr("value"))
    }

    @Test
    fun `설정 하위 메뉴가 있고 현재 화면이 활성이다`() {
        val project = newProject()
        val page = settingForm(project, login(manager(project)))
        val tabs = page.select(".project-page-wrap ul.nav-tabs li")
        assertEquals(
            listOf("subMenuProjectSetting", "subMenuProjectMember", "subMenuRuleset", "subMenuIssueLabel", "subMenuWebhook", "subMenuProjectTransfer", "subMenuProjectDelete", "subMenuProjectChangeVCS"),
            tabs.map { it.id() }
        )
        assertEquals(listOf("Settings", "Member", "Rulesets", "Issue Label", "Webhooks", "Transfer", "Delete project", "Repository Type Change"), tabs.map { it.text() }, "규칙 묶음 탭은 추가 개발 항목 X-34이다")
        assertEquals(listOf("active"), tabs.filter { it.selectFirst("a")!!.hasClass("active") }.map { "active" }, tabs.outerHtml())
        assertTrue(tabs[0].selectFirst("a")!!.hasClass("active"))
        assertEquals("/${project.owner}/${project.name}/members", tabs[1].selectFirst("a")?.attr("href"))
        assertEquals("/${project.owner}/${project.name}/setting/rulesets", tabs[2].selectFirst("a")?.attr("href"))
        assertEquals("/${project.owner}/${project.name}/issue/labelsform", tabs[3].selectFirst("a")?.attr("href"))
    }

    @Test
    fun `가입 신청이 있으면 구성원 메뉴에 개수 배지가 붙는다`() {
        val project = newProject()
        val applicant = newUser()
        applicant.enrolledProjects.add(project)
        users.save(applicant)
        val page = settingForm(project, login(manager(project)))
        assertEquals("1", page.selectFirst("#subMenuProjectMember .num-badge")?.text())
    }

    @Test
    fun `저장소 종류 변경 메뉴는 코드 메뉴가 꺼져 있으면 숨긴다`() {
        val project = newProject(menu = { code = false })
        val page = settingForm(project, login(manager(project)))
        assertTrue(page.selectFirst("#subMenuProjectChangeVCS")!!.attr("style").contains("display:none"))
    }

    @Test
    fun `이전 위치가 있으면 안내한다`() {
        val project = newProject(configure = { previousOwnerLoginId = "oldowner"; previousName = "oldname" })
        val page = settingForm(project, login(manager(project)))
        assertTrue(page.selectFirst("#project-name")!!.parent()!!.text().contains("This project was moved from oldowner/oldname"))
    }

    // --- 저장 ---

    @Test
    fun `설명과 공개 범위를 저장하고 설정 폼으로 돌아간다`() {
        val project = newProject()
        val result = save(project, login(manager(project)), "name" to project.name!!, "overview" to "새 설명", "projectScope" to "PRIVATE", *allMenus)
        assertEquals(303, result.response.status)
        assertEquals("/${project.owner}/${project.name}/settingform", result.response.getHeader("Location"))
        val saved = reload(project)
        assertEquals("새 설명", saved.overview)
        assertEquals(ProjectScope.PRIVATE, saved.projectScope)
    }

    @Test
    fun `코드 접근 제한을 저장하고 보내 온 리뷰어 설정은 더 이상 받지 않는다 (D-45)`() {
        val project = newProject()
        save(
            project, login(manager(project)), "name" to project.name!!, "isCodeAccessibleMemberOnly" to "true",
            "isUsingReviewerCount" to "true", "defaultReviewerCount" to "2", *allMenus
        )
        val saved = reload(project)
        assertEquals(true, saved.isCodeAccessibleMemberOnly)
        assertEquals(false, saved.isUsingReviewerCount, "리뷰어 설정은 무력화되어 저장하지 않는다"); assertEquals(1, saved.defaultReviewerCount)
    }

    @Test
    fun `새 커밋의 승인 해제 설정을 저장하고 보내지 않으면 그대로 둔다 (X-12, D-35 5번)`() {
        val project = newProject()
        assertEquals(true, reload(project).isDismissingStaleApprovals, "기본은 켬이다")
        val session = login(manager(project))
        save(project, session, "name" to project.name!!, "isDismissingStaleApprovals" to "false", *allMenus)
        assertEquals(false, reload(project).isDismissingStaleApprovals)
        save(project, session, "name" to project.name!!, *allMenus)
        assertEquals(false, reload(project).isDismissingStaleApprovals, "보내지 않으면 그대로이다")
        save(project, session, "name" to project.name!!, "isDismissingStaleApprovals" to "true", *allMenus)
        assertEquals(true, reload(project).isDismissingStaleApprovals)
    }

    @Test
    fun `설정 화면에 승인 해제 항목이 리뷰어 수 영역에 있다 (X-12, D-35 5번)`() {
        val project = newProject(configure = { isDismissingStaleApprovals = false })
        val doc = org.jsoup.Jsoup.parse(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/${project.owner}/${project.name}/settingform").session(login(manager(project)))).andReturn().response.contentAsString)
        assertEquals("false", doc.select("#reviewerCountSettingPanel input[name=isDismissingStaleApprovals][checked]").single().attr("value"))
        assertEquals(listOf("true", "false"), doc.select("#reviewerCountSettingPanel input[name=isDismissingStaleApprovals]").map { it.attr("value") })
    }

    @Test
    fun `새 프로젝트는 세 병합 방식을 모두 허용하고 기본은 병합 커밋이다 (X-28, D-41)`() {
        val saved = reload(newProject())
        assertEquals(true, saved.allowMergeCommit); assertEquals(true, saved.allowSquashMerge); assertEquals(true, saved.allowRebaseMerge)
        assertEquals("merge", saved.defaultMergeMethod)
    }

    @Test
    fun `병합 방식 설정을 저장하고 보내지 않으면 그대로 둔다 (X-28, D-41)`() {
        val project = newProject(); val session = login(manager(project))
        save(project, session, "name" to project.name!!, "allowMergeCommit" to "false", "allowSquashMerge" to "true", "allowRebaseMerge" to "false", "defaultMergeMethod" to "squash", *allMenus)
        val saved = reload(project)
        assertEquals(false, saved.allowMergeCommit); assertEquals(true, saved.allowSquashMerge); assertEquals(false, saved.allowRebaseMerge); assertEquals("squash", saved.defaultMergeMethod)
        save(project, session, "name" to project.name!!, *allMenus)
        val again = reload(project)
        assertEquals(false, again.allowMergeCommit); assertEquals("squash", again.defaultMergeMethod)
    }

    @Test
    fun `허용한 방식이 하나도 없거나 기본 방식이 허용되지 않았거나 모르는 값이면 거절한다 (X-28, D-41)`() {
        val project = newProject(); val session = login(manager(project))
        assertEquals(400, save(project, session, "name" to project.name!!, "allowMergeCommit" to "false", "allowSquashMerge" to "false", "allowRebaseMerge" to "false", *allMenus).response.status)
        assertEquals(400, save(project, session, "name" to project.name!!, "allowMergeCommit" to "false", "defaultMergeMethod" to "merge", *allMenus).response.status, "기본 방식이 허용되지 않았다")
        assertEquals(400, save(project, session, "name" to project.name!!, "defaultMergeMethod" to "fast-forward", *allMenus).response.status)
        assertEquals(400, save(project, session, "name" to project.name!!, "allowSquashMerge" to "maybe", *allMenus).response.status)
        val unchanged = reload(project)
        assertEquals(true, unchanged.allowMergeCommit); assertEquals("merge", unchanged.defaultMergeMethod)
    }

    @Test
    fun `설정 화면에 병합 방식 허용과 기본 방식이 풀 리퀘스트 설정 영역에 있다 (X-28, D-41)`() {
        val project = newProject(configure = { allowRebaseMerge = false; defaultMergeMethod = "squash" })
        val doc = settingForm(project, login(manager(project)))
        assertEquals(listOf("allowMergeCommit", "allowSquashMerge", "allowRebaseMerge"), doc.select("#mergeMethodSettingPanel input[type=checkbox]").map { it.attr("name") })
        assertEquals(listOf("allowMergeCommit", "allowSquashMerge"), doc.select("#mergeMethodSettingPanel input[type=checkbox][checked]").map { it.attr("name") })
        assertEquals("squash", doc.select("#mergeMethodSettingPanel input[name=defaultMergeMethod][checked]").single().attr("value"))
        assertEquals(listOf("merge", "squash", "rebase"), doc.select("#mergeMethodSettingPanel input[name=defaultMergeMethod]").map { it.attr("value") })
    }

    @Test
    fun `병합 방식 체크박스는 숨은 false 값보다 앞에 있어 체크하면 true를 먼저 보낸다 (X-28)`() {
        val project = newProject()
        val doc = settingForm(project, login(manager(project)))
        listOf("allowMergeCommit", "allowSquashMerge", "allowRebaseMerge").forEach { name ->
            val inputs = doc.select("#mergeMethodSettingPanel input[name=$name]")
            assertEquals(listOf("checkbox", "hidden"), inputs.map { it.attr("type") }, name)
        }
    }

    @Test
    fun `보내지 않은 항목은 그대로 둔다`() {
        val project = newProject(configure = { isCodeAccessibleMemberOnly = true })
        save(project, login(manager(project)), "name" to project.name!!, *allMenus)
        val saved = reload(project)
        assertEquals(true, saved.isCodeAccessibleMemberOnly)
        assertEquals(project.overview, saved.overview)
    }

    @Test
    fun `체크하지 않은 메뉴는 꺼진다`() {
        val project = newProject()
        save(project, login(manager(project)), "name" to project.name!!, "code" to "true", "issue" to "true")
        val menu = menuSettings.findFirstByProjectId(project.id!!)!!
        assertEquals(true, menu.code)
        assertEquals(true, menu.issue)
        assertEquals(false, menu.pullRequest)
        assertEquals(false, menu.review)
        assertEquals(false, menu.milestone)
        assertEquals(false, menu.board)
    }

    @Test
    fun `이름을 바꾸면 저장소 디렉터리를 옮기고 이전 위치를 기록한다`() {
        val project = newProject()
        val oldName = project.name!!
        val before = System.currentTimeMillis()
        val result = save(project, login(manager(project)), "name" to "renamed", *allMenus)
        assertEquals("/${project.owner}/renamed/settingform", result.response.getHeader("Location"))
        val saved = reload(project)
        assertEquals("renamed", saved.name)
        assertEquals(oldName, saved.previousName)
        assertEquals(project.owner, saved.previousOwnerLoginId)
        assertTrue(saved.previousNameChangedTime!! >= before)
        assertTrue(File("build/tmp/it-project-setting/repo/git/${project.owner}/renamed.git").isDirectory)
        assertFalse(File("build/tmp/it-project-setting/repo/git/${project.owner}/$oldName.git").exists())
    }

    @Test
    fun `이름을 바꾸면 즐겨찾기의 이름도 바뀐다`() {
        val project = newProject()
        val fan = newUser()
        favorites.save(FavoriteProject(user = fan, project = project, owner = project.owner, projectName = project.name))
        save(project, login(manager(project)), "name" to "renamed2", *allMenus)
        favorites.flush()
        assertEquals("renamed2", favorites.findAll().single().projectName)
    }

    @Test
    fun `24시간 안에 다시 바꾸면 처음의 이전 위치를 유지한다`() {
        val project = newProject()
        val firstName = project.name!!
        val session = login(manager(project))
        save(project, session, "name" to "second", *allMenus)
        val again = reload(project)
        save(again, session, "name" to "third", *allMenus)
        val saved = reload(project)
        assertEquals("third", saved.name)
        assertEquals(firstName, saved.previousName)
    }

    @Test
    fun `이전 변경이 24시간을 넘었으면 이전 위치를 새로 기록한다`() {
        val project = newProject(configure = { previousName = "ancient"; previousOwnerLoginId = "x"; previousNameChangedTime = System.currentTimeMillis() - 26L * 3_600_000 })
        val oldName = project.name!!
        save(project, login(manager(project)), "name" to "fresh", *allMenus)
        assertEquals(oldName, reload(project).previousName)
    }

    @Test
    fun `이름이 같으면 이전 위치를 건드리지 않는다`() {
        val project = newProject()
        save(project, login(manager(project)), "name" to project.name!!, "overview" to "x", *allMenus)
        assertNull(reload(project).previousName)
    }

    @Test
    fun `슬래시가 있는 브랜치도 기본 브랜치로 선택되어 보인다 (v1_6 itemName)`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray()))
        repositories.openRepository(project).use { repository ->
            repository.updateRef("refs/heads/feature/x").apply { setNewObjectId(repository.resolve(Constants.HEAD)) }.update()
        }
        save(project, login(manager(project)), "name" to project.name!!, "defaultBranch" to "refs/heads/feature/x", *allMenus)
        val selected = settingForm(project, login(manager(project))).select("select#project-default-branch option[selected]").map { it.attr("value") }
        assertEquals(listOf("refs/heads/feature/x"), selected)
    }

    @Test
    fun `기본 브랜치를 바꾼다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray()))
        repositories.openRepository(project).use { repository ->
            repository.updateRef("refs/heads/dev").apply { setNewObjectId(repository.resolve(Constants.HEAD)) }.update()
        }
        save(project, login(manager(project)), "name" to project.name!!, "defaultBranch" to "refs/heads/dev", *allMenus)
        assertEquals("refs/heads/dev", repositories.defaultBranch(project))
    }

    // --- 저장 검증 ---

    private fun warningOf(result: MvcResult): String? = Jsoup.parse(result.response.contentAsString)
        .select("script").map { it.data() }.firstOrNull { "yobi.notify" in it || "yobi.alert" in it }

    @Test
    fun `같은 소유자에 같은 이름이 있으면 거부하고 안내한다`() {
        val project = newProject()
        val other = projects.save(Project(owner = project.owner, name = "Taken", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val result = save(project, login(manager(project)), "name" to "taken", *allMenus)
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("This project name already exists."))
        assertEquals(project.name, reload(project).name)
        assertNotNull(other.id)
    }

    @Test
    fun `이름 형식이 틀리면 거부하고 안내한다`() {
        val project = newProject()
        val result = save(project, login(manager(project)), "name" to "bad name!", *allMenus)
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Enter name in alphabetnumerical or symbol characters(_-.)"))
        assertEquals(project.name, reload(project).name)
    }

    @Test
    fun `예약어 이름은 거부한다`() {
        val project = newProject()
        val result = save(project, login(manager(project)), "name" to ".git", *allMenus)
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("You can&#x27;t use reserved names.") || result.response.contentAsString.contains("You can't use reserved names."))
    }

    @Test
    fun `이름이 없으면 거부한다`() {
        val project = newProject()
        assertEquals(400, save(project, login(manager(project)), *allMenus).response.status)
    }

    @Test
    fun `거부된 폼은 원래 값으로 다시 보여 준다`() {
        val project = newProject()
        val result = save(project, login(manager(project)), "name" to "bad name!", "overview" to "바꾸려던 설명", *allMenus)
        val page = Jsoup.parse(result.response.contentAsString)
        assertEquals(project.name, page.selectFirst("#project-name")?.attr("value"))
        assertEquals(project.overview, page.selectFirst("#project-desc")?.text())
    }

    // --- 로고 ---

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Test
    fun `로고를 올리면 프로젝트 첨부로 저장하고 화면에 쓴다`() {
        val project = newProject()
        val session = login(manager(project))
        save(project, session, "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "logo.png", "image/png", png))
        val stored = attachments.findByContainer(ResourceType.PROJECT, project.id!!)
        assertEquals(listOf("logo.png"), stored.map { it.name })
        val page = settingForm(project, session)
        assertTrue(page.selectFirst(".logo-wrap")!!.attr("style").contains("/files/${stored.single().id}"))
    }

    @Test
    fun `새 로고를 올리면 이전 로고를 지운다`() {
        val project = newProject()
        val session = login(manager(project))
        save(project, session, "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "a.png", "image/png", png))
        save(project, session, "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "b.png", "image/png", png + 9))
        assertEquals(listOf("b.png"), attachments.findByContainer(ResourceType.PROJECT, project.id!!).map { it.name })
    }

    @Test
    fun `파일을 고르지 않으면 로고를 바꾸지 않는다`() {
        val project = newProject()
        val session = login(manager(project))
        save(project, session, "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "a.png", "image/png", png))
        save(project, session, "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "", "application/octet-stream", ByteArray(0)))
        assertEquals(listOf("a.png"), attachments.findByContainer(ResourceType.PROJECT, project.id!!).map { it.name })
    }

    @Test
    fun `이미지가 아닌 파일은 거부한다`() {
        val project = newProject()
        val result = save(project, login(manager(project)), "name" to "keep", *allMenus, logo = MockMultipartFile("logoPath", "doc.txt", "text/plain", "x".toByteArray()))
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("This is not an image file."))
        assertTrue(attachments.findByContainer(ResourceType.PROJECT, project.id!!).isEmpty())
        assertEquals(project.name, reload(project).name)
    }

    @Test
    fun `5MB를 넘는 이미지는 거부한다`() {
        val project = newProject()
        val big = ByteArray(1024 * 1000 * 5 + 1)
        val result = save(project, login(manager(project)), "name" to project.name!!, *allMenus, logo = MockMultipartFile("logoPath", "big.png", "image/png", big))
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Images should be less than 5MB in size."))
    }

    // --- 보안 ---

    @Test
    fun `폼이 보내지 않는 항목은 요청으로 바꿀 수 없다`() {
        val project = newProject()
        val other = newProject()
        save(
            project, login(manager(project)), "name" to project.name!!, "owner" to "hacker", "vcs" to "Subversion",
            "siteurl" to "http://evil", "id" to other.id.toString(), *allMenus
        )
        val saved = reload(project)
        assertEquals(project.owner, saved.owner)
        assertEquals("GIT", saved.vcs)
        assertEquals(project.overview, saved.overview)
        assertEquals(other.overview, reload(other).overview)
    }
}
