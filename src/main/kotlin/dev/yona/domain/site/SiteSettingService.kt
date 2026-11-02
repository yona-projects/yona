package dev.yona.domain.site

import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사이트 설정의 읽기와 쓰기이다(추가 개발 항목 X-2, `decisions.md` D-69). 바꾼 설정은 감사 기록에 남긴다.
 */
@Service
class SiteSettingService(private val settings: SiteSettingRepository, private val audit: AuditRecorder) {

    /** 2단계 인증 강제 방식이다. 저장한 값이 없거나 알아볼 수 없으면 [TwoFactorEnforcement.OFF]이다. */
    @Transactional(readOnly = true)
    fun twoFactorEnforcement(): TwoFactorEnforcement =
        settings.findById(TWO_FACTOR_ENFORCEMENT).orElse(null)?.value?.let { v -> TwoFactorEnforcement.entries.firstOrNull { it.name == v } } ?: TwoFactorEnforcement.OFF

    /** 2단계 인증 강제 방식을 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setTwoFactorEnforcement(value: TwoFactorEnforcement) {
        val before = twoFactorEnforcement()
        settings.save(SiteSetting(TWO_FACTOR_ENFORCEMENT, value.name))
        if (before != value) audit.record(AuditEvent("site.two_factor_enforcement_changed", targetType = "site", details = mapOf("from" to before.name, "to" to value.name)))
    }

    /** OAuth 앱의 클라이언트 ID 메타데이터 문서(CIMD)를 받는지이다(추가 개발 항목 X-1). 기본은 받는다. */
    @Transactional(readOnly = true)
    fun oauthCimdEnabled(): Boolean = settings.findById(OAUTH_CIMD_ENABLED).orElse(null)?.value?.let { it == "true" } ?: true

    /** CIMD 허용 여부를 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setOauthCimdEnabled(value: Boolean) {
        val before = oauthCimdEnabled()
        settings.save(SiteSetting(OAUTH_CIMD_ENABLED, value.toString()))
        if (before != value) audit.record(AuditEvent("site.oauth_cimd_changed", targetType = "site", details = mapOf("from" to before, "to" to value)))
    }

    /** 일반 사용자가 OAuth 앱을 만들 수 있는지이다(추가 개발 항목 X-1). 기본은 만들 수 있다. */
    @Transactional(readOnly = true)
    fun oauthUserAppsEnabled(): Boolean = settings.findById(OAUTH_USER_APPS_ENABLED).orElse(null)?.value?.let { it == "true" } ?: true

    /** 사용자의 앱 만들기 허용 여부를 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setOauthUserAppsEnabled(value: Boolean) {
        val before = oauthUserAppsEnabled()
        settings.save(SiteSetting(OAUTH_USER_APPS_ENABLED, value.toString()))
        if (before != value) audit.record(AuditEvent("site.oauth_user_apps_changed", targetType = "site", details = mapOf("from" to before, "to" to value)))
    }

    companion object {
        const val OAUTH_USER_APPS_ENABLED = "security.oauth-user-apps-enabled"
        const val OAUTH_CIMD_ENABLED = "security.oauth-cimd-enabled"
        const val TWO_FACTOR_ENFORCEMENT = "security.two-factor-enforcement"
    }
}
