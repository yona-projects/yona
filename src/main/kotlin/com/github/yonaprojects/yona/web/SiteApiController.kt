package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.site.SiteService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.site.DataBackupService
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Controller
@RequestMapping(value = ["/site", "/sites"])
class SiteApiController(
    private val siteService: SiteService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val mailService: MailService,
    private val yonaUpdateService: YonaUpdateService,
    private val dataBackupService: DataBackupService,
    private val objectMapper: ObjectMapper,
    private val environment: Environment
) {

    private fun checkAdmin(authentication: Authentication?): User {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return loginUser
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleUnauthorized(e: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
    }

    // 1. 메일 발송 API (POST /site/mails, POST /site/mail)
    @PostMapping(value = ["/mail", "/mails"])
    fun sendMail(
        @RequestParam("to") toEmail: String,
        @RequestParam("from") fromEmail: String,
        @RequestParam("subject") subject: String,
        @RequestParam("body") body: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)

        val notConfiguredItems = mutableListOf<String>()
        if (environment.getProperty("spring.mail.host").isNullOrBlank()) {
            notConfiguredItems.add("smtp.host")
        }
        if (environment.getProperty("spring.mail.username").isNullOrBlank()) {
            notConfiguredItems.add("smtp.user")
        }
        if (environment.getProperty("spring.mail.password").isNullOrBlank()) {
            notConfiguredItems.add("smtp.password")
        }

        var sended = false
        var errorMessage: String? = null
        try {
            mailService.sendHtmlMail(fromEmail, toEmail, toEmail, subject, body)
            sended = true
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to send email"
        }

        model.addAttribute("message", "title.sendMail")
        model.addAttribute("notConfiguredItems", notConfiguredItems)
        model.addAttribute("sender", fromEmail)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("sended", sended)
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage)
        }
        return "site/mail"
    }

    // 2. 계정 잠금/해제 토글
    @PostMapping("/toggleAccountLock")
    fun toggleAccountLock(
        @RequestParam loginId: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        siteService.toggleAccountLock(loginId)
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 3. 게스트 모드 토글
    @PostMapping("/toggleGuestMode")
    fun toggleGuestMode(
        @RequestParam loginId: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        siteService.toggleGuestMode(loginId)
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 4. 관리자 권한 토글
    @PostMapping(value = ["/toggleSiteAdminRole/{loginId}", "/toggleSiteAdminRole"])
    fun toggleSiteAdminRole(
        @PathVariable(required = false) loginId: String?,
        @RequestParam(required = false) loginIdParam: String?,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        val targetLoginId = loginId ?: loginIdParam ?: throw IllegalArgumentException("Login ID is required")
        siteService.toggleSiteAdminRole(targetLoginId)
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 5. 임시 비밀번호 강제 초기화
    @PostMapping("/users/{loginId}/reset-password")
    @ResponseBody
    fun resetUserPasswordBySiteManager(
        @PathVariable loginId: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            checkAdmin(authentication)
            val targetUser = userRepository.findByLoginId(loginId).orElse(null)
                ?: return ResponseEntity.status(404).body(mapOf("isSuccess" to false, "reason" to "USER_NOT_FOUND"))

            val newPassword = siteService.resetUserPassword(loginId)

            ResponseEntity.ok(mapOf(
                "loginId" to targetUser.loginId,
                "name" to targetUser.name,
                "newPassword" to newPassword,
                "isSuccess" to true
            ))
        } catch (e: Exception) {
            ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
        }
    }

    // 6. 회원 탈퇴 (논리 삭제)
    @DeleteMapping(value = ["/user/delete/{userId}", "/users/{userId}/delete"])
    @ResponseBody
    fun deleteUser(
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            checkAdmin(authentication)
            siteService.deleteUser(userId)
            ResponseEntity.ok(mapOf("isSuccess" to true))
        } catch (e: IllegalStateException) {
            if (e.message == "ONLY_MANAGER") {
                ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "ONLY_MANAGER"))
            } else {
                ResponseEntity.status(500).body(mapOf("isSuccess" to false, "reason" to "SERVER_ERROR"))
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "USER_NOT_FOUND") {
                ResponseEntity.status(404).body(mapOf("isSuccess" to false, "reason" to "USER_NOT_FOUND"))
            } else {
                ResponseEntity.status(400).body(mapOf("isSuccess" to false, "reason" to "BAD_REQUEST"))
            }
        } catch (e: Exception) {
            ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
        }
    }

    // 7. 프로젝트 강제 삭제
    @DeleteMapping(value = ["/project/delete/{projectId}", "/projects/{projectId}/delete"])
    fun deleteProject(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): String {
        checkAdmin(authentication)
        siteService.deleteProject(projectId)
        return "redirect:/sites/projectList"
    }

    // 8. 대량 메일 수신처 목록 조회 API
    @PostMapping("/mailList")
    @ResponseBody
    fun mailList(
        @RequestParam params: MultiValueMap<String, String>,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        checkAdmin(authentication)
        val all = params.containsKey("all") && params.getFirst("all") == "true"
        val projects = params["projects"] ?: emptyList<String>()
        val emails = siteService.getMailList(all, projects)
        return ResponseEntity.ok(emails)
    }

    // 9. 전체 데이터 백업 다운로드 (모든 테이블, P0-07: users/projects만 백업되던 문제 해결)
    @GetMapping("/export")
    fun exportData(
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        checkAdmin(authentication)
        val jsonBytes = dataBackupService.exportAll()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"yona-data-${Instant.now().epochSecond}.json\"")
        return ResponseEntity.ok()
            .headers(headers)
            .body(jsonBytes)
    }

    // 10. 데이터 업로드 복원 (모든 테이블 전체 교체 - 완전 복원)
    @PostMapping("/import")
    fun importData(
        @RequestParam("data") file: MultipartFile,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        if (!file.isEmpty) {
            try {
                dataBackupService.importAll(file.bytes)
            } catch (e: Exception) {
                return "error/400"
            }
        }
        return "redirect:/"
    }

    // 11. 아바타 없는 유저 리스트 조회 API
    @GetMapping("/noAvatarUsers")
    @ResponseBody
    fun noAvatarUsers(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)
        val users = siteService.getNoAvatarUsers()
        return ResponseEntity.ok(mapOf("users" to users))
    }

    // 12. 아바타 지정 API (yona SiteApp.setAttachmentToUserAvatar 대응, P2-03)
    @PostMapping("/setAttachmentToUserAvatar")
    @ResponseBody
    fun setAttachmentToUserAvatar(
        @RequestBody body: Map<String, Any>,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)

        val avatarFileId = (body["avatarFileId"] as? Number)?.toLong()
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Expecting Json data"))
        val email = body["email"] as? String
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Expecting Json data"))

        return try {
            siteService.setUserAvatar(avatarFileId, email)
            ResponseEntity.ok(mapOf("status" to 200, "message" to "OK"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Bad request")))
        }
    }

    // 13. 업데이트 알림 무시 비동기 API
    @PostMapping("/unwatchUpdate")
    @ResponseBody
    fun unwatchUpdate(
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)
        yonaUpdateService.isWatched = false
        return ResponseEntity.ok(mapOf("status" to 200, "message" to "OK"))
    }
}
