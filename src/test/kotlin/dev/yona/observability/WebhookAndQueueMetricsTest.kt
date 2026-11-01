package dev.yona.observability

import com.sun.net.httpserver.HttpServer
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.Webhook
import dev.yona.domain.webhook.WebhookRepository
import dev.yona.domain.webhook.WebhookSender
import dev.yona.domain.notification.NotificationMailRepository
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetSocketAddress
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 웹훅 발송 결과와 알림 메일 대기열 길이 지표(로드맵 X-10 C5d)를 검증한다. */
class WebhookAndQueueMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var sender: WebhookSender
    @Autowired private lateinit var webhooks: WebhookRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var mails: NotificationMailRepository
    @Autowired private lateinit var registry: MeterRegistry

    private lateinit var server: HttpServer
    @Volatile private var status = 200
    private lateinit var project: Project
    private val saved = mutableListOf<Webhook>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hook") { e -> e.requestBody.readAllBytes(); e.sendResponseHeaders(status, -1); e.close() }
            start()
        }
        project = projects.save(Project(owner = "zzwh", name = "zzwh${System.nanoTime()}", vcs = "GIT"))
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        webhooks.deleteAll(saved); projects.delete(project)
    }

    private fun hook(url: String = "http://127.0.0.1:${server.address.port}/hook") = webhooks.save(Webhook(project = project, payloadUrl = url, createdAt = Date())).also { saved += it }
    private fun sent(result: String) = registry.find("yona.webhook.sent").tag("result", result).counter()?.count() ?: 0.0

    @Test
    fun `웹훅이 2xx로 응답하면 success이고 오류 응답과 연결 실패를 나눠 센다`() {
        val ok = sent("success"); val serverError = sent("server_error"); val unreachable = sent("unreachable")
        sender.send(hook(), "{}")
        status = 503; sender.send(hook(), "{}")
        sender.send(hook("http://127.0.0.1:1/none"), "{}")
        assertEquals(ok + 1, sent("success")); assertEquals(serverError + 1, sent("server_error")); assertEquals(unreachable + 1, sent("unreachable"))
    }

    @Test
    fun `웹훅 주소는 태그에 들어가지 않는다`() {
        sender.send(hook(), "{}")
        assertEquals(emptyList(), registry.meters.filter { it.id.name.startsWith("yona.webhook") }.flatMap { m -> m.id.tags.map { it.value } }.filter { it.contains("127.0.0.1") || it.contains("zzwh") })
    }

    @Test
    fun `알림 메일 대기열 길이를 알린다`() {
        val gauge = registry.find("yona.notification.mail.queue").gauge()
        assertTrue(gauge != null)
        assertEquals(mails.count().toDouble(), gauge!!.value())
    }
}
