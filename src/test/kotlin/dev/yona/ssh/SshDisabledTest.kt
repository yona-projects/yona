package dev.yona.ssh

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals

/** 설정을 켜지 않으면 SSH 서버가 만들어지지 않고 포트도 열리지 않는다(X-3, `decisions.md` D-66). v1.6의 동작은 그대로이다. */
class SshDisabledTest : AbstractIntegrationTest() {

    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `기본 설정에서는 SSH 서버가 없다`() {
        assertEquals(0, context.getBeanNamesForType(SshServerLifecycle::class.java).size)
        // 인증과 인가 서비스와 처리기는 있어도 포트를 열지 않는다
        assertEquals(1, context.getBeanNamesForType(SshAccess::class.java).size)
    }
}
