package com.github.yonaprojects.yona.config.svn

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.config.git.DeployKeyAuthenticationProvider
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.Base64

private const val TEST_PUBLIC_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"
private const val TEST_PUBLIC_KEY_2 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx tester2@example.com"

// 2026-09-07 — SvnAuthorizationFilter에 GitAuthorizationFilter와 동일한 Deploy Key 분기를 추가한
// 회귀 가드. 이전까지는 DeployKeyAuthenticationProvider가 SecurityConfig에 전역 등록돼 있어
// Deploy Key로 SVN 요청도 "인증"까지는 통과했지만, SvnAuthorizationFilter가 이 인증 타입을
// 인식하지 못하고 일반 로그인 사용자처럼 취급해(loginId가 "x-access-deploykey" 고정 문자열이라)
// 항상 403이 나는 죽은 기능이었다(DeployKeyController가 vcs 종류로 생성을 막지 않아 실제로
// 재현 가능한 함정이었음 — 이번에 발견/수정). DeployKeyGitAuthorizationIntegrationSpec과
// 동일한 검증 수준(MockMvc + 실제 springSecurityFilterChain, HTTP 상태 코드만 확인).
class DeployKeySvnAuthorizationIntegrationSpec @Autowired constructor(
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

        describe("SVN Deploy Key 인증/인가 통합 테스트") {
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

            it("스코프 내 PRIVATE SVN 프로젝트는 Deploy Key로 읽기(PROPFIND)가 허용되어야 한다") {
                val project = projectRepository.save(
                    Project(name = "svn-dk-scoped-repo", owner = "gildong", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "clone용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    request(HttpMethod.valueOf("PROPFIND"), "/svn/gildong/svn-dk-scoped-repo/trunk")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }

            // 보안 리뷰 항목 — Deploy Key가 repository_id 스코프 밖 저장소에는 절대 접근하지 못해야 한다.
            it("다른 프로젝트에 스코프된 Deploy Key로는 이 SVN 프로젝트에 접근할 수 없어야 한다(403)") {
                val ownProject = projectRepository.save(
                    Project(name = "svn-dk-own-repo", owner = "gildong", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val otherProject = projectRepository.save(
                    Project(name = "svn-dk-other-repo", owner = "gildong", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(ownProject, "own 전용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    request(HttpMethod.valueOf("PROPFIND"), "/svn/gildong/svn-dk-other-repo/trunk")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldBe 403
                    }

                // sanity check: 대상 프로젝트가 실제로 존재함(404가 아니라 403이어야 스코프 검사가 된 것)
                otherProject.id shouldNotBe null
            }

            // 보안 리뷰 항목 — read_only 플래그가 실제로 쓰기를 막아야 한다.
            it("read_only Deploy Key로 SVN 쓰기(PUT) 시도 시 403을 응답해야 한다") {
                val project = projectRepository.save(
                    Project(name = "svn-dk-readonly-repo", owner = "gildong", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "읽기전용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                mockMvc.perform(
                    put("/svn/gildong/svn-dk-readonly-repo/trunk/a.txt")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldBe 403
                    }
            }

            it("read_only가 아닌 Deploy Key는 SVN 쓰기(PUT) 요청이 인가 레이어를 통과해야 한다") {
                val project = projectRepository.save(
                    Project(name = "svn-dk-writable-repo", owner = "gildong", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                val issued = deployKeyService.create(project, "쓰기 허용 키", TEST_PUBLIC_KEY_2, readOnly = false)

                mockMvc.perform(
                    put("/svn/gildong/svn-dk-writable-repo/trunk/a.txt")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader(issued.rawHttpsToken))
                )
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }
        }
    }
}
