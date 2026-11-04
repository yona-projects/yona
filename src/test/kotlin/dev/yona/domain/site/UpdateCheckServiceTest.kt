package dev.yona.domain.site

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 업데이트 확인(`UpdateCheckService`)을 검증한다. 원본: `app/models/YobiUpdate.java:38-127`. */
class UpdateCheckServiceTest {

    private fun service(tags: List<String>, current: String = "1.16.0", properties: YonaProperties = YonaProperties()) =
        UpdateCheckService(properties, RemoteTags { tags }, current)

    @Test
    fun `현재보다 높은 가장 큰 버전을 찾는다`() {
        val service = service(listOf("refs/tags/v1.16.0", "refs/tags/v1.17.0", "refs/tags/v1.18.2", "refs/tags/v1.9.0"))
        assertEquals("1.18.2", service.refresh())
        assertEquals("1.18.2", service.versionToUpdate)
    }

    @Test
    fun `새 버전이 없으면 null이다`() {
        val service = service(listOf("refs/tags/v1.16.0", "refs/tags/v1.15.0"))
        assertNull(service.refresh())
        assertNull(service.versionToUpdate)
    }

    @Test
    fun `v로 시작하지 않는 태그와 읽을 수 없는 태그는 건너뛴다`() {
        val service = service(listOf("refs/tags/2.0.0", "refs/tags/vfoo", "refs/tags/v1.17.0", "refs/tags/release-9"))
        assertEquals("1.17.0", service.refresh())
    }

    @Test
    fun `프리릴리스는 같은 번호의 현재 정식 버전보다 낮다`() {
        assertNull(service(listOf("refs/tags/v1.16.0-rc.1"), current = "1.16.0").refresh())
        assertEquals("1.17.0-rc.1", service(listOf("refs/tags/v1.17.0-rc.1")).refresh())
    }

    @Test
    fun `현재 버전은 정규화해서 비교한다`() {
        assertEquals("1.1.0", service(listOf("refs/tags/v1.1.0"), current = "v1.0").refresh())
    }

    @Test
    fun `저장소를 읽지 못하면 오류가 그대로 나가고 이전 값은 남는다`() {
        val service = UpdateCheckService(YonaProperties(), RemoteTags { throw IllegalStateException("network down") }, "1.16.0")
        assertFailsWith<IllegalStateException> { service.refresh() }
        assertNull(service.versionToUpdate)
    }

    @Test
    fun `알림을 끄면 새 버전이 있어도 알림 대상이 아니다`() {
        val service = service(listOf("refs/tags/v1.17.0"))
        service.refresh()
        assertTrue(service.shouldNotify())
        service.unwatch()
        assertTrue(!service.shouldNotify())
        service.refresh()
        assertTrue(!service.shouldNotify(), "갱신해도 알림은 다시 켜지지 않는다")
    }

    @Test
    fun `릴리스 주소는 형식에 버전을 넣어 만든다`() {
        val service = service(emptyList())
        assertEquals("https://github.com/yona-projects/yona/releases/tag/v1.17.0", service.releaseUrl("1.17.0"))
    }

    @Test
    fun `응답하지 않는 저장소는 제한 시간 안에 실패한다 (R-55-3)`() {
        // v1.6은 시간 제한이 없어 저장소가 느리면 화면이 늦게 열렸다
        java.net.ServerSocket(0).use { server ->
            val accepted = java.util.concurrent.CopyOnWriteArrayList<java.net.Socket>()
            val acceptor = Thread { while (!server.isClosed) { try { accepted += server.accept() } catch (e: Exception) { break } } }.apply { isDaemon = true; start() }
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(15)) {
                assertFailsWith<Exception> { JGitRemoteTags(timeoutSeconds = 1).list("http://127.0.0.1:${server.localPort}/never.git") }
            }
            accepted.forEach { it.close() }; acceptor.interrupt()
        }
    }
}
