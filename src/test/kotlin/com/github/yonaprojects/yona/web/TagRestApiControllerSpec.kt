package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.GitTag
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Date
import java.util.Optional

// yona-wiki P3-10 — TagRestApiController(/api/v1/projects/{owner}/{project}/tags) 단위 테스트.
// BranchApiControllerSpec/ProjectRestApiControllerSpec과 동일한 mockk 기반 standalone MockMvc
// 패턴 — Issue/PR REST API와 달리 위임 대상 세션 컨트롤러가 없어 이 컨트롤러가 AccessControl을
// 직접 호출하므로, 권한 판정 자체를 여기서 검증한다.
class TagRestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val controller = TagRestApiController(projectRepository, userRepository, repositoryService, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val publicProject = Project(id = 1L, owner = "owner", name = "TestProject", vcs = "git", projectScope = ProjectScope.PUBLIC)
    val svnProject = Project(id = 2L, owner = "owner", name = "SvnProject", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
    val managerUser = User(id = 10L, loginId = "manageruser", name = "매니저", email = "manager@example.com")
    managerUser.projectUsers.add(ProjectUser(id = 100L, user = managerUser, project = publicProject, role = Role(id = RoleType.MANAGER.roleType)))
    val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
    val memberUser = User(id = 11L, loginId = "memberuser", name = "멤버", email = "member@example.com")
    memberUser.projectUsers.add(ProjectUser(id = 101L, user = memberUser, project = publicProject, role = Role(id = RoleType.MEMBER.roleType)))
    val memberAuth = UsernamePasswordAuthenticationToken("memberuser", "password")

    beforeTest {
        clearMocks(projectRepository, userRepository, projectUserRepository, repositoryService, playRepository)
        // clearMocks()가 stub 응답까지 지우므로(BranchApiControllerSpec과 달리 이 스펙은 로그인
        // 사용자 조회를 매 테스트에서 반복 선언하지 않기 위해 공용으로 둔다) beforeTest 안에서
        // clearMocks 이후 다시 선언해야 한다.
        every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
        every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
    }

    val mockCommit = mockk<Commit>()
    every { mockCommit.getId() } returns "abc123"
    every { mockCommit.getCommitterDate() } returns Date(0)

    describe("GET /api/v1/projects/{owner}/{project}/tags") {
        it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/owner/nosuch/tags"))
                .andExpect(status().isNotFound)
        }

        it("READ 권한이 없으면(비공개+비로그인) 403을 반환해야 한다") {
            val privateProject = Project(id = 3L, owner = "owner", name = "PrivateProject", vcs = "git", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProject") } returns Optional.of(privateProject)

            mockMvc.perform(get("/api/v1/projects/owner/PrivateProject/tags"))
                .andExpect(status().isForbidden)
        }

        it("git이 아닌 프로젝트면 400을 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnProject") } returns Optional.of(svnProject)

            mockMvc.perform(get("/api/v1/projects/owner/SvnProject/tags"))
                .andExpect(status().isBadRequest)
        }

        it("공개 프로젝트면 비로그인 사용자도 태그 목록을 조회할 수 있어야 한다") {
            val lightweight = GitTag(name = "refs/tags/v1.0", targetCommit = mockCommit, annotated = false)
            val annotated = GitTag(name = "refs/tags/v2.0", targetCommit = mockCommit, message = "메모", tagger = managerUser, annotated = true)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.getTags() } returns listOf(lightweight, annotated)

            mockMvc.perform(get("/api/v1/projects/owner/TestProject/tags"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'v1.0')].annotated").value(false))
                .andExpect(jsonPath("$[?(@.name == 'v2.0')].message").value("메모"))
                .andExpect(jsonPath("$[?(@.name == 'v2.0')].tagger").value("manageruser"))
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/tags") {
        it("매니저가 아니면(일반 멤버) 403을 반환하고 createTag를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(memberAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v1.0"}""")
            ).andExpect(status().isForbidden)

            verify(exactly = 0) { playRepository.createTag(any(), any(), any(), any(), any()) }
        }

        it("경로 탈출/유효하지 않은 태그 이름은 400을 반환하고 createTag를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"../../etc/passwd"}""")
            ).andExpect(status().isBadRequest)

            verify(exactly = 0) { playRepository.createTag(any(), any(), any(), any(), any()) }
        }

        it("message 없이 요청하면 lightweight 태그(message=null)로 createTag를 호출해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.createTag("v1.0", "HEAD", null, "매니저", "manager@example.com") } returns Unit
            val created = GitTag(name = "refs/tags/v1.0", targetCommit = mockCommit, annotated = false)
            every { playRepository.getTags() } returns listOf(created)

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v1.0"}""")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("v1.0"))
                .andExpect(jsonPath("$.annotated").value(false))

            verify { playRepository.createTag("v1.0", "HEAD", null, "매니저", "manager@example.com") }
        }

        it("message가 있으면 annotated 태그로 createTag를 호출하고, target을 지정하면 그대로 전달해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.createTag("v2.0", "feature-a", "릴리즈 메모", "매니저", "manager@example.com") } returns Unit
            val created = GitTag(name = "refs/tags/v2.0", targetCommit = mockCommit, message = "릴리즈 메모", annotated = true)
            every { playRepository.getTags() } returns listOf(created)

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v2.0","target":"feature-a","message":"릴리즈 메모"}""")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.annotated").value(true))
                .andExpect(jsonPath("$.message").value("릴리즈 메모"))
        }

        it("refs/tags/ 접두사가 붙은 이름을 보내도 접두사를 제거하고 생성해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.createTag("v3.0", "HEAD", null, "매니저", "manager@example.com") } returns Unit
            every { playRepository.getTags() } returns listOf(GitTag(name = "refs/tags/v3.0", targetCommit = mockCommit))

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"refs/tags/v3.0"}""")
            ).andExpect(status().isCreated)

            verify { playRepository.createTag("v3.0", "HEAD", null, "매니저", "manager@example.com") }
        }

        it("git이 아닌 프로젝트면 400을 반환하고 createTag를 호출하지 않아야 한다") {
            managerUser.projectUsers.add(ProjectUser(id = 102L, user = managerUser, project = svnProject, role = Role(id = RoleType.MANAGER.roleType)))
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnProject") } returns Optional.of(svnProject)

            mockMvc.perform(
                post("/api/v1/projects/owner/SvnProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v1.0"}""")
            ).andExpect(status().isBadRequest)

            verify(exactly = 0) { playRepository.createTag(any(), any(), any(), any(), any()) }
        }

        it("createTag()가 IllegalArgumentException을 던지면 400과 에러 메시지를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.createTag("v1.0", "no-such", null, "매니저", "manager@example.com") } throws IllegalArgumentException("존재하지 않는 시작점입니다: no-such")

            mockMvc.perform(
                post("/api/v1/projects/owner/TestProject/tags")
                    .principal(managerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v1.0","target":"no-such"}""")
            ).andExpect(status().isBadRequest)
        }
    }

    describe("DELETE /api/v1/projects/{owner}/{project}/tags/{tag}") {
        it("매니저가 삭제를 요청하면 204를 반환하고 deleteTag가 호출돼야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)
            every { repositoryService.getRepository(publicProject) } returns playRepository
            every { playRepository.deleteTag("v1.0") } returns Unit

            mockMvc.perform(delete("/api/v1/projects/owner/TestProject/tags/v1.0").principal(managerAuth))
                .andExpect(status().isNoContent)

            verify { playRepository.deleteTag("v1.0") }
        }

        it("매니저가 아니면 403을 반환하고 deleteTag를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)

            mockMvc.perform(delete("/api/v1/projects/owner/TestProject/tags/v1.0").principal(memberAuth))
                .andExpect(status().isForbidden)

            verify(exactly = 0) { playRepository.deleteTag(any()) }
        }

        it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(delete("/api/v1/projects/owner/nosuch/tags/v1.0").principal(managerAuth))
                .andExpect(status().isNotFound)
        }

        // 코디네이터 push 전 리뷰(2026-09-07) — create()는 Repository.isValidRefName()으로 경로
        // 탈출/인젝션 문자를 걸러내는데 delete()는 원래 이 검증 없이 곧바로 deleteTag()를 호출했다.
        // MockMvc로는 URL 경로 변수에 "../"를 실어 보내는 것 자체가 URI 정규화 때문에 신뢰성 있게
        // 재현되지 않아(클라이언트/서블릿 레이어가 먼저 정규화해버릴 수 있음), 컨트롤러 메서드를
        // 직접 호출해 이 검증 로직 자체를 확인한다.
        it("경로 탈출 문자가 포함된 태그 이름을 직접 넘기면 404를 반환하고 deleteTag를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(publicProject)

            val response = controller.delete("owner", "TestProject", "../../../etc/evil", managerAuth)

            response.statusCode.value() shouldBe 404
            verify(exactly = 0) { playRepository.deleteTag(any()) }
        }
    }
})
