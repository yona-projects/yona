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
import kotlin.test.assertTrue

/**
 * 실제 서버(내장 Tomcat)에서 화면이 끝까지 그려지는지 검증한다. MockMvc는 응답을 커밋하지 않아 못 잡는 문제이다.
 *
 * springdoc(OpenAPI 문서, X-31)이 Jackson 2를 클래스패스에 끌어오면 타임리프가 `th:inline="javascript"` 값을 Jackson으로 직렬화하면서 응답 작성기를 비운다.
 * 템플릿을 그리는 대로 내보내는 기본 설정에서는 이 때 응답이 커밋되어, 곧이어 사용자 메뉴가 세션을 만들려다 `Cannot create a session after the response has been
 * committed`로 실패하고 모든 화면이 3KB쯤에서 끊겼다(`decisions.md` A-273). 템플릿을 끝까지 그린 뒤 내보내는 설정이 이를 막는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-encoded-slash"])
class RealServerRenderTest {

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

    private fun get(path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `홈과 로그인과 오류 화면은 끝까지 그려지고 세션 쿠키가 만들어진다`() {
        for (path in listOf("/", "/users/login", "/no%2Fsuch")) {
            val response = get(path)
            val body = response.body()
            assertTrue(body.trimEnd().endsWith("</html>"), "$path 화면이 끝까지 그려지지 않았다(${body.length}자): ...${body.takeLast(80)}")
            assertTrue(response.headers().allValues("Set-Cookie").any { it.startsWith("JSESSIONID=") }, "$path 에서 세션 쿠키가 만들어지지 않았다")
        }
        assertEquals(200, get("/").statusCode())
    }
}
