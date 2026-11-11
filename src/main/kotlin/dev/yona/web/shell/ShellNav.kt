package dev.yona.web.shell

import dev.yona.domain.vcs.VcsSupport
import dev.yona.util.UrlPaths

/** 사이드바가 속한 맥락이다(D-138, D-141). 맥락마다 사이드바 하나이다. */
enum class NavContext { HOME, PROJECT, PROJECT_SETTINGS, ORGANIZATION, ORGANIZATION_SETTINGS, USER_SETTINGS, SITE_ADMIN }

/**
 * 사이드바의 항목 하나이다. 묶음 제목이나 하위 항목은 없다(D-141).
 *
 * @property key 항목을 가리키는 안정된 이름이다. 활성 표시와 시험이 쓴다.
 * @property labelKey 화면에 보이는 글자의 메시지 키이다. 이미 있는 v1.6 용어를 그대로 쓴다.
 * @property path 컨텍스트 경로가 붙기 전의 서버 경로이다. 템플릿이 `@{...}`로 접두어를 붙인다.
 * @property shortcut 항목에서 파생되는 한 글자 단축키이다(D-137). 없으면 `null`.
 * @property badge 열린 수나 글 수 같은 배지이다. 0이면 `null`이다.
 */
data class NavItem(val key: String, val labelKey: String, val path: String, val shortcut: Char? = null, val badge: Long? = null)

/** 설정 전용 사이드바의 맨 위에 붙는 "프로젝트로 돌아가기" 같은 단추이다. */
data class NavBack(val labelKey: String, val path: String)

/** 한 맥락의 사이드바이다. 보이는 항목만 담는다. */
data class NavMenu(val context: NavContext, val back: NavBack?, val items: List<NavItem>) {
    /** 항목 이름의 순서 목록이다. */
    fun keys(): List<String> = items.map { it.key }
}

/** 프로젝트 메뉴 켜짐 설정이다(`ProjectMenuSetting`). */
data class ProjectMenuFlags(
    val code: Boolean, val issue: Boolean, val pullRequest: Boolean, val review: Boolean,
    val milestone: Boolean, val board: Boolean, val wiki: Boolean
)

/** 배지에 쓰는 개수이다. */
data class ProjectNavCounts(
    val openIssues: Long = 0, val openPullRequests: Long = 0, val openReviews: Long = 0,
    val postings: Long = 0, val enrolledUsers: Long = 0
)

/**
 * 프로젝트 사이드바를 판정하는 입력이다. 화면 문맥(`ProjectViewContext`)이 이미 가진 값을 그대로 옮긴다.
 *
 * @property pullRequestPath 코드 주고받기 항목의 주소이다. 포크는 보낸 요청 목록이므로 화면 문맥이 정해 준다.
 * @property settingShortcut 프로젝트 관리자일 때만 `Q` 단축키를 준다(`ProjectViewContext.settingShortcutEnabled`).
 */
data class ProjectNavInput(
    val owner: String,
    val name: String,
    val menu: ProjectMenuFlags,
    val codeAccessible: Boolean,
    val canUpdate: Boolean,
    val settingShortcut: Boolean,
    val vcs: VcsSupport,
    val pullRequestPath: String,
    val counts: ProjectNavCounts = ProjectNavCounts()
)

/**
 * 새 UI의 사이드바 항목을 한 곳에서 판정한다(D-138, D-140, D-141, `architecture/ui-ia-v2.md` 4.3절).
 *
 * 항목의 보이는 조건을 템플릿에 흩어 두지 않고 여기서 정한다. 서버의 접근 검사(`ProjectCheckStage.checkMenuEnabled`)가 쓰는
 * 메뉴 설정 값과 같은 값을 쓰므로 "항목은 보이는데 주소는 404"인 어긋남이 생기지 않는다.
 * 순수 함수이며 DB를 읽지 않는다.
 */
object ShellNav {

    private fun badge(count: Long): Long? = count.takeIf { it > 0 }

