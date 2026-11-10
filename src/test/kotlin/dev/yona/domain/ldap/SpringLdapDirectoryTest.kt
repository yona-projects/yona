package dev.yona.domain.ldap

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 스프링 시큐리티의 LDAP 부품으로 LDAP 서버에 바인드하고 사용자를 찾는 부분을 인메모리 LDAP 서버로 검증한다(로드맵 X-43, `decisions.md` D-132).
 * 원본: `utils.LdapService`(`app/utils/LdapService.java`). 이전 JNDI 구현의 시험을 그대로 옮겼고(동작이 같아야 한다), 검색 조건의 특수 문자 시험을 더했다.
 */
class SpringLdapDirectoryTest {

    companion object {
        private val server = InMemoryDirectoryServer(
            InMemoryDirectoryServerConfig("dc=example,dc=com").apply { setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0)) }
        ).also { s ->
            s.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example")
            s.add("dn: ou=user,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: user")
            fun person(cn: String, login: String, mail: String, display: String, department: String? = null, given: String? = null) = s.add(
                *listOfNotNull(
                    "dn: CN=$cn,ou=user,dc=example,dc=com", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson",
                    "cn: $cn", "sn: $cn", "uid: $login", "mail: $mail", "displayName: $display", "userPassword: secret-$cn",
                    department?.let { "departmentNumber: $it" }, given?.let { "givenName: $it" }
                ).toTypedArray()
            )
            person("alice", "alice", "alice@example.com", "앨리스", given = "Alice")
            person("bob", "bob", "bob@example.com", "밥")
            // 같은 로그인 속성(uid) 값을 가진 항목이 둘이면 검색 결과가 둘이다.
            person("shared", "shared", "shared1@example.com", "공유1")
            person("shared2", "shared", "shared2@example.com", "공유2")
            s.startListening()
        }

        @JvmStatic
        @AfterAll
        fun stop() = server.shutDown(true)
    }

    private fun directory(port: Int = server.listenPort, loginProperty: String = "uid", postfix: String = "ou=user,dc=example,dc=com", english: String = "givenName") =
        SpringLdapDirectory(
            YonaProperties.Ldap(
                enabled = true, host = "127.0.0.1", port = port.toString(), baseDn = "dc=example,dc=com", distinguishedNamePostfix = postfix,
                loginProperty = loginProperty, displayNameProperty = "displayName", userNameProperty = "CN", emailProperty = "mail", englishNameAttributeName = english
            ),
            protocol = "ldap"
        )

    @Test
    fun `바인드에 성공하면 사용자 속성을 담아 돌려준다`() {
        val user = assertNotNull(directory().authenticate("alice", "secret-alice"))
        assertEquals("앨리스", user.displayName)
        assertEquals("alice@example.com", user.email)
        assertEquals("alice", user.loginId)
        assertEquals("Alice", user.englishName)
    }

    @Test
    fun `영문 이름 속성이 비어 있으면 읽지 않는다`() {
        assertEquals("", assertNotNull(directory(english = "").authenticate("alice", "secret-alice")).englishName)
    }

    @Test
    fun `비밀번호가 틀리면 인증 예외이다`() {
        assertFailsWith<AuthenticationException> { directory().authenticate("alice", "wrong") }
    }

    @Test
    fun `서버에 연결하지 못하면 통신 예외이다`() {
        assertFailsWith<CommunicationException> { directory(port = 1).authenticate("alice", "secret-alice") }
    }

    @Test
    fun `로그인 속성으로 찾은 사용자가 없으면 null이다`() {
        // 바인드는 CN으로 하고 검색은 로그인 속성으로 한다. 속성 값이 다르면 찾지 못한다.
        assertNull(directory(loginProperty = "sAMAccountName").authenticate("bob", "secret-bob"))
    }

    @Test
    fun `검색 결과가 둘 이상이면 null이다`() {
        assertNull(directory().authenticate("shared", "secret-shared"))
    }

    @Test
    fun `이메일 형식의 이름은 그대로 바인드 주체로 쓰고 이메일 속성으로 찾는다`() {
        val queries = LdapQueries(userNameProperty = "CN", distinguishedNamePostfix = "ou=user,dc=example,dc=com", loginProperty = "sAMAccountName", emailProperty = "mail")
        assertEquals("a@b.c", queries.principalOf("a@b.c"))
        assertEquals("mail", queries.filterAttributeOf("a@b.c"))
        assertEquals("CN=alice,ou=user,dc=example,dc=com", queries.principalOf("alice"))
        assertEquals("sAMAccountName", queries.filterAttributeOf("alice"))
    }

    @Test
    fun `이름에 검색 조건의 특수 문자를 넣어도 다른 사용자로 인증되지 않는다 (R-60-2)`() {
        // 바인드 주체가 CN=*,... 이라 바인드가 실패하거나, 검색 조건의 *가 이스케이프되어 모든 항목과 일치하지 않아야 한다
        val outcome = runCatching { directory().authenticate("*", "secret-alice") }
        assertNull(outcome.getOrNull(), "특수 문자 이름으로는 사용자를 돌려주지 않는다")
        assertNull(runCatching { directory().authenticate("alice)(uid=*", "secret-alice") }.getOrNull())
    }

    @Test
    fun `연결과 읽기 제한 시간이 있다 (R-60-10)`() {
        val directory = SpringLdapDirectory(YonaProperties.Ldap(), "ldap")
        assertEquals("5000", directory.baseEnvironment["com.sun.jndi.ldap.read.timeout"])
        assertEquals("5000", directory.baseEnvironment["com.sun.jndi.ldap.connect.timeout"])
        assertEquals(5000, directory.searchTimeLimitMillis)
    }

    @Test
    fun `표시 이름에는 부서가 붙는다`() {
        assertEquals("앨리스 [개발]", LdapUser("앨리스", "a@e.com", "alice", "개발", "").displayNameWithDepartment)
        assertEquals("앨리스", LdapUser("앨리스", "a@e.com", "alice", "", "").displayNameWithDepartment)
    }
}
