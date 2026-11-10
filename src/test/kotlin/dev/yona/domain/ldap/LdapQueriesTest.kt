package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** LDAP 바인드 주체와 검색 속성을 검증한다(R-60-2). 검색 조건의 이스케이프는 `SpringLdapDirectoryTest`가 실제 서버로 확인한다. */
class LdapQueriesTest {

    private val queries = LdapQueries("CN", "OU=user,DC=abc,DC=com", "sAMAccountName", "mail")

    @Test
    fun `검색 속성은 이메일이면 이메일 속성이고 아니면 로그인 속성이다`() {
        assertEquals("sAMAccountName", queries.filterAttributeOf("alice"))
        assertEquals("mail", queries.filterAttributeOf("bob@corp.test"))
    }

    @Test
    fun `바인드 주체의 이름에 든 DN 구분자는 이스케이프한다 (R-60-2)`() {
        assertEquals("CN=alice,OU=user,DC=abc,DC=com", queries.principalOf("alice"))
        assertEquals("CN=x\\,OU=evil,OU=user,DC=abc,DC=com", queries.principalOf("x,OU=evil").replace("\\=", "="))
        assertEquals("bob@corp.test", queries.principalOf("bob@corp.test"))
    }
}
