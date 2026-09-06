package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-04(브랜치 보호) 2라운드 — 엔티티/훅/병합체크(1라운드)는 이미 있지만 이를 실제로
// 만들거나 조회·수정·삭제할 진입점(웹 UI)이 전무했던 갭을 메운다. WebhookController(같은 성격의
// 프로젝트 범위 설정, 매니저 전용 관리 화면)와 동일한 패턴 — 권한 체크(Operation.UPDATE)까지 그대로.
//
// 폼 파라미터는 GitHub의 "Settings > Branches > Branch protection rules" 화면 문구를 그대로 차용한다
// (사용자 방침: 모호하면 GitHub 방식을 기본값으로). GitHub는 세 필드를 긍정문으로 노출하는데 우리
// 엔티티는 부정형으로 저장하므로 폼 파라미터 → 엔티티 필드 변환 시 뒤집는다:
//   allowForcePush(체크=허용)          → disallowForcePush = !allowForcePush
//   allowDeletions(체크=허용)          → disallowDelete     = !allowDeletions
//   doNotAllowBypassing(체크=우회금지) → adminsCanBypass    = !doNotAllowBypassing
class BranchProtectionControllerSpec : DescribeSpec({
    val protectedBranchRepository = mockk<ProtectedBranchRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val accessControl = mockk<AccessControl>()

    val controller = BranchProtectionController(protectedBranchRepository, projectRepository, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(protectedBranchRepository, projectRepository, userRepository, accessControl)
    }

    describe("BranchProtectionController") {
        val userAuth = UsernamePasswordAuthenticationToken("owner", "password")
        val project = Project(id = 1L, owner = "owner", name = "test-project")
        val managerUser = User(id = 100L, loginId = "owner", name = "owner")
        val rule = ProtectedBranch(
            id = 10L,
            project = project,
            branchPattern = "main",
            requirePullRequest = true,
            requireApprovals = 0,
            requireSignedCommits = false,
            restrictPushTo = "owner",
            disallowForcePush = true,
            disallowDelete = true,
            adminsCanBypass = true
        )

        beforeTest {
            every { userRepository.findByLoginId("owner") } returns Optional.of(managerUser)
            every { accessControl.isAllowed(managerUser, project, Operation.UPDATE) } returns true
        }

        describe("GET /projects/{owner}/{projectName}/branch-protections") {
            it("브랜치 보호 설정 페이지 뷰를 정상 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findByProjectId(1L) } returns listOf(rule)

                mockMvc.perform(
                    get("/projects/owner/test-project/branch-protections")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting_branch_protection"))
                    .andExpect(model().attributeExists("project"))
                    .andExpect(model().attributeExists("branchProtections"))
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(get("/projects/owner/nosuch/branch-protections").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자는 403으로 거부된다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { accessControl.isAllowed(null, project, Operation.UPDATE) } returns false

                mockMvc.perform(get("/projects/owner/test-project/branch-protections"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(get("/projects/owner/test-project/branch-protections").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /projects/{owner}/{projectName}/branch-protections (생성)") {
            it("유효한 폼 데이터를 받아 규칙을 생성하고(GitHub 문구 → 엔티티 반전 포함) 목록으로 리다이렉트한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val savedSlot = slot<ProtectedBranch>()
                every { protectedBranchRepository.save(capture(savedSlot)) } returns rule

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", "release/*")
                        .param("requirePullRequest", "true")
                        .param("requireApprovals", "2")
                        .param("requireSignedCommits", "false")
                        .param("restrictPushTo", "alice, bob")
                        .param("allowForcePush", "false")
                        .param("allowDeletions", "true")
                        .param("doNotAllowBypassing", "false")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/test-project/branch-protections"))

                savedSlot.captured.branchPattern shouldBe "release/*"
                savedSlot.captured.requirePullRequest shouldBe true
                savedSlot.captured.requireApprovals shouldBe 2
                savedSlot.captured.requireSignedCommits shouldBe false
                savedSlot.captured.restrictPushTo shouldBe "alice, bob"
                savedSlot.captured.disallowForcePush shouldBe true // allowForcePush=false 반전
                savedSlot.captured.disallowDelete shouldBe false // allowDeletions=true 반전
                savedSlot.captured.adminsCanBypass shouldBe true // doNotAllowBypassing=false 반전
                savedSlot.captured.project shouldBe project
            }

            // 체크박스는 HTML 폼 관례상 미체크 시 파라미터 자체가 전송되지 않는다 — webhook의 gitPush와
            // 동일하게 defaultValue="false"로 처리되는지 확인한다.
            it("체크박스 파라미터를 전부 생략하면 기본값(false)으로 처리해 등록한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val savedSlot = slot<ProtectedBranch>()
                every { protectedBranchRepository.save(capture(savedSlot)) } returns rule

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", "main")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                savedSlot.captured.requirePullRequest shouldBe false
                savedSlot.captured.requireApprovals shouldBe 0
                savedSlot.captured.requireSignedCommits shouldBe false
                savedSlot.captured.disallowForcePush shouldBe true // allowForcePush 생략(=false) 반전 → 기본 차단
                savedSlot.captured.disallowDelete shouldBe true // allowDeletions 생략(=false) 반전 → 기본 차단
                savedSlot.captured.adminsCanBypass shouldBe true // doNotAllowBypassing 생략(=false) 반전 → 기본 우회 허용
            }

            it("branchPattern이 비어있으면 400 Bad Request를 반환하고 저장을 시도하지 않는다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", "")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }

            it("branchPattern이 250자를 넘으면 400 Bad Request를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLong = "a".repeat(251)

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", tooLong)
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }

            it("restrictPushTo가 1000자를 넘으면 400 Bad Request를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLong = "a".repeat(1001)

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", "main")
                        .param("restrictPushTo", tooLong)
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    post("/projects/owner/nosuch/branch-protections")
                        .param("branchPattern", "main")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections")
                        .param("branchPattern", "main")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }
        }

        describe("POST /projects/{owner}/{projectName}/branch-protections/{id} (수정)") {
            it("기존 규칙을 갱신하고 목록으로 리다이렉트한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(10L) } returns Optional.of(rule)
                val savedSlot = slot<ProtectedBranch>()
                every { protectedBranchRepository.save(capture(savedSlot)) } returns rule

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections/10")
                        .param("branchPattern", "develop")
                        .param("requirePullRequest", "false")
                        .param("requireApprovals", "0")
                        .param("requireSignedCommits", "false")
                        .param("restrictPushTo", "")
                        .param("allowForcePush", "true")
                        .param("allowDeletions", "true")
                        .param("doNotAllowBypassing", "true")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/test-project/branch-protections"))

                savedSlot.captured.id shouldBe 10L
                savedSlot.captured.branchPattern shouldBe "develop"
                savedSlot.captured.requirePullRequest shouldBe false
                savedSlot.captured.disallowForcePush shouldBe false // allowForcePush=true 반전
                savedSlot.captured.disallowDelete shouldBe false // allowDeletions=true 반전
                savedSlot.captured.adminsCanBypass shouldBe false // doNotAllowBypassing=true 반전
            }

            it("존재하지 않는 규칙 id면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections/999")
                        .param("branchPattern", "develop")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }

            it("다른 프로젝트 소유의 규칙 id면 404를 반환한다") {
                val otherProject = Project(id = 2L, owner = "owner2", name = "other-project")
                val otherRule = ProtectedBranch(id = 10L, project = otherProject, branchPattern = "main")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(10L) } returns Optional.of(otherRule)

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections/10")
                        .param("branchPattern", "develop")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    post("/projects/owner/test-project/branch-protections/10")
                        .param("branchPattern", "develop")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)

                verify(exactly = 0) { protectedBranchRepository.save(any()) }
            }
        }

        describe("DELETE /projects/{owner}/{projectName}/branch-protections/{id}") {
            it("규칙 삭제를 호출하고 200 OK를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(10L) } returns Optional.of(rule)
                every { protectedBranchRepository.deleteById(10L) } returns Unit

                mockMvc.perform(
                    delete("/projects/owner/test-project/branch-protections/10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { protectedBranchRepository.deleteById(10L) }
            }

            it("존재하지 않는 규칙 id면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/projects/owner/test-project/branch-protections/999").principal(userAuth)
                ).andExpect(status().isNotFound)

                verify(exactly = 0) { protectedBranchRepository.deleteById(any()) }
            }

            it("다른 프로젝트 소유의 규칙 id면 404를 반환한다") {
                val otherProject = Project(id = 2L, owner = "owner2", name = "other-project")
                val otherRule = ProtectedBranch(id = 10L, project = otherProject, branchPattern = "main")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { protectedBranchRepository.findById(10L) } returns Optional.of(otherRule)

                mockMvc.perform(
                    delete("/projects/owner/test-project/branch-protections/10").principal(userAuth)
                ).andExpect(status().isNotFound)

                verify(exactly = 0) { protectedBranchRepository.deleteById(any()) }
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    delete("/projects/owner/nosuch/branch-protections/10").principal(userAuth)
                ).andExpect(status().isNotFound)
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    delete("/projects/owner/test-project/branch-protections/10").principal(strangerAuth)
                ).andExpect(status().isForbidden)

                verify(exactly = 0) { protectedBranchRepository.deleteById(any()) }
            }
        }
    }
})
