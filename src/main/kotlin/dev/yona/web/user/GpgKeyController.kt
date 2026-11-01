package dev.yona.web.user

import dev.yona.domain.gpgkey.GpgKey
import dev.yona.domain.gpgkey.GpgKeyAddition
import dev.yona.domain.gpgkey.GpgKeyService
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
 * 설정의 GPG 키 화면이다(추가 개발 항목 X-4, `decisions.md` D-65). 목록 화면과 추가 화면 두 개로 나누고 목록의 행 동작은 삭제 하나뿐이다(편집과 상세 없음, D-65 8번).
 * 키 관리는 사용자 본인 것만 다룬다. 다른 사용자의 키 삭제는 404이다.
 */
@Controller
class GpgKeyController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val gpgKeys: GpgKeyService,
    private val helper: TemplateHelper
) {

    /** 목록의 한 줄이다. [fingerprint]는 보기 좋게 네 글자씩 끊었다. */
    data class GpgKeyRow(
        val id: Long, val name: String, val keyId: String, val fingerprint: String, val emails: String, val subkeyCount: Int,
        val createdText: String, val expiresText: String?, val expired: Boolean
    )

    @GetMapping("/user/editform/gpg")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(): ModelAndView = listView(currentUser())

    @GetMapping("/user/editform/gpg/new")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun newForm(): ModelAndView = newView(currentUser(), null, "", "")

    /** 공개키를 추가하고 목록으로 돌아간다. 받을 수 없는 키는 입력을 되돌려 주며 추가 화면에 400으로 이유를 보인다. */
    @PostMapping("/user/gpg")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUser()
        val name = request.getParameter("name").orEmpty()
        val armored = request.getParameter("publicKey").orEmpty()
        return when (val result = gpgKeys.add(user, name, armored)) {
            is GpgKeyAddition.Added -> RedirectSupport.see(request, response, "/user/editform/gpg")
            is GpgKeyAddition.Rejected -> newView(user, result.code, name, armored).also { it.status = HttpStatus.BAD_REQUEST }
        }
    }

    /** 자기 키를 지우고 목록으로 돌아간다. 다른 사용자의 키나 없는 키는 404이다. */
    @PostMapping("/user/gpg/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!gpgKeys.delete(currentUser(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/user/editform/gpg")
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun listView(user: User): ModelAndView = base("user/edit-gpg", user).addObject("keys", gpgKeys.list(user).map(::rowOf))

    private fun newView(user: User, error: String?, name: String, publicKey: String): ModelAndView =
        base("user/edit-gpg-new", user).addObject("gpgError", error).addObject("gpgName", name).addObject("gpgPublicKey", publicKey)

    private fun base(template: String, user: User) = ModelAndView(template).addObject("title", user.loginId).addObject("tabId", "gpg")

    private fun rowOf(key: GpgKey) = GpgKeyRow(
        requireNotNull(key.id), key.name.orEmpty(), key.keyId.orEmpty(), key.fingerprint.orEmpty().chunked(FINGERPRINT_GROUP).joinToString(" "), key.emails.orEmpty().replace(",", ", "),
        gpgKeys.subkeys(key).size, helper.dateString(key.createdDate), key.expiresDate?.let(helper::dateString), key.expiresDate != null && key.expiresDate!!.time <= System.currentTimeMillis()
    )

    private companion object {
        const val FINGERPRINT_GROUP = 4
    }
}
