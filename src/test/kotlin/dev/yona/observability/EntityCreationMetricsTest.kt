package dev.yona.observability

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

/**
 * 협업 객체의 생성 수(로드맵 X-10 C5d, A-320 6번)를 검증한다. 서비스마다 흩어진 생성 경로를 따로 세지 않고, 커밋된 삽입을 한 곳에서 허용 목록의 엔티티만 센다
 * (`yona.entities.created{entity}`). 롤백된 삽입은 세지 않는다. 이름과 번호는 태그에 넣지 않는다.
 */
class EntityCreationMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private fun created(entity: String) = registry.find("yona.entities.created").tag("entity", entity).counter()?.count() ?: 0.0

    private fun newUser(n: Int): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return User(loginId = "zzent$n-${System.nanoTime()}", name = "생성$n", email = "zzent$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))
    }

    @Test
    fun `커밋된 삽입을 엔티티별로 센다`() {
        val before = created("user")
        val saved = tx.execute { users.save(newUser(1)) }!!
        try {
            assertEquals(before + 1, created("user"))
        } finally {
            users.delete(saved)
        }
    }

    @Test
    fun `롤백된 삽입은 세지 않는다`() {
        val before = created("user")
        tx.execute { status -> users.save(newUser(2)); status.setRollbackOnly() }
        assertEquals(before, created("user"))
    }

    @Test
    fun `허용 목록에 없는 엔티티는 세지 않고 태그 값이 고정이다`() {
        val tags = registry.meters.filter { it.id.name == "yona.entities.created" }.mapNotNull { it.id.getTag("entity") }
        assertEquals(true, tags.all { it in EntityCreationMetrics.ENTITIES.values }, tags.toString())
        assertEquals(true, EntityCreationMetrics.ENTITIES.values.containsAll(listOf("project", "user", "issue", "issue_comment", "post", "post_comment", "pull_request", "review_comment", "attachment")))
    }

    @Test
    fun `엔티티 이름은 클래스 이름으로 찾고 모르면 null이다`() {
        assertEquals("issue", EntityCreationMetrics.ENTITIES["Issue"])
        assertEquals(null, EntityCreationMetrics.ENTITIES["Role"])
    }
}
