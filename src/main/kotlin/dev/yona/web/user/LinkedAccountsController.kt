package dev.yona.web.user

import dev.yona.domain.sso.LinkedAccountService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 설정의 "연결된 계정" 화면이다(추가 개발 항목 X-16, `decisions.md` D-80 18, 22번). 본인에게 연결된 SSO와 소셜 로그인 계정을 보이고 해제할 수 있게 한다.
 * 식별자는 일부만 보이며, 모든 동작은 로그인한 본인의 연결만 다룬다.
 */
@Controller
class LinkedAccountsController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val accounts: LinkedAccountService
) {

    @GetMapping("/user/editform/linked-accounts")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(): ModelAndView = ModelAndView("user/edit-linked-accounts").addObject("title", "userinfo.linkedAccounts").addObject("rows", accounts.list(currentUser()))

    /** 연결 하나를 해제한다. SSO 전용 모드이거나 본인의 연결이 아니면 해제하지 않고 안내한다. */
    @PostMapping("/user/linked-accounts/{id}/unlink")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun unlink(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val flash = when (accounts.unlinkOwn(currentUser(), id)) {
            LinkedAccountService.Result.UNLINKED -> RedirectSupport.SUCCESS to "linked.unlinked"
            LinkedAccountService.Result.SSO_ONLY -> RedirectSupport.WARNING to "linked.unlink.ssoOnly"
            LinkedAccountService.Result.NOT_FOUND -> RedirectSupport.WARNING to "linked.notFound"
        }
        return RedirectSupport.see(request, response, "/user/editform/linked-accounts", mapOf(flash))
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()
}
