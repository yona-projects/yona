package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LoginSessionService
import dev.yona.web.RedirectSupport
import dev.yona.web.layout.FlashMessageTranslator
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
 * 가입 화면, 가입, 이메일 인증, 사용 여부 확인이다. v1.6 `UserApp.signupForm`, `newUser`, `verifyUser`, `isUsed`,
 * `isEmailExist`(`app/controllers/UserApp.java:369-405, 478-495, 1008-1024`)에 해당한다.
 */
@Controller
class SignupController(
    private val signupService: SignupService,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val loginSession: LoginSessionService,
    private val flashTranslator: FlashMessageTranslator
) {

    @GetMapping("/users/signupform")
    fun form(request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!currentUserProvider.get().isAnonymous) return RedirectSupport.see(request, response, "/")
        return signupView()
    }

    @PostMapping("/users/signup")
    @Transactional
    fun signup(request: HttpServletRequest, response: HttpServletResponse): Any {
        val form = SignupForm(
            loginId = request.getParameter("loginId"), name = request.getParameter("name"),
            email = request.getParameter("email"), password = request.getParameter("password")
        )
        if (signupService.hasErrors(form)) return signupView().also { it.status = HttpStatus.BAD_REQUEST }

        if (!signupService.isAllowedEmailDomain(form.email)) {
            return signupView().also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.INFO to "user.unacceptable.email.domain")) }
                .also { it.status = HttpStatus.BAD_REQUEST }
        }

        val user = signupService.create(form)
        val flash = linkedMapOf<String, Any>()
        if (signupService.usesEmailVerification()) flash[RedirectSupport.INFO] = "user.verification.mail.sent"
        if (user.state == UserState.LOCKED && signupService.requiresAdminConfirm()) {
            flash[RedirectSupport.INFO] = "user.signup.requested"
        } else {
            loginSession.start(request, response, user)
        }
        return RedirectSupport.see(request, response, "/", flash)
    }

    @GetMapping("/verify/{loginId}/{verificationCode}")
    @Transactional
    fun verify(
        @PathVariable loginId: String,
        @PathVariable verificationCode: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        if (!currentUserProvider.get().isAnonymous) return RedirectSupport.see(request, response, "/")
        val verified = signupService.verify(loginId, verificationCode)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Invalid verification")
        return ModelAndView("user/verified").addObject("title", "").addObject("loginId", verified)
    }

    @GetMapping("/user/isUsed")
    fun isUsed(@RequestParam name: String): ResponseEntity<Map<String, Boolean>> =
        json(mapOf("isExist" to signupService.isLoginIdUsed(name), "isReserved" to dev.yona.domain.user.LoginIdRules.isReserved(name)))

    /**
     * 이메일이 쓰이는지 알린다. v1.6은 이 액션에 `@BodyParser.Of(BodyParser.Json.class)`가 있다. 공식 v1.16.0을 실행해 본 결과
     * 내용 형식이 없거나 JSON이 아닌 요청(`text/plain`, 폼 등)은 본문을 해석하지 않고 그대로 처리한다. 내용 형식이 `application/json`이나
     * `text/json`일 때만 본문이 JSON이어야 하며 비어 있거나 JSON이 아니면 400 화면이다([refactoring-backlog.md] R-110).
     */
    @GetMapping("/user/isEmailExist")
    fun isEmailExist(@RequestParam email: String, request: HttpServletRequest): Any {
        val type = request.contentType?.let { runCatching { org.springframework.http.MediaType.parseMediaType(it) }.getOrNull() }
        val jsonType = type != null && ((type.type == "application" && type.subtype == "json") || (type.type == "text" && type.subtype == "json"))
        if (jsonType && !hasJsonBody(request)) throw dev.yona.web.error.BindFailureException("JSON body expected")
        return json(mapOf("isExist" to signupService.isEmailExist(email)))
    }

    private fun hasJsonBody(request: HttpServletRequest): Boolean = try {
        tools.jackson.databind.json.JsonMapper.builder().build().readTree(request.inputStream).let { !it.isMissingNode }
    } catch (e: Exception) {
        false
    }

    private fun signupView(): ModelAndView {
        val admin = users.findByLoginId("admin")
        return ModelAndView("user/signup")
            .addObject("title", "title.signup")
            .addObject("requiresConfirm", signupService.requiresAdminConfirm())
            .addObject("adminEmailReversed", admin?.email?.reversed())
    }

    private fun json(body: Map<String, Boolean>): ResponseEntity<Map<String, Boolean>> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
}
