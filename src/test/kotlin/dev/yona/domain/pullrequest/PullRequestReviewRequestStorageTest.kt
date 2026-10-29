package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 풀 리퀘스트 리뷰 요청의 저장을 검증한다(로드맵 X-27, `decisions.md` D-37). 연결 테이블 `pull_request_review_request`에 대기 중인 요청만 저장하고,
 * 프로젝트와 사용자가 지워질 때 연결 행이 남지 않아야 한다.
 */
@Transactional
class PullRequestReviewRequestStorageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projectDeletion: ProjectDeletionService
    @Autowired private lateinit var siteUsers: SiteUserService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "prr${++sequence}", name = "prr$sequence", email = "prr$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = Project(owner = "prro${++sequence}", name = "prrp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }

    private fun pullRequest(to: Project, contributor: User = user()) =
        PullRequest(toProject = to, fromProject = to, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature").also { em.persist(it) }

    private fun count(where: String): Long {
        em.flush()
        return (em.createNativeQuery("select count(*) from pull_request_review_request where $where").singleResult as Number).toLong()
    }

    @Test
    fun `리뷰 요청을 저장하고 다시 읽는다`() {
        val p = project(); val reviewer = user()
        val pr = pullRequest(p).also { it.reviewRequests.add(reviewer) }
        em.flush(); em.clear()
        assertEquals(setOf(reviewer.id), em.find(PullRequest::class.java, pr.id).reviewRequests.map { it.id }.toSet())
        assertEquals(1, count("pull_request_id = ${pr.id}"))
    }

    @Test
    fun `프로젝트를 지우면 그 프로젝트가 받는 풀 리퀘스트의 요청 행도 지워진다`() {
        val p = project(); val other = project(); val reviewer = user()
        val mine = pullRequest(p).also { it.reviewRequests.add(reviewer) }
        val others = pullRequest(other).also { it.reviewRequests.add(reviewer) }
        em.flush()
        projectDeletion.delete(p)
        em.flush(); em.clear()
        assertEquals(0, count("pull_request_id = ${mine.id}"))
        assertEquals(1, count("pull_request_id = ${others.id}"), "다른 프로젝트의 풀 리퀘스트는 그대로이다")
    }

    @Test
    fun `사용자를 삭제 처리하면 리뷰 요청에서 빠진다`() {
        val p = project(); val gone = user(); val stay = user()
        val pr = pullRequest(p).also { it.reviewRequests.addAll(listOf(gone, stay)) }
        em.flush()
        siteUsers.deleteUser(gone)
        em.flush(); em.clear()
        assertEquals(setOf(stay.id), em.find(PullRequest::class.java, pr.id).reviewRequests.map { it.id }.toSet())
        assertEquals(0, count("user_id = ${gone.id}"))
    }
}
