package dev.yona.web.user

import dev.yona.config.YonaProperties
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 사용자 페이지이다. v1.6 `UserApp.userInfo`(`app/controllers/UserApp.java:724-807`)와 `app/views/user/view.scala.html`에 해당한다.
 *
 * - 조직 이름이면 조직 페이지로 보낸다. 없는 사용자는 404이다.
 * - 이슈와 풀 리퀘스트를 거슬러 올라갈 일수(`daysAgo`)는 쿼리 값이 우선이다. 쿼리에 없으면(0) 쿠키 `daysAgo`를 쓰고,
 *   쿠키도 없으면 14일이며 쿠키에 저장한다. 쿼리 값이 음수이면 1이다. 쿼리 값은 쿠키에 저장한다.
 * - `selected`는 처음 열 탭(`issues`, `pullRequests`, `projects`)이다. 기본은 `issues`이다.
 * - 게스트가 보면 활동 탭이 없다. 사이트가 프로젝트 목록을 숨기고 보는 사람이 익명이면 활동이 비어 있다.
 */
@Controller
@AnonymousCheck
class UserPageController(
    private val users: UserRepository,
    private val organizations: OrganizationRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val assembler: UserPageAssembler,
    private val properties: YonaProperties
) {

    @GetMapping("/{user}")
    @Transactional(readOnly = true)
    fun userInfo(
        @PathVariable("user") loginId: String,
        @RequestParam(defaultValue = "0") daysAgo: Int,
        @RequestParam(defaultValue = "issues") selected: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        organizations.findByNameIgnoreCase(loginId)?.let { return RedirectSupport.see(request, response, "/organizations/${it.name}") }

        val days = resolveDaysAgo(daysAgo, request, response)
        val user = users.findByLoginIdIgnoreCase(loginId) ?: return ErrorViews.notFound("user.notExists.name")
        val viewer = currentUserProvider.get().user
        val activity = assembler.activity(user, viewer, days, hidden = properties.hideProjectListing && viewer == null)
        return org.springframework.web.servlet.ModelAndView("user/view")
            .addObject("profile", assembler.profile(user, viewer, properties.showUserEmail))
            .addObject("activity", activity)
            .addObject("daysAgo", days)
            .addObject("selected", selected)
            .addObject("showStream", viewer?.isGuest != true)
            .addObject("openIssueCount", activity.issues.count { it.open })
            .addObject("closedIssueCount", activity.issues.count { !it.open })
    }

    /** 기간을 정하고 쿠키에 저장한다. 쿼리에 값이 없고 쿠키가 있으면 저장하지 않는다. */
    private fun resolveDaysAgo(daysAgo: Int, request: HttpServletRequest, response: HttpServletResponse): Int {
        if (daysAgo == UNDEFINED) {
            val cookie = request.cookies?.firstOrNull { it.name == COOKIE }?.value
            if (!cookie.isNullOrEmpty()) return cookie.toInt()
            saveCookie(response, DEFAULT_DAYS_AGO)
            return DEFAULT_DAYS_AGO
        }
        val days = if (daysAgo < 0) 1 else daysAgo
        saveCookie(response, days)
        return days
    }

    private fun saveCookie(response: HttpServletResponse, days: Int) {
        response.addCookie(Cookie(COOKIE, days.toString()).apply { path = "/"; isHttpOnly = true })
    }

    private companion object {
        /** 쿼리에 값이 없음을 뜻한다 (v1.6 `UserApp.UNDEFINED`). */
        const val UNDEFINED = 0

        /** 기본 일수이다 (v1.6 `UserApp.DAYS_AGO`). */
        const val DEFAULT_DAYS_AGO = 14

        /** 일수를 저장하는 쿠키 이름이다 (v1.6 `UserApp.DAYS_AGO_COOKIE`). */
        const val COOKIE = "daysAgo"
    }
}
