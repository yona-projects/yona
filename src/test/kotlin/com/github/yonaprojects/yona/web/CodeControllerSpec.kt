package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.ObjectMapper
import java.util.Optional

class CodeControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val repositoryService = mockk<RepositoryService>()
    val userRepository = mockk<com.github.yonaprojects.yona.domain.user.UserRepository>()
    val projectUserRepository = mockk<com.github.yonaprojects.yona.domain.project.ProjectUserRepository>()
    val organizationUserRepository = mockk<com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val accessControl = com.github.yonaprojects.yona.config.security.AccessControl(
        projectUserRepository, organizationUserRepository,
        mockk<com.github.yonaprojects.yona.domain.user.UserRepository>(), mockk<com.github.yonaprojects.yona.domain.organization.OrganizationRepository>(),
        mockk<com.github.yonaprojects.yona.domain.issue.IssueRepository>(), mockk<com.github.yonaprojects.yona.domain.board.PostingRepository>(),
        mockk<com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository>(), mockk<com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository>(),
        mockk<com.github.yonaprojects.yona.domain.milestone.MilestoneRepository>()
    )
    val controller = CodeController(projectRepository, repositoryService, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val project = Project(id = 1L, name = "proj", owner = "owner", projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PUBLIC)
    val repo = mockk<PlayRepository>()
    val objectMapper = ObjectMapper()

    // v1.6 CodeApp.ajaxRequest*/showRawFile은 @With(CodeAccessCheckAction) — 프로젝트 READ(AbstractProjectCheckAction)와 "코드 멤버 전용"
    // (isCodeAccessibleMemberOnly) 검사를 모두 통과해야 했다. 같은 데이터를 보여 주는 화면 라우트(CodeViewController)는 이미 검사한다.
    describe("접근 제어 (v1.6 CodeAccessCheckAction)") {
        val privateProject = Project(id = 2L, name = "priv", owner = "owner", projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PRIVATE)
        val memberOnly = Project(id = 3L, name = "memonly", owner = "owner", projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true)
        val stranger = com.github.yonaprojects.yona.domain.user.User(id = 20L, loginId = "stranger", name = "외부인")
        val strangerAuth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken("stranger", "pw")

        beforeTest {
            io.mockk.clearMocks(projectUserRepository, userRepository, answers = false)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memonly") } returns Optional.of(memberOnly)
            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectUserRepository.existsByProjectIdAndUserId(any(), any()) } returns false
        }

        for (endpoint in listOf("meta", "meta/ancestors")) {
            it("/$endpoint: 익명은 비공개 프로젝트를 읽을 수 없다(403)") {
                mockMvc.perform(get("/api/vcs/owner/priv/$endpoint")).andExpect(status().isForbidden)
                io.mockk.verify(exactly = 0) { repositoryService.getRepository(any()) }
            }
            it("/$endpoint: 로그인한 비멤버도 비공개 프로젝트를 읽을 수 없다(403)") {
                mockMvc.perform(get("/api/vcs/owner/priv/$endpoint").principal(strangerAuth)).andExpect(status().isForbidden)
                io.mockk.verify(exactly = 0) { repositoryService.getRepository(any()) }
            }
            it("/$endpoint: 코드 멤버 전용 프로젝트는 공개여도 비멤버에게 403이다") {
                mockMvc.perform(get("/api/vcs/owner/memonly/$endpoint").principal(strangerAuth)).andExpect(status().isForbidden)
                io.mockk.verify(exactly = 0) { repositoryService.getRepository(any()) }
            }
        }

        it("/raw: 익명은 비공개 프로젝트의 파일 원문을 읽을 수 없다(403)") {
            mockMvc.perform(get("/api/vcs/owner/priv/raw").param("revision", "HEAD").param("path", "a.txt")).andExpect(status().isForbidden)
            io.mockk.verify(exactly = 0) { repositoryService.getFileAsRaw(any(), any(), any(), any()) }
        }

        it("/raw: 코드 멤버 전용 프로젝트는 비멤버에게 403이다") {
            mockMvc.perform(get("/api/vcs/owner/memonly/raw").param("revision", "HEAD").param("path", "a.txt").principal(strangerAuth))
                .andExpect(status().isForbidden)
            io.mockk.verify(exactly = 0) { repositoryService.getFileAsRaw(any(), any(), any(), any()) }
        }

        it("멤버는 코드 멤버 전용 프로젝트도 읽을 수 있다") {
            every { projectUserRepository.existsByProjectIdAndUserId(3L, 20L) } returns true
            every { repositoryService.getRepository(memberOnly) } returns repo
            every { repo.getMetaDataFromPath("HEAD", "") } returns objectMapper.createObjectNode().put("type", "dir")
            mockMvc.perform(get("/api/vcs/owner/memonly/meta").principal(strangerAuth)).andExpect(status().isOk)
        }
    }

    describe("GET /api/vcs/{owner}/{projectName}/meta") {
        it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(get("/api/vcs/owner/nosuch/meta")).andExpect(status().isNotFound)
        }

        it("메타데이터를 찾을 수 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "proj") } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getMetaDataFromPath("HEAD", "") } returns null

            mockMvc.perform(get("/api/vcs/owner/proj/meta")).andExpect(status().isNotFound)
        }

        it("메타데이터를 찾으면 200과 함께 반환해야 한다") {
            val node: ObjectNode = objectMapper.createObjectNode().put("type", "file")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "proj") } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getMetaDataFromPath("main", "src") } returns node

            mockMvc.perform(get("/api/vcs/owner/proj/meta").param("branch", "main").param("path", "src"))
                .andExpect(status().isOk)
        }
    }

    describe("GET /api/vcs/{owner}/{projectName}/meta/ancestors") {
        it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(get("/api/vcs/owner/nosuch/meta/ancestors")).andExpect(status().isNotFound)
        }

        it("조상 디렉터리 메타데이터를 찾을 수 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "proj") } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns repo
            every { repositoryService.getMetaDataFromAncestorDirectories(repo, "HEAD", "") } returns null

            mockMvc.perform(get("/api/vcs/owner/proj/meta/ancestors")).andExpect(status().isNotFound)
        }

        it("조상 디렉터리 메타데이터를 찾으면 200과 함께 반환해야 한다") {
            val node: ObjectNode = objectMapper.createObjectNode().put("type", "dir")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "proj") } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns repo
            every { repositoryService.getMetaDataFromAncestorDirectories(repo, "HEAD", "src") } returns listOf(node)

            mockMvc.perform(get("/api/vcs/owner/proj/meta/ancestors").param("path", "src"))
                .andExpect(status().isOk)
        }
    }

    describe("GET /api/vcs/{owner}/{projectName}/raw") {
        it("파일을 찾을 수 없으면 404를 반환해야 한다") {
            every { repositoryService.getFileAsRaw("owner", "proj", "HEAD", "src/a.txt") } returns null

            mockMvc.perform(
                get("/api/vcs/owner/proj/raw").param("revision", "HEAD").param("path", "src/a.txt")
            ).andExpect(status().isNotFound)
        }

        it("파일을 찾으면 200과 원본 바이트, Content-Disposition 헤더를 반환해야 한다") {
            every { repositoryService.getFileAsRaw("owner", "proj", "HEAD", "src/dir/a.txt") } returns byteArrayOf(1, 2, 3)

            mockMvc.perform(
                get("/api/vcs/owner/proj/raw").param("revision", "HEAD").param("path", "src/dir/a.txt")
            )
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Disposition", "inline; filename=\"a.txt\""))
        }

        it("경로에 슬래시가 없으면 경로 전체를 파일명으로 사용해야 한다") {
            every { repositoryService.getFileAsRaw("owner", "proj", "HEAD", "root.txt") } returns byteArrayOf(9)

            mockMvc.perform(
                get("/api/vcs/owner/proj/raw").param("revision", "HEAD").param("path", "root.txt")
            )
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Disposition", "inline; filename=\"root.txt\""))
        }
    }
})
