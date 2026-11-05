package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * 같은 프로젝트에 풀 리퀘스트를 동시에 만들어도 번호가 겹치지 않음을 검증한다 (C081).
 *
 * v1.6은 가장 큰 번호에 1을 더해 매기므로 동시에 만들면 같은 번호가 나올 수 있었고, 번호에 유일 제약도 없어 조용히 겹쳤다.
 * 시험은 커밋이 필요해 트랜잭션 없이 돌리고, 쓴 자료는 고유한 이름을 쓴다.
 */
class PullRequestNumberConcurrencyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PullRequestService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `동시에 만든 풀 리퀘스트는 서로 다른 번호를 받는다`() {
        val tag = System.nanoTime()
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "cn$tag", name = "cn$tag", email = "cn$tag@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val to = projects.save(Project(owner = "cno$tag", name = "to", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val from = projects.save(Project(owner = "cnf$tag", name = "from", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val numbers = (0 until threads).map { i ->
            pool.submit<Long> {
                barrier.await(30, TimeUnit.SECONDS)
                service.create(PullRequestForm("요청 $i", "본문", from, to, "refs/heads/b$i", "refs/heads/master"), user).pullRequest.number!!
            }
        }.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(threads, numbers.toSet().size, "번호가 겹쳤다: $numbers")
    }
}
