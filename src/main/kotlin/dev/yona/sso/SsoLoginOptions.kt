package dev.yona.sso

import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.sso.SsoSettingsService
import org.springframework.stereotype.Component

/**
 * 로그인 화면에 보일 SSO 단추와 SSO 전용 모드를 알려 준다(추가 개발 항목 X-16, `decisions.md` D-80 12번). 켠 SSO마다 단추 하나이다.
 */
@Component
class SsoLoginOptions(private val settings: SsoSettingsService, private val site: SiteSettingService) {

    /**
     * 로그인 단추 하나이다.
     *
     * @property path 로그인 시작 주소(사이트 안의 경로)
     * @property label 사용자가 정한 표시 이름. 비어 있으면 화면이 기본 문구를 쓴다
     */
    data class Button(val protocol: String, val path: String, val label: String)

    /** 켠 SSO의 단추들이다. */
    fun buttons(): List<Button> = listOfNotNull(
        settings.oidc().takeIf { it.enabled == true }?.let { Button("oidc", "/sso/oidc/login", it.displayName.orEmpty()) },
        settings.saml2().takeIf { it.enabled == true }?.let { Button("saml2", "/sso/saml2/login", it.displayName.orEmpty()) }
    )

    /** SSO 전용 모드이면 `true`이다. */
    fun only(): Boolean = site.ssoOnly()

    /** 화면에 넘기는 읽기 전용 보기이다. 값은 화면이 처음 읽을 때 구한다(로그인 화면이 아닌 화면은 조회하지 않는다). */
    fun view(): View = View(this)

    /** 템플릿에서 `ssoLogin.providers`, `ssoLogin.only`로 쓴다. */
    class View(private val options: SsoLoginOptions) {
        val providers: List<Button> by lazy { options.buttons() }
        val only: Boolean by lazy { options.only() }
    }
}
