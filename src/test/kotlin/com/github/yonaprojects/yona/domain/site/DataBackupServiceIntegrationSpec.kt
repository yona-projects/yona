package com.github.yonaprojects.yona.domain.site

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import javax.sql.DataSource

/**
 * 실제 MariaDB(Testcontainers)를 대상으로 DataBackupService의 export→import
 * 전체 왕복이 실제 DB 메타데이터/FK 제약과 함께 정상 동작하는지 검증한다.
 * (순수 목 객체로는 "실제 DB 스키마/방언에서 동작하는가"를 검증할 수 없어 통합테스트로 작성)
 */
class DataBackupServiceIntegrationSpec @Autowired constructor(
    private val dataBackupService: DataBackupService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    private val dataSource: DataSource
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private val jdbc: JdbcTemplate by lazy { JdbcTemplate(dataSource) }

    init {
        // 이 스펙은 MariaDB 전용 검증 SQL(INFORMATION_SCHEMA.TABLES.AUTO_INCREMENT, DATABASE())을
        // 직접 쓴다 — PostgreSQL 등 다른 DB 기준으로 전체 스위트를 돌릴 때는(-Dyona.it.db=postgres)
        // 같은 메커니즘을 DataBackupServicePostgresIntegrationSpec이 방언에 맞는 SQL로 이미
        // 별도 검증하므로, 여기서는 MariaDB(기본값)일 때만 실행한다.
        if (System.getProperty("yona.it.db", "mariadb") == "mariadb") {
        describe("DataBackupService export/import 왕복") {
            it("백업 시점 이후에 추가된 데이터는 해당 백업으로 복원하면 사라져야 한다") {
                // Given: 기준 시점 데이터
                val baseline = userRepository.save(
                    User(loginId = "backup-baseline-user", name = "기준사용자", email = "baseline@example.com")
                )
                projectRepository.save(
                    Project(name = "backup-baseline-project", owner = "backup-baseline-user", projectScope = ProjectScope.PUBLIC)
                )

                val backup = dataBackupService.exportAll()

                // When: 기준 시점 이후 데이터가 더 추가됨
                userRepository.save(
                    User(loginId = "backup-extra-user", name = "추가사용자", email = "extra@example.com")
                )

                val countBeforeRestore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id IN ('backup-baseline-user', 'backup-extra-user')",
                    Int::class.java
                )
                countBeforeRestore shouldBe 2

                // 백업 시점으로 복원
                dataBackupService.importAll(backup)

                // Then: 백업 이후에 추가된 사용자는 사라지고, 기준 시점 사용자는 남아있어야 한다
                val baselineExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id = 'backup-baseline-user'",
                    Int::class.java
                )
                val extraExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id = 'backup-extra-user'",
                    Int::class.java
                )

                baselineExists shouldBe 1
                extraExists shouldBe 0

                val restoredProjectCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE name = 'backup-baseline-project'",
                    Int::class.java
                )
                restoredProjectCount shouldBe 1
            }

            it("exportAll이 만든 백업에는 여러 테이블이 포함되어야 한다 (users/projects 전용이 아님)") {
                val backup = dataBackupService.exportAll()
                val json = String(backup, Charsets.UTF_8)

                // n4user/project 외에도 role, organization 같은 다른 테이블이 최소 포함돼야 한다
                (json.contains("\"role\"") || json.contains("\"n4user\"")) shouldBe true
                (json.contains("\"organization\"")) shouldBe true
            }

            // yona DefaultExchanger.exportData()/importSequence() 대응 (P2-07) — export 시점에 실제
            // DB가 갖고 있던 auto-increment "다음 값"을 그대로 캡처했다가 복원 시 그 값으로 되돌려야
            // 한다(백업된 행들의 max(id)+1을 복원 시점에 재계산하는 게 아니라). 복원 사이에 카운터가
            // 임의로 바뀌어도(다른 프로세스가 손댔거나, 이전 구현처럼 잘못 재계산됐거나) export 시점
            // 값으로 정확히 되돌아오는지를 직접 확인해 "캡처된 값을 그대로 복원한다"는 메커니즘 자체를
            // 증명한다(우연히 max(id)와 일치해서 통과하는 약한 검증이 되지 않도록).
            it("복원 시 auto-increment가 export 시점에 캡처해둔 값으로 정확히 되돌아가야 한다(재계산이 아님)") {
                userRepository.save(
                    User(loginId = "seq-capture-user", name = "시퀀스캡처유저", email = "seq-capture@example.com")
                )

                val nextValueAtExport = jdbc.queryForObject(
                    "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'n4user'",
                    Long::class.java
                )

                val backup = dataBackupService.exportAll()

                // 복원 사이에 카운터를 의도적으로 훨씬 낮은 값으로 흐트러뜨려둔다 — 만약 복원 로직이
                // (이전 구현처럼) 백업된 행의 max(id)+1을 다시 계산하는 방식이라면 이 훼손과 무관하게
                // 우연히 비슷한 값이 나올 수 있지만, "캡처된 값을 그대로 복원"하는 올바른 메커니즘이라면
                // 이 훼손 여부와 무관하게 항상 export 시점 값으로 정확히 돌아와야 한다.
                jdbc.execute("ALTER TABLE n4user AUTO_INCREMENT = 1")

                dataBackupService.importAll(backup)

                val nextValueAfterRestore = jdbc.queryForObject(
                    "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'n4user'",
                    Long::class.java
                )

                nextValueAfterRestore shouldBe nextValueAtExport
            }

            // "가끔 전체 스위트에서만 실패하는 flake"로 보였던 문제의 실체 — datetime(Instant) 컬럼에
            // 실제 값(NULL이 아닌)이 있는 행이 하나라도 있으면 100% 결정적으로 재현되는 버그였다.
            // exportAll()이 Instant를 JSON에 ISO-8601 문자열로 직렬화하는데, importAll()은 이를
            // 타입 정보 없는 Map<String, Any?>로 역직렬화해 평범한 String이 되고, 그 String을 그대로
            // PreparedStatement에 바인딩하면 MariaDB가 ISO-8601('T'/'Z')을 datetime으로 파싱하지
            // 못해 거부한다(`insertRow`의 `dateTimeColumns` 기반 String→Timestamp 변환으로 수정).
            it("created 값이 있는 organization 행도 export/import 왕복 시 실패하지 않아야 한다") {
                val saved = organizationRepository.save(
                    Organization(name = "datetime-roundtrip-org-${System.nanoTime()}", created = Instant.now(), descr = "datetime 왕복 검증용")
                )

                val backup = dataBackupService.exportAll()
                dataBackupService.importAll(backup)

                val restoredCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM organization WHERE id = ?",
                    Int::class.java,
                    saved.id
                )
                restoredCount shouldBe 1
            }
        }
        }
    }
}
