package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-09(Swagger/OpenAPI UI 노출) 대응 — legacy Yona에는 없던 신규 기능. springdoc이
// 자동 스캔한 API 문서(관리자 API 포함)가 비로그인 사용자에게도 그대로 노출되는 걸 막기 위해
// SecurityConfig.kt의 /site/**,/sites/**와 동일한 패턴(hasAnyRole("ADMIN","SITE_ADMIN"))으로 제한한다.
class SwaggerUiAccessIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var adminDetails: YonaUserDetails

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            // BootstrapSetupInterceptor(별개 기능)가 "DB에 회원 0명이면 무조건 /bootstrap-setup으로
            // 리다이렉트"하는 로직을 갖고 있어(config/BootstrapSetupInterceptor.kt), 관리자 유저를
            // 먼저 만들어 이 게이트를 우회해야 아래 테스트들이 실제로 접근 제어(hasAnyRole)만
            // 검증하게 된다 — 안 그러면 비로그인 테스트가 부트스트랩 리다이렉트(302)와 우연히
            // 같은 상태코드라 엉뚱한 이유로 통과해버린다(실제로 이 순서로 확인됨).
            val admin = userRepository.save(
                User(loginId = "swagger-admin", name = "관리자", email = "swagger-admin@example.com", state = UserState.SITE_ADMIN)
            )
            adminDetails = YonaUserDetails(
                id = admin.id!!,
                loginId = admin.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_SITE_ADMIN", "ROLE_ADMIN")
            )
        }

        describe("Swagger UI/OpenAPI 문서 접근 제어") {
            // httpBasic()도 함께 설정돼 있어(SecurityConfig.kt), 인증정보 없는 요청은 formLogin
            // 리다이렉트가 아니라 401 + WWW-Authenticate로 응답한다 — /site/**와 동일한 방언.
            it("비로그인 사용자가 /v3/api-docs에 접근하면 401이어야 한다(httpBasic 인가 미제공)") {
                val result = mockMvc.perform(get("/v3/api-docs")).andReturn()

                result.response.status shouldBe 401
            }

            it("비로그인 사용자가 /swagger-ui/index.html에 접근하면 401이어야 한다(httpBasic 인가 미제공)") {
                val result = mockMvc.perform(get("/swagger-ui/index.html")).andReturn()

                result.response.status shouldBe 401
            }

            it("사이트 관리자는 /v3/api-docs에 접근해 기존 @RestController가 자동 스캔된 OpenAPI 스펙을 받아야 한다") {
                val result = mockMvc.perform(get("/v3/api-docs").with(user(adminDetails))).andReturn()

                result.response.status shouldBe 200
                result.response.contentAsString.contains("openapi") shouldBe true
                // IssueController.kt의 실제 매핑 경로 — springdoc이 기존 컨트롤러를 실제로
                // 스캔하고 있는지(빈 스펙이 아닌지) 구체적으로 확인한다.
                result.response.contentAsString.contains("/api/projects/{projectId}/issues") shouldBe true
            }

            it("일반 로그인 사용자(사이트 관리자가 아님)는 /v3/api-docs에 접근하면 403이어야 한다") {
                val member = userRepository.save(
                    User(loginId = "swagger-member", name = "일반유저", email = "swagger-member@example.com", state = UserState.ACTIVE)
                )
                val memberDetails = YonaUserDetails(
                    id = member.id!!,
                    loginId = member.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val result = mockMvc.perform(get("/v3/api-docs").with(user(memberDetails))).andReturn()

                result.response.status shouldBe 403
            }
        }
    }
}
