package dev.yona.domain.upgrade

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6 `conf/application.conf`를 v2 설정으로 바꾸는 변환기를 검증한다. 변환기는 데이터베이스나 스프링 없이 돌아야 하므로 순수 함수로 시험한다.
 */
class V16ConfigConverterTest {

    private fun convert(text: String) = V16ConfigConverter.convert(V16ConfigConverter.parse(text))

    @Test
    fun `한 줄 키와 중괄호로 묶은 키는 같은 평탄한 키가 된다`() {
        val flat = V16ConfigConverter.parse("application.noreferrer = true")
        val nested = V16ConfigConverter.parse("application {\n  noreferrer = true\n}")
        assertEquals(mapOf("application.noreferrer" to "true"), flat.values)
        assertEquals(flat.values, nested.values)
    }

    @Test
    fun `깊게 묶은 키와 따옴표 값과 주석과 뒤에 붙은 주석을 읽는다`() {
        val parsed = V16ConfigConverter.parse(
            """
            # 한 줄 주석
            // 이것도 주석
            application.navbar.custom {
                link {
                    name="내 링크"   # 뒤에 붙은 주석
                    url="https://example.com/a?b=1//c"
                }
            }
            smtp.host = smtp.gmail.com
            """.trimIndent()
        )
        assertEquals("내 링크", parsed.values["application.navbar.custom.link.name"])
        assertEquals("https://example.com/a?b=1//c", parsed.values["application.navbar.custom.link.url"])
        assertEquals("smtp.gmail.com", parsed.values["smtp.host"])
    }

    @Test
    fun `포함 파일은 읽지 않고 이름만 알려 준다`() {
        val parsed = V16ConfigConverter.parse("include \"social-login.conf\"\nauthor = 1")
        assertEquals(listOf("social-login.conf"), parsed.includes)
        assertEquals("1", parsed.values["author"])
    }

    @Test
    fun `이스케이프한 따옴표를 풀고 빈 문자열도 값으로 둔다`() {
        val parsed = V16ConfigConverter.parse("a = \"x\\\"y\"\nb = \"\"")
        assertEquals("x\"y", parsed.values["a"])
        assertEquals("", parsed.values["b"])
    }

    @Test
    fun `prod 접두어 키는 접두어를 뗀 키를 덮어쓰고 다른 모드는 건너뛴다`() {
        val parsed = V16ConfigConverter.parse("application.scheme = \"http\"\n%prod.application.scheme = \"https\"\n%test.application.scheme = \"ftp\"")
        assertEquals("https", parsed.values["application.scheme"])
        assertTrue(parsed.skippedModes.contains("%test.application.scheme"))
    }

    @Test
    fun `데이터베이스와 메일과 비밀 키는 스프링 키로 옮긴다`() {
        val out = convert(
            """
            application.secret = "s3cret"
            db.default.url = "jdbc:mysql://db:3306/yona?characterEncoding=utf8"
            db.default.user = yona
            db.default.password = "pw"
            smtp.host = smtp.example.com
            smtp.port = 465
            smtp.ssl = true
            smtp.user = bot
            smtp.password = "mailpw"
            smtp.domain = example.com
            """.trimIndent()
        )
        assertEquals("s3cret", out.properties["yona.secret"])
        assertEquals("jdbc:mariadb://db:3306/yona?characterEncoding=utf8", out.properties["spring.datasource.url"])
        assertEquals("yona", out.properties["spring.datasource.username"])
        assertEquals("pw", out.properties["spring.datasource.password"])
        assertEquals("smtp.example.com", out.properties["spring.mail.host"])
        assertEquals("465", out.properties["spring.mail.port"])
        assertEquals("true", out.properties["spring.mail.properties.mail.smtp.ssl.enable"])
        assertEquals("bot", out.properties["spring.mail.username"])
        assertEquals("mailpw", out.properties["spring.mail.password"])
        assertEquals("bot", out.properties["yona.smtp.user"])
        assertEquals("example.com", out.properties["yona.smtp.domain"])
        assertTrue(out.notes.any { "jdbc:mysql" in it }, "MySQL 주소를 바꿨다고 알려야 한다")
    }

    @Test
    fun `기간 값은 v1_6과 같은 형식 그대로 둔다`() {
        val out = convert("application.notification.bymail.interval = 60s\napplication.update.notification.interval = 6h")
        assertEquals("60s", out.properties["yona.notification-mail-interval"])
        assertEquals("6h", out.properties["yona.update.interval"])
    }

