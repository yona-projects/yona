package dev.yona.observability

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

/**
 * 저장소 총 용량 지표(`yona.storage.repositories{vcs}`, 바이트)이다(로드맵 X-10 C5e, `decisions.md` D-64 4번). 용량 계획에 쓴다. 디렉터리를 순회해야 해서 저장소가
 * 수천 개이면 디스크 I/O를 크게 쓰므로 **기본 꺼짐**(`yona.observability-storage-scan=true`로 켬)이고, 켜면 10분마다 계산한다. 프로젝트별 라벨은 없고 VCS별 합계뿐이다
 * (프로젝트별 상위 N은 지표가 아니라 사이트 관리 화면의 몫이다). 위키 저장소(`.wiki.git`)는 git 저장소 디렉터리 아래에 있어 git 합계에 들어간다.
 */
@Component
class RepositoryStorageMetrics(
    private val dataDirectory: DataDirectory,
    private val properties: YonaProperties,
    private val registry: MeterRegistry,
    private val jobs: JobMetrics
) : MeterBinder {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sizes = mapOf("git" to AtomicLong(), "svn" to AtomicLong())

    override fun bindTo(registry: MeterRegistry) {
        if (!properties.observabilityStorageScan) return
        sizes.forEach { (vcs, size) -> Gauge.builder("yona.storage.repositories", size) { it.get().toDouble() }.tag("vcs", vcs).baseUnit("bytes").description("저장소 디렉터리의 총 바이트").register(registry) }
    }

    /** 10분마다 계산한다(켜져 있을 때만). 서버를 띄운 1분 뒤 처음 계산한다. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    fun scheduled() {
        if (!properties.observabilityStorageScan) return
        try {
            jobs.run("storage_scan") { scan() }
        } catch (e: Exception) {
            log.warn("저장소 총 용량을 계산하지 못했습니다", e)
        }
    }

    /** git과 svn 저장소 디렉터리의 총 바이트를 다시 계산한다. */
    fun scan() {
        val root = File(dataDirectory.root(), "repo")
        sizes.forEach { (vcs, size) -> size.set(directorySize(File(root, vcs))) }
    }

    private fun directorySize(directory: File): Long {
        if (!directory.isDirectory) return 0
        var total = 0L
        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) total += attrs.size()
                return FileVisitResult.CONTINUE
            }

            // 저장소가 바뀌는 중에 사라진 파일은 건너뛴다
            override fun visitFileFailed(file: java.nio.file.Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return total
    }
}
