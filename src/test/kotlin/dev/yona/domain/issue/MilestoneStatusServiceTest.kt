package dev.yona.domain.issue

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 홈과 마일스톤 화면이 보여 주는 마일스톤 현황을 검증한다.
 *
 * 원본: `app/models/Milestone.java:92-96`(개수), `:135-137`(완료율), `:168-175`(마감일 문자열),
 * `:261-279`(남은 기간, 지연 여부), `:151-153`(열린 마일스톤 조회, 마감일 오름차순).
 */
@Transactional
class MilestoneStatusServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: MilestoneStatusService
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var projects: ProjectRepository

    private val zone = ZoneId.of("Asia/Seoul")
    private var sequence = 0

    private fun project() = projects.save(Project(owner = "ms${++sequence}", name = "p$sequence", projectScope = ProjectScope.PUBLIC))

    private fun day(iso: String): Date = Date.from(LocalDate.parse(iso).atStartOfDay(zone).toInstant())

    private fun now(iso: String): Clock = Clock.fixed(Instant.parse(iso), zone)

    private fun milestone(project: Project, title: String, due: String? = null, state: State = State.OPEN) =
        milestones.save(Milestone(title = title, dueDate = due?.let(::day), state = state, project = project))

    private fun issue(project: Project, milestone: Milestone, state: State) =
        issues.save(Issue(project = project, milestone = milestone, state = state, number = (++sequence).toLong(), title = "t"))

    @Test
    fun `완료율은 닫힌 이슈 수를 전체 이슈 수로 나눈 백분율의 정수 부분이다`() {
        val project = project()
        val milestone = milestone(project, "m")
        repeat(2) { issue(project, milestone, State.CLOSED) }
        issue(project, milestone, State.OPEN)
        val status = service.statusOf(milestone, now("2026-10-22T00:00:00Z"))
        assertEquals(66, status.completionRate)
        assertEquals(2, status.closedCount)
        assertEquals(1, status.openCount)
    }

    @Test
    fun `이슈가 없으면 완료율은 0이다`() {
        val milestone = milestone(project(), "empty")
        assertEquals(0, service.statusOf(milestone, now("2026-10-22T00:00:00Z")).completionRate)
    }

    @Test
    fun `마감일이 없으면 마감일 문자열과 남은 기간이 없다`() {
        val status = service.statusOf(milestone(project(), "nodue"), now("2026-10-22T00:00:00Z"))
        assertNull(status.dueDateString)
        assertNull(status.until)
        assertFalse(status.overDue)
    }

    @Test
    fun `마감일은 yyyy-MM-dd 형식이다`() {
        val status = service.statusOf(milestone(project(), "due", "2026-11-05"), now("2026-10-22T00:00:00Z"))
        assertEquals("2026-11-05", status.dueDateString)
    }

    @Test
    fun `마감일이 오늘이면 오늘이라고 하지만 자정이 지났으므로 지연으로 판정한다`() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        val status = service.statusOf(milestone(project(), "today", "2026-10-22"), now("2026-10-22T03:00:00Z"))
        assertEquals("Today", status.until)
        // v1.6 isOverDueDate는 지금이 마감일 0시를 지났는지만 본다.
        assertTrue(status.overDue)
    }

    @Test
    fun `마감일이 지났으면 지난 날수를 알려 준다`() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        val status = service.statusOf(milestone(project(), "over", "2026-10-19"), now("2026-10-22T03:00:00Z"))
        assertTrue(status.overDue)
        assertEquals("3 days past", status.until)
    }

    @Test
    fun `마감일이 남았으면 남은 날수를 알려 준다`() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        val status = service.statusOf(milestone(project(), "left", "2026-10-25"), now("2026-10-22T03:00:00Z"))
        assertFalse(status.overDue)
        assertEquals("3 days left", status.until)
    }

    @Test
    fun `열린 마일스톤만 마감일 오름차순으로 찾고 마감일이 없는 것이 먼저이다`() {
        val project = project()
        milestone(project, "late", "2026-12-01")
        milestone(project, "none")
        milestone(project, "early", "2026-11-01")
        milestone(project, "closed", "2026-10-01", State.CLOSED)
        assertEquals(listOf("none", "early", "late"), service.findOpenMilestones(requireNotNull(project.id)).map { it.title })
    }
}
