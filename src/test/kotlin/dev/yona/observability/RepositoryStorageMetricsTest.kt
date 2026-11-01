package dev.yona.observability

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 저장소 총 용량 지표(로드맵 X-10 C5e, `decisions.md` D-64 4번)를 검증한다. 디렉터리를 순회해야 해서 비용이 크므로 기본 꺼짐이고 켜면 주기적으로 계산한다.
 * 프로젝트별 라벨은 없다(합계만).
 */
class RepositoryStorageMetricsTest {

    @TempDir
    lateinit var dir: File

    private val registry = SimpleMeterRegistry()
    private fun metrics(enabled: Boolean) = RepositoryStorageMetrics(
        DataDirectory(YonaProperties(dataDir = dir.path)), YonaProperties(dataDir = dir.path, observabilityStorageScan = enabled), registry, JobMetrics(registry, Clock.systemUTC())
    )

    private fun write(path: String, size: Int) = File(dir, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(size)) }
    private fun gauge(vcs: String) = registry.find("yona.storage.repositories").tag("vcs", vcs).gauge()

    @Test
    fun `꺼져 있으면 지표를 만들지 않는다`() {
        val m = metrics(false); m.bindTo(registry); m.scheduled()
        assertNull(gauge("git")); assertNull(gauge("svn"))
    }

    @Test
    fun `켜면 git과 svn 저장소의 총 바이트를 센다`() {
        write("repo/git/alice/demo.git/objects/aa", 100); write("repo/git/alice/demo.git/HEAD", 20); write("repo/git/bob/other.wiki.git/x", 5)
        write("repo/svn/alice/demo/db/rev", 1000)
        val m = metrics(true); m.bindTo(registry); m.scan()
        assertEquals(125.0, gauge("git")!!.value()); assertEquals(1000.0, gauge("svn")!!.value())
        assertEquals("bytes", gauge("git")!!.id.baseUnit)
    }

    @Test
    fun `저장소 디렉터리가 없으면 0이다`() {
        val m = metrics(true); m.bindTo(registry); m.scan()
        assertEquals(0.0, gauge("git")!!.value()); assertEquals(0.0, gauge("svn")!!.value())
    }

    @Test
    fun `다시 계산하면 바뀐 용량을 반영한다`() {
        val m = metrics(true); m.bindTo(registry)
        write("repo/git/a/b.git/f", 10); m.scan()
        write("repo/git/a/b.git/g", 15); m.scan()
        assertEquals(25.0, gauge("git")!!.value())
    }

    @Test
    fun `스케줄 실행은 작업 지표에 기록된다`() {
        val m = metrics(true); m.bindTo(registry); m.scheduled()
        assertEquals(1, registry.find("yona.job.duration").tags("job", "storage_scan", "result", "success").timer()!!.count())
    }

    @Test
    fun `태그에 프로젝트 이름이 들어가지 않는다`() {
        write("repo/git/zzowner/zzproject.git/f", 3)
        val m = metrics(true); m.bindTo(registry); m.scan()
        assertEquals(emptyList(), registry.meters.flatMap { x -> x.id.tags.map { it.value } }.filter { it.contains("zzowner") || it.contains("zzproject") })
    }
}
