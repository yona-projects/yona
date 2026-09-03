package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.site.SiteService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

import tools.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.domain.support.DiagnosticService
import org.springframework.core.env.Environment
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import com.github.yonaprojects.yona.domain.site.DataBackupService
import io.mockk.clearMocks
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.http.MediaType

class SiteControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val projectService = mockk<ProjectService>()
    val mailService = mockk<MailService>()
    val diagnosticService = mockk<DiagnosticService>()
    val yonaUpdateService = mockk<YonaUpdateService>()
    val environment = mockk<Environment>()
    val objectMapper = ObjectMapper()

    val siteService = mockk<SiteService>()
    val dataBackupService = mockk<DataBackupService>()

    val siteViewController = SiteViewController(
        userRepository,
        projectRepository,
        issueRepository,
        postingRepository,
        diagnosticService,
        yonaUpdateService,
        environment
    )

    val siteApiController = SiteApiController(
        siteService,
        userRepository,
        projectRepository,
        mailService,
        yonaUpdateService,
        dataBackupService,
        objectMapper,
        environment
    )

    val mockMvcView = MockMvcBuilders.standaloneSetup(siteViewController).build()
    val mockMvcApi = MockMvcBuilders.standaloneSetup(siteApiController).build()

    beforeTest {
        clearMocks(
            userRepository,
            projectRepository,
            projectUserRepository,
            issueRepository,
            postingRepository,
            projectService,
            mailService,
            diagnosticService,
            yonaUpdateService,
            environment,
            siteService
        )
    }

    describe("SiteViewController & SiteApiController 관리 기능 명세") {
        var adminUser = User(id = 1L, loginId = "admin", name = "어드민", email = "admin@example.com", state = UserState.SITE_ADMIN)
        var normalUser = User(id = 2L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
        val adminAuth = UsernamePasswordAuthenticationToken("admin", "pass")
        val normalAuth = UsernamePasswordAuthenticationToken("gildong", "pass")

        beforeEach {
            adminUser = User(id = 1L, loginId = "admin", name = "어드민", email = "admin@example.com", state = UserState.SITE_ADMIN)
            normalUser = User(id = 2L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
        }

        describe("GET /site/users") {
            it("로그인한 주체가 사이트 관리자인 경우 200 OK와 사용자 관리 뷰를 리턴해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findUsersForAdmin(UserState.ACTIVE, any(), any()) } returns PageImpl(listOf(normalUser))
                every { userRepository.countUsersForAdmin(UserState.SITE_ADMIN, any()) } returns 1

                // When & Then
                mockMvcView.perform(
                    get("/site/users")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/userList"))
                    .andExpect(model().attributeExists("users"))
                    .andExpect(model().attribute("adminCount", 1))
            }

            it("로그인한 주체가 관리자가 아니면 403 Forbidden 뷰(error/403)로 리다이렉트가 아닌 뷰를 렌더링해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)

                // When & Then
                mockMvcView.perform(
                    get("/site/users")
                        .principal(normalAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /site/userList") {
            it("로그인한 주체가 사이트 관리자인 경우 200 OK와 사용자 관리 뷰를 리턴해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findUsersForAdmin(UserState.ACTIVE, any(), any()) } returns PageImpl(listOf(normalUser))
                every { userRepository.countUsersForAdmin(UserState.SITE_ADMIN, any()) } returns 1

                // When & Then
                mockMvcView.perform(
                    get("/site/userList")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/userList"))
            }
        }

        describe("POST /site/toggleAccountLock") {
            it("정상적으로 대상 사용자의 계정 잠금 여부를 반전해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.toggleAccountLock("gildong") } returns Unit

                // When & Then
                mockMvcApi.perform(
                    post("/site/toggleAccountLock")
                        .param("loginId", "gildong")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/userList"))

                verify { siteService.toggleAccountLock("gildong") }
            }
        }

        describe("POST /site/toggleGuestMode") {
            it("정상적으로 게스트 사용자 모드를 토글해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.toggleGuestMode("gildong") } returns Unit

                // When & Then
                mockMvcApi.perform(
                    post("/site/toggleGuestMode")
                        .param("loginId", "gildong")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/userList"))

                verify { siteService.toggleGuestMode("gildong") }
            }
        }

        describe("POST /site/users/{loginId}/reset-password") {
            it("임시 비밀번호를 무작위 생성하여 암호화 저장 후 JSON 응답을 주어야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)
                every { siteService.resetUserPassword("gildong") } returns "123456"

                // When & Then
                mockMvcApi.perform(
                    post("/site/users/gildong/reset-password")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.newPassword").value("123456"))

                verify { siteService.resetUserPassword("gildong") }
            }
        }

        describe("DELETE /site/user/delete/{userId}") {
            it("프로젝트 내 유일한 매니저가 아니라면 탈퇴 처리를 수행해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(2L) } returns Unit

                // When & Then
                mockMvcApi.perform(
                    delete("/site/user/delete/2")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))

                verify { siteService.deleteUser(2L) }
            }

            it("프로젝트 내 유일한 매니저인 경우 탈퇴 처리를 반려해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(2L) } throws IllegalStateException("ONLY_MANAGER")

                // When & Then
                mockMvcApi.perform(
                    delete("/site/user/delete/2")
                        .principal(adminAuth)
                )
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.reason").value("ONLY_MANAGER"))
            }
        }

        describe("GET /site/projects") {
            it("프로젝트 목록을 200 OK와 함께 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test-project", owner = "gildong")
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { projectRepository.findProjectsForAdmin(any(), any()) } returns PageImpl(listOf(testProject))

                // When & Then
                mockMvcView.perform(
                    get("/site/projects")
                        .param("projectName", "test")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/projectList"))
                    .andExpect(model().attributeExists("projects"))
            }
        }

        describe("DELETE /site/project/delete/{projectId}") {
            it("프로젝트를 강제 소거하고 리다이렉트해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteProject(100L) } returns Unit

                // When & Then
                mockMvcApi.perform(
                    delete("/site/project/delete/100")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/projectList"))

                verify { siteService.deleteProject(100L) }
            }
        }

        describe("GET /site/issueList") {
            it("전체 미해결 이슈 목록을 페이징하여 정상 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test", owner = "gildong")
                val testIssue = Issue(id = 10L, title = "버그", body = "버그수정", project = testProject)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { issueRepository.findByState(State.OPEN, any()) } returns PageImpl(listOf(testIssue))

                // When & Then
                mockMvcView.perform(
                    get("/site/issueList")
                        .param("state", "open")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/issueList"))
                    .andExpect(model().attributeExists("issues"))
            }
        }

        describe("GET /site/postList") {
            it("전체 자유게시판 글 목록을 페이징하여 정상 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test", owner = "gildong")
                val testPost = Posting(id = 20L, title = "공지", body = "공지내용", project = testProject)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { postingRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(testPost))

                // When & Then
                mockMvcView.perform(
                    get("/site/postList")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/postList"))
                    .andExpect(model().attributeExists("posts"))
            }
        }

        describe("GET /site/mail") {
            it("메일 작성 폼 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user@gmail.com"
                every { environment.getProperty("spring.mail.password") } returns "password"
                every { environment.getProperty("spring.mail.properties.mail.smtp.from") } returns "user@gmail.com"

                mockMvcView.perform(
                    get("/site/mail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/mail"))
                    .andExpect(model().attributeExists("sender"))
            }
        }

        describe("POST /site/mails") {
            it("메일을 정상적으로 발송하고 메일 결과와 함께 리다이렉트가 아닌 메일 뷰를 렌더링해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user@gmail.com"
                every { environment.getProperty("spring.mail.password") } returns "password"
                every { mailService.sendHtmlMail("admin@example.com", "target@example.com", "target@example.com", "제목", "내용") } returns Unit

                mockMvcApi.perform(
                    post("/site/mails")
                        .param("to", "target@example.com")
                        .param("from", "admin@example.com")
                        .param("subject", "제목")
                        .param("body", "내용")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/mail"))
                    .andExpect(model().attribute("sended", true))
            }
        }

        describe("GET /site/massmail") {
            it("대량 메일 작성 폼 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvcView.perform(
                    get("/site/massmail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/massMail"))
            }
        }

        describe("POST /site/mailList") {
            it("all=true일 때 전체 사용자의 이메일 목록을 JSON으로 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.getMailList(true, emptyList()) } returns listOf("admin@example.com", "gildong@example.com")

                mockMvcApi.perform(
                    post("/site/mailList")
                        .param("all", "true")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("admin@example.com"))
                    .andExpect(jsonPath("$[1]").value("gildong@example.com"))
            }
        }

        describe("GET /site/data") {
            it("로그인한 주체가 관리자일 때 데이터 백업 화면 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvcView.perform(
                    get("/site/data")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/data"))
            }
        }

        describe("GET /site/export") {
            it("로그인한 주체가 관리자일 때 DataBackupService가 만든 전체 DB 백업을 파일로 내려주어야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { dataBackupService.exportAll() } returns "{\"n4user\":[]}".toByteArray()

                mockMvcApi.perform(
                    get("/site/export")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/json"))

                verify(exactly = 1) { dataBackupService.exportAll() }
            }
        }

        describe("POST /site/import") {
            it("로그인한 주체가 관리자이고 파일이 있으면 DataBackupService로 전체 DB를 복원해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { dataBackupService.importAll(any()) } returns Unit

                val file = MockMultipartFile(
                    "data", "backup.json", "application/json", "{\"n4user\":[]}".toByteArray()
                )

                mockMvcApi.perform(
                    MockMvcRequestBuilders.multipart("/site/import")
                        .file(file)
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { dataBackupService.importAll(any()) }
            }
        }

        describe("GET /site/noAvatarUsers") {
            it("아바타가 설정되지 않은 회원들의 리스트를 JSON 형태로 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.getNoAvatarUsers() } returns listOf(mapOf("loginId" to "gildong", "name" to "홍길동", "email" to "gildong@example.com"))

                mockMvcApi.perform(
                    get("/site/noAvatarUsers")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.users[0].loginId").value("gildong"))
            }
        }

        describe("POST /site/setAttachmentToUserAvatar (P2-03)") {
            it("첨부파일ID/이메일을 서비스에 그대로 전달해 아바타를 지정해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.setUserAvatar(100L, "gildong@example.com") } returns Unit

                mockMvcApi.perform(
                    post("/site/setAttachmentToUserAvatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"avatarFileId": 100, "email": "gildong@example.com"}""")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value(200))

                verify(exactly = 1) { siteService.setUserAvatar(100L, "gildong@example.com") }
            }
        }

        describe("GET /site/diagnostic") {
            it("시스템 자가진단 분석 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { diagnosticService.checkAll() } returns listOf("Test Database Warning")

                mockMvcView.perform(
                    get("/site/diagnostic")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/diagnostic"))
                    .andExpect(model().attributeExists("errors"))
            }
        }

        describe("SiteViewController.checkAdmin() 공통 인증/인가 분기") {
            it("인증되지 않은 요청(authentication == null)은 error/403 뷰를 반환해야 한다") {
                mockMvcView.perform(get("/site/users"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("로그인 ID에 해당하는 사용자가 DB에 없으면 error/403 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.empty()

                mockMvcView.perform(get("/site/users").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /site/userList - state 파라미터 분기") {
            it("존재하지 않는 state 값이면 UserState.of()가 null을 반환해 기본값 ACTIVE로 대체해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findUsersForAdmin(UserState.ACTIVE, any(), any()) } returns PageImpl(listOf(normalUser))
                every { userRepository.countUsersForAdmin(UserState.SITE_ADMIN, any()) } returns 1

                mockMvcView.perform(
                    get("/site/userList")
                        .param("state", "존재하지않는상태값")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("userState", UserState.ACTIVE))
            }
        }

        describe("GET /site/issueList - state=all 분기") {
            it("state=all이면 전체 이슈 목록(findAll)을 조회해야 한다") {
                val testProject = Project(id = 100L, name = "test", owner = "gildong")
                val testIssue = Issue(id = 11L, title = "전체이슈", body = "내용", project = testProject)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { issueRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(testIssue))

                mockMvcView.perform(
                    get("/site/issueList")
                        .param("state", "all")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("currentState", State.ALL))

                verify(exactly = 1) { issueRepository.findAll(any<Pageable>()) }
            }
        }

        describe("GET /site/mail - SMTP 설정 미비/기본값 분기") {
            it("host/username/password가 모두 비어있으면 notConfiguredItems에 셋 다 담기고 sender는 하드코딩 기본값이어야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns ""
                every { environment.getProperty("spring.mail.username") } returns null
                every { environment.getProperty("spring.mail.password") } returns null
                every { environment.getProperty("spring.mail.properties.mail.smtp.from") } returns null

                mockMvcView.perform(
                    get("/site/mail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("sender", "yona@yona.io"))
            }

            it("공백 문자만 있는 값도 isNullOrBlank에서 blank로 취급되어 notConfiguredItems에 담겨야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "   "
                every { environment.getProperty("spring.mail.username") } returns "   "
                every { environment.getProperty("spring.mail.password") } returns "   "
                every { environment.getProperty("spring.mail.properties.mail.smtp.from") } returns null

                mockMvcView.perform(
                    get("/site/mail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("notConfiguredItems", listOf("smtp.host", "smtp.user", "smtp.password")))
            }

            it("smtp.from은 없지만 username이 있으면 sender는 username으로 대체되어야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "fallback@example.com"
                every { environment.getProperty("spring.mail.password") } returns "password"
                every { environment.getProperty("spring.mail.properties.mail.smtp.from") } returns null

                mockMvcView.perform(
                    get("/site/mail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("sender", "fallback@example.com"))
            }
        }

        describe("GET /site/update") {
            it("갱신이 필요 없으면 versionToUpdate는 null이고 예외도 없어야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { yonaUpdateService.refreshVersionToUpdate() } returns Unit
                every { yonaUpdateService.isUpdateRequired() } returns false
                every { yonaUpdateService.getLatestVersion() } returns "1.15.0"
                every { yonaUpdateService.getReleaseUrl() } returns "https://example.com/release"

                mockMvcView.perform(
                    get("/site/update")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/update"))
                    .andExpect(model().attribute("versionToUpdate", null as String?))
                    .andExpect(model().attribute("exception", null as Exception?))
            }

            it("갱신이 필요하면 versionToUpdate에 최신 버전이 담겨야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { yonaUpdateService.refreshVersionToUpdate() } returns Unit
                every { yonaUpdateService.isUpdateRequired() } returns true
                every { yonaUpdateService.getLatestVersion() } returns "1.16.0"
                every { yonaUpdateService.getReleaseUrl() } returns "https://example.com/release"

                mockMvcView.perform(
                    get("/site/update")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("versionToUpdate", "1.16.0"))
            }

            it("refreshVersionToUpdate()에서 예외가 발생해도 뷰는 정상 렌더링되고 exception이 모델에 담겨야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { yonaUpdateService.refreshVersionToUpdate() } throws RuntimeException("network down")
                every { yonaUpdateService.isUpdateRequired() } returns false
                every { yonaUpdateService.getLatestVersion() } returns null
                every { yonaUpdateService.getReleaseUrl() } returns "https://example.com/release"

                mockMvcView.perform(
                    get("/site/update")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/update"))
                    .andExpect(model().attributeExists("exception"))
            }
        }
    }
})