    @Test
    fun `프로덕션 포트는 서버 포트가 되고 LDAP과 슬랙과 깃허브 키를 옮긴다`() {
        val out = convert(
            """
            %prod.http.port=80
            application.use.ldap.login.supoort = true
            ldap { host = "ldap.corp"
              port = 636
              baseDN = "ou=x,dc=corp"
              options { useEmailBaseLogin = true }
            }
            slack { NEW_ISSUE = "https://hooks.example/1"
              NEW_COMMENT = "" }
            github.client.id = "abc"
            """.trimIndent()
        )
        assertEquals("80", out.properties["server.port"])
        assertEquals("true", out.properties["yona.ldap.enabled"])
        assertEquals("ldap.corp", out.properties["yona.ldap.host"])
        assertEquals("636", out.properties["yona.ldap.port"])
        assertEquals("ou=x,dc=corp", out.properties["yona.ldap.base-dn"])
        assertEquals("true", out.properties["yona.ldap.use-email-base-login"])
        assertEquals("https://hooks.example/1", out.properties["yona.slack.NEW_ISSUE"])
        assertNull(out.properties["yona.slack.NEW_COMMENT"], "빈 웹훅 주소는 옮기지 않는다")
        assertEquals("abc", out.properties["yona.github.client-id"])
    }

    @Test
    fun `프로젝트 생성 메뉴는 두 가지 v1_6 키 이름을 모두 받는다`() {
        assertEquals("issue, board", convert("project.creation.default.menus = \"issue, board\"").properties["yona.project-default-menus"])
        assertEquals("code", convert("project.default.menus.when.create = \"code\"").properties["yona.project-default-menus"])
    }

    @Test
    fun `플레이와 액카와 이빈 설정은 조용히 버리고 알 수 없는 키만 알려 준다`() {
        val out = convert(
            """
            play { akka { loglevel = WARNING } }
            akka.loglevel = WARNING
            ebean.default = "models.*"
            applyEvolutions.default = true
            application.global = Global
            db.default.driver = org.h2.Driver
            application.use.social.login.only = false
            smtp.mock = true
            my.custom.key = 1
            """.trimIndent()
        )
        assertEquals(setOf("my.custom.key"), out.unmapped.toSet(), "알 수 없는 키만 알린다(smtp.mock은 따로 다룬다)")
        assertEquals(listOf("application.use.social.login.only"), out.removed, "v2에서 없앤 기능은 따로 알린다")
        assertEquals(V16ConfigConverter.V16_DEFAULTS.keys, out.properties.keys, "조용히 버린 키가 설정으로 새어 나오면 안 된다. 남는 것은 v1.6 기본값 고정뿐이다")
    }

    @Test
    fun `LDAP 프로토콜은 v1_6이 읽던 최상위 protocol 키를 옮기고 읽지 않던 ldap_protocol은 옮기지 않는다`() {
        val out = convert("protocol = \"ldaps\"\nldap.protocol = \"ldaps\"")
        assertEquals("ldaps", out.properties["yona.protocol"])
        assertNull(out.properties["yona.ldap.protocol"], "v1.6이 무시하던 값을 v2에서 효력이 생기게 하면 로그인이 끊길 수 있다")
        assertTrue(out.notes.any { "ldap.protocol" in it }, "옮기지 않은 이유를 알려야 한다")
    }

    @Test
    fun `모의 발송을 켠 v1_6 사이트는 메일 서버 접속 정보를 옮기지 않아 이관 뒤에도 메일을 보내지 않는다`() {
        val out = convert(
            "smtp.host = smtp.gmail.com\nsmtp.port = 465\nsmtp.ssl = true\nsmtp.user = bot\nsmtp.password = \"pw\"\nsmtp.domain = example.com\nsmtp.mock = true\nsmtp.archive.size = 5"
        )
        assertNull(out.properties["spring.mail.host"], "v1.6은 모의 발송이라 아무것도 보내지 않았다. v2에는 모의 발송이 없어 접속 정보가 있으면 실제로 보낸다")
        assertNull(out.properties["spring.mail.password"])
        assertEquals("bot", out.properties["yona.smtp.user"], "보내는 주소 설정은 옮긴다")
        assertEquals("example.com", out.properties["yona.smtp.domain"])
        assertTrue(out.notes.any { "smtp.mock" in it }, "옮기지 않은 이유를 알려야 한다")
        assertTrue(out.unmapped.isEmpty(), "smtp.mock과 archive.size는 아는 키이다: ${out.unmapped}")
    }

