package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

// yona-wiki P3-01(Observability) 계측 지점 4(트레이싱) 대응 — @Async("taskExecutor")를 쓰는 실제
// 리스너들(WebhookNotificationEventListener, PullRequestMergeEventListener 등, config/AsyncConfig.kt
// 참고)은 모두 이 taskExecutor 빈을 공유한다. AsyncConfig.kt에 ContextPropagatingTaskDecorator를
// 달아두지 않으면 호출 스레드의 현재 컨텍스트가 워커 스레드로 넘어가지 않아, 비동기 작업 안에서는
// 항상 새로운(또는 없는) trace-id로 보인다 — 실제 OTLP 콜렉터 없이도 로컬에서 생성되는 trace-id
// 자체로 이 성질을 검증할 수 있다(export 성공 여부와 무관).
//
// 처음엔 tracer.withSpan(span)+tracer.currentSpan()으로 직접 검증을 시도했으나 항상 실패했다 —
// ContextPropagatingTaskDecorator가 기본으로 쓰는 전역 ContextRegistry에는 io.micrometer.tracing의
// Span 전용 ThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor 등)가 자동 등록되지 않고,
// micrometer-observation의 ObservationThreadLocalAccessor만 META-INF/services로 자동 등록돼 있다
// (jar 안의 META-INF/services/io.micrometer.context.ThreadLocalAccessor로 실제 확인). Observation
// API(ObservationRegistry)로 관측을 시작하면 DefaultTracingObservationHandler가 Span 생성과
// tracer.withSpan() 진입을 함께 처리하고, 그 상태가 Observation 스코프를 통해 전파되므로 Observation
// 경유가 실제로 프로덕션 코드(WebhookNotificationEventListener 등)가 겪는 전파 경로와 동일하다.
class AsyncTraceContextPropagationSpec @Autowired constructor(
    private val tracer: Tracer,
    private val observationRegistry: ObservationRegistry,
    @Qualifier("taskExecutor") private val taskExecutor: Executor
) : AbstractIntegrationTest() {

    init {
        describe("taskExecutor의 트레이스 컨텍스트 전파") {
            it("@Async 경계를 넘어도 trace-id가 끊기지 않아야 한다") {
                val observation = Observation.createNotStarted("test-observation", observationRegistry).start()
                val scope = observation.openScope()
                val callerTraceId = tracer.currentSpan()?.context()?.traceId()

                val future = CompletableFuture<String?>()
                taskExecutor.execute {
                    future.complete(tracer.currentSpan()?.context()?.traceId())
                }
                val propagatedTraceId = future.get(5, TimeUnit.SECONDS)

                scope.close()
                observation.stop()

                callerTraceId shouldNotBe null
                propagatedTraceId shouldNotBe null
                propagatedTraceId shouldBe callerTraceId
            }
        }
    }
}
