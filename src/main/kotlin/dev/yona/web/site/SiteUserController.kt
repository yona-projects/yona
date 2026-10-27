package dev.yona.web.site

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.net.URLEncoder

/**
 * 사이트 관리의 사용자 관리이다. v1.6 `SiteApp.userList`, `deleteUser`, `toggleSiteAdminRole`, `toggleAccountLock`, `toggleGuestMode`
 * (`app/controllers/SiteApp.java:131-136, 163-205, 238-275`)와 `UserApp.resetUserPasswordBySiteManager`에 해당한다.
 *
 * 모든 요청은 사이트 관리자만 쓴다([SiteManagerOnly]). 쪽 번호는 1부터이다.
 */
@Controller
// v1.6 SiteApp: @With(SiteManagerAuthAction) 다음 @AnonymousCheck 순서라 익명은 관리자 검사의 403을 먼저 받는다
@SiteManagerOnly
@AnonymousCheck
class SiteUserController(
    private val service: SiteUserService,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val helper: TemplateHelper
) {

    /** 목록의 한 줄이다. */
    data class UserRow(
        val id: Long, val loginId: String, val name: String, val email: String, val avatarUrl: String, val createdText: String,
        val guest: Boolean, val state: String, val siteManager: Boolean, val lastStateModified: String,
        val guestUrl: String, val lockUrl: String, val siteAdminUrl: String, val resetPasswordUrl: String, val deleteUrl: String
    ) {
        /** 기본 아바타이면 `true`이다. v1.6은 이때 `alt`, `width`, `height` 없이 그림만 낸다. */
        val defaultAvatar: Boolean get() = avatarUrl == dev.yona.security.AvatarService.DEFAULT_AVATAR_URL
    }

    @GetMapping("/sites/userList")
    @Transactional(readOnly = true)
    fun userList(
        @RequestParam(defaultValue = "1") pageNum: Int,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) state: String?
    ): Any {
        val userState = parseState(state) ?: return ErrorViews.badRequest()
        return listView(pageNum - 1, query, userState)
    }

    @DeleteMapping("/sites/user/delete{userId}")
    @Transactional
    fun deleteUser(@PathVariable userId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (service.isOnlyManager(userId)) {
            RedirectSupport.saveFlash(request, response, mapOf(RedirectSupport.WARNING to "site.userList.deleteAlert"))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }
        val user = users.findById(userId).orElseThrow { NullPointerException("사용자가 없습니다: $userId") }
        service.deleteUser(user)
        return RedirectSupport.see(request, response, "/sites/userList")
    }

    @PostMapping("/sites/toggleSiteAdminRole/{loginId}")
    @Transactional
    fun toggleSiteAdminRole(@PathVariable loginId: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = users.findByLoginIdIgnoreCase(loginId) ?: return ErrorViews.badRequest()
        service.toggleSiteAdmin(user)
        return RedirectSupport.see(request, response, "/sites/userList")
    }

    @PostMapping("/sites/toggleAccountLock")
    @Transactional
    fun toggleAccountLock(
        @RequestParam loginId: String, @RequestParam(required = false) state: String?, @RequestParam(required = false) query: String?,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val userState = parseState(state) ?: return ErrorViews.badRequest()
        val target = users.findByLoginIdIgnoreCase(loginId)
            ?: return RedirectSupport.see(request, response, "/sites/userList?pageNum=0", mapOf(RedirectSupport.WARNING to "user.notExists.name"))
        service.changeState(target, if (target.state == UserState.ACTIVE) UserState.LOCKED else UserState.ACTIVE)
        return listView(0, query, userState)
    }

    @PostMapping("/sites/toggleGuestMode")
    @Transactional
    fun toggleGuestMode(
        @RequestParam loginId: String, @RequestParam(required = false) state: String?, @RequestParam(required = false) query: String?,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val userState = parseState(state) ?: return ErrorViews.badRequest()
        val target = users.findByLoginIdIgnoreCase(loginId)
            ?: return RedirectSupport.see(request, response, "/sites/userList?pageNum=0", mapOf(RedirectSupport.WARNING to "user.notExists.name"))
        service.toggleGuest(target)
        return listView(0, query, userState)
    }

    /** 상태 이름을 읽는다. 없으면 `ACTIVE`이고, 모르는 값이면 `null`이다(v1.6은 500, A-24). */
    private fun parseState(state: String?): UserState? =
        if (state.isNullOrBlank()) UserState.ACTIVE else UserState.entries.firstOrNull { it.name == state }

    private fun listView(pageIndex: Int, query: String?, state: UserState): ModelAndView {
        val page = service.findUsers(pageIndex, query, state)
        val rows = page.items.map { row(it, query) }
        return ModelAndView("site/user-list")
            .addObject("title", "title.siteSetting")
            .addObject("rows", rows)
            .addObject("userState", state.name)
            .addObject("query", query)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("siteAdminCount", service.siteAdminBadgeCount())
    }

    /**
     * 목록 한 줄이다. 조작 주소는 v1.6 역방향 라우터가 만들던 모양이다: 쿼리가 없으면 `query`를 뺀다.
     */
    private fun row(user: User, query: String?): UserRow {
        val loginId = user.loginId.orEmpty()
        val state = user.state?.name.orEmpty()
        fun toggle(path: String): String {
            val suffix = query?.let { "&query=" + URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }.orEmpty()
            return "$path?loginId=${URLEncoder.encode(loginId, Charsets.UTF_8)}&state=$state$suffix"
        }
        return UserRow(
            id = requireNotNull(user.id), loginId = loginId, name = user.name.orEmpty(), email = user.email.orEmpty(),
            avatarUrl = avatars.avatarUrl(user, AVATAR_SIZE), createdText = helper.socialDate(user.createdDate),
            guest = user.isGuest == true, state = state, siteManager = service.isSiteManager(user),
            lastStateModified = dev.yona.util.JavaDates.toString(user.lastStateModifiedDate).orEmpty(),
            guestUrl = toggle("/sites/toggleGuestMode"), lockUrl = toggle("/sites/toggleAccountLock"),
            siteAdminUrl = "/sites/toggleSiteAdminRole/$loginId", resetPasswordUrl = "/$loginId?action=resetPassword",
            deleteUrl = "/sites/user/delete${user.id}"
        )
    }

    private companion object {
        const val AVATAR_SIZE = 64
    }
}
