package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import kotlin.test.assertEquals

/**
 * 서버를 시작할 때 도는 경로(`ApplicationRunner.run`)로 이전이 실제로 저장되는지 검증한다(로드맵 X-33, `decisions.md` D-45 4번). 서비스를 바로 부르는
 * 테스트는 감싼 트랜잭션 안에서 돌아 시작 경로의 트랜잭션 누락을 잡지 못하므로, 이 테스트는 트랜잭션 없이 돌린다.
 */
class ReviewerSettingMigrationRunnerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migration: ReviewerSettingMigration
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var rules: BranchProtectionRuleRepository

    @Test
    fun `시작할 때 도는 이전은 트랜잭션 없이 호출해도 규칙을 저장하고 사용 여부를 끈다`() {
        val project = projects.save(Project(owner = "mr${System.nanoTime()}", name = "mrp", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { it.isUsingReviewerCount = true; it.defaultReviewerCount = 2 })
        try {
            migration.run(DefaultApplicationArguments())
            assertEquals(2, rules.findByProjectIdOrderByPatternAsc(project.id!!).single().requiredApprovals)
            assertEquals(false, projects.findById(project.id!!).get().isUsingReviewerCount)
        } finally {
            rules.deleteAll(rules.findByProjectIdOrderByPatternAsc(project.id!!)); projects.deleteById(project.id!!)
        }
    }
}
