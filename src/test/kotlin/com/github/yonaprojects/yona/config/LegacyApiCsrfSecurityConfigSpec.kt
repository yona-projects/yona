package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import tools.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// `/-_-api/v1/**`(+ 레거시 `/api/**`, `/api/v1/**`는 제외, LegacyApiSecurityConfig.kt)는 세션
// 쿠키(웹 UI 자신, 예: site/layout.html의 즐겨찾기 토글)와 PAT 헤더(yona-cli 등) 양쪽으로
// 호출되는 유일한 네임스페이스라 CSRF를 통째로 켜거나 끌 수 없다 — 인증 방식에 따라 분기해야
// 한다는 것을 실제 통합테스트로 고정한다. `POST /-_-api/v1/favoriteProjects/{projectId}`
// (FavoriteController)를 대표 엔드포인트로 쓴다 — 인증만 되면 별도 파라미터/스코프 검증 없이
// 즉시 상태를 바꾸는 가장 단순한 이 네임스페이스 엔드포인트라 CSRF 자체의 효과만 순수하게
// 검증하기 좋다.
class LegacyApiCsrfSecurityConfigSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {

    private val mockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    private fun authOf(u: User) = user(
        YonaUserDetails(
            id = u.id ?: 0L,
            loginId = u.loginId,
            passwordVal = "h",
            passwordSalt = "s",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )
    )

    init {
        // h2는 이 테스트 JVM 전체가 스키마를 공유한다(AbstractIntegrationTest 주석 참고) — 이
        // 스펙이 만드는 FavoriteProject가 정리되지 않으면 다른 스펙이 같은 Project를 삭제할 때
        // FK 제약 위반으로 실패해 무관한 스펙들이 연쇄로 깨진다. 자식(FavoriteProject) →
        // 부모(Project) → User 순서로 반드시 정리한다.
        afterSpec {
            listOf("legacy-api-csrf-proj1", "legacy-api-csrf-proj2", "legacy-api-csrf-proj3").forEach { name ->
                projectRepository.findAll().filter { it.name == name }.forEach { project ->
                    favoriteProjectRepository.findByProjectId(project.id!!).forEach { favoriteProjectRepository.delete(it) }
                    projectRepository.delete(project)
                }
            }
            listOf("legacy-api-csrf-owner1", "legacy-api-csrf-owner2", "legacy-api-csrf-owner3").forEach { loginId ->
                userRepository.findByLoginId(loginId).ifPresent { userRepository.delete(it) }
            }
        }

        describe("POST /-_-api/v1/favoriteProjects/{projectId} (세션 쿠키 인증)") {
            it("CSRF 토큰 없이 세션 쿠키로만 호출하면 403으로 거절돼야 한다") {
                val owner = userRepository.save(User(loginId = "legacy-api-csrf-owner1", name = "소유자1", email = "legacy-api-csrf-owner1@yona.io"))
                val project = projectRepository.save(Project(name = "legacy-api-csrf-proj1", owner = owner.loginId, projectScope = ProjectScope.PUBLIC))

                mockMvc.perform(
                    post("/-_-api/v1/favoriteProjects/${project.id}")
                        .with(authOf(owner))
                ).andExpect(status().isForbidden)
            }

            it("CSRF 토큰을 함께 보내면 정상적으로 즐겨찾기가 토글돼야 한다") {
                val owner = userRepository.save(User(loginId = "legacy-api-csrf-owner2", name = "소유자2", email = "legacy-api-csrf-owner2@yona.io"))
                val project = projectRepository.save(Project(name = "legacy-api-csrf-proj2", owner = owner.loginId, projectScope = ProjectScope.PUBLIC))

                val body = mockMvc.perform(
                    post("/-_-api/v1/favoriteProjects/${project.id}")
                        .with(authOf(owner))
                        .with(csrf())
                ).andExpect(status().isOk).andReturn().response.contentAsString

                val node = objectMapper.readTree(body)
                node.path("projectId").asText() shouldBe project.id.toString()
                node.path("favored").asBoolean() shouldBe true
            }
        }

        describe("POST /-_-api/v1/favoriteProjects/{projectId} (PAT/Yona-Token 헤더 인증)") {
            it("CSRF 토큰이 전혀 없어도 세션 쿠키가 아니므로 차단되지 않아야 한다") {
                val owner = userRepository.save(
                    User(
                        loginId = "legacy-api-csrf-owner3", name = "소유자3", email = "legacy-api-csrf-owner3@yona.io",
                        token = "legacy-full-access-token-for-csrf-test"
                    )
                )
                val project = projectRepository.save(Project(name = "legacy-api-csrf-proj3", owner = owner.loginId, projectScope = ProjectScope.PUBLIC))

                // 세션 인증(.with(user(...)))도, CSRF 토큰(.with(csrf()))도 전혀 쓰지 않는다 —
                // 오직 Yona-Token 헤더만으로 ApiTokenAuthenticationFilter(레거시 전권 토큰 경로,
                // authenticateLegacy())가 인증하고, LegacyApiSecurityConfig의
                // tokenAuthenticatedRequest 매처가 이 요청을 CSRF 검증에서 제외해야 한다.
                val body = mockMvc.perform(
                    post("/-_-api/v1/favoriteProjects/${project.id}")
                        .header("Yona-Token", "legacy-full-access-token-for-csrf-test")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                val node = objectMapper.readTree(body)
                node.path("projectId").asText() shouldBe project.id.toString()
                node.path("favored").asBoolean() shouldBe true
            }
        }
    }
}
