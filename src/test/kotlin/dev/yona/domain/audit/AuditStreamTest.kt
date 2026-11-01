package dev.yona.domain.audit

import com.sun.net.httpserver.HttpServer
import dev.yona.config.YonaProperties
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 외부 스트리밍 코어와 웹훅(로드맵 X-9 B4, `decisions.md` D-63)을 검증한다. 기록 번호 커서로 순서대로, 적어도 한 번 보내고
 * 실패하면 커서를 올리지 않아 다시 보낸다. 수신 쪽은 JDK 내장 HTTP 서버로 흉내 낸다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-audit-stream", "yona.audit-file-enabled=false"])
class AuditStreamTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var cursors: AuditStreamCursorRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val mapper = JsonMapper.builder().build()
    private lateinit var server: HttpServer
    private val received = CopyOnWriteArrayList<Received>()
    @Volatile private var status = 200

    private class Received(val body: String, val headers: Map<String, String>)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hook") { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            received += Received(body, exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() })
            exchange.sendResponseHeaders(status, -1); exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        tx.executeWithoutResult { cursors.deleteAllInBatch(); logs.deleteAllInBatch(); heads.deleteAllInBatch() }
    }

    private fun url() = "http://127.0.0.1:${server.address.port}/hook"
    private fun streamer(secret: String? = null, batch: Int = 100, url: String? = url()): Pair<AuditStreamer, WebhookAuditTarget> {
        val properties = YonaProperties(auditStreamWebhookUrl = url, auditStreamWebhookSecret = secret, auditStreamBatchSize = batch)
        val target = WebhookAuditTarget(properties)
        return AuditStreamer(listOf(target), logs, cursors, heads, transactionManager, properties) to target
    }
    private fun rec(action: String) = tx.executeWithoutResult { recorder.record(AuditEvent(action, details = mapOf("k" to "v"))) }
    private fun events(r: Received): List<String> {
        val out = mutableListOf<String>()
        for (node in mapper.readTree(r.body).get("events")) out += node.get("action").asString()
        return out
    }

    @Test
    fun `처음 설정하면 지금까지의 기록은 보내지 않고 이후 기록부터 보낸다`() {
        rec("old.one"); rec("old.two")
        val (streamer, _) = streamer()
        assertEquals(0, streamer.deliver("webhook"))
        assertTrue(received.isEmpty())
        assertEquals(2L, cursors.findById("webhook").get().lastId)
        rec("new.one")
        assertEquals(1, streamer.deliver("webhook"))
        assertEquals(listOf("new.one"), events(received.single()))
    }

    @Test
    fun `새 기록을 번호 순서대로 한 번에 보내고 커서를 올린다`() {
        val (streamer, _) = streamer()
        streamer.deliver("webhook")
        rec("a"); rec("b"); rec("c")
        assertEquals(3, streamer.deliver("webhook"))
        assertEquals(listOf("a", "b", "c"), events(received.single()))
        val cursor = cursors.findById("webhook").get()
        assertEquals(logs.findAll().maxOf { it.id }, cursor.lastId)
        assertNotNull(cursor.lastSentAt); assertNull(cursor.lastError)
        assertEquals(0, streamer.deliver("webhook"))
        assertEquals(1, received.size)
    }

    @Test
    fun `묶음 크기를 넘으면 나눠서 모두 보낸다`() {
        val (streamer, _) = streamer(batch = 2)
        streamer.deliver("webhook")
        listOf("a", "b", "c", "d", "e").forEach { rec(it) }
        assertEquals(5, streamer.deliver("webhook"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), received.map { events(it) })
    }

    @Test
    fun `비밀값이 있으면 본문에 대한 HMAC 서명을 헤더에 싣는다`() {
        val (streamer, _) = streamer(secret = "s3cret")
        streamer.deliver("webhook"); rec("signed")
        streamer.deliver("webhook")
        val r = received.single()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("s3cret".toByteArray(), "HmacSHA256")) }
        val expected = "sha256=" + mac.doFinal(r.body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals(expected, r.headers["x-yona-signature"])
        assertEquals("application/json; charset=utf-8", r.headers["content-type"])
        assertEquals("audit", r.headers["x-yona-event"])
    }

    @Test
    fun `비밀값이 없으면 서명 헤더를 싣지 않는다`() {
        val (streamer, _) = streamer()
        streamer.deliver("webhook"); rec("x"); streamer.deliver("webhook")
        assertNull(received.single().headers["x-yona-signature"])
    }

    @Test
    fun `수신 쪽이 실패하면 커서를 올리지 않고 오류를 남기며 다음에 같은 묶음을 다시 보낸다`() {
        val (streamer, _) = streamer()
        streamer.deliver("webhook"); rec("a"); rec("b")
        val before = cursors.findById("webhook").get().lastId
        status = 500
        assertEquals(0, streamer.deliver("webhook"))
        var cursor = cursors.findById("webhook").get()
        assertEquals(before, cursor.lastId); assertEquals(1, cursor.failures)
        assertTrue(cursor.lastError!!.contains("500"), cursor.lastError)
        status = 200
        assertEquals(2, streamer.deliver("webhook"))
        assertEquals(listOf("a", "b"), events(received.last())); assertEquals(listOf("a", "b"), events(received.first()))
        cursor = cursors.findById("webhook").get()
        assertEquals(0, cursor.failures); assertNull(cursor.lastError)
    }

    @Test
    fun `연결하지 못해도 예외 없이 오류만 남긴다`() {
        val (streamer, _) = streamer(url = "http://127.0.0.1:1/none")
        streamer.deliver("webhook"); rec("a")
        assertEquals(0, streamer.deliver("webhook"))
        assertNotNull(cursors.findById("webhook").get().lastError)
    }

    @Test
    fun `주소가 없거나 http가 아니면 설정되지 않은 것으로 본다`() {
        assertEquals(emptyList(), streamer(url = null).first.status())
        assertEquals(emptyList(), streamer(url = "file:///etc/passwd").first.status())
        assertEquals(emptyList(), streamer(url = "").first.status())
    }

    @Test
    fun `상태에 마지막으로 보낸 번호와 밀린 건수와 마지막 오류가 있다`() {
        val (streamer, _) = streamer()
        streamer.deliver("webhook"); rec("a"); rec("b")
        status = 500; streamer.deliver("webhook")
        val state = streamer.status().single()
        assertEquals("webhook", state.name); assertEquals(2L, state.backlog); assertNotNull(state.lastError)
        status = 200; streamer.deliver("webhook")
        assertEquals(0L, streamer.status().single().backlog)
    }

    @Test
    fun `아직 보내지 않은 가장 앞 번호를 돌려주어 보존이 지우지 않게 한다`() {
        val (streamer, _) = streamer()
        assertNull(streamer.minUnsentId())
        streamer.deliver("webhook"); rec("a"); rec("b")
        val first = logs.findAll().minOf { it.id }
        assertEquals(first, streamer.minUnsentId())
        streamer.deliver("webhook")
        assertEquals(logs.findAll().maxOf { it.id } + 1, streamer.minUnsentId())
    }

    @Test
    fun `비운 기록도 보내며 비운 시각이 실린다`() {
        val (streamer, _) = streamer()
        streamer.deliver("webhook"); rec("git.push")
        tx.executeWithoutResult { logs.findAll().forEach { it.actorLogin = null; it.redactedAt = 5L } }
        streamer.deliver("webhook")
        val node = mapper.readTree(received.single().body).get("events").first()
        assertTrue(node.get("actor_login").isNull); assertEquals("1970-01-01T00:00:00.005Z", node.get("redacted_at").asString())
    }
}
