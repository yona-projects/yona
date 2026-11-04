package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** LDAP 검색 조건과 접속 설정을 검증한다(R-60-2, R-60-10). */
class LdapQueriesTest {

    private val queries = LdapQueries("CN", "OU=user,DC=abc,DC=com", "sAMAccountName", "mail")

    @Test
    fun `검색 조건의 특수 문자는 이스케이프한다 (R-60-2)`() {
        // v1.6은 사용자 입력을 그대로 이어 붙여 `*`나 `)(`로 검색 조건을 바꿀 수 있었다
        assertEquals("(sAMAccountName=alice)", queries.filterOf("alice"))
        assertEquals("(sAMAccountName=a\\2a\\29\\28uid=\\2a)", queries.filterOf("a*)(uid=*"))
        assertEquals("(sAMAccountName=back\\5cslash)", queries.filterOf("back\\slash"))
        assertEquals("(mail=bob@corp.test)", queries.filterOf("bob@corp.test"))
    }

    @Test
    fun `바인드 주체의 이름에 든 DN 구분자는 이스케이프한다 (R-60-2)`() {
        assertEquals("CN=alice,OU=user,DC=abc,DC=com", queries.principalOf("alice"))
        assertEquals("CN=x\\,OU=evil,OU=user,DC=abc,DC=com", queries.principalOf("x,OU=evil").replace("\\=", "="))
        assertEquals("bob@corp.test", queries.principalOf("bob@corp.test"))
    }

    @Test
    fun `응답하지 않는 서버를 위해 읽기와 검색 제한 시간이 있다 (R-60-10)`() {
        val directory = JndiLdapDirectory(YonaProperties.Ldap(), "ldap")
        val env = directory.environmentFor("alice", "pw")
        assertEquals("5000", env["com.sun.jndi.ldap.read.timeout"])
        assertEquals("5000", env["com.sun.jndi.ldap.connect.timeout"])
        assertTrue(directory.searchTimeLimitMillis > 0)
    }
}
