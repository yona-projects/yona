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
    val controller = CodeController(projectRepository, repositoryService)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val project = Project(id = 1L, name = "proj", owner = "owner")
    val repo = mockk<PlayRepository>()
    val objectMapper = ObjectMapper()

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
