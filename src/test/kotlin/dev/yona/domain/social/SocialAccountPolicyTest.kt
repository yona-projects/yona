package dev.yona.domain.social

import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 허용 이메일 도메인과 이름 동기화 설정이 켜진 소셜 로그인 규칙이다.
 * 원본: `UserApp.createLocalUserWithOAuth`(`app/controllers/UserApp.java:417-448`), `YonaUserServicePlugin.updateLocalUserName`.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.allowed-sending-mail-domains=corp.test", "yona.social.name-sync=true"])
@Transactional
class SocialAccountPolicyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var social: SocialAccountService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var credentials: UserCredentialRepository

    @Test
    fun `허용되지 않은 이메일 도메인이면 자격 증명을 지우고 안내와 함께 강제 로그아웃한다`() {
        val before = credentials.count()
        val result = social.signIn(SocialIdentity("github", "policy-1", "x@outside.test", "밖"))
        assertNull(result.user)
        assertTrue(result.forceLogout)
        assertEquals("user.unacceptable.email.domain", result.flash["info"])
        assertEquals(before, credentials.count())
        assertNull(users.findByLoginId("x"))
    }

    @Test
    fun `허용된 이메일 도메인이면 가입한다`() {
        assertNotNull(social.signIn(SocialIdentity("github", "policy-2", "y@corp.test", "안")).user)
    }

    @Test
    fun `이름 동기화가 켜져 있으면 제공자의 새 이름으로 자격 증명과 사용자를 고친다`() {
        val id = SocialIdentity("github", "policy-3", "sync@corp.test", "처음")
        social.signIn(id)
        val result = social.signIn(id.copy(name = "바뀐"))
        assertEquals("바뀐", result.user?.name)
        assertEquals("바뀐", users.findByLoginId("sync")?.name)
        assertEquals("바뀐", credentials.findAll().first { it.email == "sync@corp.test" }.name)
    }
}
