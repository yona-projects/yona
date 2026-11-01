package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * syslog 전송(RFC 5424, X-9 B5, `decisions.md` D-63)을 검증한다. UDP는 기록마다 데이터그램 하나이고 TCP는 옥텟 세기(RFC 6587) 틀로 보낸다.
 */
class SyslogAuditTargetTest {

    private fun entry(id: Long, action: String = "auth.login_success", outcome: AuditOutcome = AuditOutcome.SUCCESS) =
        AuditLog(id = id, createdMillis = 1_700_000_000_123L, actorType = AuditActorType.USER, actorId = 1, actorLogin = "alice", action = action, outcome = outcome,
            targetType = "user", targetId = "1", targetLabel = "alice", ip = "10.0.0.1", requestId = "req-1", details = "{\"k\":\"v\"}", hash = "h$id")

    private fun target(host: String? = "127.0.0.1", port: Int = 514, protocol: String = "udp", hostname: String = "yona-host") =
        SyslogAuditTarget(YonaProperties(auditStreamSyslogHost = host, auditStreamSyslogPort = port, auditStreamSyslogProtocol = protocol, auditStreamSyslogHostname = hostname))

    @Test
    fun `메시지는 RFC 5424 형식이고 우선순위는 감사 시설과 결과로 정한다`() {
        val ok = SyslogAuditTarget.format(entry(7), "yona-host")
        // <13*8+6=110>1 2023-11-14T22:13:20.123Z yona-host yona - 7 [yona@32473 ...] {json}
        assertTrue(ok.startsWith("<110>1 2023-11-14T22:13:20.123Z yona-host yona - 7 "), ok)
        assertTrue(ok.contains("[audit@32473 action=\"auth.login_success\" outcome=\"SUCCESS\" actor=\"alice\" ip=\"10.0.0.1\"]"), ok)
        assertTrue(ok.endsWith("{\"id\":7,\"time\":\"2023-11-14T22:13:20.123Z\",\"actor_type\":\"USER\",\"actor_id\":1,\"actor_login\":\"alice\",\"action\":\"auth.login_success\",\"outcome\":\"SUCCESS\",\"target_type\":\"user\",\"target_id\":\"1\",\"target_label\":\"alice\",\"organization_id\":null,\"project_id\":null,\"ip\":\"10.0.0.1\",\"user_agent\":null,\"request_id\":\"req-1\",\"token_id\":null,\"token_scopes\":null,\"details\":\"{\\\"k\\\":\\\"v\\\"}\",\"prev_hash\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"hash\":\"h7\",\"content_hash\":null,\"redacted_at\":null}"), ok)
        assertTrue(SyslogAuditTarget.format(entry(8, outcome = AuditOutcome.DENIED), "h").startsWith("<108>1 "))
        assertTrue(SyslogAuditTarget.format(entry(9, outcome = AuditOutcome.FAILURE), "h").startsWith("<108>1 "))
    }

    @Test
    fun `구조화 데이터 값은 따옴표와 역슬래시와 닫는 대괄호를 이스케이프한다`() {
        val msg = SyslogAuditTarget.format(entry(1).apply { actorLogin = "a\"b]c\\d" }, "h")
        assertTrue(msg.contains("actor=\"a\\\"b\\]c\\\\d\""), msg)
    }

    @Test
    fun `행위자나 IP가 없으면 그 항목을 구조화 데이터에서 뺀다`() {
        val msg = SyslogAuditTarget.format(entry(1).apply { actorLogin = null; ip = null }, "h")
        assertTrue(msg.contains("[audit@32473 action=\"auth.login_success\" outcome=\"SUCCESS\"]"), msg)
    }

    @Test
    fun `UDP는 기록마다 데이터그램 하나를 보낸다`() {
        DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            socket.soTimeout = 3000
            target(port = socket.localPort).send(listOf(entry(1), entry(2)))
            val got = (1..2).map { DatagramPacket(ByteArray(8192), 8192).also { p -> socket.receive(p) }.let { String(it.data, 0, it.length, Charsets.UTF_8) } }
            assertTrue(got[0].startsWith("<110>1 ") && got[0].contains(" yona - 1 "))
            assertTrue(got[1].contains(" yona - 2 "))
        }
    }

    @Test
    fun `TCP는 옥텟 세기 틀로 보낸다`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableFuture.supplyAsync { server.accept().use { s -> val out = ByteArrayOutputStream(); s.getInputStream().transferTo(out); out.toString(Charsets.UTF_8) } }
            target(port = server.localPort, protocol = "tcp").send(listOf(entry(1), entry(2)))
            val text = received.get(3, java.util.concurrent.TimeUnit.SECONDS)
            val frames = mutableListOf<String>(); var rest = text
            while (rest.isNotEmpty()) {
                val space = rest.indexOf(' ')
                val len = rest.substring(0, space).toInt()
                val bytes = rest.substring(space + 1).toByteArray(Charsets.UTF_8)
                frames += String(bytes, 0, len, Charsets.UTF_8)
                rest = String(bytes, len, bytes.size - len, Charsets.UTF_8)
            }
            assertEquals(2, frames.size)
            assertTrue(frames[0].startsWith("<110>1 ")); assertTrue(frames[1].contains(" yona - 2 "))
        }
    }

    @Test
    fun `TCP 연결이 안 되면 예외를 던진다`() {
        assertFailsWith<Exception> { target(port = 1, protocol = "tcp").send(listOf(entry(1))) }
    }

    @Test
    fun `호스트가 없거나 프로토콜을 모르면 설정되지 않은 것이다`() {
        assertFalse(target(host = null).configured()); assertFalse(target(host = "").configured()); assertFalse(target(protocol = "carrier-pigeon").configured())
        assertTrue(target().configured()); assertTrue(target(protocol = "TCP").configured()); assertTrue(target(protocol = "tls").configured())
    }
}
