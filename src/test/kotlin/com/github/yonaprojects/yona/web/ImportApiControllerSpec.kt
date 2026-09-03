package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.eclipse.jgit.api.errors.TransportException
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.util.*
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import java.text.MessageFormat
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.internal.JGitText

class ImportApiControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val gitService = mockk<GitService>()
    val messageSource = mockk<MessageSource>(relaxed = true)

    val controller = ImportApiController(
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

    describe("ImportApiController 테스트") {
        val loginUser = User(id = 1L, loginId = "testuser", name = "Test User")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("POST /api/new/import") {
            it("성공 시 200 OK와 함께 생성된 프로젝트 정보를 반환해야 한다") {
                val mockRepoPath = File("/tmp/yona/git/testuser/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "testuser", overview = "프로젝트 설명")

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "testuser",
                        "name": "yona-imported",
                        "overview": "프로젝트 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.name").value("yona-imported"))
                    .andExpect(jsonPath("$.owner").value("testuser"))
            }

            it("인증 정보가 없고 unauthorized 에러가 발생한 경우 다국어 처리된 인증 에러 메시지와 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.unauthorized", null, any()) } returns "인증 권한이 필요합니다."

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "testuser",
                        "name": "yona-imported"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 권한이 필요합니다."))
            }

            it("인증 정보가 없을 경우 401 Unauthorized를 반환해야 한다") {
                val requestJson = """{"url": "a", "owner": "a", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isUnauthorized)
            }

            it("게스트 유저일 경우 403 Forbidden을 반환해야 한다") {
                val guestUser = User(id = 2L, loginId = "guest", name = "Guest", isGuest = true)
                every { userRepository.findByLoginId("guest") } returns Optional.of(guestUser)
                val requestJson = """{"url": "a", "owner": "a", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(UsernamePasswordAuthenticationToken("guest", "")).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("Guest users cannot create projects."))
            }

            it("owner가 유저도 아니고 조직도 아닐 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("invalid_owner") } returns Optional.empty()
                every { organizationRepository.findByName("invalid_owner") } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "invalid_owner", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Invalid owner"))
            }

            it("owner가 다른 유저일 경우 400 Bad Request를 반환해야 한다") {
                val otherUser = User(id = 3L, loginId = "other", name = "Other")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
                every { organizationRepository.findByName("other") } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "other", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Invalid owner"))
            }

            it("owner가 조직인데 어드민 권한이 없을 경우 403 Forbidden을 반환해야 한다") {
                val org = Organization(id = 10L, name = "myorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "myorg", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("No permission for this organization"))
            }

            it("프로젝트 이름이 이미 존재할 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.of(Project())
                val requestJson = """{"url": "a", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Project name already exists"))
            }

            it("URL이 비어있을 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                val requestJson = """{"url": "  ", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("URL cannot be empty"))
            }

            it("InvalidRemoteException 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://invalid", "testuser", "yona-imported", null, null) } throws InvalidRemoteException("invalid")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.wrong.url", null, any()) } returns "잘못된 URL"
                val requestJson = """{"url": "http://invalid", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("잘못된 URL"))
            }

            it("JGitInternalException 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://invalid", "testuser", "yona-imported", null, null) } throws JGitInternalException("internal")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.wrong.url", null, any()) } returns "잘못된 URL"
                val requestJson = """{"url": "http://invalid", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("잘못된 URL"))
            }

            it("TransportException with credentials not authorized") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://auth", "testuser", "yona-imported", "id", "pw") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.failedToAuth", null, any()) } returns "인증 실패"
                val requestJson = """{"url": "http://auth", "owner": "testuser", "name": "yona-imported", "authId": "id", "authPw": "pw"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 실패"))
            }

            it("TransportException with service not permitted") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://forbidden", "testuser", "yona-imported", null, null) } throws TransportException(MessageFormat.format(JGitText.get().serviceNotPermitted, ""))
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.forbidden", null, any()) } returns "접근 금지"
                val requestJson = """{"url": "http://forbidden", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("접근 금지"))
            }

            it("TransportException other") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://other", "testuser", "yona-imported", null, null) } throws TransportException("some 404 error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport", arrayOf("404"), any()) } returns "전송 에러"
                val requestJson = """{"url": "http://other", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("전송 에러"))
            }

            it("일반 Exception 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://error", "testuser", "yona-imported", null, null) } throws RuntimeException("Unknown error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                val requestJson = """{"url": "http://error", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unknown error"))
            }

            it("일반 Exception의 message가 null이면 기본 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://error-nomsg", "testuser", "yona-imported", null, null) } throws RuntimeException()
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                val requestJson = """{"url": "http://error-nomsg", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Git Import Failed"))
            }

            it("TransportException의 message가 null이면 빈 문자열로 처리해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://transport-nomsg", "testuser", "yona-imported", null, null) } throws TransportException(null as String?)
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport", arrayOf("Unknown"), any()) } returns "전송 에러"
                val requestJson = """{"url": "http://transport-nomsg", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("전송 에러"))
            }

            it("TransportException 메시지에 공백이 없으면(split 결과 1개) statusCode를 Unknown으로 처리해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://nospace", "testuser", "yona-imported", null, null) } throws TransportException("NoSpaceInMessage")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport", arrayOf("Unknown"), any()) } returns "전송 에러"
                val requestJson = """{"url": "http://nospace", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("전송 에러"))
            }

            it("TransportException notAuthorized + authId만 있고 authPw가 없으면 인증 실패 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://auth-partial", "testuser", "yona-imported", "id", null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.failedToAuth", null, any()) } returns "인증 실패"
                val requestJson = """{"url": "http://auth-partial", "owner": "testuser", "name": "yona-imported", "authId": "id"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 실패"))
            }

            it("조직 멤버(관리자 아님)가 조직으로 임포트 시도하면 403을 반환해야 한다") {
                val org = Organization(id = 10L, name = "myorg")
                val mockOrgUser = mockk<OrganizationUser>()
                every { mockOrgUser.role.id } returns RoleType.ORG_MEMBER.roleType

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.of(mockOrgUser)
                val requestJson = """{"url": "a", "owner": "myorg", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isForbidden)
            }

            it("조직으로 임포트 성공 시 200 OK와 함께 생성된 프로젝트 정보를 반환해야 한다") {
                val org = Organization(id = 10L, name = "myorg")
                val mockOrgUser = mockk<OrganizationUser>()
                every { mockOrgUser.role.id } returns RoleType.ORG_ADMIN.roleType

                val mockRepoPath = File("/tmp/yona/git/myorg/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "myorg", overview = "프로젝트 설명")
                
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.of(mockOrgUser)
                every { projectRepository.findByOwnerAndName("myorg", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "myorg", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("myorg", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "myorg",
                        "name": "yona-imported",
                        "overview": "프로젝트 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(100L))
            }

            // finally 블록의 clonedDir?.let { if (it.exists()) ... }와 defaultRepoDir.exists()는
            // 기존 테스트들이 전부 실제로 존재하지 않는 File 경로(mock 반환값)만 써서 항상 false만
            // 탔다 — 실제 임시 디렉터리를 만들어 true 쪽 분기(삭제 실행)를 닫는다.
            it("성공 시 clonedDir와 기존 저장소 디렉터리가 실제로 존재하면 finally에서 삭제해야 한다") {
                val tempClonedDir = kotlin.io.path.createTempDirectory(prefix = "yona-import-cloned-").toFile()
                val tempRepoDir = kotlin.io.path.createTempDirectory(prefix = "yona-import-repo-").toFile()
                val savedProject = Project(id = 101L, name = "yona-imported2", owner = "testuser", overview = null)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported2") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported2", null, null) } returns tempClonedDir
                every { gitService.getRepositoryPath("testuser", "yona-imported2") } returns tempRepoDir
                every { projectService.createProject(any(), loginUser) } returns savedProject

                val requestJson = """{"url": "https://github.com/naver/yona.git", "owner": "testuser", "name": "yona-imported2"}"""

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)

                tempClonedDir.exists() shouldBe false
                tempRepoDir.exists() shouldBe false
            }

            // hasNoCredentials = authId.isNullOrEmpty() && authPw.isNullOrEmpty()의 isEmpty() 서브
            // 분기는 기존 테스트가 null(미지정)만 다뤄서, "빈 문자열(null 아님)" 케이스가 비어 있었다.
            it("TransportException notAuthorized + authId/authPw가 둘 다 빈 문자열이면 unauthorized 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://auth-empty", "testuser", "yona-imported", "", "") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yona/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.unauthorized", null, any()) } returns "인증 권한이 필요합니다."
                val requestJson = """{"url": "http://auth-empty", "owner": "testuser", "name": "yona-imported", "authId": "", "authPw": ""}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 권한이 필요합니다."))
            }
        }
    }
})
