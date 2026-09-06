package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.Base64

private const val TEST_PUBLIC_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"
private const val TEST_PUBLIC_KEY_2 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx tester2@example.com"

// yona-wiki P3-03 Step2 — HTTPS Deploy Key 인증이 실제 SecurityFilterChain(DeployKeyAuthenticationProvider
// + GitAuthorizationFilter)을 거쳐 기대대로 동작하는지 검증. GitAuthorizationFilterIntegrationSpec과
// 동일한 방식(MockMvc + 실제 springSecurityFilterChain)으로, 실제 git wire 프로토콜까지는 실행하지
// 않고 인증/인가 레이어의 HTTP 상태 코드만 검증한다(기존 스펙과 동일한 검증 수준).
class DeployKeyGitAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val deployKeyRepository: DeployKeyRepository,
    private val deployKeyService: DeployKeyService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()
        }

        describe("HTTPS Deploy Key 인증/인가 통합 테스트") {
            beforeEach {
                deployKeyRepository.deleteAll()
                projectRepository.deleteAll()
            }

            afterSpec {
                deployKeyRepository.deleteAll()
                projectRepository.deleteAll()
            }

            fun basicHeader(rawToken: String): String =
                "Basic " + Base64.getEncoder().encodeToString(
                    "${DeployKeyAuthenticationProvider.DEPLOY_KEY_USERNAME}:$rawToken".toByteArray()
                )

            it("스코프 내 PRIVATE 프로젝트는 Deploy Key로 clone(GET)이 허용되어야 한다") {
                val project = projectRepository.save(
                    Project(name = "scoped-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "clone용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    get("/git/gildong/scoped-repo.git/info/refs?service=git-upload-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }

            // 보안 리뷰 항목 — Deploy Key가 repository_id 스코프 밖 저장소에는 절대 접근하지 못해야 한다.
            it("다른 프로젝트에 스코프된 Deploy Key로는 이 프로젝트에 접근할 수 없어야 한다(403)") {
                val ownProject = projectRepository.save(
                    Project(name = "own-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val otherProject = projectRepository.save(
                    Project(name = "other-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(ownProject, "own 전용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    get("/git/gildong/other-repo.git/info/refs?service=git-upload-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldBe 403
                    }

                // sanity check: 대상 프로젝트가 실제로 존재함(404가 아니라 403이어야 스코프 검사가 된 것)
                otherProject.id shouldNotBe null
            }

            // 보안 리뷰 항목 — read_only 플래그가 실제로 push를 막아야 한다.
            it("read_only Deploy Key로 push(POST git-receive-pack) 시도 시 403을 응답해야 한다") {
                val project = projectRepository.save(
                    Project(name = "readonly-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "읽기전용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    post("/git/gildong/readonly-repo.git/git-receive-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldBe 403
                    }
            }

            it("read_only가 아닌 Deploy Key는 push(POST git-receive-pack) 요청이 인가 레이어를 통과해야 한다") {
                val project = projectRepository.save(
                    Project(name = "writable-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "쓰기 허용 키", TEST_PUBLIC_KEY_2, readOnly = false)

                mockMvc.perform(
                    post("/git/gildong/writable-repo.git/git-receive-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }

            it("알 수 없는 토큰으로는 PRIVATE 프로젝트 접근 시 401을 응답해야 한다") {
                val project = projectRepository.save(
                    Project(name = "unknown-token-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )

                mockMvc.perform(
                    get("/git/gildong/unknown-token-repo.git/info/refs?service=git-upload-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader("yona_dk_not-a-real-token"))
                )
                    .andExpect { result ->
                        result.response.status shouldBe 401
                    }
            }
        }
    }
}
