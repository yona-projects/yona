package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// WikiRestApiController end-to-end 검증 — TagRestApiControllerIntegrationSpec과 동일한
// AbstractIntegrationTest 패턴(실제 DB + 실제 파일시스템 bare 위키 저장소). 이 컨트롤러가
// yona-cli `yona wiki` 명령/MCP 위키 도구가 실제로 호출하는 백엔드이므로, 여기서 통과하면 그
// 위의 얇은 클라이언트들도 동작함을 신뢰할 수 있다.
class WikiRestApiControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    @Value("\${yona.git.base-dir:/tmp/yona/git}") private val gitBaseDir: String
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val ownerName = "wiki-e2e-owner"
    private val outsiderName = "wiki-e2e-outsider"
    private val projName = "wiki-e2e-repo"

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            userRepository.findByLoginId(outsiderName).ifPresent { userRepository.delete(it) }

            File(File(gitBaseDir), "$ownerName/$projName.wiki.git").deleteRecursively()

            val owner = userRepository.save(User(loginId = ownerName, name = "위키E2E소유자", email = "$ownerName@example.com"))
            userRepository.save(User(loginId = outsiderName, name = "위키E2E외부인", email = "$outsiderName@example.com"))
            // PUBLIC — "쓰기는 멤버만, 읽기는 공개 프로젝트 읽기 권한과 동일" 시나리오를 실제로
            // 검증하려면 비멤버가 읽을 수 있는 공개 프로젝트여야 한다.
            val project = projectRepository.save(Project(owner = ownerName, name = projName, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))
        }

        fun authOf(loginId: String): org.springframework.test.web.servlet.request.RequestPostProcessor {
            val u = userRepository.findByLoginId(loginId).get()
            val details = YonaUserDetails(
                id = u.id!!,
                loginId = u.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            return user(details)
        }
        fun ownerAuth() = authOf(ownerName)
        fun outsiderAuth() = authOf(outsiderName)

        describe("POST/GET/PUT/DELETE /api/v1/projects/{owner}/{project}/wiki/pages — 실제 bare 위키 저장소 end-to-end") {
            it("페이지를 생성하면 목록/조회에 나타나고, 수정하면 내용이 바뀌고, 삭제하면 사라져야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"# Hello","message":"Create Home"}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.title").value("Home"))

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("Home"))

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/Home").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content").value("# Hello"))
                    .andExpect(jsonPath("$.renderedHtml").value(org.hamcrest.Matchers.containsString("Hello")))

                mockMvc.perform(
                    put("/api/v1/projects/$ownerName/$projName/wiki/pages/Home")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"# Updated","message":"Update Home"}""")
                ).andExpect(status().isOk)

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/Home").with(ownerAuth()))
                    .andExpect(jsonPath("$.content").value("# Updated"))

                mockMvc.perform(delete("/api/v1/projects/$ownerName/$projName/wiki/pages/Home").with(ownerAuth()))
                    .andExpect(status().isNoContent)

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/Home").with(ownerAuth()))
                    .andExpect(status().isNotFound)
            }

            it("PUT으로 newTitle을 주면 이름변경(rename)까지 한 번에 반영해야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"OldTitle","content":"본문"}""")
                ).andExpect(status().isCreated)

                mockMvc.perform(
                    put("/api/v1/projects/$ownerName/$projName/wiki/pages/OldTitle")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"newTitle":"NewTitle","content":"본문"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("NewTitle"))

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/OldTitle").with(ownerAuth()))
                    .andExpect(status().isNotFound)
                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/NewTitle").with(ownerAuth()))
                    .andExpect(status().isOk)
            }

            it("중첩 경로(슬래시) 제목의 페이지도 생성/조회/수정할 수 있어야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Guides/Setup","content":"설치 안내"}""")
                ).andExpect(status().isCreated)

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages/Guides/Setup").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("Guides/Setup"))
                    .andExpect(jsonPath("$.path").value("Guides/Setup.md"))
            }

            it("이미 존재하는 제목으로 생성을 요청하면 409를 반환해야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"1"}""")
                ).andExpect(status().isCreated)

                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"2"}""")
                ).andExpect(status().isConflict)
            }

            it("프로젝트 멤버가 아닌 로그인 사용자는 읽기는 되지만 쓰기는 403이어야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"내용"}""")
                ).andExpect(status().isCreated)

                mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages").with(outsiderAuth()))
                    .andExpect(status().isOk)

                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(outsiderAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Hacked","content":"x"}""")
                ).andExpect(status().isForbidden)

                mockMvc.perform(
                    put("/api/v1/projects/$ownerName/$projName/wiki/pages/Home")
                        .with(outsiderAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"x"}""")
                ).andExpect(status().isForbidden)

                mockMvc.perform(delete("/api/v1/projects/$ownerName/$projName/wiki/pages/Home").with(outsiderAuth()))
                    .andExpect(status().isForbidden)
            }

            it("멤버가 되면(project member) 위키에 쓸 수 있어야 한다 — 코드 push와 동일한 권한 문턱") {
                val outsider = userRepository.findByLoginId(outsiderName).get()
                val project = projectRepository.findByOwnerAndName(ownerName, projName).get()
                val memberRole = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                }
                projectUserRepository.save(ProjectUser(user = outsider, project = project, role = memberRole))

                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(outsiderAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"멤버가 만든 페이지"}""")
                ).andExpect(status().isCreated)
            }
        }

        describe("GET /api/v1/projects/{owner}/{project}/wiki/history, /diff — 히스토리 + diff") {
            it("history는 최신순 리비전을 반환하고, diff는 그 리비전이 반영한 변경만 돌려줘야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Home","content":"v1\n","message":"첫 커밋 메시지"}""")
                ).andExpect(status().isCreated)

                val updateBody = mockMvc.perform(
                    put("/api/v1/projects/$ownerName/$projName/wiki/pages/Home")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"v2\n","message":"두번째 커밋 메시지"}""")
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val revision = com.fasterxml.jackson.databind.ObjectMapper().readTree(updateBody).get("revision").asText()

                val historyBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/history/Home").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                historyBody shouldContain "두번째 커밋 메시지"
                historyBody shouldContain "첫 커밋 메시지"

                val diffBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/diff/$revision/Home").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                diffBody shouldContain "-v1"
                diffBody shouldContain "+v2"
            }
        }

        describe("GET /api/v1/projects/{owner}/{project}/wiki/pages?q= — 검색") {
            it("제목 부분일치로 검색 결과를 반환해야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Guides/Advanced","content":"x"}""")
                ).andExpect(status().isCreated)
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/wiki/pages")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"FAQ","content":"x"}""")
                ).andExpect(status().isCreated)

                val body = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/wiki/pages?q=guid").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                body shouldContain "Guides/Advanced"
                (body.contains("\"title\":\"FAQ\"")) shouldBe false
            }
        }
    }
}