    @Test
    fun `모의 발송을 끄면 메일 서버 접속 정보를 옮긴다`() {
        val out = convert("smtp.host = smtp.example.com\nsmtp.port = 25\nsmtp.mock = false")
        assertEquals("smtp.example.com", out.properties["spring.mail.host"])
        assertEquals("25", out.properties["spring.mail.port"])
        assertTrue(out.notes.none { "smtp.mock" in it })
    }

    @Test
    fun `v1_6의 H2 파일 데이터베이스는 v2가 읽지 못한다고 알린다`() {
        val out = convert("db.default.url=\"jdbc:h2:file:./yona;MODE=PostgreSQL;MV_STORE=FALSE;MVCC=FALSE;AUTO_SERVER=TRUE\"")
        val note = out.notes.firstOrNull { "H2" in it }
        assertTrue(note != null, "H2 파일 데이터베이스는 v2(H2 2.x)가 읽지 못하므로 알려야 한다: ${out.notes}")
        assertTrue("migrate-v16-h2.sh" in note, "첫 번째 대안(옛 H2 파일을 직접 읽는 이관 스크립트)을 알려야 한다: $note")
        assertTrue("/sites/export" in note, "다른 대안(v1.6의 내보내기)도 알려야 한다: $note")
        assertTrue(out.notes.none { "H2" in it } || convert("db.default.url=\"jdbc:mariadb://db/yona\"").notes.none { "H2" in it }, "MariaDB 주소에는 알리지 않는다")
        assertTrue(convert("db.default.url=\"jdbc:mysql://db/yona\"").notes.none { "H2" in it })
    }

    @Test
    fun `LDAP을 켰는데 키가 없으면 v1_6의 기본값을 적는다`() {
        val out = convert("application.use.ldap.login.supoort = true\nldap.host = \"ldap.corp\"")
        assertEquals("ldap.corp", out.properties["yona.ldap.host"])
        assertEquals("389", out.properties["yona.ldap.port"], "v1.6 기본 포트")
        assertEquals("", out.properties["yona.ldap.base-dn"], "v1.6 기본 기준 DN은 빈 문자열이다(v2의 예시 값이 아니다)")
        assertEquals("", out.properties["yona.ldap.distinguished-name-postfix"])
        assertEquals("sAMAccountName", out.properties["yona.ldap.login-property"])
        assertEquals("CN", out.properties["yona.ldap.user-name-property"])
        assertEquals("", out.properties["yona.ldap.english-name-attribute-name"])
        assertEquals("127.0.0.1", convert("application.use.ldap.login.supoort = true").properties["yona.ldap.host"], "v1.6 기본 서버 주소")
    }

    @Test
    fun `LDAP이 꺼져 있으면 기본값을 적지 않는다`() {
        assertNull(convert("application.use.ldap.login.supoort = false").properties["yona.ldap.host"])
        assertNull(convert("").properties["yona.ldap.port"])
    }

    @Test
    fun `v1_6 코드의 기본값이 v2 기본값과 다른 설정은 키가 없을 때 v1_6 값으로 고정한다`() {
        val out = convert("")
        assertEquals("false", out.properties["yona.noreferrer"])
        assertEquals("0", out.properties["yona.notification-mail-recipient-limit"])
        assertEquals("1h", out.properties["yona.update.interval"])
        assertTrue(out.notes.any { "yona.noreferrer" in it }, "고정한 키를 알려야 한다")
        val given = convert("application.noreferrer = true\napplication.notification.bymail.recipientLimit = 100")
        assertEquals("true", given.properties["yona.noreferrer"], "설정 파일에 있으면 그 값이 이긴다")
        assertEquals("100", given.properties["yona.notification-mail-recipient-limit"])
    }

    @Test
    fun `옛 설정 파일 전체를 바꾸면 v2가 모르는 yona 키가 하나도 없다`() {
        val text = javaClass.getResource("/v16/application.conf")!!.readText()
        val out = convert(text)
        assertTrue(out.properties.size > 40, "실제 파일에서 많은 키가 옮겨져야 한다: ${out.properties.size}")
        val unknown = out.properties.keys.filter { it.startsWith("yona.") }.filterNot { exists(YonaProperties::class, it.removePrefix("yona.").split('.')) }
        assertEquals(emptyList(), unknown, "v2에 없는 키를 만들었다")
        assertEquals("Yona", out.properties["yona.site-name"])
        assertEquals("projects.yona@gmail.com", out.properties["yona.easymail.from-email"])
        assertEquals("issue, milestone, board", out.properties["yona.project-default-menus"])
        assertTrue(out.removed.contains("application.use.social.login.only"))
        assertEquals(listOf("social-login.conf"), V16ConfigConverter.parse(text).includes)
    }

