package dev.yona.web.site

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.pullrequest.PullRequestReviewBackfill
import dev.yona.domain.project.ReviewerSettingMigration
import dev.yona.domain.site.DataService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.Date
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 데이터 내보내기가 한 시점의 자료를 읽고, 쓰다가 실패하면 성공한 것처럼 끝내지 않음을 검증한다 (R-56-5).
 * v1.6은 내보내는 동안 격리가 없어 테이블 사이가 어긋날 수 있었고, 쓰다가 실패해도 오류를 로그로만 남겨 잘린 파일이 정상 응답으로 갔다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteExportConsistencyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var properties: YonaProperties
    @Autowired private lateinit var backfill: PullRequestReviewBackfill
    @Autowired private lateinit var migration: ReviewerSettingMigration
    @MockitoSpyBean private lateinit var data: DataService

    /** 연결 설정 호출을 기록하는 데이터 소스이다. */
    private fun recordingSource(calls: MutableList<String>) = object : DataSource by dataSource {
        override fun getConnection(): Connection {
            val real = dataSource.connection
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (method.name in setOf("setAutoCommit", "setReadOnly", "setTransactionIsolation", "commit", "rollback")) calls += "${method.name}:${args?.firstOrNull()}"
                try { method.invoke(real, *(args ?: emptyArray())) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            } as Connection
        }
    }

    @Test
    fun `내보내기는 DB에 맞게 읽기 전용 반복 읽기 트랜잭션 하나로 읽거나 기본 격리로 읽는다`() {
        val calls = mutableListOf<String>()
        val service = DataService(recordingSource(calls), properties, backfill, migration)
        service.export(ByteArrayOutputStream())
        val product = dataSource.connection.use { it.metaData.databaseProductName }
        if (service.usesRepeatableRead(product)) {
            assertTrue(calls.contains("setTransactionIsolation:${Connection.TRANSACTION_REPEATABLE_READ}"), "반복 읽기 격리여야 한다: $calls")
            assertTrue(calls.contains("setReadOnly:true"), "읽기 전용이어야 한다: $calls")
            assertTrue(calls.contains("setAutoCommit:false"), "트랜잭션 하나로 읽어야 한다: $calls")
        } else {
            assertTrue(calls.none { it.startsWith("setTransactionIsolation") }, "$product 에서 격리 수준을 바꿨다: $calls")
        }
    }

    /** SQL Server와 Cubrid의 반복 읽기는 쓰는 쪽과 서로 막으므로 쓰지 않는다. MSSQL에서 내려받기가 시간 초과로 실패했던 문제를 막는다. */
    @Test
    fun `잠금 방식 DB에서는 반복 읽기를 쓰지 않는다`() {
        val service = DataService(dataSource, properties, backfill, migration)
        assertEquals(false, service.usesRepeatableRead("Microsoft SQL Server"))
        assertEquals(false, service.usesRepeatableRead("CUBRID"))
        listOf("H2", "PostgreSQL", "MariaDB", "MySQL").forEach { assertEquals(true, service.usesRepeatableRead(it), it) }
    }

    @Test
    fun `쓰다가 실패하면 정상 응답으로 끝내지 않고 오류를 전한다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val admin = users.save(User(loginId = "ex1", name = "ex1", email = "ex1@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        siteAdmins.save(SiteAdmin(admin = admin))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "ex1").param("password", "1234")) }
        Mockito.doThrow(IllegalStateException("쓰다가 실패")).`when`(data).export(Mockito.any(java.io.OutputStream::class.java) ?: ByteArrayOutputStream())
        val started = mockMvc.perform(get("/sites/export").session(session)).andExpect(request().asyncStarted()).andReturn()
        // 오류는 예외로 올라오거나 오류 상태로 응답된다. 정상(200)으로 끝나면 잘린 파일이 성공으로 내려간 것이다
        val outcome = runCatching { mockMvc.perform(asyncDispatch(started)).andReturn().response.status }
        assertTrue(outcome.isFailure || outcome.getOrThrow() >= 500, "정상 응답으로 끝났다: $outcome")
    }
}
