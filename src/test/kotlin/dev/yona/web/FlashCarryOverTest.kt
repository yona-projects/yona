package dev.yona.web

import dev.yona.support.TestDatabase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * 컨트롤러가 일회성 메시지를 심은 뒤 같은 요청에서 화면을 그리면(`forward:`로 첫 화면을 그리는 경우) 그 메시지가 이번 화면에도 보이고
 * 바로 다음 요청에도 한 번 더 보이는지 실제 서버로 검증한다.
 *
 * 원본: `IssueApp.newDirectIssueForm`은 `flash(...)` 뒤 `Application.index()`를 직접 불러 그리고, Play의 flash 쿠키는 응답에 실려 다음 요청에서 읽힌다.
 * MockMvc는 `forward:`를 실행하지 않으므로 서버를 띄워 쿠키를 주고받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-flash-carry"])
class FlashCarryOverTest {

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

    private fun body(client: HttpClient, path: String): String =
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()

    private fun count(text: String): Int = Regex("""notify\("Project is non existent""").findAll(text).count()

    @Test
    fun `가려는 프로젝트가 없다는 안내는 그 화면에 보이고 다음 요청에도 한 번 보인다`() {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        assertEquals(1, count(body(client, "/user/issues/new")))
        assertEquals(1, count(body(client, "/no/such/path/here")))
        assertEquals(0, count(body(client, "/no/such/path/here")))
    }
}
