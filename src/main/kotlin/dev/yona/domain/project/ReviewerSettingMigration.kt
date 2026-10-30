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
 * v1.6의 프로젝트 전체 리뷰어 설정(`project.is_using_reviewer_count`, `default_reviewer_count`)을 브랜치 보호 규칙으로 옮긴다(추가 개발 항목 X-33,
 * `decisions.md` D-45 4번). 서버를 시작할 때와 데이터를 가져온 뒤에 돈다.
 *
 * 리뷰어 사용이 켜진 프로젝트마다 모든 브랜치(`**`)에 리뷰어 수만큼의 승인과 변경 요청 해결을 요구하는 규칙을 하나 만든다. v1.6 동작을 그대로 보존하려고
 * "관리자에게도 적용"은 켜고(v1.6에는 관리자 우회가 없었다), 직접 푸시 차단은 끄고, 강제 푸시와 삭제는 허용한다. 옮긴 뒤 `is_using_reviewer_count`를 꺼서
 * 이전한 표시로 쓰고 `default_reviewer_count`는 기록으로 남긴다. 두 컬럼은 이 버전에서 읽지도 쓰지도 않으며 차기 버전에서 제거한다.
 *
 * 멱등하다. 이전한 프로젝트는 사용 여부가 꺼지므로 다시 돌려도 건너뛴다. 이미 `**` 규칙이 있는 프로젝트는 그 규칙을 건드리지 않고 사용 여부만 끈다.
 */
@Component
@Order(10)
class ReviewerSettingMigration(private val entityManager: EntityManager) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        migrate()
    }

    /** 옮긴 프로젝트 수를 돌려준다. 이미 모든 브랜치 규칙이 있어 사용 여부만 끈 프로젝트는 세지 않는다. */
    @Transactional
    fun migrate(): Int {
        val projects = entityManager.createQuery("select p from Project p where p.isUsingReviewerCount = true", Project::class.java).resultList
        var migrated = 0
        for (project in projects) {
            val hasAll = entityManager.createQuery("select count(r) from BranchProtectionRule r where r.project.id = :id and r.pattern = :pattern", java.lang.Long::class.java)
                .setParameter("id", project.id).setParameter("pattern", ALL_BRANCHES).singleResult.toLong() > 0
            if (!hasAll) {
                entityManager.persist(
                    BranchProtectionRule(
                        project = project, pattern = ALL_BRANCHES, requiredApprovals = maxOf(project.defaultReviewerCount ?: 1, 1), requireNoChangesRequested = true,
                        allowForcePushes = true, allowDeletions = true, enforceAdmins = true, createdDate = Date()
                    )
                )
                migrated++
            }
            project.isUsingReviewerCount = false
        }
        if (migrated > 0) log.info("Moved the reviewer settings of {} projects to branch protection rules (every branch)", migrated)
        return migrated
    }

    private companion object {
        const val ALL_BRANCHES = "**"
    }
}
