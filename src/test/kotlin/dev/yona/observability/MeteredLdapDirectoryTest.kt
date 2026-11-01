package dev.yona.observability

import dev.yona.domain.ldap.LdapDirectory
import dev.yona.domain.ldap.LdapUser
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** LDAP 인증의 지연과 실패 지표(로드맵 X-10 C5d)를 검증한다. LDAP이 느리면 로그인이 멈추므로 결과별 시간이 필요하다. 사용자 이름은 태그에 넣지 않는다. */
class MeteredLdapDirectoryTest {

    private val registry = SimpleMeterRegistry()

    private fun count(result: String) = registry.find("yona.ldap.authenticate").tag("result", result).timer()?.count() ?: 0L
    private fun metered(block: (String, String) -> LdapUser?) = MeteredLdapDirectory(LdapDirectory { u, p -> block(u, p) }, registry)

    @Test
    fun `사용자를 찾으면 success이다`() {
        val user = LdapUser("Alice", "a@example.com", "alice", "dev", "Alice")
        assertEquals(user, metered { _, _ -> user }.authenticate("alice", "pw"))
        assertEquals(1, count("success"))
    }

    @Test
    fun `사용자를 못 찾으면 not_found이다`() {
        assertNull(metered { _, _ -> null }.authenticate("ghost", "pw"))
        assertEquals(1, count("not_found"))
    }

    @Test
    fun `인증 실패와 연결 실패와 그 밖의 오류를 나누고 예외를 다시 던진다`() {
        assertFailsWith<AuthenticationException> { metered { _, _ -> throw AuthenticationException("bad") }.authenticate("u", "p") }
        assertFailsWith<CommunicationException> { metered { _, _ -> throw CommunicationException("down") }.authenticate("u", "p") }
        assertFailsWith<NamingException> { metered { _, _ -> throw NamingException("other") }.authenticate("u", "p") }
        assertEquals(1, count("auth_failure")); assertEquals(1, count("unreachable")); assertEquals(1, count("error"))
    }

    @Test
    fun `태그에 사용자 이름이 들어가지 않는다`() {
        metered { _, _ -> null }.authenticate("zzldap-secret-user", "pw")
        assertEquals(emptyList(), registry.meters.flatMap { m -> m.id.tags.map { it.value } }.filter { it.contains("zzldap-secret-user") })
    }
}
