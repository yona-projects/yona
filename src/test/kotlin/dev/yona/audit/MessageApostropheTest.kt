package dev.yona.audit

import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertTrue

/**
 * 메시지는 `always-use-message-format: true`로 `MessageFormat`을 거치므로 작은따옴표 하나는 화면에서 사라진다(두 개여야 하나로 보인다). 이번 추가 항목(SSO, LDAP, 연결된 계정, 소셜 차단)의 메시지에는
 * 홀로 있는 작은따옴표가 없어야 한다.
 */
class MessageApostropheTest {

    private val prefixes = listOf("sso.", "site.sso.", "linked.", "social.error.disabled", "button.user.unlinkSso", "userinfo.linkedAccounts", "site.sidebar.sso")

    @Test
    fun `추가 항목의 메시지에는 홀로 있는 작은따옴표가 없다`() {
        val lone = Regex("(?<!')'(?!')")
        val offenders = listOf("messages.properties", "messages_ko_KR.properties", "messages_ja_JP.properties", "messages_ru_RU.properties", "messages_uz_UZ.properties").flatMap { file ->
            val properties = Properties().apply { MessageApostropheTest::class.java.getResourceAsStream("/$file")!!.reader(Charsets.UTF_8).use { load(it) } }
            properties.stringPropertyNames().filter { key -> prefixes.any { key.startsWith(it) } && lone.containsMatchIn(properties.getProperty(key)) }.map { "$file: $it" }
        }
        assertTrue(offenders.isEmpty(), "홀로 있는 작은따옴표가 있는 메시지: $offenders")
    }
}
