package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.sshkey.SshKeyRepository
import com.github.yonaprojects.yona.domain.sshkey.SshKeyService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

private const val USER_PUBLIC_KEY =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"

/**
 * yona-wiki P3-03/P3-18 — `ssh-auth.sh`(`docs/guide/ssh-system-sshd-setup.md`)가 호출할
 * `/internal/ssh/authenticate` 엔드포인트 자체의 동작을 검증한다(`/internal/ssh/authorize`는
 * P3-18에서 죽은 코드로 판명돼 삭제됐다 — SshRelayServer가 인가 판정을 직접 담당). 실제 시스템
 * sshd 연동은 호스트 시스템을 건드릴 수 없어(작업 지시) 이 세션에서 수행할 수 없으므로, "sshd가
 * 이 API를 이렇게 호출하면 이렇게 응답한다"는 계약을 실제 HTTP 요청(JDK HttpClient)으로 검증하는
 * 것으로 대체한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SshInternalControllerIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val sshKeyService: SshKeyService,
    private val sshKeyRepository: SshKeyRepository,
    private val secretProvider: SshInternalSecretProvider
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        private val secretPathHolder = Files.createTempDirectory("ssh-internal-secret-it-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("yona.ssh.internal-secret-path") { "${secretPathHolder.absolutePath}/internal-secret" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun postJson(path: String, json: String, secret: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
        if (secret != null) builder.header("X-Yona-Internal-Secret", secret)
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    init {
        describe("SshInternalController") {
            beforeEach {
                sshKeyRepository.deleteAll()
                userRepository.deleteAll()
            }

            afterSpec {
                sshKeyRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("공유 시크릿 헤더가 없으면 403을 응답해야 한다") {
                val response = postJson(
                    "/internal/ssh/authenticate",
                    """{"publicKey":"$USER_PUBLIC_KEY"}""",
                    secret = null
                )
                response.statusCode() shouldBe 403
            }

            it("공유 시크릿이 틀리면 403을 응답해야 한다") {
                val response = postJson(
                    "/internal/ssh/authenticate",
                    """{"publicKey":"$USER_PUBLIC_KEY"}""",
                    secret = "wrong-secret"
                )
                response.statusCode() shouldBe 403
            }

            it("올바른 시크릿 + 알 수 없는 공개키는 404를 응답해야 한다") {
                val response = postJson(
                    "/internal/ssh/authenticate",
                    """{"publicKey":"$USER_PUBLIC_KEY"}""",
                    secret = secretProvider.secret
                )
                response.statusCode() shouldBe 404
            }

            it("올바른 시크릿 + 등록된 SshKey는 sshkey: principal을 반환해야 한다") {
                val user = userRepository.save(User(loginId = "internal-user", name = "내부호출자", email = "internal-user@example.com"))
                val sshKey = sshKeyService.create(user, "노트북", USER_PUBLIC_KEY)

                val response = postJson(
                    "/internal/ssh/authenticate",
                    """{"publicKey":"$USER_PUBLIC_KEY"}""",
                    secret = secretProvider.secret
                )

                response.statusCode() shouldBe 200
                response.body() shouldContain "\"principal\":\"sshkey:${sshKey.id}\""
            }

            it("SshInternalSecretProvider는 매 호출마다 동일한 시크릿을 반환해야 한다(파일 영속화)") {
                val first = secretProvider.secret
                val second = secretProvider.secret
                first shouldBe second
            }
        }
    }
}
