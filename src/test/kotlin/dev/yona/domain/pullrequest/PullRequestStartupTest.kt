package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 서버를 시작할 때 풀 리퀘스트를 바로잡는 일을 검증한다.
 *
 * 원본: `app/models/PullRequest.java`의 `onStart`, `regulateNumbers`, `changeStateToClosed`.
 */
@Transactional
class PullRequestStartupTest : AbstractIntegrationTest() {

    @Autowired private lateinit var startup: PullRequestStartup
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun project(): Project = projects.save(Project(owner = "pst${++sequence}", name = "pst$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    private fun pull(to: Project, number: Long?, state: State = State.OPEN, created: Long = 0) =
        pullRequests.save(PullRequest(toProject = to, fromProject = to, number = number, state = state, created = Date(created)))

    @Test
    fun `번호가 빈 요청에 프로젝트별로 이어서 번호를 붙인다`() {
        val a = project(); val b = project()
        val first = pull(a, 3); val second = pull(a, null, created = 2_000); val third = pull(a, null, created = 1_000); val other = pull(b, null)
        startup.regulate()
        entityManager.flush(); entityManager.clear()
        assertEquals(3L, pullRequests.findById(first.id!!).get().number)
        assertEquals(4L, pullRequests.findById(third.id!!).get().number)
        assertEquals(5L, pullRequests.findById(second.id!!).get().number)
        assertEquals(1L, pullRequests.findById(other.id!!).get().number)
    }

    @Test
    fun `거절 상태는 닫힘으로 바꾸고 받은 시각을 정한다`() {
        val p = project()
        val rejected = pull(p, 1, State.REJECTED); val open = pull(p, 2)
        startup.regulate()
        entityManager.flush(); entityManager.clear()
        val closed = pullRequests.findById(rejected.id!!).get()
        assertEquals(State.CLOSED, closed.state); assertNotNull(closed.received)
        assertEquals(State.OPEN, pullRequests.findById(open.id!!).get().state); assertNull(pullRequests.findById(open.id!!).get().received)
    }
}
