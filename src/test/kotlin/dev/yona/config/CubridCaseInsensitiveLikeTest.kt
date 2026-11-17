package dev.yona.config

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.TestDatabase
import dev.yona.util.LikeEscape
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cubrid의 대소문자 무시 비교(`decisions.md` A-630)를 검증한다. 검색은 `lower(x) like :패턴`으로 쓰는데 Cubrid에서는 `lower()`가 질의 비용의 절반 이상이어서,
 * 방언이 이를 `x collate utf8_en_ci like :패턴`으로 그린다(같은 결과이고 약 4배 빠르다). 이 시험은 Cubrid로 돌릴 때만 의미가 있다(`-Dyona.it.db=cubrid`).
 * 다른 데이터베이스의 SQL은 바뀌지 않으며 기존 검색 시험이 그것을 지킨다.
 */
@TestPropertySource(properties = ["spring.jpa.properties.hibernate.session_factory.statement_inspector=dev.yona.config.CubridCaseInsensitiveLikeTest\$Recorder"])
class CubridCaseInsensitiveLikeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var deletion: ProjectDeletionService

    private val created = mutableListOf<Project>()

    /** 나간 SQL을 기록한다. */
    class Recorder : org.hibernate.resource.jdbc.spi.StatementInspector {
        override fun inspect(sql: String): String { log.add(sql.replace(Regex("\\s+"), " ")); return sql }
        companion object { val log = java.util.concurrent.CopyOnWriteArrayList<String>() }
    }

    @AfterEach
    fun cleanUp() {
        created.forEach { runCatching { deletion.delete(projects.findById(it.id!!).get()) } }
    }

    private fun projectWith(vararg titles: String): Project {
        val project = projects.save(Project(owner = "ci${System.nanoTime()}", name = "cip", vcs = "GIT", projectScope = ProjectScope.PUBLIC)).also { created += it }
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        titles.forEachIndexed { i, t ->
            issues.save(Issue(project = project, number = i + 1L, title = t, body = "본문 $t", state = State.OPEN, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        }
        return project
    }

    private fun count(hql: String, project: Project, keyword: String): Long =
        entityManager.createQuery(hql, java.lang.Long::class.java).setParameter("pid", project.id).setParameter("kw", LikeEscape.contains(keyword)).singleResult.toLong()

    private fun requireCubrid() = Assumptions.assumeTrue(TestDatabase.current == TestDatabase.CUBRID, "Cubrid로 돌릴 때만 본다(-Dyona.it.db=cubrid)")

    @Test
    fun `대소문자가 다른 제목과 한글을 lower와 같은 결과로 찾는다`() {
        requireCubrid()
        val p = projectWith("Login Error", "LOGIN fail", "로그인 오류", "logout", "Deploy done")
        val hql = "select count(i) from Issue i where i.project.id = :pid and lower(i.title) like :kw escape '\\'"
        assertEquals(2, count(hql, p, "login"), "대문자와 소문자가 섞인 제목")
        assertEquals(2, count(hql, p, "LOGIN"), "검색어의 대소문자는 상관없다")
        assertEquals(1, count(hql, p, "로그인"))
        assertEquals(0, count(hql, p, "없는낱말"))
    }

    @Test
    fun `cast가 있어도 같은 결과이고 not like도 맞다`() {
        requireCubrid()
        val p = projectWith("Login Error", "LOGIN fail", "logout", "Deploy done")
        val withCast = "select count(i) from Issue i where i.project.id = :pid and lower(cast(i.body as string)) like :kw escape '\\'"
        assertEquals(2, count(withCast, p, "login"), "cast 안의 본문")
        val negated = "select count(i) from Issue i where i.project.id = :pid and lower(i.title) not like :kw escape '\\'"
        assertEquals(2, count(negated, p, "login"), "not like는 일치하지 않는 것만")
    }

    @Test
    fun `퍼센트 같은 특수 문자는 이스케이프한 대로 찾는다`() {
        requireCubrid()
        val p = projectWith("100% done", "1000 done", "A_B", "AxB")
        val hql = "select count(i) from Issue i where i.project.id = :pid and lower(i.title) like :kw escape '\\'"
        assertEquals(1, count(hql, p, "100%"), "퍼센트는 글자 그대로")
        assertEquals(1, count(hql, p, "a_b"), "밑줄도 글자 그대로")
    }

    @Test
    fun `내보낸 SQL은 collate를 쓰고 lower를 쓰지 않는다`() {
        requireCubrid()
        val p = projectWith("Login Error")
        val mark = Recorder.log.size
        count("select count(i) from Issue i where i.project.id = :pid and lower(i.title) like :kw escape '\\'", p, "login")
        val sql = Recorder.log.drop(mark).firstOrNull { "like" in it && "issue" in it } ?: error("검색 SQL이 기록되지 않았다: ${Recorder.log.drop(mark)}")
        assertTrue("collate utf8_en_ci" in sql, "대소문자 무시 정렬 규칙을 쓴다: $sql")
        assertFalse("lower(" in sql, "lower를 쓰지 않는다: $sql")
    }

    @Test
    fun `정렬 규칙이 없으면 켜지 않는다`() {
        // 켜는 조건(DB에 utf8_en_ci가 있는지)은 시작할 때 한 번 조사한다. 켜지지 않았으면 lower로 비교해야 하므로 스위치를 직접 끄고 SQL을 본다
        requireCubrid()
        val p = projectWith("Login Error")
        val before = CubridCaseInsensitiveCollation.enabled
        try {
            CubridCaseInsensitiveCollation.enabled = false
            val mark = Recorder.log.size
            // 하이버네이트는 같은 HQL의 번역 결과를 캐시하므로 켜 둔 채 실행한 다른 시험과 겹치지 않게 별칭이 다른 HQL을 쓴다
            assertEquals(1, count("select count(x) from Issue x where x.project.id = :pid and lower(x.title) like :kw escape '\\'", p, "login"))
            val sql = Recorder.log.drop(mark).first { "like" in it && "issue" in it }
            assertTrue("lower(" in sql && "collate" !in sql, "꺼 두면 지금처럼 lower로 비교한다: $sql")
        } finally {
            CubridCaseInsensitiveCollation.enabled = before
        }
    }

    @Test
    fun `시작할 때 Cubrid에 utf8_en_ci가 있으면 켠다`() {
        requireCubrid()
        assertTrue(CubridCaseInsensitiveCollation.enabled, "utf8 로케일(en_US.utf8)로 만든 DB에는 utf8_en_ci가 있어 켜져야 한다")
    }
}
