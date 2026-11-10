package dev.yona.oauth2server

import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.support.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 하루에 한 번 도는 정리 작업의 **실제 실행 경로**(`scheduled()`)가 인가를 지우는지 검증한다(`decisions.md` D-71).
 * `purge()`의 시험([OAuth2AuthorizationFlowTest])은 클래스에 걸린 시험 트랜잭션 안에서 프록시를 거쳐 부르므로, `scheduled()`가 같은 객체의 `purge()`를 직접 부를 때
 * 트랜잭션이 없어 지우기가 실패하는 결함을 가린다. 그래서 이 시험은 **트랜잭션 없이** 운영과 같은 경로로 부른다.
 */
class OAuthAuthorizationPurgerScheduleTest : AbstractIntegrationTest() {

    @Autowired private lateinit var purger: OAuthAuthorizationPurger
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository

    @Test
    fun `정해진 시각의 실행은 오래된 미완료 인가를 실제로 지운다`() {
        val id = "purge-schedule-${System.nanoTime()}"
        authorizations.save(OAuthAuthorization(id = id, grantType = "authorization_code", attributes = "{}", createdDate = Date.from(Instant.now().minus(Duration.ofHours(3)))))
        try {
            purger.scheduled()
            assertFalse(authorizations.existsById(id), "정리 작업이 오래된 미완료 인가를 지워야 한다")
        } finally {
            if (authorizations.existsById(id)) authorizations.deleteById(id)
        }
    }

    @Test
    fun `정해진 시각의 실행은 살아 있는 인가를 남긴다`() {
        val id = "purge-keep-${System.nanoTime()}"
        authorizations.save(OAuthAuthorization(id = id, grantType = "authorization_code", attributes = "{}", createdDate = Date()))
        try {
            purger.scheduled()
            assertTrue(authorizations.existsById(id))
        } finally {
            authorizations.deleteById(id)
        }
    }
}
