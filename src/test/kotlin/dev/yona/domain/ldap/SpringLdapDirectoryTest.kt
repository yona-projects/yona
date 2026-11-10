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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 스프링 시큐리티의 LDAP 부품으로 LDAP 서버에 바인드하고 사용자를 찾는 부분을 인메모리 LDAP 서버로 검증한다(로드맵 X-43, `decisions.md` D-132).
 * 원본: `utils.LdapService`(`app/utils/LdapService.java`). 이전 JNDI 구현의 시험을 그대로 옮겼고(동작이 같아야 한다), 검색 조건의 특수 문자 시험을 더했다.
 */
class SpringLdapDirectoryTest {

    companion object {
        /** 시험용 서버를 만든다. [restrictSearch]이면 검색에 인증이 필요하다(익명 검색을 막은 서버). 조회용 계정 `cn=svc,dc=example,dc=com`(비밀번호 `svc-pw`)이 있다. */
        private fun newServer(restrictSearch: Boolean): InMemoryDirectoryServer = InMemoryDirectoryServer(
            InMemoryDirectoryServerConfig("dc=example,dc=com").apply {
                setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0))
                addAdditionalBindCredentials("cn=svc,dc=example,dc=com", "svc-pw")
                if (restrictSearch) setAuthenticationRequiredOperationTypes(com.unboundid.ldap.sdk.OperationType.SEARCH)
            }
        ).also { s ->
            s.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example")
            s.add("dn: ou=user,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: user")
            fun person(cn: String, login: String, mail: String, display: String, department: String? = null, given: String? = null) = s.add(
                *listOfNotNull(
                    "dn: CN=$cn,ou=user,dc=example,dc=com", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson",
                    "cn: $cn", "sn: $cn", "uid: $login", "mail: $mail", "displayName: $display", "userPassword: secret-$login",
                    department?.let { "departmentNumber: $it" }, given?.let { "givenName: $it" }
                ).toTypedArray()
            )
            person("alice", "alice", "alice@example.com", "앨리스", given = "Alice")
            person("bob", "bob", "bob@example.com", "밥")
            // 같은 로그인 속성(uid) 값을 가진 항목이 둘이면 검색 결과가 둘이다.
            person("shared", "shared", "shared1@example.com", "공유1")
            person("shared2", "shared", "shared2@example.com", "공유2")
            // CN이 로그인 ID와 다른 사용자(실제 Active Directory처럼 CN이 표시 이름인 경우)
            person("Carol Park", "carol", "carol@example.com", "캐롤")
            s.startListening()
        }

        private val server = newServer(restrictSearch = false)
        private val restricted = newServer(restrictSearch = true)

        @JvmStatic
        @AfterAll
        fun stop() { server.shutDown(true); restricted.shutDown(true) }
    }

    private fun directory(
        port: Int = server.listenPort, loginProperty: String = "uid", postfix: String = "ou=user,dc=example,dc=com", english: String = "givenName",
        bindDn: String = "", bindPassword: String = ""
    ) = SpringLdapDirectory(
        YonaProperties.Ldap(
            enabled = true, host = "127.0.0.1", port = port.toString(), baseDn = "dc=example,dc=com", distinguishedNamePostfix = postfix,
            loginProperty = loginProperty, displayNameProperty = "displayName", userNameProperty = "CN", emailProperty = "mail", englishNameAttributeName = english,
            bindDn = bindDn, bindPassword = bindPassword
        ),
        protocol = "ldap"
    )

    private val restrictedPort get() = restricted.listenPort

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

    // --- 먼저 검색해 DN을 찾는 방식(D-133) ---

    @Test
    fun `CN이 로그인 ID와 다른 사용자도 익명 검색으로 DN을 찾아 로그인된다`() {
        val user = assertNotNull(directory().authenticate("carol", "secret-carol"))
        assertEquals("캐롤", user.displayName)
        assertEquals("carol", user.loginId)
    }

    @Test
    fun `이메일로 입력해도 검색 조건이 이메일 속성이라 DN을 찾아 로그인된다`() {
        assertEquals("carol", assertNotNull(directory().authenticate("carol@example.com", "secret-carol")).loginId)
        assertEquals("alice", assertNotNull(directory().authenticate("alice@example.com", "secret-alice")).loginId)
    }

    @Test
    fun `먼저 검색해 찾은 사용자도 비밀번호가 틀리면 인증 예외이다`() {
        assertFailsWith<AuthenticationException> { directory().authenticate("carol", "wrong") }
    }

    @Test
    fun `먼저 검색했을 때 둘 이상이면 null이고 없으면 이전 방식으로 확인해 인증 예외이다`() {
        assertNull(directory().authenticate("shared", "secret-shared"))
        assertFailsWith<AuthenticationException> { directory().authenticate("nobody", "x") }
    }

    @Test
    fun `익명 검색이 막혀 있고 조회용 계정이 없으면 이전 방식으로 로그인된다 (CN이 로그인 ID와 같은 사용자)`() {
        assertEquals("alice", assertNotNull(directory(port = restrictedPort).authenticate("alice", "secret-alice")).loginId)
    }

    @Test
    fun `익명 검색이 막혀 있고 조회용 계정이 없으면 CN이 다른 사용자는 로그인되지 않는다 (이전과 같은 한계)`() {
        assertFailsWith<AuthenticationException> { directory(port = restrictedPort).authenticate("carol", "secret-carol") }
    }

    @Test
    fun `조회용 계정이 있으면 익명이 막힌 서버에서도 CN이 다른 사용자가 로그인된다`() {
        val d = directory(port = restrictedPort, bindDn = "cn=svc,dc=example,dc=com", bindPassword = "svc-pw")
        assertEquals("carol", assertNotNull(d.authenticate("carol", "secret-carol")).loginId)
        assertEquals("carol", assertNotNull(d.authenticate("carol@example.com", "secret-carol")).loginId)
    }

    @Test
    fun `조회용 계정이 있으면 사용자 비밀번호가 틀릴 때 인증 예외이고 없는 사용자는 null이다`() {
        val d = directory(port = restrictedPort, bindDn = "cn=svc,dc=example,dc=com", bindPassword = "svc-pw")
        assertFailsWith<AuthenticationException> { d.authenticate("carol", "wrong") }
        assertNull(d.authenticate("nobody", "x"))
        assertNull(d.authenticate("shared", "secret-shared"), "검색 결과가 둘 이상이면 null이다")
    }

    @Test
    fun `조회용 계정의 비밀번호가 틀리면 사용자 인증 예외가 아니라 일반 오류이다`() {
        val d = directory(port = restrictedPort, bindDn = "cn=svc,dc=example,dc=com", bindPassword = "wrong")
        val e = assertFailsWith<javax.naming.NamingException> { d.authenticate("carol", "secret-carol") }
        assertFalse(e is AuthenticationException, "조회용 계정의 설정 오류를 사용자의 잘못된 비밀번호로 오해하지 않는다: $e")
    }

    @Test
    fun `표시 이름에는 부서가 붙는다`() {
        assertEquals("앨리스 [개발]", LdapUser("앨리스", "a@e.com", "alice", "개발", "").displayNameWithDepartment)
        assertEquals("앨리스", LdapUser("앨리스", "a@e.com", "alice", "", "").displayNameWithDepartment)
    }
}