    @Test
    fun `v1_6이 읽던 나머지 키도 옮긴다`() {
        val out = convert(
            """
            application.use.email.verification = true
            application.allowed.sending.mail.domains = "a.com, b.com"
            application.temporaryfiles.keep-up.time = 12h
            application.mailbox.polling.interval = 10m
            application.issue-event.draft-time = 20s
            application.update.notification.initdelay = 7s
            application.extras.translation.api = "https://t.example/api"
            application.extras.translation.headerKey = "X-Key"
            application.extras.translation.headerValue = "v"
            signup.require.confirm = true
            data.batch.size = 500
            parsers.text.maxLength = 204800
            """.trimIndent()
        )
        assertEquals("true", out.properties["yona.use-email-verification"])
        assertEquals("a.com, b.com", out.properties["yona.allowed-sending-mail-domains"])
        assertEquals("12h", out.properties["yona.temporary-files-keep-up-time"])
        assertEquals("10m", out.properties["yona.mailbox-polling-interval"])
        assertEquals("20s", out.properties["yona.issue-event-draft-time"])
        assertEquals("7s", out.properties["yona.update.init-delay"])
        assertEquals("https://t.example/api", out.properties["yona.translation-api"])
        assertEquals("X-Key", out.properties["yona.translation-header-key"])
        assertEquals("v", out.properties["yona.translation-header-value"])
        assertEquals("true", out.properties["yona.signup.require-confirm"])
        assertEquals("500", out.properties["yona.data-batch-size"])
        assertEquals("204800", out.properties["yona.parsers-text-max-length"])
        assertTrue(out.unmapped.isEmpty(), "모두 옮겨야 한다: ${out.unmapped}")
    }

    @Test
    fun `YonaProperties 주석에 적힌 v1_6 키는 모두 변환기가 알고 있다`() {
        val source = java.io.File("src/main/kotlin/dev/yona/config/YonaProperties.kt").readText()
        val documented = Regex("`([A-Za-z][A-Za-z0-9_.-]*\\.[A-Za-z0-9_.-]+)`").findAll(source).map { it.groupValues[1] }
            .filter { !it.startsWith("yona.") && !it.startsWith("spring.") && !it.endsWith(".kt") && !it.endsWith(".md") }
            .filter { key -> source.lines().any { line -> "`$key`" in line && ("application." in key || key.substringBefore('.') in V16ConfigConverter.v16Roots) } }
            .toSet()
        val unknown = documented.filter { it !in V16ConfigConverter.knownKeys }
        assertEquals(emptyList(), unknown.sorted(), "v2 설정 주석에 v1.6 키로 적혀 있는데 변환기가 모르는 키가 있다. 변환기에 더하거나 이 시험의 예외에 이유와 함께 적는다")
    }

    @Test
    fun `출력은 키 순서가 일정한 속성 파일이고 비밀번호가 있으면 알린다`() {
        val out = convert("application.secret = \"s\"\napplication.siteName = \"A\\B\"")
        val text = V16ConfigConverter.render(out)
        val lines = text.lines().filter { !it.startsWith("#") && it.isNotBlank() }
        assertEquals(lines.sorted(), lines)
        assertTrue("yona.site-name=A\\\\B" in text, "속성 파일의 역슬래시는 두 번 쓴다")
        assertTrue(out.sensitive.contains("yona.secret"))
    }

    /** 점으로 나눈 키 경로(`ldap.base-dn`)가 [type]의 프로퍼티 경로로 있는지 본다. 이름은 케밥 표기를 카멜 표기로 바꿔 비교한다. */
    private fun exists(type: KClass<*>, path: List<String>): Boolean {
        if (type == Map::class) return path.isNotEmpty()
        val name = path.first().split('-').mapIndexed { i, s -> if (i == 0) s else s.replaceFirstChar { it.uppercase() } }.joinToString("")
        val property = type.memberProperties.firstOrNull { it.name == name } ?: return false
        if (path.size == 1) return true
        return exists(property.returnType.classifier as? KClass<*> ?: return false, path.drop(1))
    }
}
