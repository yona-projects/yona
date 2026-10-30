package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * v1.6의 프로젝트 전체 리뷰어 설정을 브랜치 보호 규칙으로 옮기는 이전을 검증한다(로드맵 X-33, `decisions.md` D-45 4번).
 * 리뷰어 사용이 켜진 프로젝트는 모든 브랜치(`**`)에 리뷰어 수만큼의 승인과 변경 요청 해결을 요구하는 규칙 하나로 옮기고, 이전한 표시로 사용 여부를 끈다.
 */
@Transactional
class ReviewerSettingMigrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migration: ReviewerSettingMigration
    @Autowired private lateinit var rules: BranchProtectionRuleRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun project(using: Boolean?, count: Int?) = Project(owner = "mo${++sequence}", name = "mp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
        .also { it.isUsingReviewerCount = using; it.defaultReviewerCount = count; em.persist(it) }

    @Test
    fun `리뷰어를 쓰던 프로젝트는 모든 브랜치에 같은 승인 수를 요구하는 규칙으로 옮기고 v1_6 동작을 보존한다`() {
        val project = project(true, 3); em.flush()
        assertEquals(1, migration.migrate())
        val rule = rules.findByProjectIdOrderByPatternAsc(project.id!!).single()
        assertEquals("**", rule.pattern); assertEquals(3, rule.requiredApprovals)
        assertTrue(rule.requireNoChangesRequested == true, "v1.6은 변경 요청도 막는다")
        assertTrue(rule.enforceAdmins == true, "v1.6에는 관리자 우회가 없었다")
        assertFalse(rule.requirePullRequest == true, "직접 푸시는 v1.6처럼 막지 않는다")
        assertTrue(rule.allowForcePushes == true && rule.allowDeletions == true, "강제 푸시와 삭제도 v1.6처럼 허용한다")
        assertTrue(rule.matches("refs/heads/feature/a/b"), "슬래시가 있는 브랜치까지 모든 브랜치")
        assertNotNull(rule.createdDate)
        em.flush(); em.refresh(project)
        assertEquals(false, project.isUsingReviewerCount, "이전했다는 표시이다")
        assertEquals(3, project.defaultReviewerCount, "값은 기록으로 남긴다")
    }

    @Test
    fun `리뷰어를 쓰지 않던 프로젝트는 규칙을 만들지 않고 몇 번을 돌려도 한 번만 옮긴다`() {
        val off = project(false, 2); val unset = project(null, 1); val on = project(true, null); em.flush()
        assertEquals(1, migration.migrate())
        assertEquals(0, rules.findByProjectIdOrderByPatternAsc(off.id!!).size); assertEquals(0, rules.findByProjectIdOrderByPatternAsc(unset.id!!).size)
        assertEquals(1, rules.findByProjectIdOrderByPatternAsc(on.id!!).single().requiredApprovals, "승인 수가 비었으면 1이다")
        assertEquals(0, migration.migrate(), "다시 돌려도 새로 옮길 것이 없다")
        assertEquals(1, rules.findByProjectIdOrderByPatternAsc(on.id!!).size)
    }

    @Test
    fun `이미 모든 브랜치 규칙이 있으면 건드리지 않고 사용 여부만 끈다`() {
        val project = project(true, 2)
        rules.save(BranchProtectionRule(project = project, pattern = "**", requiredApprovals = 5))
        em.flush()
        migration.migrate()
        assertEquals(5, rules.findByProjectIdOrderByPatternAsc(project.id!!).single().requiredApprovals)
        em.flush(); em.refresh(project); assertEquals(false, project.isUsingReviewerCount)
    }
}