    /**
     * 프로젝트 사이드바이다. 지금의 가로 메뉴와 같은 이름과 순서의 평평한 목록이다(D-140).
     * 코드의 파일, 커밋, 브랜치, 태그와 코드 주고받기의 열림, 닫힘은 항목이 아니라 화면 안 탭이다.
     */
    fun project(input: ProjectNavInput): NavMenu {
        val base = UrlPaths.projectPath(input.owner, input.name)
        val menu = input.menu
        val items = buildList {
            add(NavItem("home", "title.projectHome", base, 'H'))
            if (menu.code && input.codeAccessible) add(NavItem("code", "menu.code", "$base/code", 'C'))
            if (menu.issue) add(NavItem("issues", "menu.issue", "$base/issues", 'I', badge(input.counts.openIssues)))
            if (menu.pullRequest && input.vcs.pullRequests && input.codeAccessible) {
                add(NavItem("pullRequests", "menu.pullRequest", input.pullRequestPath, 'P', badge(input.counts.openPullRequests)))
            }
            if (menu.review && input.codeAccessible) add(NavItem("reviews", "menu.review", "$base/reviews", null, badge(input.counts.openReviews)))
            if (menu.milestone) add(NavItem("milestones", "milestone", "$base/milestones", 'M'))
            if (menu.board) add(NavItem("board", "menu.board", "$base/posts", 'B', badge(input.counts.postings)))
            if (menu.wiki) add(NavItem("wiki", "menu.wiki", "$base/wiki"))
            if (input.canUpdate) {
                add(NavItem("settings", "menu.admin", "$base/settingform", if (input.settingShortcut) 'Q' else null, badge(input.counts.enrolledUsers)))
            }
        }
        return NavMenu(NavContext.PROJECT, null, items)
    }

    /**
     * 프로젝트 설정 사이드바이다. 설정을 누르면 사이드바가 이 목록으로 바뀌고 맨 위에 프로젝트로 돌아가는 단추가 붙는다(D-141).
     * 라벨은 이슈 메뉴를 꺼도 서버가 주소를 열어 두므로 항상 보인다.
     */
    fun projectSettings(input: ProjectNavInput): NavMenu {
        val base = UrlPaths.projectPath(input.owner, input.name)
        val vcs = input.vcs
        val items = buildList {
            add(NavItem("general", "project.setting", "$base/settingform"))
            add(NavItem("members", "project.member", "$base/members", null, badge(input.counts.enrolledUsers)))
            if (vcs.protectedRefs) add(NavItem("rulesets", "project.ruleset", "$base/setting/rulesets"))
            if (vcs.deployKeys) add(NavItem("deployKeys", "project.deployKey", "$base/setting/deploykeys"))
            if (vcs.lfsScreen) add(NavItem("lfs", if (vcs.lfs) "project.lfs" else "project.lfs.hg", "$base/setting/lfs"))
            add(NavItem("webhooks", "project.webhook", "$base/webhooks"))
            add(NavItem("labels", "issue.label", "$base/issue/labelsform"))
            add(NavItem("transfer", "project.transfer", "$base/transfer"))
            add(NavItem("delete", "project.delete", "$base/deleteform"))
            if (input.menu.code) add(NavItem("changeVcs", "project.changeVCS", "$base/changeVCS"))
        }
        return NavMenu(NavContext.PROJECT_SETTINGS, NavBack("sidebar.back.project", base), items)
    }

    /** 프로젝트 밖(홈과 탐색) 사이드바이다. 목록 숨김이 켜지면 프로젝트와 그룹 항목이 없고, 비로그인은 목록 항목만 있다. */
    fun home(loggedIn: Boolean, hideProjectListing: Boolean): NavMenu {
        val items = buildList {
            if (!hideProjectListing) {
                add(NavItem("projects", "title.projectList", "/projects"))
                add(NavItem("organizations", "title.organization.list", "/orgs"))
            }
            if (loggedIn) {
                add(NavItem("myIssues", "issue.myIssue", "/user/issues"))
                add(NavItem("notifications", "notification", "/"))
                add(NavItem("myFiles", "user.files", "/user/files"))
            }
        }
        return NavMenu(NavContext.HOME, null, items)
    }

