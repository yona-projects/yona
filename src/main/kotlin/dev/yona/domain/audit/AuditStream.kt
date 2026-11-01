package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 감사 기록을 외부로 흘려보내는 대상 하나이다(웹훅, syslog 등, `decisions.md` D-63). 구현은 [configured]일 때만 쓰이고
 * [send]는 묶음 전체를 보내지 못하면 예외를 던져야 한다(그러면 [AuditStreamer]가 커서를 올리지 않고 다시 보낸다).
 */
interface AuditStreamTarget {

    /** 커서의 이름이다(`audit_stream_cursor.name`). */
    val name: String

    /** 설정이 있어 보낼 수 있는지이다. */
    fun configured(): Boolean

    /** 번호 순서의 [events]를 한 묶음으로 보낸다. 실패하면 예외를 던진다. */
    fun send(events: List<AuditLog>)
}

/**
 * 대상별로 어디까지 보냈는지 적어 두는 커서이다. 서버를 다시 띄워도 이어서 보내고, 보존 처리는 아직 보내지 않은 기록을 지우거나 비우지 않는다.
 *
 * @property name 대상 이름(`webhook`, `syslog`)
 * @property lastId 마지막으로 보낸 기록 번호
 * @property lastSentAt 마지막으로 보낸 시각(에폭 밀리초)
 * @property lastError 마지막 실패의 메시지(최근에 성공했으면 `null`)
 * @property lastErrorAt 마지막 실패 시각
 * @property failures 연속 실패 횟수
 */
@Entity
@Table(name = "audit_stream_cursor")
class AuditStreamCursor(
    @Id
    @Column(length = 32)
    var name: String = "",
    @Column(name = "last_id", nullable = false)
    var lastId: Long = 0,
    @Column(name = "last_sent_at")
    var lastSentAt: Long? = null,
    @Column(name = "last_error", length = 500)
    var lastError: String? = null,
    @Column(name = "last_error_at")
    var lastErrorAt: Long? = null,
    @Column(nullable = false)
    var failures: Int = 0
)

/** [AuditStreamCursor] 저장소이다. */
interface AuditStreamCursorRepository : JpaRepository<AuditStreamCursor, String>

/**
 * 외부 스트리밍 상태이다. 화면과 API가 보여 준다.
 *
 * @property backlog 아직 보내지 않은 기록 수
 */
data class AuditStreamStatus(val name: String, val lastId: Long, val backlog: Long, val lastSentAt: Long?, val lastError: String?, val lastErrorAt: Long?, val failures: Int)

/**
 * 설정된 대상으로 감사 기록을 기록 번호 순서대로 보낸다(적어도 한 번). 번호는 커밋 순서이므로(사슬 잠금, D-62) 커서보다 큰 기록은 모두 커밋된 것이다.
 * 처음 설정하면 그 시점까지의 기록은 보내지 않고(과거는 파일과 내보내기로 얻는다) 이후 기록부터 보낸다. 묶음을 보내지 못하면 커서를 올리지 않고 오류를 적어
 * 다음 주기에 같은 묶음을 다시 보낸다. 서버 한 대를 전제로 한다.
 */
