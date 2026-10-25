package dev.yona.web.home

import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.user.UserSetting
import dev.yona.domain.user.UserSettingRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/**
 * 홈 화면과 알림 화면이다. v1.6 `Application.index`, `Application.notifications`
 * (`app/controllers/Application.java:45-60`)에 해당한다.
 *
 * - 익명 사용자에게는 사이트 소개를, 로그인한 사용자에게는 환영 표와 알림 목록을 보여 준다.
 * - 홈(`/`)은 사용자가 기본 로그인 페이지를 정해 두었으면 그곳으로 보낸다. 알림 화면(`/notifications`)은 보내지 않는다.
 *
 * 알림 항목은 [NotificationItemAssembler]가 만든다. 더 보기는 [NotificationController]가 맡는다.
 */
@Controller
@AnonymousCheck
class HomeController(
    private val currentUserProvider: CurrentUserProvider,
    private val userSettings: UserSettingRepository,
    private val notificationEvents: NotificationEventRepository,
    private val items: NotificationItemAssembler
) {

    /** 홈 화면이다. v1.6은 `/users/login`, `/-_-api`, `/-_-api/v1/`의 GET도 같은 액션(`Application.index`)에 연결했다. */
    @GetMapping("/", "/users/login", "/-_-api", "/-_-api/v1/")
    fun index(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUserProvider.get().user
        val loginDefaultPage = user?.id?.let { userSettings.findByUserId(it)?.loginDefaultPage }
        if (!loginDefaultPage.isNullOrBlank()) {
            return RedirectSupport.see(request, response, loginDefaultPage)
        }
        return notificationsPage()
    }

    @GetMapping("/notifications")
    fun notifications(): ModelAndView = notificationsPage()

    /**
     * 현재 화면을 기본 로그인 페이지로 저장한다. v1.6 `UserApp.setDefaultLoginPage`이다.
     *
     * 로그인하지 않은 사용자의 요청은 v1.6에서도 오류(HTTP 500)가 된다([refactoring-backlog.md] R-07).
     *
     * @param path 저장할 경로. 앞의 `/`를 뺀 값이다 (예: `notifications`)
     */
    @PostMapping("/-_-api/v1/user/defultLoginPage")
    @ResponseBody
    fun setDefaultLoginPage(@RequestParam(required = false) path: String?): Map<String, String?> {
        val user = requireNotNull(currentUserProvider.get().user) { "로그인이 필요합니다." }
        val setting = userSettings.findByUserId(requireNotNull(user.id)) ?: UserSetting(user = user)
        setting.loginDefaultPage = path
        userSettings.save(setting)
        return mapOf("defaultLoginPage" to setting.loginDefaultPage)
    }

    private fun notificationsPage(): ModelAndView {
        val user = currentUserProvider.get().user
        val notifications = user?.let { items.assemble(notificationEvents.findByReceiver(it, 0, PAGE_SIZE), it) }.orEmpty()
        return ModelAndView("index/notifications")
            .addObject("notifications", notifications)
            .addObject("menuTab", "TAB_NOTI")
    }

    private companion object {
        /** 첫 화면에 보여 주는 알림 개수이다 (v1.6 `partial_notifications(0, 20)`). */
        const val PAGE_SIZE = 20
    }
}
