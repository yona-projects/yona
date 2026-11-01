package dev.yona.observability

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * 데이터 디렉터리(저장소와 첨부 파일이 쌓이는 파티션)의 상태 확인이다(로드맵 X-10 C5a, `decisions.md` D-64 5번). 디렉터리가 있고 쓸 수 있으며 여유 용량이
 * `yona.observability-min-free-percent`(기본 5%) 이상이면 UP이다. readiness 그룹에 들어가 가득 찬 서버를 로드 밸런서가 마지막으로 빼게 한다.
 * 디스크 경고는 알림 규칙(85% 등)이 먼저 알리고 이것은 마지막 방어선이다. 진단용 파일은 만들지 않는다. 이름은 `dataDirectory`이다.
 */
@Component("dataDirectoryHealthIndicator")
class DataDirectoryHealthIndicator(private val dataDirectory: DataDirectory, private val properties: YonaProperties) : HealthIndicator {

    override fun health(): Health {
        val root = dataDirectory.root()
        if (!root.isDirectory) return Health.down().withDetail("reason", "not a directory").build()
        if (!root.canWrite()) return Health.down().withDetail("reason", "not writable").build()
        val total = root.totalSpace
        val freePercent = if (total > 0) root.usableSpace * 100.0 / total else 100.0
        val min = properties.observabilityMinFreePercent
        val builder = if (min > 0 && freePercent < min) Health.down().withDetail("reason", "low free space") else Health.up()
        return builder.withDetail("freePercent", Math.round(freePercent * 10) / 10.0).withDetail("minFreePercent", min).build()
    }
}

/**
 * 데이터 디렉터리 파티션의 전체 용량과 여유 용량 지표이다(`yona.storage.disk.total`, `yona.storage.disk.free`, 바이트). 저장소와 첨부가 쌓이는 곳이라
 * 가득 차면 푸시와 가져오기가 실패하므로 디스크 알림의 근거가 된다. 파티션 전체 값이라 비용이 작고 프로젝트별 라벨은 없다.
 */
@Component
class DataDirectoryMetrics(private val dataDirectory: DataDirectory) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder("yona.storage.disk.total", dataDirectory) { it.root().totalSpace.toDouble() }.baseUnit("bytes").description("데이터 디렉터리가 있는 파티션의 전체 용량").register(registry)
        Gauge.builder("yona.storage.disk.free", dataDirectory) { it.root().usableSpace.toDouble() }.baseUnit("bytes").description("데이터 디렉터리가 있는 파티션의 쓸 수 있는 여유 용량").register(registry)
    }
}
