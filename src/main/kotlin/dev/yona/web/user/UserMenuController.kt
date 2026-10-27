package dev.yona.web.user

import dev.yona.domain.user.UserSettingRepository
import dev.yona.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 사용자 메뉴(사이드바)이다. v1.6 `UserApp.usermenuTabContentList`, `Application.sidebar`
 * (`app/controllers/UserApp.java:1383`, `app/controllers/Application.java:56`)에 해당한다.
 *
 * - `GET /user/usermenuTabContentList`: 화면 오른쪽 사용자 메뉴가 Ajax로 불러 넣는 세 탭(즐겨찾기, 프로젝트, 최근 본 이슈)의 내용이다.
 * - `GET /user/sidebar`: 사이드바를 왼쪽에 고정한 화면이다. `path`(와 `hash`)로 지정한 주소를 오른쪽 프레임에 보여 준다.
 */
@Controller
class UserMenuController(
    private val assembler: UserMenuAssembler,
    private val userSettings: UserSettingRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val properties: dev.yona.config.YonaProperties
) {

    @GetMapping("/user/usermenuTabContentList")
    @Transactional(readOnly = true)
    fun tabContentList(): ModelAndView =
        ModelAndView("index/usermenu-tab-content-list").addObject("menu", assembler.assemble(currentUserProvider.get().user))

    @GetMapping("/user/sidebar")
    @Transactional(readOnly = true)
    fun sidebar(request: HttpServletRequest): ModelAndView {
        val viewer = currentUserProvider.get().user
        // 경로를 주지 않으면 로그인 기본 화면(지정하지 않았으면 알림 화면)이다. v1.6은 `hash`가 없으면 예외로 `500`이었으나 빈 해시로 본다([decisions.md] A-47).
        val default = viewer?.id?.let { userSettings.findByUserId(it)?.loginDefaultPage } ?: "/notifications"
        val path = request.getParameter("path") ?: default
        val hash = request.getParameter("hash").orEmpty()
        return ModelAndView("layout/layout-framed")
            .addObject("title", properties.siteName)
            .addObject("menu", assembler.assemble(viewer))
            .addObject("iframePath", if (hash.isNotEmpty()) "$path#$hash" else path)
    }
}
