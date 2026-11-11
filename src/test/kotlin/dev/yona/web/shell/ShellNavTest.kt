package dev.yona.web.shell

import dev.yona.domain.vcs.VcsSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 새 UI의 사이드바 항목을 서버가 한 곳의 데이터로 판정하는 [ShellNav]를 검증한다(D-138, D-140, D-141).
 *
 * 항목의 보이는 조건은 템플릿이 아니라 이 판정이 정하므로, 서버의 접근 검사(`ProjectCheckStage.checkMenuEnabled`)와
 * 화면의 항목이 어긋나지 않는다. 이 시험은 `ui-ia-v2.md` 4.3절의 표를 그대로 옮긴 것이다.
 */
class ShellNavTest {

    private val allOn = ProjectMenuFlags(code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true)

    private fun project(
        menu: ProjectMenuFlags = allOn,
        codeAccessible: Boolean = true,
        canUpdate: Boolean = true,
        settingShortcut: Boolean = true,
        vcs: String = "GIT",
        counts: ProjectNavCounts = ProjectNavCounts()
    ) = ProjectNavInput(
        owner = "owner", name = "proj", menu = menu, codeAccessible = codeAccessible, canUpdate = canUpdate,
        settingShortcut = settingShortcut, vcs = VcsSupport(vcs), pullRequestPath = "/owner/proj/pullRequests", counts = counts
    )

    // ---- 프로젝트 메인 사이드바 ----

    @Test
    fun `프로젝트 사이드바는 지금의 가로 메뉴와 같은 이름과 순서의 평평한 목록이다`() {
        val menu = ShellNav.project(project())
        assertEquals(NavContext.PROJECT, menu.context)
        assertEquals(listOf("home", "code", "issues", "pullRequests", "reviews", "milestones", "board", "wiki", "settings"), menu.keys())
        assertEquals(
            listOf("title.projectHome", "menu.code", "menu.issue", "menu.pullRequest", "menu.review", "milestone", "menu.board", "menu.wiki", "menu.admin"),
            menu.items.map { it.labelKey }
        )
        assertNull(menu.back)
    }

    @Test
    fun `프로젝트 항목의 주소와 단축키`() {
        val items = ShellNav.project(project()).items.associateBy { it.key }
        assertEquals("/owner/proj", items.getValue("home").path)
        assertEquals("/owner/proj/code", items.getValue("code").path)
        assertEquals("/owner/proj/issues", items.getValue("issues").path)
        assertEquals("/owner/proj/pullRequests", items.getValue("pullRequests").path)
        assertEquals("/owner/proj/reviews", items.getValue("reviews").path)
        assertEquals("/owner/proj/milestones", items.getValue("milestones").path)
        assertEquals("/owner/proj/posts", items.getValue("board").path)
        assertEquals("/owner/proj/wiki", items.getValue("wiki").path)
        assertEquals("/owner/proj/settingform", items.getValue("settings").path)
        assertEquals(mapOf("home" to 'H', "code" to 'C', "issues" to 'I', "pullRequests" to 'P', "milestones" to 'M', "board" to 'B', "settings" to 'Q'),
            items.filterValues { it.shortcut != null }.mapValues { it.value.shortcut })
    }

    @Test
    fun `이슈와 마일스톤 메뉴를 끄면 항목이 사라진다(서버가 같은 값으로 주소를 404로 막는다)`() {
        val keys = ShellNav.project(project(menu = allOn.copy(issue = false, milestone = false))).keys()
        assertTrue("issues" !in keys && "milestones" !in keys, keys.toString())
    }

    @Test
    fun `게시판과 위키와 코드 주고받기와 리뷰는 각자의 메뉴 설정이 꺼지면 사라진다`() {
        val keys = ShellNav.project(project(menu = allOn.copy(board = false, wiki = false, pullRequest = false, review = false))).keys()
        assertEquals(listOf("home", "code", "issues", "milestones", "settings"), keys)
    }

    @Test
    fun `코드 접근이 막히면 코드와 코드 주고받기와 리뷰가 사라지고 단축키도 없다`() {
        val menu = ShellNav.project(project(codeAccessible = false))
        assertEquals(listOf("home", "issues", "milestones", "board", "wiki", "settings"), menu.keys())
    }

    @Test
    fun `코드 메뉴가 꺼지면 코드 항목이 사라진다`() {
        assertTrue("code" !in ShellNav.project(project(menu = allOn.copy(code = false))).keys())
    }

