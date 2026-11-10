package dev.yona.domain.upgrade

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 서버를 시작할 때 이관하지 않은 v1.6 데이터가 있으면 경고만 남기고 정상적으로 시작한다. 이관 전에는 옛 사용자 토큰이 동작하지 않고
 * 번호가 빈 풀 리퀘스트가 있을 수 있다. 서버를 끈 뒤 `scripts/migrate-v16.sh`로 이관하면 바로 해소된다. 이관 명령으로 실행 중일 때는 알리지 않는다.
 * 건수를 세지 못해도 시작을 막지 않는다.
 */
@Component
class V16MigrationNotice(
    private val migration: V16DataMigration,
    @Value("\${yona.migrate:}") private val mode: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (mode.isNotBlank()) return
        try {
            val pending = migration.pending()
            if (pending.total > 0) {
                log.warn(
                    "v1.6 data has not been migrated: pullRequests={}, legacyTokens={}, reviewerSettings={}, missingApprovals={}, scopeColumns={}. " +
                        "Stop the server and run scripts/migrate-v16.sh", pending.pullRequests, pending.legacyTokens, pending.reviewerSettings, pending.missingApprovals, pending.scopeColumns
                )
            }
        } catch (e: Exception) {
            log.warn("Could not check for unmigrated v1.6 data", e)
        }
    }
}
