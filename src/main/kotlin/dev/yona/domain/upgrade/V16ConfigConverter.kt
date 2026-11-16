package dev.yona.domain.upgrade

/**
 * v1.6(Play 2.3)의 `conf/application.conf`를 v2의 설정 키로 바꾸는 변환기이다. 데이터베이스와 스프링 없이 돌 수 있도록 순수 함수로 만들었고,
 * 이관 스크립트(`scripts/migrate-v16.sh --v16-conf=...`)가 [V16ConfigMain]으로 호출한다. 키 대응은 `architecture/configuration-keys.md`를 따른다.
 *
 * 읽을 수 있는 문법은 v1.6 설정 파일이 실제로 쓰는 HOCON의 부분집합이다.
 * - `a.b = 값`, `a.b : 값` 없이 `=`만 쓴다. 값은 따옴표 문자열이거나 따옴표 없는 한 줄이다.
 * - `a { b = 1 }`처럼 중괄호로 묶은 키(여러 줄, 한 줄, 중첩 모두)는 `a.b`로 펼친다.
 * - `#`과 `//` 주석(따옴표 밖), `include "파일"`(이름만 기록하고 읽지 않는다).
 * - `%prod.키`는 `키`를 덮어쓴다(Play 2.3의 모드별 설정). 다른 모드의 키는 건너뛴다.
 *
 * 목록(`[ ... ]`)이나 치환(`${...}`) 같은 나머지 문법은 해석하지 않고 값 문자열 그대로 둔다. v1.6 기본 설정 파일에서는 Akka 설정에만 나타나며 v2가 쓰지 않는다.
 */
object V16ConfigConverter {

    /**
     * 설정 파일을 읽은 결과이다.
     *
     * @property values 펼친 키(`application.navbar.custom.link.name`)와 값. `%prod.` 키가 반영되어 있다
     * @property includes `include`로 포함한 파일 이름들
     * @property skippedModes `%test.`처럼 prod가 아닌 모드의 키들(적용하지 않았다)
     */
    data class Parsed(val values: Map<String, String>, val includes: List<String>, val skippedModes: List<String>)

    /**
     * 변환 결과이다.
     *
     * @property properties v2 설정 키와 값(키 순서 정렬)
     * @property notes 값을 바꿨거나 확인이 필요한 사항
     * @property unmapped 대응하는 v2 키를 모르는 v1.6 키(직접 확인이 필요하다)
     * @property removed v2에서 기능을 없앤 v1.6 키(옮기지 않는다)
     * @property sensitive 값이 비밀인 v2 키(출력 파일의 권한을 좁혀야 한다)
     */
    data class Converted(
        val properties: Map<String, String>,
        val notes: List<String>,
        val unmapped: List<String>,
        val removed: List<String>,
        val sensitive: Set<String>
    )

