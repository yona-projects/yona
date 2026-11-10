package dev.yona.domain.upgrade

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6 데이터베이스를 이어받을 때 필요한 데이터 보정 4건을 한 번에 돌리는 이관을 검증한다. 서버를 시작할 때는 자동으로 돌지 않고
 * 이관 명령(`--yona.migrate=v16`)으로 따로 실행하므로, 미적용 건수를 알려 주는 [V16DataMigration.pending]과 몇 번을 돌려도 같은 결과인지를 확인한다.
 */
@Transactional
class V16DataMigrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migration: V16DataMigration
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(token: String? = null) =
        users.save(User(loginId = "v16u${++sequence}", name = "v16u$sequence", email = "v16u$sequence@example.com", state = UserState.ACTIVE, token = token))

    private fun project(reviewerCount: Boolean = false) =
        Project(owner = "v16o${++sequence}", name = "v16p$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { it.isUsingReviewerCount = reviewerCount; em.persist(it) }

    private fun pull(to: Project, number: Long?, state: State = State.OPEN) =
        PullRequest(toProject = to, fromProject = to, number = number, title = "요청", contributor = user(), state = state, created = Date(1_700_000_000_000L)).also { em.persist(it) }

    @Test
    fun `이관한 뒤에는 미적용 건수가 모두 0이다`() {
        em.flush()
        migration.migrate(); em.flush(); em.clear()
        assertEquals(0L, migration.pending().total, "이관 뒤에는 남은 것이 없다")
    }

    @Test
    fun `미적용 건수는 네 가지를 따로 센다`() {
        val base = migration.pending()
        val p = project(reviewerCount = true)
        pull(p, null); pull(p, 7, State.REJECTED)
        val reviewed = pull(p, 8); reviewed.reviewers.add(user())
        user(token = "old-token-${++sequence}")
        em.flush()

        val now = migration.pending()
        assertEquals(base.pullRequests + 2, now.pullRequests, "번호가 비었거나 거절 상태인 요청")
        assertEquals(base.legacyTokens + 1, now.legacyTokens, "이관 전 사용자 토큰")
        assertEquals(base.missingApprovals + 1, now.missingApprovals, "승인 집합에만 있고 리뷰가 없는 승인")
        assertEquals(base.reviewerSettings + 1, now.reviewerSettings, "리뷰어 사용이 켜진 프로젝트")
    }

    @Test
    fun `이관은 네 가지를 모두 고치고 몇 번을 돌려도 같다`() {
        val p = project(reviewerCount = true)
        val numberless = pull(p, null); val rejected = pull(p, 9, State.REJECTED)
        val reviewed = pull(p, 10); val reviewer = user(); reviewed.reviewers.add(reviewer)
        val owner = user(token = "legacy-token-${++sequence}")
        em.flush()

        val first = migration.migrate()
        em.flush(); em.clear()

        assertNotNull(em.find(PullRequest::class.java, numberless.id).number)
        assertEquals(State.CLOSED, em.find(PullRequest::class.java, rejected.id).state)
        assertNull(em.find(User::class.java, owner.id).token, "옛 토큰은 새 구조로 옮기고 사용자 열은 비운다")
        assertEquals(false, em.find(Project::class.java, p.id).isUsingReviewerCount)
        assertTrue(first.approvalsCreated >= 1, "승인 리뷰를 채운다")
        assertTrue(first.tokensMigrated >= 1)

        val second = migration.migrate()
        assertEquals(0, second.approvalsCreated); assertEquals(0, second.tokensMigrated); assertEquals(0, second.reviewerSettingsMigrated)
        assertEquals(0L, migration.pending().let { it.pullRequests + it.legacyTokens + it.missingApprovals + it.reviewerSettings })
    }
}
