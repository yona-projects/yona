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
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository

/**
 * H2(신규 지원 DB) 방언 복원 경로 검증 — DataBackupServicePostgresIntegrationSpec(P1-33/34)과
 * 동일한 시나리오를 H2 기준으로 재현한다. H2는 identity 컬럼을 내부 시퀀스로 관리하는데(2.x),
 * MariaDB의 AUTO_INCREMENT처럼 명시적 INSERT 값을 보고 스스로 다음 채번을 전진시키는지,
 * 아니면 PostgreSQL의 SERIAL처럼 전혀 별개로 관리돼 복원 후 첫 신규 insert가 PK 충돌을
 * 일으키는지 실측으로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataBackupServiceH2IntegrationSpec @Autowired constructor(
    private val dataBackupService: DataBackupService,
    private val userRepository: UserRepository,
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:h2:mem:yona-backup-it-${System.nanoTime()};DB_CLOSE_DELAY=-1" }
            registry.add("spring.datasource.username") { "sa" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.datasource.driver-class-name") { "org.h2.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.H2Dialect" }
        }
    }

    private val jdbc: JdbcTemplate by lazy { JdbcTemplate(dataSource) }

    init {
        describe("DataBackupService H2 복원 시퀀스 재설정") {
            it("복원된 PK 이후에 저장되는 신규 행이 시퀀스 충돌 없이 저장돼야 한다") {
                // H2는 unquoted DDL 식별자를 대문자로 접어 저장한다(listTables()가 실제
                // exportAll()에서 돌려주는 테이블명도 N4USER) — MariaDB/PostgreSQL과 달리
                // JDBC DatabaseMetaData 조회(hasIdColumn())는 SQL 파서처럼 대소문자를 자동으로
                // 접어주지 않아 정확한 케이스가 필요하다.
                val dump = mapOf(
                    "tables" to mapOf(
                        "N4USER" to listOf(
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
                    "sequences" to mapOf("N4USER" to 2)
                )
                val backupBytes = objectMapper.writeValueAsBytes(dump)

                dataBackupService.importAll(backupBytes)

                val restoredCount = jdbc.queryForObject("SELECT COUNT(*) FROM n4user WHERE id = 1", Int::class.java)
                restoredCount shouldBe 1

                val newUser = userRepository.save(
                    User(loginId = "new-after-restore", name = "신규유저", email = "new@example.com")
                )

                newUser.id shouldNotBe null
                newUser.id shouldNotBe 1L
            }
        }
    }
}