    @Test
    fun `저장소 종류가 풀 리퀘스트를 지원하지 않으면(Subversion) 코드 주고받기가 사라진다`() {
        val keys = ShellNav.project(project(vcs = "Subversion")).keys()
        assertTrue("pullRequests" !in keys, keys.toString())
        assertTrue("code" in keys && "reviews" in keys)
    }

    @Test
    fun `프로젝트를 수정할 권한이 없으면 설정 항목이 없고 관리자가 아니면 Q 단축키가 없다`() {
        assertTrue("settings" !in ShellNav.project(project(canUpdate = false)).keys())
        val settings = ShellNav.project(project(settingShortcut = false)).items.last()
        assertEquals("settings", settings.key)
        assertNull(settings.shortcut)
    }

    @Test
    fun `열린 수와 글 수와 가입 신청 수는 0보다 클 때만 배지로 붙는다`() {
        val items = ShellNav.project(project(counts = ProjectNavCounts(openIssues = 48, openPullRequests = 3, openReviews = 0, postings = 20, enrolledUsers = 2))).items.associateBy { it.key }
        assertEquals(48L, items.getValue("issues").badge)
        assertEquals(3L, items.getValue("pullRequests").badge)
        assertNull(items.getValue("reviews").badge)
        assertEquals(20L, items.getValue("board").badge)
        assertEquals(2L, items.getValue("settings").badge)
        assertNull(items.getValue("code").badge)
    }

    @Test
    fun `코드 주고받기 주소는 화면 문맥이 준 값을 쓴다(포크는 보낸 요청 목록)`() {
        val input = project().copy(pullRequestPath = "/owner/proj/sentPullRequests")
        assertEquals("/owner/proj/sentPullRequests", ShellNav.project(input).items.first { it.key == "pullRequests" }.path)
    }

    @Test
    fun `소유자와 이름의 특수문자는 주소에서 인코딩된다`() {
        val items = ShellNav.project(project().copy(owner = "홍 길동", name = "a b")).items
        assertEquals("/%ED%99%8D%20%EA%B8%B8%EB%8F%99/a%20b", items.first { it.key == "home" }.path)
    }

    // ---- 프로젝트 설정 사이드바 ----

    @Test
    fun `설정 사이드바는 맨 위에 프로젝트로 돌아가는 단추가 있고 항목은 저장소 종류에 따라 걸러진다`() {
        val menu = ShellNav.projectSettings(project())
        assertEquals(NavContext.PROJECT_SETTINGS, menu.context)
        assertEquals(NavBack("sidebar.back.project", "/owner/proj"), menu.back)
        assertEquals(listOf("general", "members", "rulesets", "deployKeys", "lfs", "webhooks", "labels", "transfer", "delete", "changeVcs"), menu.keys())
        assertEquals(
            listOf("project.setting", "project.member", "project.ruleset", "project.deployKey", "project.lfs", "project.webhook", "issue.label", "project.transfer", "project.delete", "project.changeVCS"),
            menu.items.map { it.labelKey }
        )
    }

    @Test
    fun `Subversion은 규칙과 배포 키와 LFS가 없고 Mercurial은 LFS 이름이 다르다`() {
        val svn = ShellNav.projectSettings(project(vcs = "Subversion"))
        assertEquals(listOf("general", "members", "webhooks", "labels", "transfer", "delete", "changeVcs"), svn.keys())
        val hg = ShellNav.projectSettings(project(vcs = "Mercurial")).items.first { it.key == "lfs" }
        assertEquals("project.lfs.hg", hg.labelKey)
    }

    @Test
    fun `설정 항목의 주소와 가입 신청 배지`() {
        val items = ShellNav.projectSettings(project(counts = ProjectNavCounts(enrolledUsers = 4))).items.associateBy { it.key }
        assertEquals("/owner/proj/settingform", items.getValue("general").path)
        assertEquals("/owner/proj/members", items.getValue("members").path)
        assertEquals(4L, items.getValue("members").badge)
        assertEquals("/owner/proj/setting/rulesets", items.getValue("rulesets").path)
        assertEquals("/owner/proj/setting/deploykeys", items.getValue("deployKeys").path)
        assertEquals("/owner/proj/setting/lfs", items.getValue("lfs").path)
        assertEquals("/owner/proj/webhooks", items.getValue("webhooks").path)
        assertEquals("/owner/proj/issue/labelsform", items.getValue("labels").path)
        assertEquals("/owner/proj/transfer", items.getValue("transfer").path)
        assertEquals("/owner/proj/deleteform", items.getValue("delete").path)
        assertEquals("/owner/proj/changeVCS", items.getValue("changeVcs").path)
    }

