package dev.yona.web.user

import dev.yona.security.SessionPermissions
import dev.yona.access.AccessControl
import dev.yona.domain.notification.PasswordResetRequestedEvent
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.PasswordResetTokens
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.web.RedirectSupport
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 비밀번호 재설정이다. v1.6 `PasswordResetApp`과 `UserApp.resetUserPasswordBySiteManager`에 해당한다.
 *
 * 흐름: 아이디와 이메일을 입력해 요청하면 일회용 재설정 주소를 만들어 메일로 보내고(메일은 이벤트로 알린다, A-25),
 * 그 주소에서 새 비밀번호를 입력하면 바뀐다. 사이트 관리자는 사용자의 비밀번호를 임시 값으로 바꿀 수 있다.
 */
@Controller
class PasswordResetController(
    private val users: UserRepository,
    private val tokens: PasswordResetTokens,
    private val currentUserProvider: CurrentUserProvider,
    private val permissions: SessionPermissions,
    private val flashTranslator: FlashMessageTranslator,
    private val events: ApplicationEventPublisher,
    private val sessionUsers: dev.yona.security.SessionUserCache,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    @GetMapping("/lostPassword")
    fun lostPassword(): ModelAndView = lostPasswordView(sent = false, errorKey = null)

    @PostMapping("/lostPassword")
    @Transactional
    fun requestReset(request: HttpServletRequest): ModelAndView {
        val loginId = request.getParameter("loginId")
        val email = request.getParameter("emailAddress")
        val target = loginId?.let { users.findByLoginId(it) }
        // 이메일은 대소문자까지 같아야 한다 (v1.6 `targetUser.email.equals`).
        if (target == null || target.email != email) {
            // 틀린 조합도 남긴다(존재 여부를 드러내지 않으려고 이유는 하나뿐이다). 시도한 로그인 ID만 남기고 이메일은 남기지 않는다
            audit.record(
                dev.yona.domain.audit.AuditEvent(
                    "account.password_reset_requested", dev.yona.domain.audit.AuditOutcome.FAILURE, targetType = "user", targetLabel = loginId?.take(255), details = mapOf("reason" to "invalid_request")
                )
            )
            return lostPasswordView(sent = false, errorKey = "site.resetPasswordEmail.invalidRequest")
        }

        val token = tokens.issue(requireNotNull(target.loginId))
        val event = PasswordResetRequestedEvent(target, token)
        events.publishEvent(event)
        audit.record(dev.yona.domain.audit.auditUser("account.password_reset_requested", target))
        // v1.6는 메일을 보내지 못하면 isSent=false이고 오류 문구는 없이 폼을 다시 보여 준다.
        return lostPasswordView(sent = event.sent, errorKey = null)
    }

    @GetMapping("/resetPassword")
    fun resetForm(@RequestParam("s") hashString: String): ModelAndView =
        ModelAndView("user/reset-password").addObject("title", "title.resetPassword").addObject("hashString", hashString)

    @PostMapping("/resetPassword")
    @Transactional
    fun reset(request: HttpServletRequest): Any {
        val hashString = request.getParameter("hashString")
        val newPassword = request.getParameter("password")
        if (!tokens.isValid(hashString)) return ErrorViews.badRequest("site.resetPasswordEmail.wrongUrl")

        val loginId = requireNotNull(tokens.loginIdOf(hashString))
        val user = requireNotNull(users.findByLoginId(loginId))
        user.password = LegacyPasswordHasher.hash(requireNotNull(newPassword), requireNotNull(user.passwordSalt))
        users.save(user)
        audit.record(dev.yona.domain.audit.auditUser("account.password_changed", user, mapOf("via" to "reset_token")))
        // v1.6 User.resetPassword: 새로 읽은 사용자 인스턴스를 서버 캐시에 넣는다
        sessionUsers.remember(requireNotNull(user.id))
        tokens.remove(hashString)

        // v1.6는 안내를 남기고 같은 응답으로 로그인 화면을 보여 준다.
        return LoginFormController.loginPage(null)
            .also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to "user.loginWithNewPassword")) }
    }

    /** 사이트 관리자가 [loginId]의 비밀번호를 여섯 글자 임시 값으로 바꾸고 그 값을 알려 준다. */
    @PostMapping("/{user}")
    @Transactional
    fun resetBySiteManager(@PathVariable("user") loginId: String, request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        if (request.getParameter("action") != "resetPassword") return json(HttpStatus.BAD_REQUEST, mapOf("isSuccess" to false, "reason" to "BAD_REQUEST"))

        val actor = currentUserProvider.get().user
        val target = users.findByLoginId(loginId)
        val isSiteManager = permissions.siteManager(actor?.id)
        if (target == null || !isSiteManager) return json(HttpStatus.FORBIDDEN, mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))

        val newPassword = tokens.generate(loginId).substring(0, TEMPORARY_PASSWORD_LENGTH)
        target.password = LegacyPasswordHasher.hash(newPassword, requireNotNull(target.passwordSalt))
        users.save(target)
        audit.record(dev.yona.domain.audit.auditUser("account.password_changed", target, mapOf("via" to "admin_reset")))
        sessionUsers.remember(requireNotNull(target.id))
        return json(HttpStatus.OK, mapOf("loginId" to loginId, "name" to target.name.orEmpty(), "newPassword" to newPassword, "isSuccess" to true))
    }

    private fun lostPasswordView(sent: Boolean, errorKey: String?): ModelAndView =
        ModelAndView("site/lost-password")
            .addObject("title", "site.resetPasswordEmail.title")
            .addObject("isSent", sent)
            .addObject("errorKey", errorKey)

    private fun json(status: HttpStatus, body: Map<String, Any>): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val TEMPORARY_PASSWORD_LENGTH = 6
    }
}
