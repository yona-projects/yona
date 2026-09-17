package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

// CSRF가 활성화되면 LogoutConfigurer가 로그아웃을 POST 전용으로 자동 전환한다
// (LogoutConfigurer.createLogoutRequestMatcher). 기존 site/layout.html의 GET 로그아웃 링크는
// 그래서 조용히 깨지므로, 전역 클릭 핸들러(.js-logout-link -> $.post)로 대체했다.
//
// 별개로, 로그아웃 성공 시 302 대신 204가 나가던 버그의 원인은 .httpBasic { }이 자동 등록하는
// HttpStatusReturningLogoutSuccessHandler(204)였다 — X-Requested-With 헤더가 있거나 Accept가
// text/html이 아닌 요청에 매치되는데, jQuery $.post가 X-Requested-With를 자동으로 붙여 여기
// 걸렸다. SecurityConfig.kt에서 .logoutSuccessHandler(SimpleUrlLogoutSuccessHandler(...))를
// 직접 지정해 이 기본 매핑을 무시하도록 고쳤다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogoutCsrfIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    init {
        describe("POST /users/logout (CSRF 헤더/쿠키 포함 — site/layout.html의 js-logout-link가 실제로 하는 것)") {
            it("403 없이 처리되고, 세션을 무효화하고, 로그인 페이지로 302 리다이렉트해야 한다") {
                val client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()

                val salt = "salt-logout"
                val loginId = "logoutcheck-${System.nanoTime()}"
                userRepository.save(
                    User(
                        loginId = loginId, name = "로그아웃검증", email = "$loginId@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )

                // 1) CSRF 토큰 확보(로그인 페이지는 th:action 폼이라 렌더링 시점에 토큰이 발급됨)
                val csrfProbe = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/users/loginform")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                val loginCsrfCookie = csrfProbe.headers().allValues("Set-Cookie")
                    .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                loginCsrfCookie.shouldNotBeNull()
                val loginCsrfToken = loginCsrfCookie.substringAfter("XSRF-TOKEN=").substringBefore(";")

                // 2) 로그인 — 세션 쿠키 확보
                val loginResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/users/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", "XSRF-TOKEN=$loginCsrfToken")
                        .header("X-XSRF-TOKEN", loginCsrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString("loginIdOrEmail=$loginId&password=password1234"))
                        .build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                val sessionCookie = loginResponse.headers().allValues("Set-Cookie")
                    .firstOrNull { it.startsWith("JSESSIONID") }
                sessionCookie.shouldNotBeNull()
                val sessionId = sessionCookie.substringAfter("JSESSIONID=").substringBefore(";")

                // 3) 로그아웃용 CSRF 토큰 확보 — JS의 $.ajaxSetup 인터셉터가 매 요청 직전 쿠키를
                // 읽는 흐름을 그대로 재현한다(1)에서 받은 토큰을 그대로 재사용해도 무방하지만,
                // 실제 흐름과 최대한 가깝게 맞춘다).
                val logoutCsrfProbe = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/"))
                        .header("Cookie", "JSESSIONID=$sessionId")
                        .GET().build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                val logoutCsrfCookie = logoutCsrfProbe.headers().allValues("Set-Cookie")
                    .firstOrNull { it.startsWith("XSRF-TOKEN=") } ?: loginCsrfCookie
                val logoutCsrfToken = logoutCsrfCookie.substringAfter("XSRF-TOKEN=").substringBefore(";")

                // 4) 로그아웃 — site/layout.html::scripts의 $(".js-logout-link") 클릭 핸들러가
                // 실제로 보내는 것과 동일한 요청(세션 쿠키 + X-XSRF-TOKEN 헤더로 POST). CSRF
                // 검증에서 막히면(토큰 누락/불일치) 403이 났을 것 — 403이 아니라는 것 자체가 이
                // 스펙의 핵심 검증이다.
                val logoutResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/users/logout"))
                        .header("Cookie", "JSESSIONID=$sessionId; XSRF-TOKEN=$logoutCsrfToken")
                        .header("X-XSRF-TOKEN", logoutCsrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.discarding()
                )

                // Accept 헤더 없는 AJAX 요청에도 httpBasic()의 기본 204 대신 302+Location이
                // 나가야 한다(SecurityConfig.kt의 명시적 logoutSuccessHandler 덕분).
                logoutResponse.statusCode() shouldBe 302
                logoutResponse.headers().firstValue("Location").orElse(null) shouldBe
                    "http://localhost:$port/users/loginform?logout"

                // 5) 세션 무효화 확인: 같은 쿠키로 접근한 응답이 완전히 새 클라이언트(쿠키 없음)의
                // 응답과 동일해야 한다(UserViewController가 미인증 시 "error/403" 뷰를 200으로
                // 반환하므로 상태 코드로는 구분 안 됨). 매 요청 새로 발급되는 CSRF 값(히든 필드
                // value, meta 태그 content)만 마스킹하고 비교한다.
                fun maskCsrfToken(body: String) =
                    body.replace(Regex("name=\"_csrf\" value=\"[^\"]*\""), "name=\"_csrf\" value=\"MASKED\"")
                        .replace(Regex("name=\"_csrf\" content=\"[^\"]*\""), "name=\"_csrf\" content=\"MASKED\"")

                val afterLogoutBody = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/user/editform"))
                        .header("Cookie", "JSESSIONID=$sessionId")
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                ).body()

                val neverLoggedInClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                val anonymousBody = neverLoggedInClient.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port/user/editform")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                ).body()

                maskCsrfToken(afterLogoutBody) shouldBe maskCsrfToken(anonymousBody)
            }
        }
    }
}
