package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BackgroundJobRunner]가 비동기로 일을 시작하는 시점을 검증한다.
 *
 * 풀 리퀘스트 만들기처럼 요청 전체가 하나의 트랜잭션인 호출에서 일을 곧바로 다른 스레드에 넘기면, 그 스레드가 아직 커밋되지 않은 행을
 * 찾지 못하고 아무 일도 하지 않은 채 끝난다(병합 검사가 가끔 건너뛰어진다). v1.6 Akka 메시지는 요청 트랜잭션이 끝난 뒤에 처리되었다.
 */
class BackgroundJobRunnerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var properties: YonaProperties
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `비동기 일은 호출한 트랜잭션이 커밋된 뒤에 시작한다`() {
        val runner = BackgroundJobRunner(properties.copy(backgroundAsync = true), transactionManager)
        val started = CountDownLatch(1)
        val committed = AtomicBoolean(false)
        val startedBeforeCommit = AtomicBoolean(false)
        TransactionTemplate(transactionManager).executeWithoutResult {
            runner.run("test job", { if (!committed.get()) startedBeforeCommit.set(true); started.countDown() })
            // 일이 다른 스레드에서 즉시 시작할 시간을 충분히 준다
            Thread.sleep(300)
            committed.set(true)
        }
        assertTrue(started.await(5, TimeUnit.SECONDS), "커밋 뒤에는 일이 시작해야 한다")
        assertFalse(startedBeforeCommit.get(), "커밋 전에 일이 시작되면 아직 저장되지 않은 행을 읽지 못한다")
        runner.shutdown()
    }

    @Test
    fun `트랜잭션 밖에서 부르면 바로 시작한다`() {
        val runner = BackgroundJobRunner(properties.copy(backgroundAsync = true), transactionManager)
        val started = CountDownLatch(1)
        runner.run("test job", { started.countDown() })
        assertTrue(started.await(5, TimeUnit.SECONDS))
        runner.shutdown()
    }
}
