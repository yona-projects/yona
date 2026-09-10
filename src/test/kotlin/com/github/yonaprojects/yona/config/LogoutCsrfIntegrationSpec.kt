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

// Spring Security의 LogoutConfigurer는 "CSRF가 활성화돼 있으면(정확히는 HttpSecurity에
// CsrfConfigurer가 등록돼 있으면 — .csrf { it.disable() }는 등록 자체를 제거하므로 이 조건이
// 꺼져 있었음) 로그아웃은 POST만 받는다"로 자동 전환된다(LogoutConfigurer.createLogoutRequestMatcher
// 참고). 기존 site/layout.html의 로그아웃 링크는 <a href="/users/logout">(GET)였으므로, CSRF를
// 켜면 조용히 깨진다 — site/layout.html::scripts의 전역 클릭 핸들러(.js-logout-link -> $.post)로
// 대체했다. 이 스펙은 그 대체 메커니즘이 실제로 동작하는지 실제 임베디드 서버 + 세션 쿠키 +
// CSRF 쿠키/헤더로 검증한다.
//
// 기존에 SimpleUrlLogoutSuccessHandler가 302 대신 204를 반환하던 결함의 근본 원인도 함께
// 수정했다: 원인은 SimpleUrlLogoutSuccessHandler 자체가 아니라 .httpBasic { }가
// HttpBasicConfigurer.registerDefaultLogoutSuccessHandler()를 통해 LogoutConfigurer에
// defaultLogoutSuccessHandlerFor(HttpStatusReturningLogoutSuccessHandler(204), preferredMatcher)를
// 자동 등록해두는 것 — preferredMatcher는 "X-Requested-With: XMLHttpRequest" 또는 "Accept가
// text/html을 명시하지 않는 요청"(Accept 헤더가 아예 없는 요청도 포함)에 매치되고, jQuery
// $.post가 자동으로 X-Requested-With를 붙이므로 site/layout.html의 로그아웃 요청이 정확히
// 여기 걸렸다. SecurityConfig.kt의 .logout { }에서 .logoutSuccessUrl(...) 대신
// .logoutSuccessHandler(SimpleUrlLogoutSuccessHandler(...))로 핸들러를 직접 지정해 이 기본
// 매핑을 완전히 무시하도록 고쳤다 — 이제 이 스펙은 CSRF 통과 여부뿐 아니라 실제 302+Location까지
// 검증한다.
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

                // 로그인 페이지로 실제 302 리다이렉트되는지 검증 — SecurityConfig.kt의
                // .logout { }가 .logoutSuccessHandler(SimpleUrlLogoutSuccessHandler(...))로
                // httpBasic()의 기본 204 매핑을 무시하도록 고쳐졌으므로, AJAX 요청(이 호출처럼
                // Accept 헤더가 없는 경우 포함)에도 302 + Location이 나가야 한다.
                logoutResponse.statusCode() shouldBe 302
                logoutResponse.headers().firstValue("Location").orElse(null) shouldBe
                    "http://localhost:$port/users/loginform?logout"

                // 5) 같은 세션 쿠키로 인증이 필요한 화면(UserViewController.editUserProfileForm —
                // 인증 안 되면 "error/403" 뷰를 200으로 반환, Spring Security 인가 규칙이 아니라
                // 컨트롤러 자체가 판단)에 접근했을 때, 완전히 새 클라이언트(쿠키 전혀 없음)로 접근한
                // 것과 응답 바디가 동일해야 한다 — 세션이 실제로 무효화됐다는(=더 이상 인증되지
                // 않는다는) 증거. 정확한 오류 메시지 문구는 로케일에 따라 달라질 수 있어 문자열
                // 대신 "완전한 익명 접근과 동일한 결과"로 검증한다. 단, 두 응답 모두 sitewide 로그인
                // 모달의 _csrf 히든 필드 값은 매 요청 새로 발급되는 난수라 그 부분만 마스킹하고
                // 비교한다.
                fun maskCsrfToken(body: String) =
                    body.replace(Regex("name=\"_csrf\" value=\"[^\"]*\""), "name=\"_csrf\" value=\"MASKED\"")

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
