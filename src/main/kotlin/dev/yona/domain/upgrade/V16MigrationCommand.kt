package dev.yona.domain.upgrade

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * 이관 명령이다. `--yona.migrate=v16`으로 시작하면 서버로 일하지 않고 [V16DataMigration]을 한 번 돌린 뒤 종료한다. 성공하면 종료 코드 0, 실패하면 1이다.
 * 서버와 같은 설정(DB 접속, `yona.data-dir`)을 읽으므로 서버와 같은 설정으로 실행해야 하며, 리뷰어 설정 이관이 저장소의 브랜치 목록을 읽기 때문이다.
 * `--yona.migrate.import-file=<JSON>`을 함께 주면 보정에 앞서 그 파일을 데이터 가져오기와 같은 코드로 넣는다([V16ImportFile], v1.6의 H2 설치용). 서버를 끈 상태에서 실행한다. `scripts/migrate-v16.sh`가 웹 서버를 임의 포트로 띄워 이 명령을 실행한다(서버가 쓰는 포트와 겹치지 않는다).
 */
@Component
@ConditionalOnProperty(name = ["yona.migrate"], havingValue = "v16")
class V16MigrationCommand(
    private val migration: V16DataMigration,
    private val importFile: V16ImportFile,
    private val context: ConfigurableApplicationContext,
    @Value("\${yona.migrate.import-file:}") private val importFilePath: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val code = try {
            // v1.6가 H2를 쓰던 설치: tools/h2-export가 만든 가져오기 파일을 먼저 넣는다(scripts/migrate-v16-h2.sh). 실패하면 보정을 돌리지 않는다
            if (importFilePath.isNotBlank()) {
                val imported = importFile.import(importFilePath)
                report("가져오기 파일", "$importFilePath 에서 ${imported.imported.size}개 테이블을 가져왔습니다")
                if (imported.ignored.isNotEmpty()) {
                    report("가져오지 않은 테이블(v2가 쓰지 않는 테이블이며 행이 있었습니다)", imported.ignored.entries.joinToString(", ") { "${it.key} ${it.value}행" })
                }
            }
            report("이관 전 미적용", migration.pending().toString())
            report("이관 결과", migration.migrate().toString())
            report("이관 후 미적용", migration.pending().toString())
            0
        } catch (e: V16ImportException) {
            // 가져오기는 테이블마다 따로 되돌리므로 가져온 테이블은 바뀌어 있을 수 있다. 같은 파일로 다시 돌리면 같은 결과이다(대상 테이블을 비우고 교체한다)
            log.error("Import of the v1.6 export file failed", e)
            System.err.println("v1.6 가져오기 파일을 넣지 못했습니다: ${e.message}. 같은 파일로 다시 실행하면 같은 결과이며, 보정 단계는 돌리지 않았습니다.")
            1
        } catch (e: Exception) {
            log.error("Migration failed; nothing was changed", e)
            System.err.println("v1.6 데이터 이관에 실패했습니다. 아무것도 바꾸지 않았습니다: ${e.message}")
            1
        }
        exitProcess(SpringApplication.exit(context, ExitCodeGenerator { code }))
    }

    /** 운영자가 명령 창에서 바로 볼 수 있게 로그와 함께 표준 출력에도 적는다. 로그 파일 설정과 상관없이 결과가 보인다. */
    private fun report(title: String, detail: String) {
        log.info("{}: {}", title, detail)
        println("[v1.6 이관] $title: $detail")
    }
}
