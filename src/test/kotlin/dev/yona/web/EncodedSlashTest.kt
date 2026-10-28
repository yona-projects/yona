package dev.yona.web

import dev.yona.support.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * 주소 경로에 인코딩된 슬래시(`%2F`)가 들어 있어도 서버가 400으로 거부하지 않고 v1.6처럼 라우트를 찾는지 검증한다.
 *
 * v1.6(Play/Netty)은 경로를 그대로 받아 라우트가 맞지 않으면 404, 와일드카드 라우트면 디코딩한 값으로 처리한다. 내장 Tomcat은 기본으로 `%2F`가 든 요청을
 * 400으로 거부하므로 실제 서버를 띄워 확인한다(MockMvc는 Tomcat을 거치지 않는다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-encoded-slash"])
class EncodedSlashTest {

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

    private fun status(path: String): Int =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode()

    @Test
    fun `경로 조각에 인코딩된 슬래시가 있어도 400이 아니라 없는 라우트로 처리한다`() {
        assertEquals(404, status("/no%2Fsuch"))
        // 익명이 없는 프로젝트(소유자 `nobody/proj1`)를 열면 v1.6은 프로젝트 검사에서 403을 낸다(실측)
        assertEquals(403, status("/nobody%2Fproj1/x"))
    }
}