    @Test
    fun `저장소 종류 변경은 코드 메뉴가 켜졌을 때만 있다(지금의 설정 탭과 같다)`() {
        assertTrue("changeVcs" !in ShellNav.projectSettings(project(menu = allOn.copy(code = false))).keys())
        assertTrue("transfer" in ShellNav.projectSettings(project(menu = allOn.copy(code = false))).keys())
    }

    @Test
    fun `라벨은 이슈 메뉴를 꺼도 항상 보인다(서버가 라벨 주소를 열어 둔다)`() {
        assertTrue("labels" in ShellNav.projectSettings(project(menu = allOn.copy(issue = false))).keys())
    }

    // ---- 프로젝트 밖(홈) ----

    @Test
    fun `프로젝트 밖 사이드바는 프로젝트, 그룹, 내 이슈, 알림, 내 파일이다`() {
        val menu = ShellNav.home(loggedIn = true, hideProjectListing = false)
        assertEquals(NavContext.HOME, menu.context)
        assertEquals(listOf("projects", "organizations", "myIssues", "notifications", "myFiles"), menu.keys())
        assertEquals(listOf("/projects", "/orgs", "/user/issues", "/", "/user/files"), menu.items.map { it.path })
    }

    @Test
    fun `비로그인은 목록 두 항목만, 목록 숨김이 켜지면 로그인 항목만 보인다`() {
        assertEquals(listOf("projects", "organizations"), ShellNav.home(loggedIn = false, hideProjectListing = false).keys())
        assertEquals(listOf("myIssues", "notifications", "myFiles"), ShellNav.home(loggedIn = true, hideProjectListing = true).keys())
        assertTrue(ShellNav.home(loggedIn = false, hideProjectListing = true).items.isEmpty())
    }

    // ---- 조직 ----

    @Test
    fun `조직 사이드바는 홈, 이슈, 게시판, 코드 주고받기이고 관리자에게만 설정이 있다`() {
        val member = ShellNav.organization("my org", canManage = false)
        assertEquals(listOf("home", "issues", "boards", "pullRequests"), member.keys())
        assertEquals(listOf("/organizations/my%20org", "/organizations/my%20org/issues", "/organizations/my%20org/boards", "/organizations/my%20org/pullrequests"), member.items.map { it.path })
        assertEquals(listOf("home", "issues", "boards", "pullRequests", "settings"), ShellNav.organization("o", canManage = true).keys())
    }

    @Test
    fun `조직 설정 사이드바`() {
        val menu = ShellNav.organizationSettings("o")
        assertEquals(NavContext.ORGANIZATION_SETTINGS, menu.context)
        assertEquals(NavBack("sidebar.back.organization", "/organizations/o"), menu.back)
        assertEquals(listOf("general", "members", "oauthApps", "oauthPolicy", "audit", "delete"), menu.keys())
        assertEquals(listOf("/organizations/o/settingform", "/organizations/o/members", "/organizations/o/oauth-apps", "/organizations/o/oauth-policy", "/organizations/o/audit", "/organizations/o/deleteForm"), menu.items.map { it.path })
    }

    // ---- 내 설정과 사이트 관리 ----

    @Test
    fun `내 설정 사이드바는 열한 항목이고 OAuth 앱은 사용 설정이 꺼지면 숨는다`() {
        val on = ShellNav.userSettings(oauthAppsEnabled = true)
        assertEquals(NavContext.USER_SETTINGS, on.context)
        assertEquals(listOf("profile", "password", "emails", "linkedAccounts", "notifications", "tokens", "ssh", "gpg", "twoFactor", "audit", "oauthApps"), on.keys())
        assertEquals("/user/editform", on.items.first().path)
        assertEquals("/user/editform/two-factor", on.items.first { it.key == "twoFactor" }.path)
        assertTrue("oauthApps" !in ShellNav.userSettings(oauthAppsEnabled = false).keys())
    }

    @Test
    fun `사이트 관리 사이드바는 열네 항목이고 새 버전이 있으면 업데이트에 배지가 붙는다`() {
        val menu = ShellNav.siteAdmin(updateAvailable = true)
        assertEquals(NavContext.SITE_ADMIN, menu.context)
        assertEquals(listOf("users", "projects", "issues", "posts", "mail", "massMail", "update", "diagnostic", "data", "audit", "security", "sso", "oauthApps", "lfs"), menu.keys())
        assertEquals(1L, menu.items.first { it.key == "update" }.badge)
        assertNull(ShellNav.siteAdmin(updateAvailable = false).items.first { it.key == "update" }.badge)
        assertEquals("/sites/data", menu.items.first { it.key == "data" }.path)
    }
}
