package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.service.MigrationService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class MigrationApiControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val migrationService = mockk<MigrationService>()

    val controller = MigrationApiController(userRepository, migrationService)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val user = User(id = 1L, loginId = "testuser", name = "테스트유저")
    val auth = UsernamePasswordAuthenticationToken("testuser", "password")

    describe("GET /migration/projects") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/projects"))
                .andExpect(status().isUnauthorized)
        }

        it("로그인 사용자를 찾을 수 없으면 401을 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.empty()

            mockMvc.perform(get("/migration/projects").principal(auth))
                .andExpect(status().isUnauthorized)
        }

        it("마이그레이션 가능한 프로젝트 목록을 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.getMigrationProjects(user) } returns listOf(
                mapOf("owner" to "owner1", "projectName" to "proj1")
            )

            mockMvc.perform(get("/migration/projects").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].owner").value("owner1"))
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1"))
                .andExpect(status().isUnauthorized)
        }

        it("마이그레이션 대상 프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.getMigrationProjectDetail("owner1", "nosuch") } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("프로젝트 상세 정보를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.getMigrationProjectDetail("owner1", "proj1") } returns mapOf(
                "owner" to "owner1", "projectName" to "proj1"
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.projectName").value("proj1"))
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}/labels") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1/labels"))
                .andExpect(status().isUnauthorized)
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportLabels("owner1", "nosuch") } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch/labels").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("라벨 목록을 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportLabels("owner1", "proj1") } returns mapOf(
                "labels" to mapOf("1" to mapOf("id" to 1L, "name" to "버그"))
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1/labels").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.labels['1'].name").value("버그"))
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}/issuelabel") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1/issuelabel"))
                .andExpect(status().isUnauthorized)
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportIssueLabelPairs("owner1", "nosuch") } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch/issuelabel").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("이슈-라벨 쌍 목록을 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportIssueLabelPairs("owner1", "proj1") } returns mapOf(
                "issueLabelPairs" to listOf(mapOf("issue_id" to 1L, "issue_label_id" to 2L))
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1/issuelabel").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.issueLabelPairs[0].issue_id").value(1))
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}/milestones") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1/milestones"))
                .andExpect(status().isUnauthorized)
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportMilestones("owner1", "nosuch") } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch/milestones").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("마일스톤 목록을 milestones 키로 감싸 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportMilestones("owner1", "proj1") } returns listOf(
                mapOf("milestone" to mapOf("id" to 1L, "title" to "1.0"))
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1/milestones").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.milestones[0].milestone.title").value("1.0"))
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}/issues") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1/issues"))
                .andExpect(status().isUnauthorized)
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportIssues("owner1", "nosuch", false) } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch/issues").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("withWikiCommit 파라미터를 생략하면 기본값 false로 서비스에 전달해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportIssues("owner1", "proj1", false) } returns listOf(
                mapOf("issue" to mapOf("id" to 1L, "title" to "제목"))
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1/issues").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.issues[0].issue.title").value("제목"))
        }

        it("withWikiCommit=true를 서비스에 그대로 전달해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportIssues("owner1", "proj1", true) } returns listOf(
                mapOf("issue" to mapOf("id" to 1L, "title" to "제목"))
            )

            mockMvc.perform(
                get("/migration/owner1/projects/proj1/issues")
                    .param("withWikiCommit", "true")
                    .principal(auth)
            )
                .andExpect(status().isOk)
        }
    }

    describe("GET /migration/{owner}/projects/{projectName}/posts") {
        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(get("/migration/owner1/projects/proj1/posts"))
                .andExpect(status().isUnauthorized)
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportPosts("owner1", "nosuch", false) } returns null

            mockMvc.perform(get("/migration/owner1/projects/nosuch/posts").principal(auth))
                .andExpect(status().isNotFound)
        }

        it("withWikiCommit 파라미터를 생략하면 기본값 false로 서비스에 전달해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportPosts("owner1", "proj1", false) } returns listOf(
                mapOf("issue" to mapOf("title" to "게시글"))
            )

            mockMvc.perform(get("/migration/owner1/projects/proj1/posts").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.issues[0].issue.title").value("게시글"))
        }

        it("withWikiCommit=true를 서비스에 그대로 전달해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { migrationService.exportPosts("owner1", "proj1", true) } returns listOf(
                mapOf("issue" to mapOf("title" to "게시글"))
            )

            mockMvc.perform(
                get("/migration/owner1/projects/proj1/posts")
                    .param("withWikiCommit", "true")
                    .principal(auth)
            )
                .andExpect(status().isOk)
        }
    }
})
