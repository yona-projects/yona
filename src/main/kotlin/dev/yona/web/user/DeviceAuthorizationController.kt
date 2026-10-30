package dev.yona.web.user

import dev.yona.domain.deviceauth.DeviceAuthorization
import dev.yona.domain.deviceauth.DeviceAuthorizationService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 기기 인증(Device Flow)의 웹 화면이다(로드맵 X-37i, `decisions.md` D-52, A-281). CLI가 보여 준 코드를 로그인한 사용자가 입력하고(GET `/users/device`),
 * 요청한 기기와 받게 될 범위를 확인한 뒤 승인하거나 거절한다(POST `/users/device/approve`, `/users/device/deny`). GitHub의 `/login/device`에 해당한다.
 * 코드 확인은 GET이라 CLI가 출력한 `verification_uri_complete`(`?user_code=`)로 바로 확인 화면에 들어온다.
 */
@Controller
class DeviceAuthorizationController(
    private val service: DeviceAuthorizationService,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 확인 화면에 보일 요청이다. [scopes]는 승인하면 받게 될 범위이다. */
    data class DeviceRequestView(val userCode: String, val clientName: String, val scopes: List<String>)

    /** 코드를 입력하는 화면이다. `user_code`가 있으면 그 코드의 확인 화면이고, 없거나 틀린 코드는 입력 화면(틀리면 400과 오류 문구)이다. */
    @GetMapping("/users/device")
    @AnonymousCheck(requiresLogin = true)
    @Transactional(readOnly = true)
    fun device(@RequestParam("user_code", required = false) userCode: String?): ModelAndView {
        if (userCode.isNullOrBlank()) return codeForm(null)
        val auth = service.findByUserCode(userCode) ?: return codeForm("deviceAuth.error.notFound").also { it.status = HttpStatus.BAD_REQUEST }
        return consent(auth, currentUser())
    }

    /** 승인한다. 이후 CLI의 토큰 교환이 성공한다. 없거나 이미 처리한 코드는 400이다. */
    @PostMapping("/users/device/approve")
    @AnonymousCheck(requiresLogin = true)
    @Transactional
    fun approve(@RequestParam("user_code", required = false) userCode: String?): ModelAndView {
        val auth = service.findByUserCode(userCode) ?: return codeForm("deviceAuth.error.notFound").also { it.status = HttpStatus.BAD_REQUEST }
        service.approve(auth, currentUser())
        return done("deviceAuth.done.approved")
    }

    /** 거절한다. 이후 CLI의 토큰 교환이 `access_denied`를 받는다. */
    @PostMapping("/users/device/deny")
    @AnonymousCheck(requiresLogin = true)
    @Transactional
    fun deny(@RequestParam("user_code", required = false) userCode: String?): ModelAndView {
        val auth = service.findByUserCode(userCode) ?: return codeForm("deviceAuth.error.notFound").also { it.status = HttpStatus.BAD_REQUEST }
        service.deny(auth)
        return done("deviceAuth.done.denied")
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun codeForm(error: String?): ModelAndView = ModelAndView("user/device").addObject("title", TITLE).addObject("mode", "form").addObject("deviceError", error)

    private fun consent(auth: DeviceAuthorization, user: User): ModelAndView = ModelAndView("user/device").addObject("title", TITLE).addObject("mode", "consent")
        .addObject("request", DeviceRequestView(auth.userCode.orEmpty(), auth.clientName.orEmpty(), service.grantableScopes(auth, user).toList().sortedBy { order(auth, it) }))
        .addObject("account", user.loginId)

    private fun done(messageKey: String): ModelAndView = ModelAndView("user/device").addObject("title", TITLE).addObject("mode", "done").addObject("doneMessage", messageKey)

    /** 요청한 순서를 지킨다(`repo`, `read:org`처럼 CLI가 준 순서가 화면에도 그대로 보이게 한다). */
    private fun order(auth: DeviceAuthorization, scope: String): Int = auth.scopes.orEmpty().split(',').indexOf(scope)

    private companion object {
        const val TITLE = "deviceAuth.title"
    }
}