    /** 조직 사이드바이다. 설정은 조직 관리자에게만 보인다. */
    fun organization(name: String, canManage: Boolean): NavMenu {
        val base = UrlPaths.organizationPath(name)
        val items = buildList {
            add(NavItem("home", "title.organizationHome", base))
            add(NavItem("issues", "menu.issue", "$base/issues"))
            add(NavItem("boards", "menu.board", "$base/boards"))
            add(NavItem("pullRequests", "menu.pullRequest", "$base/pullrequests"))
            if (canManage) add(NavItem("settings", "menu.admin", "$base/settingform"))
        }
        return NavMenu(NavContext.ORGANIZATION, null, items)
    }

    /** 조직 설정 사이드바이다. 맨 위에 조직으로 돌아가는 단추가 붙는다. */
    fun organizationSettings(name: String): NavMenu {
        val base = UrlPaths.organizationPath(name)
        val items = listOf(
            NavItem("general", "organization.settingFrom", "$base/settingform"),
            NavItem("members", "organization.member", "$base/members"),
            NavItem("oauthApps", "organization.oauthApps", "$base/oauth-apps"),
            NavItem("oauthPolicy", "organization.oauthPolicy", "$base/oauth-policy"),
            NavItem("audit", "organization.audit", "$base/audit"),
            NavItem("delete", "organization.delete", "$base/deleteForm")
        )
        return NavMenu(NavContext.ORGANIZATION_SETTINGS, NavBack("sidebar.back.organization", base), items)
    }

    /** 내 설정 사이드바이다. OAuth 앱은 사용자 앱 사용 설정이 꺼지면 숨긴다(서버가 `new`를 403으로 막는 것과 일치). */
    fun userSettings(oauthAppsEnabled: Boolean): NavMenu {
        val items = buildList {
            add(NavItem("profile", "userinfo.editProfile", "/user/editform"))
            add(NavItem("password", "userinfo.changePassword", "/user/editform/password"))
            add(NavItem("emails", "userinfo.changeEmails", "/user/editform/emails"))
            add(NavItem("linkedAccounts", "userinfo.linkedAccounts", "/user/editform/linked-accounts"))
            add(NavItem("notifications", "userinfo.changeNotifications", "/user/editform/notifications"))
            add(NavItem("tokens", "userinfo.token", "/user/editform/token"))
            add(NavItem("ssh", "userinfo.ssh", "/user/editform/ssh"))
            add(NavItem("gpg", "userinfo.gpg", "/user/editform/gpg"))
            add(NavItem("twoFactor", "userinfo.twoFactor", "/user/editform/two-factor"))
            add(NavItem("audit", "userinfo.audit", "/user/editform/audit"))
            if (oauthAppsEnabled) add(NavItem("oauthApps", "userinfo.oauth", "/user/editform/oauth-apps"))
        }
        return NavMenu(NavContext.USER_SETTINGS, null, items)
    }

    /** 사이트 관리 사이드바이다. `/sites/data`는 링크가 없던 기존 화면의 입구이다. 새 버전이 있으면 업데이트에 배지 1이 붙는다. */
    fun siteAdmin(updateAvailable: Boolean): NavMenu {
        val items = listOf(
            NavItem("users", "site.sidebar.userList", "/sites/userList"),
            NavItem("projects", "site.sidebar.projectList", "/sites/projectList"),
            NavItem("issues", "site.sidebar.issueList", "/sites/issueList"),
            NavItem("posts", "site.sidebar.postList", "/sites/postList"),
            NavItem("mail", "site.sidebar.mailSend", "/sites/mail"),
            NavItem("massMail", "site.sidebar.massMail", "/sites/massmail"),
            NavItem("update", "site.sidebar.update", "/sites/update", null, if (updateAvailable) 1L else null),
            NavItem("diagnostic", "site.sidebar.diagnostics", "/sites/diagnostic"),
            NavItem("data", "site.sidebar.data", "/sites/data"),
            NavItem("audit", "site.sidebar.audit", "/sites/audit"),
            NavItem("security", "site.sidebar.security", "/sites/security"),
            NavItem("sso", "site.sidebar.sso", "/sites/sso"),
            NavItem("oauthApps", "site.sidebar.oauthApps", "/sites/oauth-apps"),
            NavItem("lfs", "site.sidebar.lfs", "/sites/lfs")
        )
        return NavMenu(NavContext.SITE_ADMIN, null, items)
    }
}
