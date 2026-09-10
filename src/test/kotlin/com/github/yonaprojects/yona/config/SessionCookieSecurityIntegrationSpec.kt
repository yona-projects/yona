package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Base64

private fun legacyHash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.reset()
    digest.update(salt.toByteArray(Charsets.UTF_8))
    var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
    for (i in 1 until 1024) {
        digest.reset()
        hashed = digest.digest(hashed)
    }
    return Base64.getEncoder().encodeToString(hashed)
}

// application.yml에 명시한 server.servlet.session.cookie.same-site 설정이 실제 임베디드
// 서버에서 로그인 성공 응답의 Set-Cookie 헤더에 반영되는지 검증한다.
// MockMvc는 실제 서블릿 컨테이너 쿠키 처리기를 거치지 않아 SameSite 속성 부착 여부를 검증할 수
// 없으므로, RANDOM_PORT로 실제 임베디드 톰캣을 띄우고 java.net.http.HttpClient로 직접 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieSecurityIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    init {
        describe("로그인 성공 시 세션 쿠키") {
            it("Set-Cookie 헤더에 SameSite=Lax가 포함돼야 한다") {
                val salt = "salt-cookie"
                val loginId = "cookiecheck-${System.nanoTime()}"
                userRepository.save(
                    User(
                        loginId = loginId, name = "쿠키검증", email = "$loginId@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )

                val client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()

                // CookieCsrfTokenRepository는 세션 없는 더블서브밋 쿠키 패턴이라, 로그인 POST 전에
                // GET으로 XSRF-TOKEN 쿠키를 먼저 받아와야 한다(login.html은 th:action 폼이라
                // 렌더링 시점에 토큰이 생성/저장됨).
                // 받은 쿠키 값을 그대로 Cookie 헤더(서버가 "기대값"으로 비교)와 X-XSRF-TOKEN 헤더
                // (SpaCsrfTokenRequestHandler가 XOR 복원 없이 그대로 비교하는 "제출값")에 실어 보낸다 —
                // JS의 $.ajaxSetup 인터셉터가 실제로 하는 것과 동일한 매커니즘을 raw HttpClient로 재현.
                val csrfProbe = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/users/loginform")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                val xsrfCookieHeader = csrfProbe.headers().allValues("Set-Cookie")
                    .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                xsrfCookieHeader.shouldNotBeNull()
                val xsrfToken = xsrfCookieHeader.substringAfter("XSRF-TOKEN=").substringBefore(";")

                val form = "loginIdOrEmail=$loginId&password=password1234"
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$port/users/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", "XSRF-TOKEN=$xsrfToken")
                    .header("X-XSRF-TOKEN", xsrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                val sessionCookie = response.headers().allValues("Set-Cookie")
                    .firstOrNull { it.startsWith("JSESSIONID") }

                sessionCookie.shouldNotBeNull()
                sessionCookie shouldContain "SameSite=Lax"
            }
        }
    }
}
