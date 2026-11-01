package dev.yona.web.user

import dev.yona.domain.sshkey.SshKey
import dev.yona.domain.sshkey.SshKeyAddition
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 설정의 SSH 키 화면이다(추가 개발 항목 X-3, `decisions.md` D-66). [GpgKeyController]와 같은 구성으로, 목록 화면과 추가 화면 두 개로 나누고 목록의 행 동작은 삭제 하나뿐이다
 * (편집과 상세 없음). 키 관리는 사용자 본인 것만 다룬다. 다른 사용자의 키 삭제는 404이다.
 */
@Controller
class SshKeyController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val sshKeys: SshKeyService,
    private val helper: TemplateHelper
) {

    /** 목록의 한 줄이다. */
    data class SshKeyRow(val id: Long, val name: String, val keyType: String, val fingerprint: String, val createdText: String)

    @GetMapping("/user/editform/ssh")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(): ModelAndView = listView(currentUser())

    @GetMapping("/user/editform/ssh/new")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun newForm(): ModelAndView = newView(currentUser(), null, "", "")

    /** 공개키를 추가하고 목록으로 돌아간다. 받을 수 없는 키는 입력을 되돌려 주며 추가 화면에 400으로 이유를 보인다. */
    @PostMapping("/user/ssh")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUser()
        val name = request.getParameter("name").orEmpty()
        val publicKey = request.getParameter("publicKey").orEmpty()
        return when (val result = sshKeys.add(user, name, publicKey)) {
            is SshKeyAddition.Added -> RedirectSupport.see(request, response, "/user/editform/ssh")
            is SshKeyAddition.Rejected -> newView(user, result.code, name, publicKey).also { it.status = HttpStatus.BAD_REQUEST }
        }
    }

    /** 자기 키를 지우고 목록으로 돌아간다. 다른 사용자의 키나 없는 키는 404이다. */
    @PostMapping("/user/ssh/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!sshKeys.delete(currentUser(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/user/editform/ssh")
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun listView(user: User): ModelAndView = base("user/edit-ssh", user).addObject("keys", sshKeys.list(user).map(::rowOf))

    private fun newView(user: User, error: String?, name: String, publicKey: String): ModelAndView =
        base("user/edit-ssh-new", user).addObject("sshError", error).addObject("sshName", name).addObject("sshPublicKey", publicKey)

    private fun base(template: String, user: User) = ModelAndView(template).addObject("title", user.loginId).addObject("tabId", "ssh")

    private fun rowOf(key: SshKey) = SshKeyRow(requireNotNull(key.id), key.name.orEmpty(), key.keyType.orEmpty(), key.fingerprint.orEmpty(), helper.dateString(key.createdDate))
}
