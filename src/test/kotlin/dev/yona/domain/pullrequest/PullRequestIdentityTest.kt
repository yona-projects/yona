package dev.yona.domain.pullrequest

import dev.yona.domain.notification.NotificationMailSender
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시스템이 만드는 병합 커밋의 작성자 메일 주소를 검증한다.
 *
 * 원본: `app/models/Config.java`의 `getSystemEmailAddress`. SMTP 주소가 없으면 `new SiteAdmin().admin`의 메일을 쓰려 하지만
 * 방금 만든 빈 객체의 `admin`은 항상 `null`이어서 언제나 `yona@yona.io`이다.
 */
@Transactional
class PullRequestIdentityTest : AbstractIntegrationTest() {

    @Autowired private lateinit var identity: PullRequestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @MockitoSpyBean private lateinit var mailSender: NotificationMailSender

    @Test
    fun `SMTP 주소가 없으면 사이트 관리자의 메일이 있어도 yona@yona_io이다`() {
        // 초기 데이터의 사이트 관리자(admin, project.yona@gmail.com)가 있어도 쓰지 않는다
        assertTrue(siteAdmins.existsByAdminId(requireNotNull(users.findByLoginId("admin")?.id)))
        Mockito.doReturn(null).`when`(mailSender).fromAddress()
        assertEquals("yona@yona.io", identity.system().emailAddress)
    }

    @Test
    fun `SMTP 주소가 있으면 그것을 쓴다`() {
        Mockito.doReturn("noreply@example.com").`when`(mailSender).fromAddress()
        assertEquals("noreply@example.com", identity.system().emailAddress)
    }
}
