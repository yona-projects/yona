package com.github.yonaprojects.yona.domain.site

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository

/**
 * P1-33/34: PostgreSQL 방언 복원 경로가 실제 Testcontainers Postgres로 검증된 적이 없었고
 * (P1-34), importAll()이 백업된 PK를 명시적으로 그대로 INSERT하는 방식은 PostgreSQL의
 * SERIAL/시퀀스를 자동으로 전진시키지 않는다는 알려진 함정이 있다(P1-33) — MariaDB의
 * AUTO_INCREMENT는 명시적 INSERT 값을 보고 스스로 다음 채번을 올리지만, PostgreSQL의
 * nextval()은 완전히 별개로 관리되기 때문에, 복원 직후의 첫 신규 insert가 이미 복원된
 * PK와 충돌할 수 있다. 이 테스트는 그 시나리오를 실제로 재현해 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataBackupServicePostgresIntegrationSpec @Autowired constructor(
    private val dataBackupService: DataBackupService,
    private val userRepository: UserRepository,
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("yona")
            withUsername("yona")
            withPassword("yona_password")
        }

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { postgres.driverClassName }
        }
    }

    private val jdbc: JdbcTemplate by lazy { JdbcTemplate(dataSource) }

    init {
        describe("DataBackupService PostgreSQL 복원 시퀀스 재설정 (P1-33/34)") {
            it("복원된 PK 이후에 저장되는 신규 행이 시퀀스 충돌 없이 저장돼야 한다") {
                // Given: 백업 데이터 자체가 PK=1을 이미 점유하고 있는 상태(디저스터 리커버리 시나리오,
                // 신규 빈 DB에 과거 백업을 그대로 복원하는 경우와 동일)를 구성한다.
                val dump = mapOf(
                    "tables" to mapOf(
                        "n4user" to listOf(
                            mapOf(
                                "id" to 1,
                                "name" to "복원된유저",
                                "login_id" to "restored-user-1",
                                "email" to "restored1@example.com",
                                "remember_me" to false,
                                "is_guest" to false,
                                "state" to "ACTIVE"
                            )
                        )
                    ),
                    // yona export 시점의 "다음 값" 캡처(P2-07) 대응 — id=1을 이미 점유하고 있으므로
                    // 다음으로 배정될 값은 2여야 한다.
                    "sequences" to mapOf("n4user" to 2)
                )
                val backupBytes = objectMapper.writeValueAsBytes(dump)

                // When: 이 백업을 복원한다.
                dataBackupService.importAll(backupBytes)

                val restoredCount = jdbc.queryForObject("SELECT COUNT(*) FROM n4user WHERE id = 1", Int::class.java)
                restoredCount shouldBe 1

                // Then: 복원 직후 시퀀스를 사용하는 신규 저장이 PK 충돌 없이 성공해야 하고,
                // 새로 발급된 id는 이미 복원된 1과 겹치지 않아야 한다.
                val newUser = userRepository.save(
                    User(loginId = "new-after-restore", name = "신규유저", email = "new@example.com")
                )

                newUser.id shouldNotBe null
                newUser.id shouldNotBe 1L
            }
        }
    }
}
