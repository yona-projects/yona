package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 최근 방문 프로젝트 기록을 검증한다.
 *
 * 원본: `app/models/User.java:856-862`(`visits`), `app/models/RecentProject.java:54-100`.
 */
@Transactional
class RecentVisitServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: RecentVisitService
    @Autowired private lateinit var recentProjects: RecentProjectRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    private fun user() = users.save(User(loginId = "rv${++sequence}", email = "rv$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = projects.save(Project(owner = "rvo${++sequence}", name = "p$sequence", projectScope = ProjectScope.PUBLIC))

    private fun visited(user: User) = recentProjects.findByUserIdOrderByIdDesc(requireNotNull(user.id)).map { it.project?.id }

    @Test
    fun `익명은 기록하지 않는다`() {
        service.visits(null, project())
        assertTrue(recentProjects.findAll().isEmpty())
    }

    @Test
    fun `방문한 프로젝트를 기록한다`() {
        val user = user()
        val project = project()
        service.visits(user, project)
        assertEquals(listOf(project.id), visited(user))
    }

    @Test
    fun `같은 프로젝트를 연달아 방문해도 한 번만 기록한다`() {
        val user = user()
        val first = project()
        val second = project()
        service.visits(user, first)
        service.visits(user, second)
        service.visits(user, second)
        assertEquals(listOf(second.id, first.id), visited(user))
    }

    @Test
    fun `다른 프로젝트를 다녀온 뒤 다시 방문하면 기록이 맨 위로 올라온다`() {
        val user = user()
        val first = project()
        val second = project()
        service.visits(user, first)
        service.visits(user, second)
        service.visits(user, first)
        assertEquals(listOf(first.id, second.id), visited(user))
    }

    @Test
    fun `이슈나 게시글을 본 뒤 같은 프로젝트를 다시 열면 다시 기록한다 (v1_6 결함 재현)`() {
        val user = user()
        val first = project()
        val second = project()
        service.visits(user, first)
        service.visits(user, second)
        val before = recentProjects.findByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(second.id)).single().id
        // v1.6 User.visits(Issue)는 lastVisitedProjectId에 이슈 id를 넣으므로 직전 방문과 같다고 보지 않는다
        service.markIssueOrPostingVisited(user, requireNotNull(second.id) + 1_000_000)
        service.visits(user, second)
        val after = recentProjects.findByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(second.id)).single().id
        assertTrue(requireNotNull(after) > requireNotNull(before))
        assertEquals(listOf(second.id, first.id), visited(user))
    }

    @Test
    fun `이슈 번호가 우연히 프로젝트 번호와 같으면 기록하지 않는다 (v1_6 결함 재현)`() {
        val user = user()
        val project = project()
        service.visits(user, project)
        service.markIssueOrPostingVisited(user, requireNotNull(project.id))
        val before = recentProjects.findByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id)).single().id
        service.visits(user, project)
        assertEquals(before, recentProjects.findByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id)).single().id)
    }

    @Test
    fun `사용자당 30개를 넘으면 가장 오래된 것부터 지운다`() {
        val user = user()
        val all = (1..32).map { project() }
        all.forEach { service.visits(user, it) }
        val kept = visited(user)
        assertEquals(30, kept.size)
        assertEquals(all.last().id, kept.first())
        assertEquals(all[2].id, kept.last())
    }
}
