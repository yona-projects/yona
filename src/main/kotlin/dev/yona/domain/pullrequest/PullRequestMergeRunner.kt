package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 풀 리퀘스트의 병합 검사를 요청과 별개의 스레드에서 실행한다. v1.6에서 `Akka.system().actorOf(Props.create(PullRequestMergingActor.class)).tell(...)`로
 * 보내던 일이다. 메시지마다 새 액터를 만들었으므로 여러 검사가 동시에 돌 수 있고, 같은 저장소에 대한 가져오기와 병합은 [PullRequestMerger]가 직렬화한다.
 *
 * `yona.pull-request-merge-async=false`이면 호출한 스레드에서 바로 실행한다(테스트용). 단계는 각자 트랜잭션에서 실행하며 실패하면 오류만 남긴다.
 */
@Component
class PullRequestMergeRunner(private val properties: YonaProperties, transactionManager: PlatformTransactionManager) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager)
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable -> Thread(runnable, "pull-request-merging").apply { isDaemon = true } }

    /**
     * [steps]를 차례로 실행한다. 비동기이면 별개의 스레드에서, 아니면 지금 스레드에서 한다. 단계마다 트랜잭션이 따로이므로 앞 단계(병합 중 표시)가
     * 뒤 단계(검사)가 끝나기 전에 커밋되어 화면에 보인다. 한 단계가 실패하면 경고를 남기고 나머지는 실행하지 않는다.
     */
    fun run(description: String, vararg steps: () -> Unit) {
        if (properties.pullRequestMergeAsync) {
            executor.execute { execute(description, steps) }
        } else {
            execute(description, steps)
        }
    }

    private fun execute(description: String, steps: Array<out () -> Unit>) {
        try {
            steps.forEach { step -> transaction.executeWithoutResult { step() } }
        } catch (e: Exception) {
            log.error("Failed to run {}", description, e)
        }
    }

    @PreDestroy
    fun shutdown() { executor.shutdown() }
}
