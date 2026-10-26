package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 서버를 시작할 때 이전 데이터를 바로잡는다. v1.6 `Global.onStart`가 부르는 `PullRequest.onStart()`이다.
 *
 * - 번호가 비어 있는 풀 리퀘스트에 받는 프로젝트별로 다음 번호를 붙인다(`regulateNumbers`). 번호가 빈 요청이 하나도 없으면 아무것도 하지 않는다.
 * - 상태가 `REJECTED`인 요청을 `CLOSED`로 바꾸고 받은 시각을 지금으로 한다(`changeStateToClosed`).
 *
 * v1.6 코드는 `REJECTED`를 만들지 않고 번호도 항상 붙이므로, 새로 설치한 v2에서는 바꿀 것이 없다.
 * Yobi 시절의 데이터가 든 v1.6 DB를 그대로 이어받을 때만 일한다([decisions.md] A-104).
 */
@Component
class PullRequestStartup(private val entityManager: EntityManager) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) = regulate()

    /** 번호를 붙이고 `REJECTED`를 닫는다. 두 작업 모두 v1.6과 같은 순서이다. */
    @Transactional
    fun regulate() {
        regulateNumbers()
        changeStateToClosed()
    }

    private fun regulateNumbers() {
        val missing = entityManager.createQuery("select count(p) from PullRequest p where p.number is null", java.lang.Long::class.java).singleResult.toLong()
        if (missing == 0L) return
        val projectIds = entityManager.createQuery("select distinct p.toProject.id from PullRequest p where p.number is null", java.lang.Long::class.java).resultList
        for (projectId in projectIds) {
            val requests = entityManager.createQuery("select p from PullRequest p where p.toProject.id = :id order by p.created, p.id", PullRequest::class.java)
                .setParameter("id", projectId).resultList
            var next = (requests.mapNotNull { it.number }.maxOrNull() ?: 0L) + 1
            for (request in requests.filter { it.number == null }) request.number = next++
        }
        log.info("Assigned numbers to {} pull requests", missing)
    }

    private fun changeStateToClosed() {
        val rejected = entityManager.createQuery("select p from PullRequest p where p.state = :state", PullRequest::class.java).setParameter("state", State.REJECTED).resultList
        for (request in rejected) {
            request.state = State.CLOSED
            request.received = Date()
        }
    }
}