    /** v1.6 키 하나가 v2 키 하나로 가는 규칙 중 이름만 바꾸는 것이다. */
    private val RENAMES: Map<String, String> = mapOf(
        "application.siteName" to "yona.site-name",
        "application.secret" to "yona.secret",
        "application.langs" to "yona.langs",
        "application.server" to "yona.server",
        "application.scheme" to "yona.scheme",
        "application.hostname" to "yona.hostname",
        "application.port" to "yona.port",
        "application.context" to "yona.application-context",
        "application.feedback.url" to "yona.feedback-url",
        "application.allowsAnonymousAccess" to "yona.allows-anonymous-access",
        "application.guest.user.login.id.prefix" to "yona.guest-user-login-id-prefix",
        "application.noreferrer" to "yona.noreferrer",
        "application.displayPrivateRepositories" to "yona.display-private-repositories",
        "application.hide.project.listing" to "yona.hide-project-listing",
        "application.show.user.email" to "yona.show-user-email",
        "application.send.yona.usage" to "yona.send-yona-usage",
        "application.maxFileSize" to "yona.max-file-size",
        "application.codeBrowser.viewer.maxFileSize" to "yona.code-browser-max-file-size",
        "application.navbar.custom.link.name" to "yona.navbar-custom-link-name",
        "application.navbar.custom.link.url" to "yona.navbar-custom-link-url",
        "application.login.page.loginId.placeholder" to "yona.login-page-login-id-placeholder",
        "application.login.page.password.placeholder" to "yona.login-page-password-placeholder",
        "project.default.scope.when.create" to "yona.project-default-scope",
        "project.default.menus.when.create" to "yona.project-default-menus",
        "project.creation.default.menus" to "yona.project-default-menus",
        "signup.require.admin.confirm" to "yona.signup.require-admin-confirm",
        "notification.bymail.enabled" to "yona.notification-mail-enabled",
        "application.notification.bymail.hideAddress" to "yona.notification-mail-hide-address",
        "application.notification.bymail.recipientLimit" to "yona.notification-mail-recipient-limit",
        "application.notification.bymail.initdelay" to "yona.notification-mail-init-delay",
        "application.notification.bymail.interval" to "yona.notification-mail-interval",
        "application.notification.bymail.delay" to "yona.notification-mail-delay",
        "application.notification.keep-time" to "yona.notification-keep-time",
        "application.notification.draft-time" to "yona.notification-draft-time",
        "application.update.check.use" to "yona.update.check",
        "application.update.notification.interval" to "yona.update.interval",
        "application.update.repositoryUrl" to "yona.update.repository-url",
        "application.update.releaseUrlFormat" to "yona.update.release-url-format",
        "application.update.notification.initdelay" to "yona.update.init-delay",
        "application.use.email.verification" to "yona.use-email-verification",
        "application.allowed.sending.mail.domains" to "yona.allowed-sending-mail-domains",
        "application.temporaryfiles.keep-up.time" to "yona.temporary-files-keep-up-time",
        "application.mailbox.polling.interval" to "yona.mailbox-polling-interval",
        "application.issue-event.draft-time" to "yona.issue-event-draft-time",
        "application.extras.translation.api" to "yona.translation-api",
        "application.extras.translation.headerKey" to "yona.translation-header-key",
        "application.extras.translation.headerValue" to "yona.translation-header-value",
        "signup.require.confirm" to "yona.signup.require-confirm",
        "data.batch.size" to "yona.data-batch-size",
        "parsers.text.maxLength" to "yona.parsers-text-max-length",
        "play-easymail.from.email" to "yona.easymail.from-email",
        "play-easymail.from.name" to "yona.easymail.from-name",
        "github.allow.migration" to "yona.github.allow-migration",
        "github.client.id" to "yona.github.client-id",
        "github.client.secret" to "yona.github.client-secret",
        "application.use.ldap.login.supoort" to "yona.ldap.enabled",
        "ldap.host" to "yona.ldap.host",
        "ldap.port" to "yona.ldap.port",
        "ldap.baseDN" to "yona.ldap.base-dn",
        "ldap.distinguishedNamePostfix" to "yona.ldap.distinguished-name-postfix",
        "ldap.loginProperty" to "yona.ldap.login-property",
        "ldap.displayNameProperty" to "yona.ldap.display-name-property",
        "ldap.userNameProperty" to "yona.ldap.user-name-property",
        "ldap.emailProperty" to "yona.ldap.email-property",
        "ldap.options.useEmailBaseLogin" to "yona.ldap.use-email-base-login",
        "ldap.options.fallbackToLocalLogin" to "yona.ldap.fallback-to-local-login",
        "ldap.options.englishNameAttributeName" to "yona.ldap.english-name-attribute-name",
        "smtp.user" to "yona.smtp.user",
        "smtp.domain" to "yona.smtp.domain",
        "imap.use" to "yona.imap.use",
        "imap.host" to "yona.imap.host",
        "imap.user" to "yona.imap.user",
        "imap.password" to "yona.imap.password",
        "imap.domain" to "yona.imap.domain",
        "imap.address" to "yona.imap.address",
        "imap.port" to "yona.imap.port",
        "imap.ssl" to "yona.imap.ssl",
        "imap.folder" to "yona.imap.folder",
        "http.port" to "server.port",
        // v1.6의 LDAP 프로토콜은 `ldap.protocol`이 아니라 최상위 `protocol`에서 읽었다(`app/utils/LdapService.java:27`)
        "protocol" to "yona.protocol",
        "db.default.url" to "spring.datasource.url",
        "db.default.user" to "spring.datasource.username",
        "db.default.password" to "spring.datasource.password",
        "smtp.host" to "spring.mail.host",
        "smtp.port" to "spring.mail.port",
        "smtp.ssl" to "spring.mail.properties.mail.smtp.ssl.enable",
        "smtp.password" to "spring.mail.password"
    )

    /** 같은 키가 `smtp.user`처럼 두 곳으로 가는 경우의 덧붙이는 대응이다. */
    private val ALSO: Map<String, String> = mapOf("smtp.user" to "spring.mail.username")

