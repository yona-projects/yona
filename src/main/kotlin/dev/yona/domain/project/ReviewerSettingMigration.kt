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
 * v1.6의 프로젝트 전체 리뷰어 설정(`project.is_using_reviewer_count`, `default_reviewer_count`)을 규칙 묶음으로 옮긴다(추가 개발 항목 X-33, X-34, `decisions.md` D-45 4번, D-46, A-257).
 * 서버를 시작할 때와 데이터를 가져온 뒤에 돈다.
 *
 * 리뷰어 사용이 켜진 Git 프로젝트마다 **지금 있는 브랜치 하나당 규칙 묶음 하나**를 명시적으로 만든다. 이름은 [MIGRATED_NAME] 뒤에 괄호로 브랜치 이름을 붙이고
 * (`이전된 리뷰어 설정 (master)`), 대상은 그 브랜치 이름 그대로이다(와일드카드를 쓰지 않는다). 리뷰어 수만큼의 승인과 변경 요청 해결을 요구한다. v1.6 동작을 그대로
 * 보존하려고 바이패스는 비우고(v1.6에는 관리자 우회가 없었다), 직접 푸시 차단은 끄고, 삭제 제한과 강제 푸시 차단도 끈다. 저장소를 읽을 수 없거나 브랜치가 없으면
 * 기본 브랜치 이름([YonaProperties.gitDefaultBranch]) 하나를 만든다. 옮긴 뒤 `is_using_reviewer_count`를 꺼서 이전한 표시로 쓰고 `default_reviewer_count`는 기록으로
 * 남긴다. 두 컬럼은 이 버전에서 읽지도 쓰지도 않으며 차기 버전에서 제거한다. Git이 아닌 프로젝트는 규칙 묶음 없이 사용 여부만 끈다.
 *
 * 이전 뒤에 만든 브랜치에는 규칙 묶음이 없다(v1.6은 모든 브랜치에 적용했다). 필요하면 규칙 묶음 화면에서 대상 패턴을 넓히거나 더한다.
 *
 * 멱등하다. 이전한 프로젝트는 사용 여부가 꺼지므로 다시 돌려도 건너뛴다. 같은 이름의 규칙 묶음이 이미 있으면 그것은 건드리지 않는다.
 */
@Component
@Order(10)
class ReviewerSettingMigration(
    private val entityManager: EntityManager,
    private val transactions: org.springframework.transaction.support.TransactionTemplate,
    private val repositories: dev.yona.repository.RepositoryService,
    private val properties: dev.yona.config.YonaProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 서버를 시작할 때 도는 경로이다. 자기 자신을 부르면 트랜잭션이 걸리지 않으므로 직접 트랜잭션 안에서 돌린다. */
    override fun run(args: ApplicationArguments) {
        transactions.executeWithoutResult { migrate() }
    }

    /** 옮긴 프로젝트 수를 돌려준다. Git이 아니라 사용 여부만 끈 프로젝트는 세지 않는다. */
    @Transactional
    fun migrate(): Int {
        val projects = entityManager.createQuery("select p from Project p where p.isUsingReviewerCount = true", Project::class.java).resultList
        var migrated = 0
        for (project in projects) {
            if (project.vcs == "GIT") {
                val approvals = maxOf(project.defaultReviewerCount ?: 1, 1)
                for (branch in branchesOf(project)) {
                    val name = "$MIGRATED_NAME ($branch)"
                    val exists = entityManager.createQuery("select count(r) from Ruleset r where r.project.id = :id and r.name = :name", java.lang.Long::class.java)
                        .setParameter("id", project.id).setParameter("name", name).singleResult.toLong() > 0
                    if (exists) continue
                    entityManager.persist(
                        Ruleset(
                            project = project, name = name, pattern = branch, requiredApprovals = approvals, requireNoChangesRequested = true,
                            restrictDeletions = false, blockForcePushes = false, bypassAdmins = false, createdDate = Date()
                        )
                    )
                }
                migrated++
            }
            project.isUsingReviewerCount = false
        }
        if (migrated > 0) log.info("Moved the reviewer settings of {} projects to rulesets (one per existing branch)", migrated)
        return migrated
    }

    /** 지금 있는 브랜치 이름(짧은 이름)이다. 저장소를 읽을 수 없거나 브랜치가 없으면 기본 브랜치 이름 하나이다. */
    private fun branchesOf(project: Project): List<String> {
        val names = try {
            repositories.refNames(project).filter { it.startsWith(HEADS) }.map { it.removePrefix(HEADS) }
        } catch (e: Exception) {
            log.warn("Could not read the branches of {}/{} while moving the reviewer settings", project.owner, project.name, e)
            emptyList()
        }
        return names.ifEmpty { listOf(properties.gitDefaultBranch) }
    }

    companion object {
        /** 이전한 규칙 묶음 이름의 앞부분이다. 목록 화면이 이전했다는 안내를 보일 때도 이 이름으로 시작하는지 본다. */
        const val MIGRATED_NAME = "이전된 리뷰어 설정"
        private const val HEADS = "refs/heads/"
    }
}
