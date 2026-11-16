package dev.yona.domain.upgrade

import dev.yona.domain.ldap.LdapSettingsForm
import dev.yona.domain.ldap.LdapSettingsRepository
import dev.yona.domain.ldap.LdapSettingsService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v1.6에서 설정 파일로 켜 둔 LDAP을 사이트 설정(DB, `ldap_settings`)으로 옮기는 이관을 검증한다. v2는 DB 행이 있으면 그것을 쓰고 없으면 설정 파일 값을 쓴다(D-82).
 * 이관 뒤에는 행이 만들어져 사이트 관리 화면에서 고칠 수 있고, 설정 파일이 사라져도 로그인이 끊기지 않는다.
 */
@Transactional
@TestPropertySource(
    properties = [
        "yona.ldap.enabled=true", "yona.ldap.protocol=ldaps", "yona.ldap.host=ldap.corp.example", "yona.ldap.port=636",
        "yona.ldap.base-dn=ou=people,dc=corp,dc=example", "yona.ldap.distinguished-name-postfix=OU=people,DC=corp,DC=example",
        "yona.ldap.login-property=uid", "yona.ldap.display-name-property=cn", "yona.ldap.user-name-property=uid", "yona.ldap.email-property=mail",
        "yona.ldap.use-email-base-login=true", "yona.ldap.fallback-to-local-login=true", "yona.ldap.english-name-attribute-name=givenName"
    ]
)
class LdapSettingsMigrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migration: V16DataMigration
    @Autowired private lateinit var settings: LdapSettingsService
    @Autowired private lateinit var repository: LdapSettingsRepository

    @Test
    fun `설정 파일로 켠 LDAP은 이관하면 사이트 설정 행이 된다`() {
        assertFalse(settings.isSaved())
        assertEquals(1L, migration.pending().ldapSettings, "저장한 적 없는 켜진 LDAP은 미적용 1건이다")

        val result = migration.migrate()

        assertEquals(1, result.ldapSettingsMigrated)
        assertTrue(settings.isSaved())
        val row = repository.findById(1L).get()
        assertEquals(true, row.enabled)
        assertEquals("ldaps", row.protocol)
        assertEquals("ldap.corp.example", row.host)
        assertEquals("636", row.port)
        assertEquals("ou=people,dc=corp,dc=example", row.baseDn)
        assertEquals("uid", row.loginProperty)
        assertEquals(true, row.useEmailBaseLogin)
        assertEquals(true, row.fallbackToLocalLogin)
        assertEquals(0L, migration.pending().ldapSettings)
    }

    @Test
    fun `다시 이관해도 같고 화면에서 고친 설정은 덮어쓰지 않는다`() {
        migration.migrate()
        val saved = repository.findById(1L).get()
        saved.host = "ldap.changed.example"; repository.save(saved)

        val again = migration.migrate()

        assertEquals(0, again.ldapSettingsMigrated)
        assertEquals("ldap.changed.example", repository.findById(1L).get().host, "관리자가 화면에서 고친 값을 지키지 않았다")
    }

    @Test
    fun `이미 화면에서 저장한 사이트는 미적용으로 세지 않는다`() {
        settings.save(
            LdapSettingsForm(false, "ldap", "x.example", "389", "dc=x", "", "uid", "cn", "uid", "mail", false, false, "givenName"), null
        )
        assertEquals(0L, migration.pending().ldapSettings)
    }
}
