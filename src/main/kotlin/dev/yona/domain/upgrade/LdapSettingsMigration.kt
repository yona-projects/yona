package dev.yona.domain.upgrade

import dev.yona.config.YonaProperties
import dev.yona.domain.ldap.LdapSettingsForm
import dev.yona.domain.ldap.LdapSettingsService
import dev.yona.domain.sso.SsoSaveResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * v1.6에서 설정 파일(`ldap.*`, `application.use.ldap.login.supoort`)로 켜 둔 LDAP을 사이트 설정(DB의 `ldap_settings`)으로 옮긴다.
 * v2는 DB 행이 있으면 그것을 쓰고 없으면 설정 파일 값을 쓰므로(`decisions.md` D-82) 옮기지 않아도 로그인은 이어진다. 옮기면 사이트 관리 화면에서 고칠 수 있고
 * 설정 파일에서 LDAP 값을 지워도 된다.
 *
 * 대상은 설정 파일로 LDAP을 **켠** 사이트뿐이다. 꺼져 있으면 설정 파일의 값은 예시이므로 행을 만들지 않는다. 이미 화면에서 저장한 사이트는 건드리지 않는다(관리자가 고친 값을 지킨다).
 * 저장 검증(서버 주소, 포트, 기준 DN)을 통과하지 못하면 경고만 남기고 옮기지 않는다. 이때는 계속 설정 파일 값을 쓴다.
 */
@Component
class LdapSettingsMigration(private val settings: LdapSettingsService, private val properties: YonaProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 아직 옮기지 않은 건수(0 또는 1)이다. */
    fun pending(): Long = if (properties.ldap.enabled && !settings.isSaved()) 1 else 0

    /** 옮긴 건수(0 또는 1)를 돌려준다. */
    fun migrate(): Int {
        if (pending() == 0L) return 0
        val file = properties.ldap
        val form = LdapSettingsForm(
            enabled = true, protocol = file.protocol?.takeIf { it.isNotBlank() } ?: properties.protocol, host = file.host, port = file.port, baseDn = file.baseDn,
            distinguishedNamePostfix = file.distinguishedNamePostfix, loginProperty = file.loginProperty, displayNameProperty = file.displayNameProperty,
            userNameProperty = file.userNameProperty, emailProperty = file.emailProperty, useEmailBaseLogin = file.useEmailBaseLogin,
            fallbackToLocalLogin = file.fallbackToLocalLogin, englishNameAttributeName = file.englishNameAttributeName,
            bindDn = file.bindDn, bindPassword = file.bindPassword.takeIf { it.isNotEmpty() }
        )
        return when (val result = settings.save(form, null)) {
            is SsoSaveResult.Saved -> 1
            is SsoSaveResult.Rejected -> {
                log.warn("Could not move the LDAP settings to the site settings ({}); the settings file values stay in use", result.error)
                0
            }
        }
    }
}
