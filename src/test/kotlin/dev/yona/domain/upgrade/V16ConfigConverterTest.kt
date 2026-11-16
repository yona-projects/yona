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
        assertEquals(setOf("my.custom.key", "smtp.mock"), out.unmapped.toSet(), "알 수 없는 키만 알린다")
        assertEquals(listOf("application.use.social.login.only"), out.removed, "v2에서 없앤 기능은 따로 알린다")
        assertTrue(out.properties.isEmpty())
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
