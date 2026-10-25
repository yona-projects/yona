package dev.yona.domain.site

import dev.yona.config.YonaProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 서버가 시작된 뒤 일정 간격으로 새 버전을 확인한다. v1.6 `YobiUpdate.onStart`(`app/models/YobiUpdate.java:52-71`)에 해당한다.
 *
 * 간격이 0 이하이면 시작하지 않는다. `application.update.check.use`와 관계없이 돌고(v1.6도 같다), 실패하면 경고만 남긴다.
 */
@Component
class UpdateCheckScheduler(
    private val properties: YonaProperties,
    private val updates: UpdateCheckService,
    private val secret: dev.yona.security.SiteSecret
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private var executor: ScheduledExecutorService? = null

    @PostConstruct
    fun start() {
        val interval = properties.update.interval
        // 비밀 키를 정하기 전에는 시작하지 않는다 (v1.6 `Global.onStart`).
        if (interval <= 0 || secret.invalid) return
        executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "update-check").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({
                try {
                    updates.refresh()
                } catch (e: Exception) {
                    log.warn("Failed to fetch the latest Yona version to update", e)
                }
            }, properties.update.initDelay, interval, TimeUnit.MILLISECONDS)
        }
    }

    @PreDestroy
    fun stop() { executor?.shutdownNow() }
}
