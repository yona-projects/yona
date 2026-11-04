package dev.yona.domain.ldap

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * LDAP 프로토콜 설정 키(R-60-1). v1.6은 `ldap.protocol`이 아니라 접두어 없는 최상위 `protocol` 키만 읽어, 설정 파일에 `ldap.protocol`을 적으면 무시되었다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.protocol=ldaps"])
@Transactional
class LdapProtocolKeyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: LdapSettingsService

    @Test
    fun `설정 파일의 ldap 프로토콜 키를 읽는다 (R-60-1)`() {
        assertEquals("ldaps", service.protocol())
    }
}
