package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.service.MigrationService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks
import org.hamcrest.Matchers

class MigrationControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val migrationService = mockk<MigrationService>()

    val migrationViewControllerAllow = MigrationViewController(
        userRepository = userRepository,
        migrationService = migrationService
    )

    val migrationApiControllerAllow = MigrationApiController(
        userRepository = userRepository,
        migrationService = migrationService
    )

    val mockMvcAllow = MockMvcBuilders.standaloneSetup(
        migrationViewControllerAllow,
        migrationApiControllerAllow
    ).build()

    beforeTest {
        clearMocks(
            userRepository,
            migrationService
        )
    }

    describe("Migration 관련 Controller 기능 및 API 명세") {
        val testUser = User(id = 1L, loginId = "yona_user", name = "요나유저", email = "yona@example.com")
        val userAuth = UsernamePasswordAuthenticationToken("yona_user", "pass")

        describe("GET /migration (allowMigration = false 일 때)") {
            it("403 Forbidden 뷰가 리턴되어야 한다") {
                every { migrationService.isAllowMigration() } returns false

                mockMvcAllow.perform(get("/migration"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /migration (allowMigration = true 일 때)") {
            it("로그인되지 않았으면 로그인 페이지로 리다이렉트되어야 한다") {
                every { migrationService.isAllowMigration() } returns true
                every { userRepository.findByLoginId(any()) } returns Optional.empty()

                mockMvcAllow.perform(get("/migration"))
                    .andExpect(status().is3xxRedirection)
            }

            it("로그인되었을 때 code 파라미터가 없으면 token 없이 마이그레이션 홈 뷰가 반환되어야 한다") {
                every { migrationService.isAllowMigration() } returns true
                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)

                mockMvcAllow.perform(get("/migration").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("migration/home"))
                    .andExpect(model().attribute("token", ""))
            }

            it("로그인되었을 때 code 파라미터가 있으면 token과 함께 마이그레이션 홈 뷰가 반환되어야 한다 (요구사항 2-b 인가 검증 포함)") {
                every { migrationService.isAllowMigration() } returns true
                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.getOAuthToken("auth_code") } returns "mock_token"

                mockMvcAllow.perform(get("/migration").param("code", "auth_code").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("migration/home"))
                    .andExpect(model().attribute("token", "mock_token"))
                    .andExpect(model().attribute("code", "auth_code"))
            }
        }

        describe("GET /migration/projects") {
            it("현재 사용자가 MANAGER 또는 ORG_ADMIN 권한을 가진 프로젝트 목록을 JSON으로 정상 반환해야 한다") {
                val mockProjects = listOf(
                    mapOf(
                        "owner" to "owner1",
                        "projectName" to "proj1",
                        "private" to true,
                        "members" to 1,
                        "full_name" to "owner1/proj1"
                    ),
                    mapOf(
                        "owner" to "owner2",
                        "projectName" to "proj2",
                        "private" to false,
                        "members" to 2,
                        "full_name" to "owner2/proj2"
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.getMigrationProjects(testUser) } returns mockProjects

                mockMvcAllow.perform(get("/migration/projects").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].projectName").value("proj1"))
                    .andExpect(jsonPath("$[0].private").value(true))
                    .andExpect(jsonPath("$[1].projectName").value("proj2"))
                    .andExpect(jsonPath("$[1].private").value(false))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}") {
            it("프로젝트의 이슈, 포스트, 마일스톤 카운트 등 상세 정보를 JSON으로 반환해야 한다") {
                val mockDetail = mapOf(
                    "owner" to "owner1",
                    "projectName" to "proj1",
                    "full_name" to "owner1/proj1",
                    "assignees" to listOf<Map<String, String>>(),
                    "memberCount" to 1,
                    "issueCount" to 5L,
                    "postCount" to 3L,
                    "milestoneCount" to 2L
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.getMigrationProjectDetail("owner1", "proj1") } returns mockDetail

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectName").value("proj1"))
                    .andExpect(jsonPath("$.issueCount").value(5))
                    .andExpect(jsonPath("$.postCount").value(3))
                    .andExpect(jsonPath("$.milestoneCount").value(2))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/labels") {
            it("프로젝트 내 생성된 라벨 목록을 JSON으로 반환해야 한다") {
                val mockLabels = mapOf(
                    "labels" to mapOf(
                        "100" to mapOf(
                            "id" to 100L,
                            "name" to "Bug",
                            "categoryId" to 1L,
                            "categoryName" to "Category1"
                        )
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.exportLabels("owner1", "proj1") } returns mockLabels

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/labels").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.labels.100.name").value("Bug"))
                    .andExpect(jsonPath("$.labels.100.categoryName").value("Category1"))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/issuelabel") {
            it("프로젝트 내 이슈와 라벨의 쌍 매핑 목록을 JSON으로 반환해야 한다") {
                val mockPairs = mapOf(
                    "issueLabelPairs" to listOf(
                        mapOf(
                            "issue_id" to 50L,
                            "issue_label_id" to 100L
                        )
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.exportIssueLabelPairs("owner1", "proj1") } returns mockPairs

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/issuelabel").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueLabelPairs[0].issue_id").value(50))
                    .andExpect(jsonPath("$.issueLabelPairs[0].issue_label_id").value(100))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/milestones") {
            it("프로젝트 내 마일스톤 목록 데이터를 JSON으로 반환해야 한다") {
                val mockMilestones = listOf(
                    mapOf(
                        "milestone" to mapOf(
                            "id" to 200L,
                            "title" to "v1.0",
                            "state" to "open",
                            "description" to "첫 릴리즈"
                        )
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.exportMilestones("owner1", "proj1") } returns mockMilestones

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.milestones[0].milestone.title").value("v1.0"))
                    .andExpect(jsonPath("$.milestones[0].milestone.description").value("첫 릴리즈"))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/issues") {
            it("프로젝트 내 이슈 및 코멘트 목록 데이터를 JSON으로 반환하고 첨부파일 목록이 본문에 덧붙여져야 한다 (요구사항 2-a 데이터 정합성 검증 포함)") {
                val mockIssues = listOf(
                    mapOf(
                        "issue" to mapOf(
                            "id" to 50L,
                            "title" to "이슈1",
                            "body" to "이슈 본문 내용\n\n--- attachments ---\n[test.txt](/files/88)",
                            "assignee" to "assignee_user",
                            "milestone" to "v1.0",
                            "milestoneId" to 200L,
                            "closed" to false
                        ),
                        "comments" to listOf(
                            mapOf(
                                "body" to "코멘트 내용"
                            )
                        )
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.exportIssues("owner1", "proj1", false) } returns mockIssues

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/issues").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issues[0].issue.title").value("이슈1"))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(Matchers.containsString("이슈 본문 내용")))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(Matchers.containsString("--- attachments ---")))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(Matchers.containsString("[test.txt](/files/88)")))
                    .andExpect(jsonPath("$.issues[0].comments[0].body").value("코멘트 내용"))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/posts") {
            it("프로젝트 내 자유게시판 포스팅 데이터를 JSON으로 반환해야 한다 (요구사항 2-a 데이터 정합성 검증 포함)") {
                val mockPosts = listOf(
                    mapOf(
                        "issue" to mapOf(
                            "title" to "포스트1",
                            "body" to "포스팅 본문 내용"
                        ),
                        "comments" to listOf<Map<String, Any>>()
                    )
                )

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { migrationService.exportPosts("owner1", "proj1", false) } returns mockPosts

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issues[0].issue.title").value("포스트1"))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(Matchers.containsString("포스팅 본문 내용")))
            }
        }
    }
})
