package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// yona-wiki P3-10 — TagRestApiController + GitRepository.createTag()/getTags()/deleteTag()를 실제
// bare git 저장소로 end-to-end 검증한다(단위 스펙은 전부 mockk 기반이라 실제 JGit 동작까지는
// 검증하지 못함). LabelRestApiControllerIntegrationSpec/CodeBrowserListWrapRenderingSpec과 동일한
// AbstractIntegrationTest 패턴(실제 DB + 실제 파일시스템 bare 저장소).
class TagRestApiControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    @Value("\${yona.git.base-dir:/tmp/yona/git}") private val gitBaseDir: String
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()
    private val ownerName = "tag-e2e-owner"
    private val projName = "tag-e2e-repo"

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

            val gitDir = File(File(gitBaseDir), "$ownerName/$projName.git")
            gitDir.deleteRecursively()

            val owner = userRepository.save(User(loginId = ownerName, name = "태그E2E소유자", email = "$ownerName@example.com"))
            val project = projectRepository.save(Project(owner = ownerName, name = projName, vcs = "GIT"))
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))

            repositoryService.getRepository(project).create()
            BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# $projName", "초기 커밋")
        }

        fun ownerAuth(): org.springframework.test.web.servlet.request.RequestPostProcessor {
            val ownerObj = userRepository.findByLoginId(ownerName).get()
            val details = YonaUserDetails(
                id = ownerObj.id!!,
                loginId = ownerObj.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            return user(details)
        }

        describe("POST/GET/DELETE /api/v1/projects/{owner}/{project}/tags — 실제 bare 저장소 end-to-end") {
            it("lightweight 태그를 생성하면 목록에 나타나고, 삭제하면 사라져야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/tags")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"v1.0"}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.name").value("v1.0"))
                    .andExpect(jsonPath("$.annotated").value(false))
                    .andExpect(jsonPath("$.message").value(null as String?))

                val listBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/tags").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                listBody shouldContain "\"name\":\"v1.0\""

                mockMvc.perform(delete("/api/v1/projects/$ownerName/$projName/tags/v1.0").with(ownerAuth()))
                    .andExpect(status().isNoContent)

                val afterDeleteBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/tags").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                afterDeleteBody shouldBe "[]"
            }

            it("message를 주면 annotated 태그가 만들어지고 tagger/message가 응답에 채워져야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/tags")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"v2.0","message":"릴리즈 메모"}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.annotated").value(true))
                    .andExpect(jsonPath("$.message").value("릴리즈 메모"))
                    .andExpect(jsonPath("$.tagger").value(ownerName))
            }

            it("존재하지 않는 target으로 생성을 요청하면 400을 반환하고 태그를 만들지 않아야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/tags")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"bad-target","target":"no-such-rev-xyz"}""")
                ).andExpect(status().isBadRequest)

                val listBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/tags").with(ownerAuth()))
                    .andReturn().response.contentAsString
                listBody shouldBe "[]"
            }

            // 보안 리뷰(P3-10 계획 문서) — 경로 탈출 문자가 섞인 태그 이름은 JGit의 ref 이름 검증에
            // 막혀 400을 반환해야 하고, 어떤 경우에도 저장소 디렉터리 밖에 파일을 만들지 않아야 한다.
            it("경로 탈출(../) 문자가 포함된 태그 이름은 400을 반환하고 태그를 만들지 않아야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/tags")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"../../../etc/evil"}""")
                ).andExpect(status().isBadRequest)

                val listBody = mockMvc.perform(get("/api/v1/projects/$ownerName/$projName/tags").with(ownerAuth()))
                    .andReturn().response.contentAsString
                listBody shouldBe "[]"
            }
        }

        describe("코드 브라우저 ref 셀렉터에 태그가 노출돼야 한다 (P3-10)") {
            it("태그를 만든 뒤 code/view 페이지를 요청하면 select#branches에 태그 optgroup이 렌더링돼야 한다") {
                mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/tags")
                        .with(ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"v3.0"}""")
                ).andExpect(status().isCreated)

                val body = mockMvc.perform(get("/$ownerName/$projName/code/main").with(ownerAuth()))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // P3-51: Accept-Language 헤더가 없으면 root(영어) 번들로 결정적으로 렌더링된다
                // (title.tags의 root 값은 "Tags" — messages.properties).
                body shouldContain "<optgroup label=\"Tags\">"
                body shouldContain "v3.0"
            }
        }
    }
}
