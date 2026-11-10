package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.util.Hashtable
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실제 LDAP 서버(OpenLDAP, Apache Directory Server)에 대해 [SpringLdapDirectory]가 어떻게 동작하는지 확인한다(로드맵 X-43, `decisions.md` D-132).
 * 메모리 서버(UnboundID) 시험으로는 서버별 접근 규칙(ACL)과 익명 접근 설정의 영향을 알 수 없어서 따로 둔다.
 *
 * `tools/ldap-compat/run.sh`가 컨테이너를 띄우고 `-Dyona.ldap.compat=true`로 이 시험을 켠다. 꺼져 있으면 건너뛴다. 서버와 포트는 스크립트가 정한다.
 *
 * 이 앱은 **익명 접근을 쓰지 않는다**(사용자로 바인드한 뒤 그 연결로 검색한다). 그래서 익명 허용 여부와 상관없이 로그인이 되어야 하고, 대신
 * **로그인한 사용자가 검색 기준 항목을 읽을 수 있어야** 한다는 조건이 서버 설정에 따라 갈린다. 아래 시험은 관찰한 동작을 그대로 고정한 것이다.
 */
@EnabledIfSystemProperty(named = "yona.ldap.compat", matches = "true")
class LdapServerCompatibilityTest {

    private fun directory(port: Int, english: String = "givenName") = SpringLdapDirectory(
        YonaProperties.Ldap(
            enabled = true, host = "127.0.0.1", port = port.toString(), baseDn = "dc=example,dc=com", distinguishedNamePostfix = "ou=user,dc=example,dc=com",
            loginProperty = "uid", displayNameProperty = "displayName", userNameProperty = "CN", emailProperty = "mail", englishNameAttributeName = english
        ),
        protocol = "ldap"
    )

    /** 서버에 익명으로 접속해 기준 항목 아래를 검색할 수 있으면 `true`이다. 접속과 검색이 거부되면 `false`이다. */
    private fun anonymousSearchWorks(port: Int): Boolean = try {
        val env = Hashtable<String, String>().apply {
            put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory"); put("java.naming.provider.url", "ldap://127.0.0.1:$port")
            put("com.sun.jndi.ldap.connect.timeout", "5000"); put("com.sun.jndi.ldap.read.timeout", "5000")
        }
        val context = InitialDirContext(env)
        try {
            context.search("dc=example,dc=com", "(uid=alice)", SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE }).hasMoreElements()
        } finally { context.close() }
    } catch (e: NamingException) { false }

    private fun port(name: String) = requireNotNull(System.getProperty("yona.ldap.compat.$name")) { "yona.ldap.compat.$name 속성이 없다. tools/ldap-compat/run.sh로 실행한다" }.toInt()

    private fun assertLogsIn(port: Int) {
        val user = assertNotNull(directory(port).authenticate("alice", "secret-alice"))
        assertEquals("Alice Kim", user.displayName)
        assertEquals("alice@example.com", user.email)
        assertEquals("alice", user.loginId)
        assertEquals("Alice", user.englishName)
        assertFailsWith<AuthenticationException> { directory(port).authenticate("alice", "wrong") }
        assertFailsWith<AuthenticationException> { directory(port).authenticate("nobody", "x") }
    }

    // --- OpenLDAP ---

    @Test
    fun `OpenLDAP 익명 읽기 허용 서버에서 로그인된다`() {
        val port = port("openldap-allow")
        assertTrue(anonymousSearchWorks(port), "이 서버는 익명 검색을 허용한다")
        assertLogsIn(port)
    }

    @Test
    fun `OpenLDAP 익명 차단 서버에서도 로그인된다 (인증한 사용자는 읽을 수 있는 설정)`() {
        val port = port("openldap-deny")
        assertFalse(anonymousSearchWorks(port), "이 서버는 익명 접속을 막는다")
        assertLogsIn(port)
    }

    @Test
    fun `OpenLDAP 본인 항목만 읽게 한 서버에서는 비밀번호가 맞아도 검색 기준 항목을 읽지 못해 실패한다`() {
        val port = port("openldap-selfonly")
        assertFalse(anonymousSearchWorks(port))
        // 바인드에는 성공하지만 사용자가 기준 항목(dc=example,dc=com)을 볼 수 없어 서버가 '없는 객체'로 답한다. 로그인 서비스는 이를 실패로 처리한다(NamingException)
        assertFailsWith<javax.naming.NameNotFoundException> { directory(port).authenticate("alice", "secret-alice") }
        // 비밀번호가 틀린 것은 여전히 인증 예외이다
        assertFailsWith<AuthenticationException> { directory(port).authenticate("alice", "wrong") }
    }

    @Test
    fun `OpenLDAP은 이메일 형식 이름의 바인드를 받지 않는다`() {
        // Active Directory와 달리 이메일 모양의 이름(UPN)으로 바인드하지 못한다. 이메일로 로그인하려면 서버가 그 형식을 지원해야 한다
        val outcome = runCatching { directory(port("openldap-deny")).authenticate("alice@example.com", "secret-alice") }
        assertNull(outcome.getOrNull(), "이메일 형식 이름으로는 사용자를 돌려주지 않는다")
        assertTrue(outcome.exceptionOrNull() is NamingException, "서버가 바인드를 거부한다: ${outcome.exceptionOrNull()}")
    }

    // --- Apache Directory Server ---

    @Test
    fun `ApacheDS 익명 허용 서버에서 로그인된다`() {
        val port = port("apacheds-allow")
        assertTrue(anonymousSearchWorks(port), "이 서버는 익명 검색을 허용한다")
        assertLogsIn(port)
    }

    @Test
    fun `ApacheDS는 이메일 형식 이름의 바인드를 받지 않는다`() {
        val outcome = runCatching { directory(port("apacheds-allow")).authenticate("alice@example.com", "secret-alice") }
        assertNull(outcome.getOrNull())
        assertTrue(outcome.exceptionOrNull() is NamingException, "서버가 바인드를 거부한다: ${outcome.exceptionOrNull()}")
    }

    @Test
    fun `서버에 연결하지 못하면 통신 예외이다`() {
        assertFailsWith<CommunicationException> { directory(1).authenticate("alice", "secret-alice") }
    }
}
