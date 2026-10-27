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
     * 로그인하지 않은 사용자의 요청은 공식 v1.16.0 실행에서 오류가 아니라 받은 `path`를 그대로 알려 주는 200 JSON이다. 익명 사용자의 설정은
     * 어느 사용자의 것도 아니므로 저장하지 않는다([refactoring-backlog.md] R-07).
     *
     * @param path 저장할 경로. 앞의 `/`를 뺀 값이다 (예: `notifications`)
     */
    @PostMapping("/-_-api/v1/user/defultLoginPage")
    @ResponseBody
    fun setDefaultLoginPage(request: jakarta.servlet.http.HttpServletRequest): Map<String, String?> {
        // v1.6은 `request().getQueryString("path")`라 쿼리 문자열만 본다(폼 본문의 `path`는 무시한다)
        val path = dev.yona.web.QueryLinks.queryValue(request, "path")
        val user = currentUserProvider.get().user ?: return mapOf("defaultLoginPage" to path)
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
