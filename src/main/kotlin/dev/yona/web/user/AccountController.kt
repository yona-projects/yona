package dev.yona.web.user

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.descrKey
import dev.yona.domain.enumeration.notiTypes
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.notification.ProjectNotificationService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.AccountService
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LoginSessionService
import dev.yona.util.HtmlSanitizer
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.ModelAndView

/**
 * 계정 설정 화면과 저장이다. v1.6 `UserApp.editUserInfoForm`, `editUserInfoByTabForm`, `editUserInfo`, `resetUserPassword`,
 * `resetUserVisitedList`와 이메일 관리(`addEmail`, `deleteEmail`, `setAsMainEmail`, `sendValidationEmail`, `confirmEmail`)에
 * 해당한다(`app/controllers/UserApp.java:582-626, 877-990, 1035-1142`).
 *
 * 알림 탭(`notifications`)은 알림 서브시스템을 옮길 때 만든다. 그때까지 그 탭 주소는 오류이다.
 */
@Controller
class AccountController(
    private val account: AccountService,
    private val users: UserRepository,
    private val emails: EmailRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val loginSession: LoginSessionService,
    private val flashTranslator: FlashMessageTranslator,
    private val projectNotifications: ProjectNotificationService,
    private val watches: WatchRepository,
    private val projects: ProjectRepository,
    private val sessionUsers: dev.yona.security.SessionUserCache
) {

    /** 화면에 보일 계정 정보이다. */
    data class AccountView(val loginId: String, val name: String, val rawName: String, val email: String, val token: String?, val avatarUrl80: String, val avatarUrl256: String)

    /** 알림 설정 탭의 프로젝트 한 칸이다. */
    data class NotificationSettingProject(val id: Long, val owner: String, val name: String, val rows: List<NotificationSettingRow>)

    /** 알림 설정 탭에서 이벤트 종류 하나의 스위치이다. [descrKey]는 종류 설명의 메시지 키이다. */
    data class NotificationSettingRow(val type: String, val descrKey: String, val enabled: Boolean)

    /** 보조 이메일 한 줄이다. */
    data class SubEmail(val id: Long, val email: String, val valid: Boolean, val avatarUrl: String)

    @GetMapping("/user/editform")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun editForm(): ModelAndView = profileView(requireNotNull(currentUserProvider.get().user), null)

    @RequestMapping("/user/editform/{tabId}", method = [RequestMethod.GET, RequestMethod.POST])
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun editFormByTab(@PathVariable tabId: String): ModelAndView {
        val user = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()
        return when (tabId.lowercase()) {
            "password" -> tabView("user/edit-password", user, "password")
            "emails" -> tabView("user/edit-emails", user, "emails")
                .addObject("subEmails", account.subEmails(user).map { SubEmail(requireNotNull(it.id), it.email.orEmpty(), it.valid == true, avatars.gravatarUrl(it.email, SUB_EMAIL_AVATAR_SIZE)) })
            "token", "token_reset" -> {
                account.token(user, recreate = tabId.lowercase() == "token_reset")
                tabView("user/edit-token", user, "token")
            }
            "profile" -> profileView(user, null)
            "notifications" -> notificationsView(user)
            else -> throw IllegalArgumentException("Invalid tabId")
        }
    }

    @PostMapping("/user/edit")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun edit(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()
        val denied = account.updateProfile(user, request.getParameter("name"), request.getParameter("email"), request.getParameter("avatarId"))
        if (denied != null) return profileView(user, denied).also { it.status = HttpStatus.BAD_REQUEST }
        // v1.6 `editUserInfo`는 끝에서 `CacheStore.yonaUsers.put`으로 항목을 새로 만든다
        sessionUsers.remember(requireNotNull(user.id))
        return RedirectSupport.see(request, response, "/${user.loginId}?daysAgo=14&selected=projects")
    }

    @PostMapping("/user/resetPassword")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun resetPassword(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()
        val old = request.getParameter("oldPassword")
        val new = request.getParameter("password")
        if (old == null || new == null) return ErrorViews.badRequest("error.badrequest")
        if (!account.isValidPassword(user, old)) return profileView(user, "user.wrongPassword.alert").also { it.status = HttpStatus.BAD_REQUEST }

        account.resetPassword(user, new)
        loginSession.end(request, response)
        return RedirectSupport.see(request, response, "/users/loginform", mapOf(RedirectSupport.WARNING to "user.loginWithNewPassword"))
    }

    @PostMapping("/user/resetVisitedList")
    @Transactional
    fun resetVisitedList(request: HttpServletRequest, response: HttpServletResponse): Any {
        currentUserProvider.get().user?.let { account.resetVisitedProjects(it) }
        return RedirectSupport.see(request, response, "/user/editform", mapOf(RedirectSupport.INFO to "userinfo.reset.visited.project.list.done"))
    }

    // --- 보조 이메일 ---

    @PostMapping("/user/email")
    @Transactional
    fun addEmail(request: HttpServletRequest, response: HttpServletResponse): Any {
        val address = request.getParameter("email")
        // v1.6는 폼 오류를 먼저 보고(없음, 형식), 그 다음에 로그인 여부와 중복을 본다.
        val formError = when {
            address.isNullOrEmpty() -> "error.required"
            !dev.yona.domain.user.SignupService.isValidEmailFormat(address) -> "error.email"
            else -> null
        }
        if (formError != null) return RedirectSupport.see(request, response, "/user/editform", mapOf(RedirectSupport.WARNING to formError))
        val user = currentUserProvider.get().user ?: return ErrorViews.notFound().also { it.status = HttpStatus.FORBIDDEN }

        val denied = account.addEmail(users.findById(requireNotNull(user.id)).get(), address)
        return RedirectSupport.see(
            request, response, "/user/editform",
            if (denied != null) mapOf(RedirectSupport.WARNING to messageText(denied)) else emptyMap()
        )
    }

    @DeleteMapping("/user/email/delete/{emailId}")
    @Transactional
    fun deleteEmail(@PathVariable emailId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (_, email) = loadOwn(emailId, Operation.DELETE) ?: return forbiddenNotFound()
        account.deleteEmail(email.second)
        return RedirectSupport.see(request, response, "/user/editform")
    }

    @PutMapping("/user/email/setAsMain/{emailId}")
    @Transactional
    fun setAsMain(@PathVariable emailId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (user, email) = loadOwn(emailId, Operation.UPDATE) ?: return forbiddenNotFound()
        account.setAsMainEmail(users.findById(requireNotNull(user.id)).get(), email.second)
        // v1.6 `User.removeEmail`이 `CacheStore.yonaUsers.put`으로 항목을 새로 만든다
        sessionUsers.remember(requireNotNull(user.id))
        return RedirectSupport.see(request, response, "/user/editform")
    }

    @PostMapping("/user/email/sendValidationEmail/{emailId}")
    @Transactional
    fun sendValidation(@PathVariable emailId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (_, email) = loadOwn(emailId, Operation.UPDATE) ?: return forbiddenNotFound()
        account.requestValidationEmail(email.second)
        // v1.6는 안내 문구를 한국어로 고정해 두었다.
        return RedirectSupport.see(request, response, "/user/editform", mapOf(RedirectSupport.WARNING to "확인 메일을 전송했습니다."))
    }

    @GetMapping("/user/email/confirm/{emailId}/{token}")
    @Transactional
    fun confirmEmail(@PathVariable emailId: Long, @PathVariable token: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val email = emails.findById(emailId).orElse(null) ?: return forbiddenNotFound()
        if (!account.confirmEmail(email, token)) return forbiddenNotFound()
        // 확인 링크를 연 사람은 그 이메일의 사용자로 로그인된다 (v1.6 `addUserInfoToSession`).
        email.user?.let { loginSession.start(request, response, it) }
        return RedirectSupport.see(request, response, "/user/editform")
    }

    // --- 도우미 ---

    /** 로그인한 사용자와 그가 [operation]할 수 있는 보조 이메일을 찾는다. 로그인하지 않았거나 없으면 `null`이다. 권한이 없으면 오류 화면이다. */
    private fun loadOwn(emailId: Long, operation: Operation): Pair<User, Pair<User, dev.yona.domain.user.Email>>? {
        val current = currentUserProvider.get().user ?: return null
        val email = emails.findById(emailId).orElse(null) ?: return null
        val owner = email.user ?: return null
        if (!accessControl.isAllowed(current, owner.asResource(), operation)) throw dev.yona.web.error.ViewResultException(ErrorViews.forbidden("error.forbidden"))
        return current to (owner to email)
    }

    private fun forbiddenNotFound(): ModelAndView = ErrorViews.notFound().also { it.status = HttpStatus.FORBIDDEN }

    private fun profileView(user: User, warningKey: String?): ModelAndView {
        val view = tabView("user/edit", user, "profile")
        warningKey?.let { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to it)) }
        return view
    }

    /** 구독한 프로젝트마다 이벤트 종류별 알림 스위치를 보여 주는 탭이다 (v1.6 `edit_notifications`). */
    private fun notificationsView(user: User): ModelAndView {
        val settings = projectNotifications.byProject(user)
        val items = watches.findByUserIdAndResourceType(requireNotNull(user.id), ResourceType.PROJECT).mapNotNull { watch ->
            val project = watch.resourceId?.toLongOrNull()?.let { projects.findById(it).orElse(null) } ?: return@mapNotNull null
            val id = requireNotNull(project.id)
            NotificationSettingProject(id, project.owner.orEmpty(), project.name.orEmpty(), notiTypes.map { NotificationSettingRow(it.name, it.descrKey, projectNotifications.isEnabled(settings, id, it)) })
        }
        return tabView("user/edit-notifications", user, "notifications").addObject("notificationProjects", items)
    }

    private fun tabView(template: String, user: User, tabId: String): ModelAndView = ModelAndView(template)
        .addObject("title", user.loginId)
        .addObject("tabId", tabId)
        .addObject("account", AccountView(
            user.loginId.orEmpty(), HtmlSanitizer.defaultSanitize(user.name), user.name.orEmpty(), user.email.orEmpty(), user.token,
            avatars.avatarUrl(user, 80), avatars.avatarUrl(user, 256)
        ))

    /** 메시지 키를 보여 줄 문구로 바꾼다. v1.6의 이메일 추가는 중복 안내를 번역한 문구로 flash에 남긴다. */
    private fun messageText(key: String): String = flashTranslator.translate(mapOf(RedirectSupport.WARNING to key)).notices.first()

    private companion object {
        const val SUB_EMAIL_AVATAR_SIZE = 40
    }
}
