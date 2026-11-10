package dev.yona.domain.upgrade

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.ReviewerSettingMigration
import dev.yona.domain.pullrequest.PullRequestReviewBackfill
import dev.yona.domain.pullrequest.PullRequestStartup
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * v1.6 데이터베이스를 이어받을 때 필요한 데이터 보정 4건을 한 번에 돌리는 이관이다. 서버를 시작할 때 자동으로 돌지 않으므로
 * 서버를 끈 상태에서 이관 명령(`--yona.migrate=v16`, [V16MigrationCommand])으로 실행한다. 사이트 데이터 가져오기는 가져온 직후 같은 보정을 계속 돌린다.
 *
 * 네 가지 모두 몇 번을 돌려도 결과가 같다(멱등). 순서는 다음과 같고, 뒤 단계가 앞 단계의 결과를 쓰므로 바꾸면 안 된다.
 *
 * 1. 풀 리퀘스트 번호 정리: 번호가 빈 요청에 번호를 붙이고 `REJECTED`를 `CLOSED`로 바꾼다([PullRequestStartup], v1.6 `PullRequest.onStart`).
 * 2. 옛 사용자 토큰 이관: `n4user.token`을 해시해 `api_token` 행으로 옮긴다([ApiTokenService.migrateLegacyTokens], 추가 개발 항목 X-5).
 * 3. 리뷰어 설정 이관: 프로젝트별 리뷰어 수 설정을 브랜치별 규칙 묶음으로 옮긴다([ReviewerSettingMigration], X-33, X-34).
 * 4. 승인 리뷰 채우기: 승인 집합에만 있는 승인에 리뷰를 만들고, 새 규칙으로 병합 요건을 잃은 요청을 안내한다([PullRequestReviewBackfill], X-12).
 *    안내는 3번이 만든 규칙 묶음을 보므로 3번 뒤에 돌아야 한다(이전에는 시작할 때 리뷰어 설정 이관이 먼저 돌았다).
 */
@Component
class V16DataMigration(
    private val pullRequests: PullRequestStartup,
    private val apiTokens: ApiTokenService,
    private val reviewerSettings: ReviewerSettingMigration,
    private val approvals: PullRequestReviewBackfill,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이관 결과이다. 건수는 이번 실행에서 바꾼 수이며, 이미 이관된 데이터에서는 모두 0이다.
     *
     * @property tokensMigrated 새 구조로 옮긴 옛 사용자 토큰 수
     * @property reviewerSettingsMigrated 규칙 묶음으로 옮긴 프로젝트 수
     * @property approvalsCreated 새로 만든 승인 리뷰 수
     * @property noticesSent 병합 요건을 잃어 안내한 열린 풀 리퀘스트 수
     */
    data class Result(val tokensMigrated: Int, val reviewerSettingsMigrated: Int, val approvalsCreated: Int, val noticesSent: Int)

    /**
     * 아직 이관하지 않은 데이터의 건수이다.
     *
     * @property pullRequests 번호가 비었거나 `REJECTED` 상태인 풀 리퀘스트 수
     * @property legacyTokens 새 구조로 옮기기 전의 사용자 토큰 수
     * @property reviewerSettings 리뷰어 수 설정이 켜진 채 남은 프로젝트 수
     * @property missingApprovals 승인 집합에만 있고 승인 리뷰가 없는 행 수
     */
    data class Pending(val pullRequests: Long, val legacyTokens: Long, val reviewerSettings: Long, val missingApprovals: Long) {
        /** 네 가지의 합이다. 0이면 이관할 것이 없다. */
        val total: Long get() = pullRequests + legacyTokens + reviewerSettings + missingApprovals
    }

    /** 네 가지 보정을 정해진 순서로 돌리고 바꾼 건수를 돌려준다. 하나가 실패하면 전부 되돌린다. */
    @Transactional
    fun migrate(): Result {
        pullRequests.regulate()
        val tokens = apiTokens.migrateLegacyTokens()
        val settings = reviewerSettings.migrate()
        val created = approvals.backfill()
        val notices = approvals.noticeIgnoredAuthorApprovals()
        log.info("Migrated v1.6 data: tokens={}, reviewerSettings={}, approvalReviews={}, notices={}", tokens, settings, created, notices)
        return Result(tokens, settings, created, notices)
    }

    /** 이관하지 않은 데이터의 건수를 센다. 아무것도 바꾸지 않는다. */
    @Transactional(readOnly = true)
    fun pending(): Pending {
        fun count(jpql: String, vararg params: Pair<String, Any>): Long =
            entityManager.createQuery(jpql, java.lang.Long::class.java).also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        return Pending(
            pullRequests = count("select count(p) from PullRequest p where p.number is null or p.state = :rejected", "rejected" to State.REJECTED),
            legacyTokens = count("select count(u) from User u where u.token is not null and u.token <> ''"),
            reviewerSettings = count("select count(p) from Project p where p.isUsingReviewerCount = true"),
            missingApprovals = approvals.countMissing()
        )
    }
}
