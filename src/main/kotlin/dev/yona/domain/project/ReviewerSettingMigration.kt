package dev.yona.domain.project

import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * v1.6의 프로젝트 전체 리뷰어 설정(`project.is_using_reviewer_count`, `default_reviewer_count`)을 룰셋으로 옮긴다(추가 개발 항목 X-33, X-34, `decisions.md` D-45 4번, D-46).
 * 서버를 시작할 때와 데이터를 가져온 뒤에 돈다.
 *
 * 리뷰어 사용이 켜진 프로젝트마다 [MIGRATED_NAME] 룰셋을 하나 만든다. 대상은 모든 브랜치(`**`)이고 리뷰어 수만큼의 승인과 변경 요청 해결을 요구한다. v1.6 동작을 그대로
 * 보존하려고 바이패스는 비우고(v1.6에는 관리자 우회가 없었다), 직접 푸시 차단은 끄고, 삭제 제한과 강제 푸시 차단도 끈다. 옮긴 뒤 `is_using_reviewer_count`를 꺼서
 * 이전한 표시로 쓰고 `default_reviewer_count`는 기록으로 남긴다. 두 컬럼은 이 버전에서 읽지도 쓰지도 않으며 차기 버전에서 제거한다.
 *
 * 멱등하다. 이전한 프로젝트는 사용 여부가 꺼지므로 다시 돌려도 건너뛴다. 같은 이름의 룰셋이 이미 있는 프로젝트는 그 룰셋을 건드리지 않고 사용 여부만 끈다.
 */
@Component
@Order(10)
class ReviewerSettingMigration(private val entityManager: EntityManager, private val transactions: org.springframework.transaction.support.TransactionTemplate) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 서버를 시작할 때 도는 경로이다. 자기 자신을 부르면 트랜잭션이 걸리지 않으므로 직접 트랜잭션 안에서 돌린다. */
    override fun run(args: ApplicationArguments) {
        transactions.executeWithoutResult { migrate() }
    }

    /** 옮긴 프로젝트 수를 돌려준다. 이미 같은 이름의 룰셋이 있어 사용 여부만 끈 프로젝트는 세지 않는다. */
    @Transactional
    fun migrate(): Int {
        val projects = entityManager.createQuery("select p from Project p where p.isUsingReviewerCount = true", Project::class.java).resultList
        var migrated = 0
        for (project in projects) {
            val exists = entityManager.createQuery("select count(r) from Ruleset r where r.project.id = :id and r.name = :name", java.lang.Long::class.java)
                .setParameter("id", project.id).setParameter("name", MIGRATED_NAME).singleResult.toLong() > 0
            if (!exists) {
                entityManager.persist(
                    Ruleset(
                        project = project, name = MIGRATED_NAME, pattern = ALL_BRANCHES, requiredApprovals = maxOf(project.defaultReviewerCount ?: 1, 1), requireNoChangesRequested = true,
                        restrictDeletions = false, blockForcePushes = false, bypassAdmins = false, createdDate = Date()
                    )
                )
                migrated++
            }
            project.isUsingReviewerCount = false
        }
        if (migrated > 0) log.info("Moved the reviewer settings of {} projects to rulesets (every branch)", migrated)
        return migrated
    }

    companion object {
        /** 이전한 룰셋의 이름이다. 설정 화면이 이전했다는 안내를 보일 때도 이 이름으로 찾는다. */
        const val MIGRATED_NAME = "이전된 리뷰어 설정"
        private const val ALL_BRANCHES = "**"
    }
}