@Component
class AuditStreamer(
    private val targets: List<AuditStreamTarget>,
    private val logs: AuditLogRepository,
    private val cursors: AuditStreamCursorRepository,
    private val heads: AuditChainHeadRepository,
    transactionManager: PlatformTransactionManager,
    private val properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager)

    private fun configured(): List<AuditStreamTarget> = targets.filter { it.configured() }

    /** 설정된 모든 대상으로 밀린 기록을 보낸다. 대상 하나가 실패해도 다른 대상은 계속 보낸다. */
    fun run() = configured().forEach { target ->
        try { deliver(target.name) } catch (e: Exception) { log.error("감사 로그 스트리밍에 실패했습니다: {}", target.name, e) }
    }

    /** 이름이 [name]인 대상으로 밀린 기록을 묶음 단위로 모두 보낸다. 보낸 기록 수를 돌려주며 실패하면 거기서 멈춘다. */
    @Synchronized
    fun deliver(name: String): Int {
        val target = configured().firstOrNull { it.name == name } ?: return 0
        val batchSize = properties.auditStreamBatchSize.coerceAtLeast(1)
        var cursor = cursors.findById(name).orElse(null)
        if (cursor == null) {
            cursor = cursors.save(AuditStreamCursor(name = name, lastId = heads.findById(1L).map { it.lastId }.orElse(0L)))
            return 0
        }
        var sent = 0
        while (true) {
            val batch = logs.findByIdGreaterThanOrderByIdAsc(cursor!!.lastId, PageRequest.of(0, batchSize))
            if (batch.isEmpty()) return sent
            try {
                target.send(batch)
            } catch (e: Exception) {
                val message = (e.message ?: e.javaClass.simpleName).take(MAX_ERROR)
                transaction.executeWithoutResult {
                    cursors.findById(name).ifPresent { it.failures += 1; it.lastError = message; it.lastErrorAt = System.currentTimeMillis() }
                }
                log.warn("감사 로그 스트리밍 전송에 실패했습니다: {} ({})", name, message)
                return sent
            }
            val last = batch.last().id
            cursor = transaction.execute {
                cursors.findById(name).get().also { it.lastId = last; it.lastSentAt = System.currentTimeMillis(); it.lastError = null; it.lastErrorAt = null; it.failures = 0 }
            }
            sent += batch.size
        }
    }

    /** 설정된 대상별 상태이다. */
    fun status(): List<AuditStreamStatus> {
        val headId = heads.findById(1L).map { it.lastId }.orElse(0L)
        return configured().map { target ->
            val c = cursors.findById(target.name).orElse(null)
            AuditStreamStatus(target.name, c?.lastId ?: headId, if (c == null) 0 else (headId - c.lastId).coerceAtLeast(0), c?.lastSentAt, c?.lastError, c?.lastErrorAt, c?.failures ?: 0)
        }
    }

    /**
     * 설정된 대상 중 아직 보내지 않은 가장 앞 기록 번호이다. 보존 처리는 이 번호 앞쪽만 지우거나 비운다. 대상이 없거나 커서가 아직 없으면 `null`(제한 없음)이다.
     */
    fun minUnsentId(): Long? = configured().mapNotNull { cursors.findById(it.name).orElse(null)?.lastId?.plus(1) }.minOrNull()

    private companion object {
        const val MAX_ERROR = 500
    }
}

/** 외부 스트리밍을 10초마다 돈다. 밀린 기록이 없으면 아무것도 보내지 않는다. */
@Component
class AuditStreamScheduler(private val streamer: AuditStreamer) {

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT10S")
    fun run() = streamer.run()
}

/**
 * 웹훅으로 보낸다. 묶음을 `{"events":[...]}` JSON으로 POST하고(기록 모양은 내보내기와 같다) 비밀값이 있으면 `X-Yona-Signature: sha256=<본문의 HMAC-SHA256>`을 싣는다.
 * 응답이 2xx가 아니면 실패이다. 수신 쪽은 같은 번호(`id`)가 다시 올 수 있으니(적어도 한 번) 번호로 중복을 걸러야 한다.
 */
@Component
class WebhookAuditTarget(private val properties: YonaProperties) : AuditStreamTarget {

    override val name = "webhook"

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT)).build()
    private val mapper = JsonMapper.builder().build()

    override fun configured(): Boolean = properties.auditStreamWebhookUrl?.trim()?.let { it.startsWith("http://") || it.startsWith("https://") } == true

    override fun send(events: List<AuditLog>) {
        val body = mapper.writeValueAsString(mapOf("events" to events.map { AuditExport.fields(it) }))
        val builder = HttpRequest.newBuilder(URI.create(properties.auditStreamWebhookUrl!!.trim())).timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
            .header("Content-Type", "application/json; charset=utf-8").header("User-Agent", "Yona-Audit-Stream").header("X-Yona-Event", "audit")
            .header("X-Yona-Delivery", "${events.first().id}-${events.last().id}").POST(HttpRequest.BodyPublishers.ofString(body))
        properties.auditStreamWebhookSecret?.takeIf { it.isNotEmpty() }?.let { builder.header("X-Yona-Signature", "sha256=" + hmac(it, body)) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) throw java.io.IOException("웹훅이 HTTP ${response.statusCode()}로 응답했습니다")
    }

    private fun hmac(secret: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 5L
        const val REQUEST_TIMEOUT = 15L
    }
}
