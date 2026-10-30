package dev.yona.domain.project

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
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
 * v1.6의 프로젝트 전체 리뷰어 설정을 규칙 묶음으로 옮기는 이전을 검증한다(로드맵 X-33, X-34, `decisions.md` D-45 4번, D-46, A-257).
 * 리뷰어 사용이 켜진 Git 프로젝트는 지금 있는 브랜치마다 그 브랜치 이름을 대상으로 하는 규칙 묶음을 하나씩 명시적으로 만들고, 이전했다는 표시로 사용 여부를 끈다.
 */
@Transactional
class ReviewerSettingMigrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migration: ReviewerSettingMigration
    @Autowired private lateinit var rules: RulesetRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var properties: YonaProperties
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun project(using: Boolean?, count: Int?, vcs: String = "GIT") =
        Project(owner = "mo${++sequence}", name = "mp$sequence", vcs = vcs, projectScope = ProjectScope.PUBLIC).also { it.isUsingReviewerCount = using; it.defaultReviewerCount = count; em.persist(it) }

    private fun withBranches(project: Project, vararg branches: String) = project.also {
        repositories.createRepository(it)
        branches.forEachIndexed { index, branch -> GitTestSupport.pushCommits(repositories, it, TestCommit("커밋 $branch", mapOf("$index.txt" to "$index".toByteArray())), branch = branch, startTime = 1_700_000_000_000L + index * 1000) }
    }

    private fun migratedOf(project: Project) = rules.findByProjectIdOrderByNameAsc(project.id!!)

    @Test
    fun `리뷰어를 쓰던 프로젝트는 지금 있는 브랜치마다 그 이름을 대상으로 하는 규칙 묶음을 만들고 v1_6 동작을 보존한다`() {
        val project = withBranches(project(true, 3), "master", "feature/a"); em.flush()
        assertEquals(1, migration.migrate())
        val created = migratedOf(project)
        assertEquals(setOf("master", "feature/a"), created.map { it.pattern }.toSet(), "와일드카드 없이 브랜치 이름 그대로")
        assertEquals(setOf("이전된 리뷰어 설정 (master)", "이전된 리뷰어 설정 (feature/a)"), created.map { it.name }.toSet())
        created.forEach { rule ->
            assertEquals(3, rule.requiredApprovals); assertTrue(rule.requireNoChangesRequested == true, "v1.6은 변경 요청도 막는다")
            assertFalse(rule.bypassAdmins == true, "v1.6에는 관리자 우회가 없었다"); assertFalse(rule.requirePullRequest == true, "직접 푸시는 v1.6처럼 막지 않는다")
            assertFalse(rule.blockForcePushes == true || rule.restrictDeletions == true, "강제 푸시와 삭제도 v1.6처럼 막지 않는다"); assertTrue(rule.isActive); assertNotNull(rule.createdDate)
        }
        em.flush(); em.refresh(project)
        assertEquals(false, project.isUsingReviewerCount, "이전했다는 표시이다"); assertEquals(3, project.defaultReviewerCount, "값은 기록으로 남긴다")
    }

    @Test
    fun `태그는 브랜치가 아니므로 규칙 묶음을 만들지 않는다`() {
        val project = withBranches(project(true, 1), "master"); em.flush()
        repositories.openRepository(project).use { it.updateRef("refs/tags/v1").also { u -> u.setNewObjectId(it.resolve("refs/heads/master")); u.update() } }
        migration.migrate()
        assertEquals(listOf("master"), migratedOf(project).map { it.pattern })
    }

    @Test
    fun `리뷰어를 쓰지 않던 프로젝트는 만들지 않고 몇 번을 돌려도 한 번만 옮긴다`() {
        val off = project(false, 2); val unset = project(null, 1); val on = withBranches(project(true, null), "master"); em.flush()
        assertEquals(1, migration.migrate())
        assertEquals(0, migratedOf(off).size); assertEquals(0, migratedOf(unset).size)
        assertEquals(1, migratedOf(on).single().requiredApprovals, "승인 수가 비었으면 1이다")
        assertEquals(0, migration.migrate(), "다시 돌려도 새로 옮길 것이 없다"); assertEquals(1, migratedOf(on).size)
    }

    @Test
    fun `저장소를 읽을 수 없거나 브랜치가 없으면 기본 브랜치 이름으로 하나 만든다`() {
        val noRepository = project(true, 2); val emptyRepository = project(true, 4).also { repositories.createRepository(it) }; em.flush()
        assertEquals(2, migration.migrate())
        assertEquals(listOf(properties.gitDefaultBranch), migratedOf(noRepository).map { it.pattern }); assertEquals(2, migratedOf(noRepository).single().requiredApprovals)
        assertEquals(listOf(properties.gitDefaultBranch), migratedOf(emptyRepository).map { it.pattern }); assertEquals(4, migratedOf(emptyRepository).single().requiredApprovals)
    }

    @Test
    fun `Git이 아닌 프로젝트는 규칙 묶음을 만들지 않고 사용 여부만 끈다`() {
        val svn = project(true, 2, vcs = "SUBVERSION"); em.flush()
        migration.migrate()
        assertEquals(0, migratedOf(svn).size); em.flush(); em.refresh(svn); assertEquals(false, svn.isUsingReviewerCount)
    }

    @Test
    fun `이미 같은 이름의 규칙 묶음이 있으면 건드리지 않는다`() {
        val project = withBranches(project(true, 2), "master")
        rules.save(Ruleset(project = project, name = "이전된 리뷰어 설정 (master)", pattern = "master", requiredApprovals = 5)); em.flush()
        migration.migrate()
        assertEquals(5, migratedOf(project).single().requiredApprovals)
        em.flush(); em.refresh(project); assertEquals(false, project.isUsingReviewerCount)
    }
}
