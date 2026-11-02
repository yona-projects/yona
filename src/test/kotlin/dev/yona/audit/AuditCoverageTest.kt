package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 감사 로그의 구조 시험이다(로드맵 X-9 A8, `decisions.md` D-62). 감사해야 하는 변경 메서드가 빠짐없이 기록하는지와, 코드에 쓴 조치 이름이 모두 문서의 이벤트 표에 있는지를 소스에서 확인한다.
 * 새 변경 메서드를 만들면 [AUDITED]에 올리고 기록을 붙여야 이 시험이 통과한다(`ApiScopeRules`가 REST 경로를 시험으로 지키는 방식과 같다).
 */
class AuditCoverageTest {

    private val root = File("src/main/kotlin/dev/yona")

    /** 기록해야 하는 변경 메서드이다: 파일 -> 메서드 이름들. */
    private val AUDITED: Map<String, List<String>> = mapOf(
        "domain/project/ProjectMemberService.kt" to listOf("addMember", "changeRole", "removeMember"),
        "domain/project/ProjectCreateService.kt" to listOf("create"),
        "domain/project/ProjectDeletionService.kt" to listOf("delete"),
        "domain/project/ProjectSettingService.kt" to listOf("update"),
        "domain/project/ProjectTransferService.kt" to listOf("accept"),
        "domain/project/ProjectVcsService.kt" to listOf("change"),
        "domain/pullrequest/ForkService.kt" to listOf("fork"),
        "domain/project/ProjectImportService.kt" to listOf("import"),
        "domain/project/ExternalProjectService.kt" to listOf("create"),
        "domain/project/RulesetService.kt" to listOf("create", "update", "delete"),
        "domain/webhook/WebhookService.kt" to listOf("create", "delete"),
        "domain/project/OrganizationService.kt" to listOf("create"),
        "domain/project/OrganizationManagementService.kt" to listOf("addMember", "editMember", "removeMember", "leave", "update", "delete"),
        "domain/site/SiteUserService.kt" to listOf("deleteUser", "changeState", "toggleGuest", "toggleSiteAdmin"),
        "domain/user/AccountService.kt" to listOf("resetPassword", "addEmail", "deleteEmail", "confirmEmail", "setAsMainEmail", "updateProfile"),
        "domain/user/SignupService.kt" to listOf("create"),
        "domain/apitoken/ApiTokenService.kt" to listOf("create", "update", "regenerate", "delete"),
        "domain/deviceauth/DeviceAuthorizationService.kt" to listOf("approve", "deny"),
        "web/user/PasswordResetController.kt" to listOf("requestReset", "reset", "resetBySiteManager"),
        "web/user/LoginController.kt" to listOf("authenticate", "processLogout"),
        "web/social/SocialLoginController.kt" to listOf("finish"),
        "web/site/SiteDataController.kt" to listOf("export", "import"),
        "web/api/ExternalSiteBackupApiController.kt" to listOf("export", "import"),
        "security/YonaAuthenticationFilter.kt" to listOf("authenticateApi"),
        "web/access/BasicAuthResolver.kt" to listOf("identify"),
        "security/ApiScopeInterceptor.kt" to listOf("deny"),
        "domain/oauth2server/OAuthClientService.kt" to listOf("create", "update", "regenerateSecret", "delete", "deleteAsAdmin"),
        "domain/oauth2server/OAuthAuthorizedAppsService.kt" to listOf("revoke")
    )

    /** 소스에서 [name] 함수의 본문(다음 같은 들여쓰기의 함수가 시작하기 전까지)이다. */
    private fun body(text: String, name: String): String {
        val start = Regex("""\n    (?:private |internal |override |inline )*fun (?:<[^>]+> )?$name\(""").find(text)?.range?.first ?: error("함수를 찾지 못했다: $name")
        val next = Regex("""\n    (?:/\*\*|@|(?:private |internal |override |inline )*fun )""").find(text, start + 5)?.range?.first ?: text.length
        return text.substring(start, next)
    }

    @Test
    fun `감사해야 하는 변경 메서드는 모두 감사 기록을 남긴다`() {
        val missing = AUDITED.flatMap { (file, methods) ->
            val text = File(root, file).readText()
            methods.filter { m -> !Regex("""audit\w*\.(record|recordIndependent|loginSuccess|loginFailure|logout|tokenFailure|basicFailure|blocked|accessDenied|apiScopeDenied)\(""").containsMatchIn(body(text, m)) }.map { "$file#$it" }
        }
        assertEquals(emptyList(), missing, "감사 기록이 없는 변경 메서드이다. `AuditRecorder.record`나 `AuditBoundary`를 부른다")
    }

    @Test
    fun `코드에 쓴 조치 이름은 모두 문서의 이벤트 표에 있다`() {
        val doc = File("yona-v2-wiki/architecture/audit-log.md").readText()
        // 감사 이벤트를 만드는 파일만 훑는다(메시지 키와 섞이지 않게). 조치 이름은 `범주.조치` 두 조각이고 범주는 아래 여덟 가지이다
        val files = AUDITED.keys + listOf("domain/audit/AuditBoundary.kt", "domain/audit/AuditRetention.kt", "web/audit/AuditControllers.kt", "web/api/ExternalAuditApiController.kt")
        val literal = Regex(""""((?:auth|access|account|token|project|org|site|audit)\.[a-z_]+)"""")
        val actions = files.flatMap { f -> literal.findAll(File(root, f).readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertTrue(actions.size >= 45, "조치 이름을 충분히 찾지 못했다: ${actions.size} $actions")
        // 문서는 `project.member_added`, `member_role_changed`처럼 앞 범주를 생략한 줄임 표기도 쓰므로 범주와 조치를 따로 찾는다
        val undocumented = actions.filter { action ->
            val (category, name) = action.split(".", limit = 2)
            !doc.contains("`$action`") && !(doc.contains("`$category.") && doc.contains(Regex("""`(?:$category\.)?$name`""")))
        }
        assertEquals(emptyList(), undocumented, "문서(`architecture/audit-log.md`)의 이벤트 표에 없는 조치 이름이다")
    }

    @Test
    fun `비밀 값을 담을 만한 이름의 상세 키를 코드에서 쓰지 않는다`() {
        // 가리는 규칙(`AuditDetails`)과 별개로, 감사 이벤트를 만드는 줄에 비밀 값 이름이 직접 들어가지 않게 한다
        val forbidden = Regex("""audit\w*\.record\(.*(?:"password"|"secret"|"webhookSecret"|"tokenValue"|"hashString"|"deviceCode")""")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { f -> f.readLines().any { forbidden.containsMatchIn(it) } }.map { it.name }.toList()
        assertEquals(emptyList(), offenders)
    }
}
