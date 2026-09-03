package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.util.*
import java.nio.file.Files
import java.text.MessageFormat

class ImportViewControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val gitService = mockk<GitService>()
    val messageSource = mockk<MessageSource>(relaxed = true)

    val controller = ImportViewController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository,
        organizationUserRepository,
        organizationRepository,
        gitService,
        messageSource
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectService,
            projectRepository,
            projectUserRepository,
            userRepository,
            organizationUserRepository,
            organizationRepository,
            gitService,
            messageSource
        )
    }

    describe("ImportViewController 테스트") {
        val loginUser = User(id = 1L, loginId = "testuser", name = "Test User")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /new/import") {
            it("로그인하지 않은 경우 로그인 폼으로 리다이렉트되어야 한다") {
                every { userRepository.findByLoginId(any()) } returns Optional.empty()

                mockMvc.perform(get("/new/import"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("로그인한 사용자 정보와 소속 조직 목록을 조회하여 가져오기 화면을 보여주어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

                mockMvc.perform(get("/new/import").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().attributeExists("importForm"))
                    .andExpect(model().attributeExists("currentUser"))
                    .andExpect(model().attributeExists("organizations"))
            }

            it("owner 쿼리파라미터가 명시되면 그 값을 폼의 기본 owner로 사용해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

                val result = mockMvc.perform(get("/new/import").param("owner", "explicitowner").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val boundForm = result.modelAndView?.model?.get("importForm") as ImportForm
                boundForm.owner shouldBe "explicitowner"
            }
        }

        describe("POST /new/import") {
            val validForm = ImportForm().apply {
                url = "https://github.com/naver/yona.git"
                owner = "testuser"
                name = "yona-imported"
            }

            it("성공적으로 Git 저장소를 복제하고 새 프로젝트를 생성해야 한다") {
                val mockRepoPath = File("/tmp/yona/git/testuser/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "testuser")

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", validForm.owner)
                        .param("name", validForm.name)
                        .param("overview", validForm.overview)
                        .param("projectScope", validForm.projectScope.name)
                        .param("vcs", validForm.vcs)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testuser/yona-imported"))

                verify(exactly = 1) { projectService.createProject(any(), loginUser) }
            }

            it("잘못된 Git 리포지토리 URL인 경우 wrong.url 에러와 함께 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/invalid/repo.git", "testuser", "yona-imported", null, null) } throws InvalidRemoteException("Invalid remote")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/invalid/repo.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.wrong.url"))
            }

            it("인증 정보가 없고 unauthorized 에러가 발생한 경우 required 및 unauthorized 에러를 필드에 추가해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/naver/yona.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport.unauthorized"))
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "repoAuth", "required"))
            }

            it("인증 정보를 보냈으나 인증 실패한 경우 authId 필드에 failedToAuth 에러를 추가해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", "wrongId", "wrongPw") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/naver/yona.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authId", "wrongId")
                        .param("authPw", "wrongPw")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "authId", "project.import.error.transport.failedToAuth"))
            }

            it("로그인하지 않으면 로그인 폼으로 리다이렉트되어야 한다") {
                every { userRepository.findByLoginId(any()) } returns Optional.empty()

                mockMvc.perform(post("/new/import"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("게스트 사용자는 프로젝트를 생성할 수 없어 403이 발생해야 한다") {
                val guestUser = User(id = 2L, loginId = "guestuser", name = "게스트", isGuest = true)
                every { userRepository.findByLoginId("guestuser") } returns Optional.of(guestUser)

                mockMvc.perform(
                    post("/new/import")
                        .principal(UsernamePasswordAuthenticationToken("guestuser", "password"))
                        .param("url", validForm.url)
                        .param("owner", "guestuser")
                        .param("name", "somename")
                ).andExpect(status().isForbidden)
            }

            it("owner가 사용자도 조직도 아니면 invalidate 에러가 발생해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { userRepository.findByLoginId("ghostowner") } returns Optional.empty()
                every { organizationRepository.findByName("ghostowner") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("ghostowner", "somename") } returns Optional.empty()

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "ghostowner")
                        .param("name", "somename")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "owner", "project.owner.invalidate"))
            }

            it("owner가 로그인한 사용자가 아닌 다른 사용자면 invalidate 에러가 발생해야 한다") {
                val otherUser = User(id = 3L, loginId = "otheruser", name = "다른유저")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { organizationRepository.findByName("otheruser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("otheruser", "somename") } returns Optional.empty()

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "otheruser")
                        .param("name", "somename")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "owner", "project.owner.invalidate"))
            }

            it("owner가 조직이지만 호출자가 관리자가 아니면 invalidate 에러가 발생해야 한다") {
                val org = Organization(id = 20L, name = "someorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { userRepository.findByLoginId("someorg") } returns Optional.empty()
                every { organizationRepository.findByName("someorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(20L, 1L) } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("someorg", "somename") } returns Optional.empty()

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "someorg")
                        .param("name", "somename")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "owner", "project.owner.invalidate"))
            }

            it("owner가 조직이고 호출자가 그 조직 관리자면 조직이 연동된 프로젝트가 생성되어야 한다") {
                val org = Organization(id = 21L, name = "myorg")
                val mockRepoPath = File("/tmp/yona/git/myorg/orgproj.git")
                val savedProject = Project(id = 200L, name = "orgproj", owner = "myorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(21L, 1L) } returns
                    Optional.of(OrganizationUser(user = loginUser, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))
                every { projectRepository.findByOwnerAndName("myorg", "orgproj") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "myorg", "orgproj", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("myorg", "orgproj") } returns mockRepoPath
                val projectSlot = slot<Project>()
                every { projectService.createProject(capture(projectSlot), loginUser) } returns savedProject

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "myorg")
                        .param("name", "orgproj")
                ).andExpect(status().is3xxRedirection)

                projectSlot.captured.organization shouldBe org
            }

            it("같은 owner/name 프로젝트가 이미 있으면 duplicate 에러가 발생해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.of(Project(id = 300L))

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "name", "project.name.duplicate"))
            }

            it("url이 비어있으면 empty.url 에러가 발생하고 git clone을 시도하지 않아야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "noturl") } returns Optional.empty()

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "")
                        .param("owner", "testuser")
                        .param("name", "noturl")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.empty.url"))

                verify(exactly = 0) { gitService.cloneRepository(any(), any(), any(), any(), any()) }
            }

            it("JGitInternalException 발생 시에도 wrong.url 에러를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws JGitInternalException("internal error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.wrong.url"))
            }

            it("서비스가 허용되지 않는(serviceNotPermitted) 에러면 forbidden 에러를 반환해야 한다") {
                val forbiddenMessage = "some " + MessageFormat.format(JGitText.get().serviceNotPermitted, "") + " here"
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws TransportException(forbiddenMessage)
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport.forbidden"))
            }

            it("알 수 없는 형태의 TransportException이면 상태 코드를 파싱해 transport 에러를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws TransportException("some 404 error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport"))
            }

            it("예상치 못한 일반 예외가 발생해도 transport 에러로 처리되어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws RuntimeException("boom")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport"))
            }

            it("clone은 성공했지만 이후 단계에서 실패하면 clone된 디렉터리와 기본 저장소 경로를 모두 삭제해야 한다") {
                val clonedTempDir = Files.createTempDirectory("yona-import-cloned").toFile()
                val defaultRepoTempDir = Files.createTempDirectory("yona-import-default").toFile()
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } returns clonedTempDir
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns defaultRepoTempDir
                every { projectService.createProject(any(), loginUser) } throws RuntimeException("save failed")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                ).andExpect(status().isBadRequest)

                clonedTempDir.exists() shouldBe false
                defaultRepoTempDir.exists() shouldBe false
            }

            it("clone된 디렉터리가 실제로는 존재하지 않으면 삭제를 시도하지 않아야 한다") {
                val nonExistentClonedDir = File("/tmp/yona-import-does-not-exist-${System.nanoTime()}")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } returns nonExistentClonedDir
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { projectService.createProject(any(), loginUser) } throws RuntimeException("save failed")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                ).andExpect(status().isBadRequest)
            }

            it("owner가 조직이고 호출자가 그 조직 멤버지만 관리자 역할이 아니면 invalidate 에러가 발생해야 한다") {
                val org = Organization(id = 22L, name = "memberorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { userRepository.findByLoginId("memberorg") } returns Optional.empty()
                every { organizationRepository.findByName("memberorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(22L, 1L) } returns
                    Optional.of(OrganizationUser(user = loginUser, organization = org, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndName("memberorg", "somename") } returns Optional.empty()

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "memberorg")
                        .param("name", "somename")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "owner", "project.owner.invalidate"))
            }

            it("TransportException의 메시지가 null이어도 예외 없이 transport 에러로 처리되어야 한다") {
                val nullMessageException = object : TransportException("placeholder") {
                    override val message: String? get() = null
                }
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws nullMessageException
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport"))
            }

            it("authId만 주어지고 authPw가 없어도 인증 정보가 있는 것으로 간주해 failedToAuth 에러를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", "onlyId", null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authId", "onlyId")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "authId", "project.import.error.transport.failedToAuth"))
            }

            it("authId가 빈 문자열이면 null과 동일하게 인증 정보 없음으로 처리되어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", "", null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authId", "")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport.unauthorized"))
            }

            it("authId는 없지만 authPw만 값이 있으면 인증 정보가 있는 것으로 간주해 failedToAuth 에러를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, "onlyPw") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authPw", "onlyPw")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "authId", "project.import.error.transport.failedToAuth"))
            }

            it("authId는 없고 authPw가 빈 문자열이면 인증 정보 없음으로 처리되어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, "") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authPw", "")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport.unauthorized"))
            }

            it("TransportException 메시지에 공백이 전혀 없으면 상태 코드를 Unknown으로 처리해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository(validForm.url, "testuser", "yona-imported", null, null) } throws TransportException("NoSpacesAtAll")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport"))
            }
        }
    }
})
