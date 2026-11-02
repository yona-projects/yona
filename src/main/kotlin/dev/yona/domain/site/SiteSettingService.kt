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

    companion object {
        const val TWO_FACTOR_ENFORCEMENT = "security.two-factor-enforcement"
    }
}
