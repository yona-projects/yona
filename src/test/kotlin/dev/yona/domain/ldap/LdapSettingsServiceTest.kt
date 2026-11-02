package dev.yona.domain.ldap

import dev.yona.domain.sso.SsoSaveError
import dev.yona.domain.sso.SsoSaveResult
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * LDAP 설정의 저장과 읽기(`decisions.md` D-82)를 검증한다. 화면에서 한 번도 저장하지 않았으면 설정 파일 값을 그대로 쓰고(v1.6에서 올라온 사이트가 끊기지 않게), 저장하면 그 뒤로는 화면 값이 이긴다.
 * 켜려면 서버 주소와 포트와 기준 DN이 맞아야 한다.
 */
@Transactional
class LdapSettingsServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: LdapSettingsService
    @Autowired private lateinit var repository: LdapSettingsRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private fun form(enabled: Boolean = true, host: String = "ldap.corp.example", port: String = "389", baseDn: String = "ou=people,dc=corp,dc=example") = LdapSettingsForm(
        enabled = enabled, protocol = "ldap", host = host, port = port, baseDn = baseDn, distinguishedNamePostfix = "ou=people,dc=corp,dc=example", loginProperty = "uid", displayNameProperty = "displayName",
        userNameProperty = "uid", emailProperty = "mail", useEmailBaseLogin = false, fallbackToLocalLogin = true, englishNameAttributeName = "givenName"
    )

    @Test
    fun `저장한 적이 없으면 설정 파일 값을 그대로 쓴다`() {
        assertFalse(service.isSaved())
        val effective = service.effective()
        assertFalse(effective.enabled); assertEquals("ldap.forumsys.com", effective.host); assertEquals("ldap", service.protocol())
    }

    @Test
    fun `화면에서 저장하면 그 값이 이긴다`() {
        assertIs<SsoSaveResult.Saved>(service.save(form(), null))
        assertTrue(service.isSaved())
        val effective = service.effective()
        assertTrue(effective.enabled); assertEquals("ldap.corp.example", effective.host); assertEquals("389", effective.port); assertEquals("uid", effective.loginProperty)
        assertTrue(effective.fallbackToLocalLogin); assertEquals("ou=people,dc=corp,dc=example", effective.baseDn)
    }

    @Test
    fun `끄는 저장은 서버 정보가 비어 있어도 되고 끈 상태를 파일보다 우선한다`() {
        service.save(form(), null)
        assertIs<SsoSaveResult.Saved>(service.save(form(enabled = false, host = "", port = "", baseDn = ""), null))
        assertFalse(service.effective().enabled)
    }

    @Test
    fun `켜려면 서버 주소와 포트와 기준 DN이 있어야 한다`() {
        assertEquals(SsoSaveError.LDAP_HOST_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.save(form(host = ""), null)).error)
        assertEquals(SsoSaveError.LDAP_PORT_INVALID, assertIs<SsoSaveResult.Rejected>(service.save(form(port = "abc"), null)).error)
        assertEquals(SsoSaveError.LDAP_PORT_INVALID, assertIs<SsoSaveResult.Rejected>(service.save(form(port = "70000"), null)).error)
        assertEquals(SsoSaveError.LDAP_BASE_DN_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.save(form(baseDn = ""), null)).error)
        assertFalse(service.isSaved())
    }

    @Test
    fun `프로토콜은 ldap과 ldaps만 받고 서버 주소에 이상한 글자가 있으면 거절한다`() {
        assertEquals(SsoSaveError.LDAP_PROTOCOL_INVALID, assertIs<SsoSaveResult.Rejected>(service.save(form().copy(protocol = "http"), null)).error)
        assertEquals(SsoSaveError.LDAP_HOST_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.save(form(host = "ldap://evil.example/x"), null)).error)
        assertIs<SsoSaveResult.Saved>(service.save(form().copy(protocol = "ldaps", port = "636"), null)); assertEquals("ldaps", service.protocol())
    }

    @Test
    fun `서버에 연결할 수 있는지 확인한다`() {
        java.net.ServerSocket(0).use { open ->
            service.save(form(host = "127.0.0.1", port = open.localPort.toString()), null)
            assertEquals(null, service.check())
        }
        service.save(form(host = "127.0.0.1", port = "1"), null)
        assertEquals(SsoSaveError.LDAP_UNREACHABLE, service.check())
    }

    @Test
    fun `설정 변경은 감사 기록에 남는다`() {
        service.save(form(), null)
        assertTrue(logs.findAll().any { it.action == "site.sso_changed" && it.details!!.contains("ldap") })
    }
}