    /** 값이 비밀인 v2 키이다. */
    private val SENSITIVE = setOf("yona.secret", "spring.datasource.password", "spring.mail.password", "yona.imap.password", "yona.github.client-secret", "yona.ldap.bind-password")

    /** v1.6에서는 있었지만 v2에서 기능을 없앤 키이다(`decisions.md` D-127: 소셜 로그인을 없앴다). */
    private val REMOVED = setOf("application.use.social.login.only", "application.use.social.login.name.sync", "application.social.login.support", "application.social.login.github.name")

    /** Play, Akka, Ebean의 내부 설정이라 v2에서 뜻이 없어 조용히 버리는 키의 접두어와 정확한 키이다. */
    private val IGNORED_PREFIXES = listOf("play.", "akka.", "ebean", "applyEvolutions.", "logger.", "application.log", "application.mode")
    private val IGNORED_KEYS = setOf("application.global", "db.default.driver", "db.default.jndiName", "smtp.archive.size")

    /**
     * v1.6의 모의 발송기 설정이다(`smtp.mock`, 기본 설정 파일에 `true`로 들어 있다. 시험용 모의 발송기는 메일을 실제로 보내지 않는다). v2에는 모의 발송기가 없고 메일 서버
     * 접속 정보(`spring.mail.host`)가 있으면 실제로 보낸다. 그래서 모의 발송을 켠 사이트는 접속 정보를 옮기지 않는다. v2는 접속 정보가 없으면 메일을 보내지 않고 경고만 남긴다.
     */
    private const val SMTP_MOCK = "smtp.mock"

    /** 모의 발송을 켜면 옮기지 않는 메일 서버 접속 정보의 v1.6 키이다. 보내는 주소(`smtp.user`, `smtp.domain`)는 모의 발송과 상관없이 옮긴다. */
    private val SMTP_CONNECTION = setOf("smtp.host", "smtp.port", "smtp.ssl", "smtp.password")

    /** `slack.<이벤트>` 키의 접두어이다. 값이 비어 있으면 옮기지 않는다. */
    private const val SLACK = "slack."

    /** v1.6 설정 파일에 적혀 있어도 v1.6 코드가 읽지 않던 키이다. 옮기면 v1.6에서 무시되던 값이 v2에서 효력을 가지므로 옮기지 않는다. */
    private const val INERT_LDAP_PROTOCOL = "ldap.protocol"

    /**
     * v1.6 코드의 기본값이 v2의 기본값과 달라서, 설정 파일에 키가 없을 때 v1.6의 동작을 지키려고 적는 값이다(v2 키 -> v1.6 기본값).
     * v2의 기본값은 v1.6 **기본 설정 파일**의 값을 따르지만(`configuration-keys.md`), 기본 파일에 없는 오래된 설정 파일을 쓰던 사이트는 코드의 기본값으로 동작했다.
     * 업데이트 확인 저장소(`application.update.repositoryUrl`)는 v1.6 코드의 기본 저장소가 더 이상 쓰이지 않아 v2 기본값을 그대로 둔다.
     */
    val V16_DEFAULTS: Map<String, String> = mapOf(
        "yona.noreferrer" to "false",
        "yona.notification-mail-recipient-limit" to "0",
        "yona.update.interval" to "1h"
    )

    /** LDAP을 켰는데 키가 없을 때 v1.6이 쓰던 기본값이다(`app/utils/LdapService.java:23-38`). v2의 기본값은 예시 서버라서 그대로 두면 다른 곳에 접속한다. */
    private val V16_LDAP_DEFAULTS: Map<String, String> = mapOf(
        "yona.ldap.host" to "127.0.0.1",
        "yona.ldap.port" to "389",
        "yona.ldap.base-dn" to "",
        "yona.ldap.distinguished-name-postfix" to "",
        "yona.ldap.login-property" to "sAMAccountName",
        "yona.ldap.display-name-property" to "displayName",
        "yona.ldap.user-name-property" to "CN",
        "yona.ldap.email-property" to "mail",
        "yona.ldap.use-email-base-login" to "false",
        "yona.ldap.fallback-to-local-login" to "false",
        "yona.ldap.english-name-attribute-name" to ""
    )

