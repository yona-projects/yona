package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*

class GitAuthorizationFilterIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
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

        describe("Git HTTP 인증/인가 통합 테스트") {
            beforeEach {
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            it("존재하지 않는 프로젝트 접근 시 404 Not Found를 응답해야 한다") {
                mockMvc.perform(get("/git/owner/non-exist.git"))
                    .andExpect { result ->
                        result.response.status shouldBe 404
                    }
            }

            it("PUBLIC 프로젝트의 clone(GET) 요청은 익명 사용자도 통과되어야 한다") {
                // Given
                projectRepository.save(
                    Project(
                        name = "public-repo",
                        owner = "gildong",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        createdDate = Instant.now()
                    )
                )

                // When & Then
                mockMvc.perform(get("/git/gildong/public-repo.git/info/refs?service=git-upload-pack"))
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }

            it("PUBLIC 프로젝트라도 push(POST) 요청 시 익명 사용자는 401 Unauthorized를 응답해야 한다") {
                // Given
                projectRepository.save(
                    Project(
                        name = "public-repo",
                        owner = "gildong",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        createdDate = Instant.now()
                    )
                )

                // When & Then
                mockMvc.perform(post("/git/gildong/public-repo.git/git-receive-pack"))
                    .andExpect { result ->
                        result.response.status shouldBe 401
                        result.response.getHeader("WWW-Authenticate") shouldBe "Basic realm=\"Git Repository\""
                    }
            }

            it("PRIVATE 프로젝트의 clone 요청 시 익명 사용자는 401 Unauthorized를 응답해야 한다") {
                // Given
                projectRepository.save(
                    Project(
                        name = "private-repo",
                        owner = "gildong",
                        projectScope = ProjectScope.PRIVATE,
                        createdDate = Instant.now()
                    )
                )

                // When & Then
                mockMvc.perform(get("/git/gildong/private-repo.git/info/refs?service=git-upload-pack"))
                    .andExpect { result ->
                        result.response.status shouldBe 401
                    }
            }

            it("PRIVATE 프로젝트 요청 시 멤버가 아닌 로그인 유저는 403 Forbidden을 응답해야 한다") {
                // Given
                projectRepository.save(
                    Project(
                        name = "private-repo",
                        owner = "gildong",
                        projectScope = ProjectScope.PRIVATE,
                        createdDate = Instant.now()
                    )
                )

                // 철수 계정 생성
                val salt = "saltsalt"
                val hashedPw = hashPassword("pass123", salt)
                userRepository.save(
                    User(
                        loginId = "chulsoo",
                        name = "김철수",
                        email = "chulsoo@example.com",
                        password = hashedPw,
                        passwordSalt = salt,
                        createdDate = Instant.now()
                    )
                )

                val basicHeader = "Basic " + Base64.getEncoder().encodeToString("chulsoo:pass123".toByteArray())

                // When & Then
                mockMvc.perform(
                    get("/git/gildong/private-repo.git/info/refs?service=git-upload-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader)
                )
                    .andExpect { result ->
                        result.response.status shouldBe 403
                    }
            }

            it("PRIVATE 프로젝트 요청 시 멤버인 로그인 유저는 접근이 허용되어야 한다") {
                // Given
                val role = roleRepository.save(Role(id = 1L, name = "manager", active = true))
                val member = userRepository.save(
                    User(
                        loginId = "chulsoo",
                        name = "김철수",
                        email = "chulsoo@example.com",
                        password = hashPassword("pass123", "saltsalt"),
                        passwordSalt = "saltsalt",
                        createdDate = Instant.now()
                    )
                )
                val project = projectRepository.save(
                    Project(
                        name = "private-repo",
                        owner = "gildong",
                        projectScope = ProjectScope.PRIVATE,
                        createdDate = Instant.now()
                    )
                )
                projectUserRepository.save(ProjectUser(user = member, project = project, role = role))

                val basicHeader = "Basic " + Base64.getEncoder().encodeToString("chulsoo:pass123".toByteArray())

                // When & Then
                mockMvc.perform(
                    get("/git/gildong/private-repo.git/info/refs?service=git-upload-pack")
                        .header(HttpHeaders.AUTHORIZATION, basicHeader)
                )
                    .andExpect { result ->
                        result.response.status shouldNotBe 401
                        result.response.status shouldNotBe 403
                    }
            }
        }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(StandardCharsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(StandardCharsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }
}
