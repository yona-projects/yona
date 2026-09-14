package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

// P3-68 — WebhookController.newWebhook()이 payloadUrl 누락 시 던지는
// ResponseStatusException(BAD_REQUEST, "Payload URL은 필수 입력 항목입니다.")의 reason이 실제
// HTTP 응답에 노출되는지 확인한다.
//
// mockk 기반 WebhookControllerSpec(standaloneSetup)이나 webAppContextSetup 기반 MockMvc로는 이
// 문제를 재현/검증할 수 없다 — 둘 다 실제 서블릿 컨테이너가 아니라서, ResponseStatusException이
// DispatcherServlet의 HandlerExceptionResolver에서 response.sendError(status, reason)까지만
// 호출되고, 컨테이너 수준의 /error 포워딩(Boot의 ErrorPageFilter가 담당 — 실제 임베디드 톰캣에서만
// 동작)이 전혀 일어나지 않는다(실측: webAppContextSetup MockMvc로 동일하게 요청해보면 상태코드는
// 400으로 정확히 나오지만 응답 바디가 완전히 빈 문자열이었다 — BasicErrorController 자체가
// 호출되지 않았다는 뜻). 그래서 RANDOM_PORT로 실제 임베디드 서버를 띄우고 java.net.http.HttpClient로
// 직접 확인해야 한다(SessionCookieSecurityIntegrationSpec과 동일한 이유·동일한 로그인/CSRF 처리
// 패턴).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookResponseStatusExceptionMessageIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    init {
        describe("P3-68: ResponseStatusException reason이 실제 에러 응답에 노출되는지") {
            it("빈 payloadUrl로 웹훅 등록 폼을 제출하면 400 응답 바디에 안내 메시지가 실제로 나타나야 한다") {
                val salt = "salt-p368"
                val loginId = "p368-mgr-${System.nanoTime()}"
                val owner = userRepository.save(
                    User(
                        loginId = loginId, name = "P368매니저", email = "$loginId@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )
                var project = projectRepository.save(Project(owner = owner.loginId, name = "p368-proj-${System.nanoTime()}"))
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
                project = projectRepository.save(project)

                try {
                    val client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()

                    // 1) 로그인 폼에서 CSRF 쿠키를 먼저 받아온다(SessionCookieSecurityIntegrationSpec와
                    // 동일한 CookieCsrfTokenRepository 더블서브밋 패턴).
                    val loginCsrfProbe = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:$port/users/loginform")).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                    )
                    val loginXsrf = loginCsrfProbe.headers().allValues("Set-Cookie")
                        .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                    loginXsrf.shouldNotBeNull()
                    val loginXsrfToken = loginXsrf.substringAfter("XSRF-TOKEN=").substringBefore(";")

                    // 2) 실제 로그인.
                    val loginForm = "loginIdOrEmail=$loginId&password=password1234"
                    val loginResponse = client.send(
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:$port/users/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Cookie", "XSRF-TOKEN=$loginXsrfToken")
                            .header("X-XSRF-TOKEN", loginXsrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString(loginForm))
                            .build(),
                        HttpResponse.BodyHandlers.discarding()
                    )
                    val sessionCookie = loginResponse.headers().allValues("Set-Cookie")
                        .firstOrNull { it.startsWith("JSESSIONID") }
                        ?.substringBefore(";")
                    sessionCookie.shouldNotBeNull()

                    // 3) 인증된 세션으로 웹훅 설정 화면을 GET해 그 세션에 유효한 CSRF 토큰을 다시
                    // 받아온다(로그인 시 세션 고정 공격 방지를 위해 토큰이 재발급될 수 있어, 로그인 전
                    // 토큰을 그대로 재사용하지 않고 새로 받는다).
                    val webhookPageProbe = client.send(
                        HttpRequest.newBuilder(
                            URI.create("http://localhost:$port/projects/${project.owner}/${project.name}/webhooks")
                        )
                            .header("Cookie", sessionCookie)
                            .GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                    )
                    val webhookXsrf = webhookPageProbe.headers().allValues("Set-Cookie")
                        .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                        ?.substringAfter("XSRF-TOKEN=")?.substringBefore(";")
                        ?: loginXsrfToken
                    val webhookXsrfCookie = webhookPageProbe.headers().allValues("Set-Cookie")
                        .firstOrNull { it.startsWith("XSRF-TOKEN=") }
                        ?.substringBefore(";")
                        ?: "XSRF-TOKEN=$loginXsrfToken"

                    // 4) payloadUrl을 비운 채(클라이언트측 검증을 우회한 상황과 동일) 웹훅 등록 폼을
                    // 직접 POST한다.
                    val webhookForm = "payloadUrl=&webhookType=SIMPLE"
                    val webhookResponse = client.send(
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:$port/projects/${project.owner}/${project.name}/webhooks"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Cookie", "$sessionCookie; $webhookXsrfCookie")
                            .header("X-XSRF-TOKEN", webhookXsrf)
                            .POST(HttpRequest.BodyPublishers.ofString(webhookForm))
                            .build(),
                        HttpResponse.BodyHandlers.ofString()
                    )

                    webhookResponse.statusCode() shouldBe 400
                    webhookResponse.body() shouldContain "Payload URL은 필수 입력 항목입니다."
                } finally {
                    projectRepository.delete(project)
                    userRepository.delete(owner)
                }
            }
        }
    }
}