    /** 변환기가 아는 v1.6 키 전부이다(옮기는 키, 없어진 기능의 키, 읽지 않던 키, 조용히 버리는 정확한 키). 시험이 `YonaProperties` 주석의 v1.6 키와 대조한다. */
    val knownKeys: Set<String> get() = RENAMES.keys + REMOVED + IGNORED_KEYS + INERT_LDAP_PROTOCOL + SMTP_MOCK

    /** v1.6 설정 키의 첫 마디 가운데 이 변환기가 다루는 것이다(시험이 주석에서 v1.6 키만 가려낼 때 쓴다). */
    val v16Roots: Set<String> = setOf("signup", "project", "notification", "play-easymail", "github", "ldap", "smtp", "imap", "data", "parsers", "slack", "db", "http")

    /** 설정 파일 본문을 읽는다. */
    fun parse(text: String): Parsed {
        val base = LinkedHashMap<String, String>()
        val prod = LinkedHashMap<String, String>()
        val includes = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val stack = ArrayDeque<String>()

        fun statement(raw: String) {
            val line = raw.trim()
            if (line.isEmpty()) return
            if (line.startsWith("include ") || line.startsWith("include\t")) {
                includes += line.removePrefix("include").trim().trim('"')
                return
            }
            val eq = indexOfOutsideQuotes(line, '=')
            if (eq < 0) return
            val key = (stack + line.substring(0, eq).trim()).filter { it.isNotEmpty() }.joinToString(".")
            val value = readValue(line.substring(eq + 1).trim())
            when {
                key.startsWith("%prod.") -> prod[key.removePrefix("%prod.")] = value
                key.startsWith("%") -> skipped += key
                else -> base[key] = value
            }
        }

        val buffer = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted -> {
                    buffer.append(c)
                    if (c == '\\' && i + 1 < text.length) { buffer.append(text[++i]) } else if (c == '"') quoted = false
                }
                c == '"' -> { quoted = true; buffer.append(c) }
                c == '#' || (c == '/' && i + 1 < text.length && text[i + 1] == '/' && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '\n')) -> {
                    while (i < text.length && text[i] != '\n') i++
                    continue
                }
                c == '\n' -> { statement(buffer.toString()); buffer.setLength(0) }
                c == '{' -> {
                    val name = buffer.toString().trim().removeSuffix("=").removeSuffix(":").trim()
                    buffer.setLength(0)
                    stack.addLast(name)
                }
                c == '}' -> { statement(buffer.toString()); buffer.setLength(0); if (stack.isNotEmpty()) stack.removeLast() }
                else -> buffer.append(c)
            }
            i++
        }
        statement(buffer.toString())
        base.putAll(prod)
        return Parsed(base, includes, skipped)
    }

    /** 값 문자열을 읽는다. 따옴표로 시작하면 닫는 따옴표까지를 풀어 쓰고, 아니면 따옴표 없는 한 줄 그대로이다. */
    private fun readValue(raw: String): String {
        if (!raw.startsWith("\"")) return raw
        val out = StringBuilder()
        var i = 1
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                // HOCON의 이스케이프(`\"`, `\\`, `\n`, `\t`)만 풀고, 그 밖의 역슬래시는 글자 그대로 둔다
                when (val next = raw[i + 1]) {
                    '"', '\\' -> out.append(next)
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(c).append(next)
                }
                i += 2; continue
            }
            if (c == '"') break
            out.append(c); i++
        }
        return out.toString()
    }

    private fun indexOfOutsideQuotes(line: String, target: Char): Int {
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (quoted) { if (c == '\\') i++ else if (c == '"') quoted = false } else if (c == '"') quoted = true else if (c == target) return i
            i++
        }
        return -1
    }

    /** 읽은 설정을 v2 키로 바꾼다. */
    fun convert(parsed: Parsed): Converted {
        val out = sortedMapOf<String, String>()
        val notes = mutableListOf<String>()
        val unmapped = mutableListOf<String>()
        val removed = mutableListOf<String>()

        val mocked = parsed.values[SMTP_MOCK]?.trim().equals("true", ignoreCase = true)
        if (mocked) {
            notes += "smtp.mock=true라서 메일 서버 접속 정보(smtp.host 등)는 옮기지 않았습니다. v1.6은 모의 발송기라 메일을 실제로 보내지 않았고, v2에는 모의 발송기가 없어 " +
                "접속 정보가 있으면 실제로 보냅니다. 메일을 보내려면 spring.mail.host 등을 직접 설정하세요"
        }
        for ((key, rawValue) in parsed.values) {
            var value = rawValue
            if (mocked && key in SMTP_CONNECTION) continue
            when {
                key == SMTP_MOCK -> continue
                key in REMOVED -> { removed += key; continue }
                key in IGNORED_KEYS || IGNORED_PREFIXES.any { key.startsWith(it) } -> continue
                key.startsWith(SLACK) -> { if (value.isNotBlank()) out["yona.slack.${key.removePrefix(SLACK)}"] = value; continue }
                key == INERT_LDAP_PROTOCOL -> {
                    notes += "ldap.protocol은 v1.6이 읽지 않던 키라 옮기지 않았습니다(v1.6은 최상위 protocol 키를 읽었습니다). " +
                        "LDAPS를 쓰려면 이관 뒤 사이트 관리 > SSO > LDAP에서 프로토콜을 지정하세요"
                    continue
                }
            }
            val target = RENAMES[key]
            if (target == null) { unmapped += key; continue }
            if (key == "db.default.url" && value.startsWith("jdbc:mysql:")) {
                value = value.replaceFirst("jdbc:mysql:", "jdbc:mariadb:")
                notes += "db.default.url의 jdbc:mysql 주소를 jdbc:mariadb로 바꿨습니다(v2는 MariaDB 드라이버를 씁니다)"
            }
            if (key == "db.default.url" && value.startsWith("jdbc:h2:file:")) {
                notes += "db.default.url이 H2 파일 데이터베이스입니다. v1.6의 H2 파일(yona.h2.db, H2 1.3)은 v2의 H2 2.x가 읽지 못하고 MVCC, MV_STORE 같은 옛 옵션도 거부합니다. " +
                    "바꿔치기하면 옆에 빈 데이터베이스가 새로 만들어집니다. v1.6을 켠 채 /sites/export로 내보내 v2의 /sites/import로 가져오거나(옛 사용자 토큰은 옮겨지지 않습니다), " +
                    "v1.6을 MariaDB로 옮긴 뒤 이관하세요"
            }
            out[target] = value
            ALSO[key]?.let { out[it] = value }
        }
        if (parsed.skippedModes.isNotEmpty()) notes += "prod가 아닌 모드의 키는 적용하지 않았습니다: ${parsed.skippedModes.joinToString()}"
        if (parsed.includes.isNotEmpty()) notes += "포함 파일은 읽지 않았습니다(소셜 로그인은 v2에서 없어졌습니다): ${parsed.includes.joinToString()}"
        val pinned = V16_DEFAULTS.filterKeys { it !in out }
        out.putAll(pinned)
        if (pinned.isNotEmpty()) notes += "설정 파일에 없던 키를 v1.6 코드의 기본값으로 고정했습니다(v2 기본값과 달라 그대로 두면 동작이 바뀝니다): " + pinned.entries.joinToString { "${it.key}=${it.value}" }
        if (out["yona.ldap.enabled"] == "true") {
            val filled = V16_LDAP_DEFAULTS.filterKeys { it !in out }
            out.putAll(filled)
            if (filled.isNotEmpty()) notes += "LDAP 설정 파일에 없던 키를 v1.6의 기본값으로 채웠습니다(v2의 기본값은 예시 서버입니다): ${filled.keys.joinToString()}"
            notes += "LDAP이 켜져 있습니다. 이관 단계가 이 값을 사이트 설정(DB, 사이트 관리 > SSO > LDAP)으로 옮기며, 그 뒤에는 화면의 값이 설정 파일보다 우선합니다"
        }
        return Converted(out, notes, unmapped.sorted(), removed, SENSITIVE.filter { it in out }.toSet())
    }

    /** 속성 파일 본문을 만든다. 키 순서가 일정하고 값의 역슬래시와 줄바꿈은 속성 파일 규칙대로 쓴다. */
    fun render(converted: Converted): String = buildString {
        appendLine("# v1.6 conf/application.conf에서 변환한 v2 설정입니다. 비밀번호와 비밀 키가 들어 있으니 이 파일의 권한을 소유자만 읽도록 좁히세요.")
        appendLine("# 서버를 시작할 때 --spring.config.additional-location=file:<이 파일> 로 지정합니다.")
        for ((key, value) in converted.properties) appendLine("$key=${escape(value)}")
    }

    private fun escape(value: String): String {
        val body = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
        return if (body.startsWith(" ")) "\\" + body else body
    }
}
