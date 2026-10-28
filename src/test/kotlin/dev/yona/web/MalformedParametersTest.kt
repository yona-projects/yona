package dev.yona.web

import dev.yona.support.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 해석할 수 없는 요청 값을 v1.6(Play)처럼 다루는지 실제 서버에 소켓으로 보내 검증한다(Tomcat의 질의 문자열 해석은 MockMvc를 거치지 않는다).
 * 공식 v1.16.0의 응답: `?a=%FF`(잘못된 UTF-8)와 `?=`(이름 없음)는 200, `?a=%ZZ`(잘못된 퍼센트 열)는 400이다. 잘못된 멀티파트 본문은 값이 없는 요청으로 보아
 * 로그인 폼 검증 화면(400)이 나온다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-malformed-params"])
class MalformedParametersTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            TestDatabase.current.register(registry)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "3" }
            registry.add("spring.datasource.hikari.minimum-idle") { "0" }
        }
    }

    @LocalServerPort private var port: Int = 0

    /** 소켓으로 요청을 보내 상태 코드와 본문을 돌려준다. */
    private fun send(method: String, target: String, headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0)): Pair<Int, String> {
        Socket("127.0.0.1", port).use { s ->
            val head = StringBuilder("$method $target HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n")
            headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
            if (body.isNotEmpty()) head.append("Content-Length: ${body.size}\r\n")
            head.append("\r\n")
            s.getOutputStream().write(head.toString().toByteArray(Charsets.ISO_8859_1) + body)
            s.getOutputStream().flush()
            val text = String(s.getInputStream().readAllBytes(), Charsets.UTF_8)
            return text.lineSequence().first().split(" ")[1].toInt() to text
        }
    }

    @Test
    fun `질의 문자열에 잘못된 UTF-8이나 이름 없는 값이 있어도 화면은 열린다`() {
        assertEquals(200, send("GET", "/projects?a=%FF").first)
        assertEquals(200, send("GET", "/projects?=").first)
        assertEquals(200, send("GET", "/projects?&&&").first)
    }

    @Test
    fun `질의 문자열의 퍼센트 열이 잘못되면 400이다`() {
        assertEquals(400, send("GET", "/projects?a=%ZZ").first)
        assertEquals(400, send("GET", "/projects?a=100%").first)
    }

    @Test
    fun `잘못된 멀티파트 본문은 값이 없는 요청이라 로그인 검증 화면이다`() {
        val (status, text) = send("POST", "/users/login", mapOf("Content-Type" to "multipart/form-data"), "loginIdOrEmail=tester&password=x".toByteArray())
        assertEquals(400, status)
        assertTrue(text.contains("<title>Log in"), text.take(300))
        val (status2, _) = send("POST", "/users/login", mapOf("Content-Type" to "multipart/form-data; boundary=XX"), "--XX\r\nContent-Disposition: form-data; name=\"loginIdOrEmail\"\r\n\r\ntester".toByteArray())
        assertEquals(400, status2)
    }
}
