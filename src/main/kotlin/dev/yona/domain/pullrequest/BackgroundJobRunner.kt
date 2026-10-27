package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 요청과 별개의 스레드에서 도는 일을 실행한다. v1.6에서 `Akka.system().actorOf(Props.create(…)).tell(…)`로 보내던 일(풀 리퀘스트 병합 검사
 * `PullRequestMergingActor`, `RelatedPullRequestMergingActor`, 푸시 뒤 이슈 언급과 새 커밋 알림 `PostReceiveActor` 계열)이다. 메시지마다 새 액터를
 * 만들었으므로 여러 일이 동시에 돌 수 있고, 같은 저장소에 대한 가져오기와 병합은 [PullRequestMerger]가 직렬화한다.
 *
 * 비동기일 때 호출한 트랜잭션이 있으면 커밋된 뒤에 시작한다(롤백되면 시작하지 않는다).
 * `yona.background-async=false`이면 호출한 스레드에서 바로 실행한다(테스트용). 단계는 각자 트랜잭션에서 실행하며 실패하면 오류만 남긴다.
 */
@Component
class BackgroundJobRunner(private val properties: YonaProperties, transactionManager: PlatformTransactionManager) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager)
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable -> Thread(runnable, "background-job").apply { isDaemon = true } }

    /**
     * [steps]를 차례로 실행한다. 비동기이면 별개의 스레드에서, 아니면 지금 스레드에서 한다. 단계마다 트랜잭션이 따로이므로 앞 단계(병합 중 표시)가
     * 뒤 단계(검사)가 끝나기 전에 커밋되어 화면에 보인다. 한 단계가 실패하면 경고를 남기고 나머지는 실행하지 않는다.
     */
    fun run(description: String, vararg steps: () -> Unit) {
        if (properties.backgroundAsync) {
            // 호출한 쪽 트랜잭션이 커밋되기 전에 다른 스레드가 시작하면 아직 저장되지 않은 행을 찾지 못해 일이 조용히 건너뛰어진다
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() { executor.execute { execute(description, steps) } }
                })
            } else {
                executor.execute { execute(description, steps) }
            }
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
