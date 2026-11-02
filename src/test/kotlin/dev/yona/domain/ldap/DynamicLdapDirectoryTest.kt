package dev.yona.domain.ldap

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 화면에서 저장한 LDAP 설정이 실제 LDAP 서버(인메모리)에 대한 인증에 쓰이는지 검증한다(D-82). */
@Transactional
class DynamicLdapDirectoryTest : AbstractIntegrationTest() {

    companion object {
        private val server = InMemoryDirectoryServer(InMemoryDirectoryServerConfig("dc=example,dc=com").apply { setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0)) }).also { s ->
            s.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example")
            s.add("dn: ou=user,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: user")
            s.add("dn: CN=alice,ou=user,dc=example,dc=com", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "cn: alice", "sn: alice", "uid: alice", "mail: alice@example.com",
                "displayName: 앨리스", "userPassword: secret-alice")
            s.startListening()
        }

        @JvmStatic
        @AfterAll
        fun stop() = server.shutDown(true)
    }

    @Autowired private lateinit var settings: LdapSettingsService
    @Autowired private lateinit var directory: LdapDirectory

    @Test
    fun `저장한 설정으로 LDAP 서버에 인증한다`() {
        settings.save(
            LdapSettingsForm(true, "ldap", "127.0.0.1", server.listenPort.toString(), "ou=user,dc=example,dc=com", "ou=user,dc=example,dc=com", "uid", "displayName", "CN", "mail", false, false, "givenName"), null
        )
        val user = directory.authenticate("alice", "secret-alice")
        assertNotNull(user); assertEquals("앨리스", user.displayName); assertEquals("alice@example.com", user.email)
    }
}
