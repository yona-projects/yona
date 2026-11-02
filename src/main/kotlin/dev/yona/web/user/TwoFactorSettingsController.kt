package dev.yona.web.user

import dev.yona.config.YonaProperties
import dev.yona.domain.twofactor.KnownDevice
import dev.yona.domain.twofactor.KnownDeviceService
import dev.yona.domain.twofactor.QrCode
import dev.yona.domain.twofactor.TotpConfirmation
import dev.yona.domain.twofactor.TotpEnrollment
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 설정의 2단계 인증 화면이다(추가 개발 항목 X-2, `decisions.md` D-69). 목록 화면 하나에서 인증 앱, 백업 코드, 알려진 기기를 다룬다. 인증 앱 추가는 `begin`으로 시작해 QR 코드가 있는 설정 화면을 보이고
 * `totp`로 확인한다. 비밀 값과 백업 코드가 담긴 응답은 이동 없이 곧바로 그려 주소나 기록에 남지 않게 하고, 캐시를 막는다. 모든 동작은 로그인한 본인의 것만 다룬다.
 */
@Controller
class TwoFactorSettingsController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val twoFactor: TwoFactorService,
    private val knownDevices: KnownDeviceService,
    private val properties: YonaProperties,
    private val helper: TemplateHelper
) {

    /** 등록한 수단 목록의 한 줄이다. [type]은 `TOTP`나 `WEBAUTHN`이다. */
    data class MethodRow(val id: Long, val type: String, val name: String, val createdText: String, val lastUsedText: String)

    /** 알려진 기기 목록의 한 줄이다. */
    data class DeviceRow(val id: Long, val name: String, val ip: String, val lastUsedText: String, val expiresText: String)

    @GetMapping("/user/editform/two-factor")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(): ModelAndView = listView(currentUser())

    /** 인증 앱 추가를 시작하고 QR 코드가 있는 설정 화면을 보인다. 이미 켜져 있으면 목록으로 돌려보낸다. */
    @PostMapping("/user/two-factor/begin")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun begin(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUser()
        val enrollment = twoFactor.beginTotp(user, properties.siteName, user.loginId.orEmpty()) ?: return RedirectSupport.see(request, response, LIST)
        return noCache(setupView(enrollment, null), response)
    }

    /** 입력한 코드로 인증 앱 등록을 확인한다. 맞으면 백업 코드를 한 번 보이고, 틀리면 같은 비밀 값으로 설정 화면을 다시 보인다. */
    @PostMapping("/user/two-factor/totp")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun confirm(request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUser()
        return when (val result = twoFactor.confirmTotp(user, request.getParameter("code").orEmpty())) {
            is TotpConfirmation.Confirmed -> noCache(codesView(result.backupCodes, "twoFactor.backup.created"), response)
            TotpConfirmation.NotStarted -> RedirectSupport.see(request, response, LIST)
            TotpConfirmation.InvalidCode -> {
                val enrollment = twoFactor.pending(user, properties.siteName, user.loginId.orEmpty()) ?: return RedirectSupport.see(request, response, LIST)
                noCache(setupView(enrollment, "twoFactor.setup.invalid").also { it.status = HttpStatus.BAD_REQUEST }, response)
            }
        }
    }

    /** 백업 코드를 새로 만들어 한 번 보인다. 켜져 있지 않으면 목록으로 돌려보낸다. */
    @PostMapping("/user/two-factor/backup-codes")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun regenerate(request: HttpServletRequest, response: HttpServletResponse): Any {
        val codes = twoFactor.regenerateBackupCodes(currentUser()) ?: return RedirectSupport.see(request, response, LIST)
        return noCache(codesView(codes, "twoFactor.backup.regenerated"), response)
    }

    /**
     * 수단 하나(인증 앱이나 보안 키)를 지운다. 남은 수단이 없으면 2단계 인증이 꺼지고 백업 코드와 알려진 기기도 지워진다. 남의 수단이나 없는 수단은 404이다.
     */
    @PostMapping("/user/two-factor/credentials/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun removeMethod(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!twoFactor.removeCredential(currentUser(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, LIST)
    }

    /** 알려진 기기 하나를 지운다. 다른 사용자의 기기나 없는 기기는 404이다. */
    @PostMapping("/user/two-factor/devices/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun removeDevice(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!knownDevices.remove(currentUser(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, LIST)
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun listView(user: User): ModelAndView = base("user/edit-two-factor").addObject("enabled", twoFactor.isEnabled(user)).addObject("hasTotp", twoFactor.hasTotp(user))
        .addObject("methods", twoFactor.methods(user).map(::methodRow))
        .addObject("backupRemaining", twoFactor.backupCodesRemaining(user)).addObject("devices", knownDevices.list(user).map(::rowOf))

    private fun setupView(enrollment: TotpEnrollment, error: String?): ModelAndView =
        base("user/edit-two-factor-setup").addObject("qrSvg", QrCode.svg(enrollment.otpauthUri)).addObject("secret", enrollment.secret.chunked(4).joinToString(" ")).addObject("setupError", error)

    private fun codesView(codes: List<String>, noticeKey: String): ModelAndView = base("user/edit-two-factor-codes").addObject("codes", codes).addObject("noticeKey", noticeKey)

    private fun base(template: String) = ModelAndView(template).addObject("title", "userinfo.twoFactor").addObject("tabId", "two-factor")

    /** 비밀 값이나 백업 코드를 담은 화면이 브라우저와 중간 서버에 남지 않게 한다. */
    private fun noCache(view: ModelAndView, response: HttpServletResponse): ModelAndView = view.also {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    private fun methodRow(c: dev.yona.domain.twofactor.TwoFactorCredential) =
        MethodRow(requireNotNull(c.id), c.type.orEmpty(), c.name.orEmpty(), helper.dateString(c.createdDate), helper.dateString(c.lastUsedDate))

    private fun rowOf(d: KnownDevice) = DeviceRow(requireNotNull(d.id), d.name.orEmpty(), d.ip.orEmpty(), helper.dateString(d.lastUsedDate), helper.dateString(d.expiresDate))

    private companion object {
        const val LIST = "/user/editform/two-factor"
    }
}
