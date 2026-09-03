package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.site.SiteService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.site.DataBackupService
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.ObjectMapper
import java.util.*

class SiteApiControllerSpec : DescribeSpec({
    val siteService = mockk<SiteService>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    val projectRepository = mockk<ProjectRepository>(relaxed = true)
    val mailService = mockk<MailService>(relaxed = true)
    val yonaUpdateService = mockk<YonaUpdateService>(relaxed = true)
    val dataBackupService = mockk<DataBackupService>(relaxed = true)
    val objectMapper = mockk<ObjectMapper>(relaxed = true)
    val environment = mockk<Environment>(relaxed = true)

    val controller = SiteApiController(
        siteService,
        userRepository,
        projectRepository,
        mailService,
        yonaUpdateService,
        dataBackupService,
        objectMapper,
        environment
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            siteService,
            userRepository,
            projectRepository,
            mailService,
            yonaUpdateService,
            dataBackupService,
            objectMapper,
            environment
        )
    }

    val adminUser = User(id = 1L, loginId = "admin", name = "Admin", state = UserState.SITE_ADMIN)
    val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

    val normalUser = User(id = 2L, loginId = "user", name = "User")
    val normalAuth = UsernamePasswordAuthenticationToken("user", "password")

    describe("SiteApiController 테스트") {
        
        describe("checkAdmin 접근 권한 테스트") {
            it("로그인하지 않은 경우 403 에러") {
                mockMvc.perform(post("/site/mail")
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isForbidden)
            }

            it("사이트 관리자가 아닌 경우 403 에러") {
                every { userRepository.findByLoginId("user") } returns Optional.of(normalUser)

                mockMvc.perform(post("/site/mail")
                    .principal(normalAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isForbidden)
            }

            it("인증은 있으나 DB에 사용자가 없는 경우 403 에러") {
                every { userRepository.findByLoginId("user") } returns Optional.empty()

                mockMvc.perform(post("/site/mail")
                    .principal(normalAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /site/mail") {
            it("메일 발송 성공") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user"
                every { environment.getProperty("spring.mail.password") } returns "pass"

                mockMvc.perform(post("/site/mail")
                    .principal(adminAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/mail"))
                    .andExpect(model().attribute("sended", true))
                    .andExpect(model().attribute("notConfiguredItems", emptyList<String>()))
            }

            it("smtp.user가 공백뿐이면(null/빈문자열 아님) notConfiguredItems에 포함해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "   "
                every { environment.getProperty("spring.mail.password") } returns "pass"

                mockMvc.perform(post("/site/mail")
                    .principal(adminAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("notConfiguredItems", listOf("smtp.user")))
            }

            it("메일 발송 실패 시 예외 message가 null이면 기본 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user"
                every { environment.getProperty("spring.mail.password") } returns "pass"
                every { mailService.sendHtmlMail(any(), any(), any(), any(), any()) } throws RuntimeException()

                mockMvc.perform(post("/site/mail")
                    .principal(adminAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("errorMessage", "Failed to send email"))
            }

            it("환경변수 미설정 시 notConfiguredItems 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns null
                every { environment.getProperty("spring.mail.username") } returns ""
                every { environment.getProperty("spring.mail.password") } returns null

                mockMvc.perform(post("/site/mail")
                    .principal(adminAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("notConfiguredItems"))
            }

            it("메일 발송 실패 시 errorMessage 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { mailService.sendHtmlMail(any(), any(), any(), any(), any()) } throws RuntimeException("Mail error")

                mockMvc.perform(post("/site/mail")
                    .principal(adminAuth)
                    .param("to", "a@a.com")
                    .param("from", "b@b.com")
                    .param("subject", "test")
                    .param("body", "body"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("sended", false))
                    .andExpect(model().attribute("errorMessage", "Mail error"))
            }
        }

        describe("POST /site/toggleAccountLock") {
            it("성공적으로 토글 후 리다이렉트") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                
                mockMvc.perform(post("/site/toggleAccountLock")
                    .principal(adminAuth)
                    .param("loginId", "targetUser")
                    .param("state", "ACTIVE")
                    .param("query", "test"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/sites/userList?state=ACTIVE&query=test"))
            }
        }

        describe("POST /site/toggleGuestMode") {
            it("성공적으로 토글 후 리다이렉트") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                
                mockMvc.perform(post("/site/toggleGuestMode")
                    .principal(adminAuth)
                    .param("loginId", "targetUser")
                    .param("state", "ACTIVE")
                    .param("query", "test"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/sites/userList?state=ACTIVE&query=test"))
            }
        }

        describe("POST /site/toggleSiteAdminRole") {
            it("PathVariable을 통한 성공") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                
                mockMvc.perform(post("/site/toggleSiteAdminRole/targetUser")
                    .principal(adminAuth)
                    .param("state", "ACTIVE")
                    .param("query", "test"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/sites/userList?state=ACTIVE&query=test"))
            }

            it("RequestParam을 통한 성공") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                
                mockMvc.perform(post("/site/toggleSiteAdminRole")
                    .principal(adminAuth)
                    .param("loginIdParam", "targetUser")
                    .param("state", "ACTIVE")
                    .param("query", "test"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/sites/userList?state=ACTIVE&query=test"))
            }

            it("loginId도 loginIdParam도 없으면 400 계열 예외로 403을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(post("/site/toggleSiteAdminRole")
                    .principal(adminAuth)
                    .param("state", "ACTIVE")
                    .param("query", "test"))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.reason").value("FORBIDDEN"))
            }
        }

        describe("POST /site/users/{loginId}/reset-password") {
            it("성공적으로 비밀번호 초기화") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val targetUser = User(id = 3L, loginId = "target", name = "Target User")
                every { userRepository.findByLoginId("target") } returns Optional.of(targetUser)
                every { siteService.resetUserPassword("target") } returns "newpass"

                mockMvc.perform(post("/site/users/target/reset-password")
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.newPassword").value("newpass"))
            }

            it("존재하지 않는 유저 404 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("target") } returns Optional.empty()

                mockMvc.perform(post("/site/users/target/reset-password")
                    .principal(adminAuth))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"))
            }
            
            it("예외 발생 시 403 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("target") } throws RuntimeException("error")

                mockMvc.perform(post("/site/users/target/reset-password")
                    .principal(adminAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("DELETE /site/user/delete/{userId}") {
            it("성공적으로 유저 삭제") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))
            }

            it("ONLY_MANAGER 예외 발생 시 403 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(3L) } throws IllegalStateException("ONLY_MANAGER")

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.reason").value("ONLY_MANAGER"))
            }

            it("기타 IllegalStateException 발생 시 500 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(3L) } throws IllegalStateException("OTHER")

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isInternalServerError)
                    .andExpect(jsonPath("$.reason").value("SERVER_ERROR"))
            }

            it("USER_NOT_FOUND 예외 발생 시 404 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(3L) } throws IllegalArgumentException("USER_NOT_FOUND")

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"))
            }

            it("기타 IllegalArgumentException 발생 시 400 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(3L) } throws IllegalArgumentException("OTHER")

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.reason").value("BAD_REQUEST"))
            }

            it("기타 Exception 발생 시 403 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.deleteUser(3L) } throws RuntimeException("OTHER")

                mockMvc.perform(delete("/site/user/delete/3")
                    .principal(adminAuth))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.reason").value("FORBIDDEN"))
            }
        }

        describe("DELETE /site/project/delete/{projectId}") {
            it("성공적으로 프로젝트 삭제") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(delete("/site/project/delete/1")
                    .principal(adminAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/sites/projectList"))
            }
        }

        describe("POST /site/mailList") {
            it("메일 리스트 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.getMailList(true, listOf("p1")) } returns listOf("a@a.com")

                mockMvc.perform(post("/site/mailList")
                    .principal(adminAuth)
                    .param("all", "true")
                    .param("projects", "p1"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("a@a.com"))
            }

            it("all이 true가 아니면 all=false로 조회해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.getMailList(false, listOf("p1")) } returns listOf("b@b.com")

                mockMvc.perform(post("/site/mailList")
                    .principal(adminAuth)
                    .param("all", "false")
                    .param("projects", "p1"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("b@b.com"))
            }

            it("projects 파라미터가 없으면 빈 목록으로 조회해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { siteService.getMailList(true, emptyList()) } returns listOf("c@c.com")

                mockMvc.perform(post("/site/mailList")
                    .principal(adminAuth)
                    .param("all", "true"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("c@c.com"))
            }
        }

        describe("GET /site/export") {
            it("데이터 익스포트 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { dataBackupService.exportAll() } returns "testdata".toByteArray()

                mockMvc.perform(get("/site/export")
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(header().exists("Content-Disposition"))
                    .andExpect(content().bytes("testdata".toByteArray()))
            }
        }

        describe("POST /site/import") {
            it("파일이 비어있지 않으면 import 수행 후 리다이렉트") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val file = MockMultipartFile("data", "test.json", "application/json", "testdata".toByteArray())

                mockMvc.perform(multipart("/site/import")
                    .file(file)
                    .principal(adminAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                verify(exactly = 1) { dataBackupService.importAll(any()) }
            }

            it("import 중 예외 발생 시 error/400 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { dataBackupService.importAll(any()) } throws RuntimeException("Error")
                val file = MockMultipartFile("data", "test.json", "application/json", "testdata".toByteArray())

                mockMvc.perform(multipart("/site/import")
                    .file(file)
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/400"))
            }

            it("파일이 비어있으면 import 수행 안하고 리다이렉트") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val file = MockMultipartFile("data", "test.json", "application/json", ByteArray(0))

                mockMvc.perform(multipart("/site/import")
                    .file(file)
                    .principal(adminAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                verify(exactly = 0) { dataBackupService.importAll(any()) }
            }
        }

        describe("GET /site/noAvatarUsers") {
            it("아바타 없는 유저 리스트 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val usersMap = listOf(mapOf("test@test.com" to "testUser"))
                every { siteService.getNoAvatarUsers() } returns usersMap

                mockMvc.perform(get("/site/noAvatarUsers")
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.users[0]['test@test.com']").value("testUser"))
            }
        }

        describe("POST /site/setAttachmentToUserAvatar") {
            it("아바타 지정 성공") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val json = """{"avatarFileId": 123, "email": "a@a.com"}"""

                mockMvc.perform(post("/site/setAttachmentToUserAvatar")
                    .principal(adminAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value(200))
            }

            it("avatarFileId가 Number가 아닌 경우 400 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val json = """{"avatarFileId": "string", "email": "a@a.com"}"""

                mockMvc.perform(post("/site/setAttachmentToUserAvatar")
                    .principal(adminAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isBadRequest)
            }

            it("email이 없는 경우 400 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val json = """{"avatarFileId": 123}"""

                mockMvc.perform(post("/site/setAttachmentToUserAvatar")
                    .principal(adminAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isBadRequest)
            }
            
            it("IllegalArgumentException 발생 시 400 반환") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val json = """{"avatarFileId": 123, "email": "a@a.com"}"""
                every { siteService.setUserAvatar(123L, "a@a.com") } throws IllegalArgumentException("error")

                mockMvc.perform(post("/site/setAttachmentToUserAvatar")
                    .principal(adminAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.message").value("error"))
            }

            it("IllegalArgumentException의 message가 null이면 기본 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val json = """{"avatarFileId": 124, "email": "a@a.com"}"""
                every { siteService.setUserAvatar(124L, "a@a.com") } throws IllegalArgumentException()

                mockMvc.perform(post("/site/setAttachmentToUserAvatar")
                    .principal(adminAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.message").value("Bad request"))
            }
        }

        describe("POST /site/unwatchUpdate") {
            it("업데이트 알림 무시 성공") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(post("/site/unwatchUpdate")
                    .principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value(200))

                verify(exactly = 1) { yonaUpdateService.isWatched = false }
            }
        }
    }
})
