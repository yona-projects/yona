package dev.yona.domain.site

import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사이트 설정의 읽기와 쓰기이다(추가 개발 항목 X-2, `decisions.md` D-69). 바꾼 설정은 감사 기록에 남긴다.
 */
@Service
class SiteSettingService(
    private val settings: SiteSettingRepository,
    private val audit: AuditRecorder,
    private val sso: dev.yona.domain.sso.SsoSettingsService
) {

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

    /** MCP 서버(`/mcp`)를 쓰는지이다(추가 개발 항목 X-6, `decisions.md` D-72). 끄면 `/mcp`는 404이다. 기본은 켠다. */
    @Transactional(readOnly = true)
    fun mcpEnabled(): Boolean = settings.findById(MCP_ENABLED).orElse(null)?.value?.let { it == "true" } ?: true

    /** MCP 서버 사용 여부를 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setMcpEnabled(value: Boolean) {
        val before = mcpEnabled()
        settings.save(SiteSetting(MCP_ENABLED, value.toString()))
        if (before != value) audit.record(AuditEvent("site.mcp_enabled_changed", targetType = "site", details = mapOf("from" to before, "to" to value)))
    }

    /** MCP 쓰기 도구(이슈 만들기 등)를 허용하는지이다. 끄면 읽기 도구만 보인다. 기본은 켠다. */
    @Transactional(readOnly = true)
    fun mcpWriteEnabled(): Boolean = settings.findById(MCP_WRITE_ENABLED).orElse(null)?.value?.let { it == "true" } ?: true

    /** MCP 쓰기 도구 허용 여부를 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setMcpWriteEnabled(value: Boolean) {
        val before = mcpWriteEnabled()
        settings.save(SiteSetting(MCP_WRITE_ENABLED, value.toString()))
        if (before != value) audit.record(AuditEvent("site.mcp_write_changed", targetType = "site", details = mapOf("from" to before, "to" to value)))
    }

    /** Git LFS 서버를 쓰는지이다(추가 개발 항목 X-8, `decisions.md` D-79). 끄면 LFS 경로는 404이다. 기본은 켠다. */
    @Transactional(readOnly = true)
    fun lfsEnabled(): Boolean = settings.findById(LFS_ENABLED).orElse(null)?.value?.let { it == "true" } ?: true

    /** LFS 사용 여부를 바꾼다. 바뀐 값이 있을 때만 감사 기록을 남긴다. */
    @Transactional
    fun setLfsEnabled(value: Boolean) {
        val before = lfsEnabled()
        settings.save(SiteSetting(LFS_ENABLED, value.toString()))
        if (before != value) audit.record(AuditEvent("site.lfs_changed", targetType = "site", details = mapOf("setting" to "enabled", "from" to before, "to" to value)))
    }

    /** LFS 객체 하나의 최대 크기(바이트)이다. 기본은 [DEFAULT_LFS_MAX_OBJECT_SIZE](2 GiB)이다. */
    @Transactional(readOnly = true)
    fun lfsMaxObjectSize(): Long = settings.findById(LFS_MAX_OBJECT_SIZE).orElse(null)?.value?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_LFS_MAX_OBJECT_SIZE

    /** LFS 객체 하나의 최대 크기를 바꾼다. 0 이하는 기본값으로 되돌린다. */
    @Transactional
    fun setLfsMaxObjectSize(value: Long) {
        val before = lfsMaxObjectSize()
        val after = if (value > 0) value else DEFAULT_LFS_MAX_OBJECT_SIZE
        settings.save(SiteSetting(LFS_MAX_OBJECT_SIZE, after.toString()))
        if (before != after) audit.record(AuditEvent("site.lfs_changed", targetType = "site", details = mapOf("setting" to "max_object_size", "from" to before, "to" to after)))
    }

    /** 프로젝트 하나가 LFS에 쓸 수 있는 용량(바이트)이다. 0이면 제한이 없다(기본). */
    @Transactional(readOnly = true)
    fun lfsProjectQuota(): Long = settings.findById(LFS_PROJECT_QUOTA).orElse(null)?.value?.toLongOrNull()?.takeIf { it > 0 } ?: 0L

    /** 프로젝트별 LFS 용량 한도를 바꾼다. 0 이하는 제한 없음이다. */
    @Transactional
    fun setLfsProjectQuota(value: Long) {
        val before = lfsProjectQuota()
        val after = value.coerceAtLeast(0)
        settings.save(SiteSetting(LFS_PROJECT_QUOTA, after.toString()))
        if (before != after) audit.record(AuditEvent("site.lfs_changed", targetType = "site", details = mapOf("setting" to "project_quota", "from" to before, "to" to after)))
    }

    /** SSO 전용 모드인지이다(추가 개발 항목 X-16, `decisions.md` D-80 12번). 켜면 로컬 비밀번호 로그인, LDAP, 소셜 로그인을 막는다(사이트 관리자의 로컬 로그인만 남긴다). 기본은 끈다. */
    @Transactional(readOnly = true)
    fun ssoOnly(): Boolean = settings.findById(SSO_ONLY).orElse(null)?.value == "true"

    /**
     * SSO 전용 모드를 바꾼다. 켜려면 켠 SSO(OIDC 또는 SAML 2)가 하나 이상 있어야 하며, 없으면 바꾸지 않고 `false`를 돌려준다(모두가 로그인하지 못하는 일을 막는다).
     * 바뀐 값이 있을 때만 감사 기록을 남긴다.
     */
    @Transactional
    fun setSsoOnly(value: Boolean): Boolean {
        if (value && !sso.anyEnabled()) return false
        val before = ssoOnly()
        settings.save(SiteSetting(SSO_ONLY, value.toString()))
        if (before != value) audit.record(AuditEvent("site.sso_changed", targetType = "site", details = mapOf("setting" to "sso_only", "from" to before, "to" to value)))
        return true
    }

    companion object {
        const val SSO_ONLY = "security.sso-only"
        const val LFS_ENABLED = "lfs.enabled"
        const val LFS_MAX_OBJECT_SIZE = "lfs.max-object-size"
        const val LFS_PROJECT_QUOTA = "lfs.project-quota"

        /** LFS 객체 하나의 기본 최대 크기이다(2 GiB, 첨부 파일의 기본 한도와 같은 규모). */
        const val DEFAULT_LFS_MAX_OBJECT_SIZE = 2L * 1024 * 1024 * 1024

        const val MCP_ENABLED = "security.mcp-enabled"
        const val MCP_WRITE_ENABLED = "security.mcp-write-enabled"
        const val OAUTH_USER_APPS_ENABLED = "security.oauth-user-apps-enabled"
        const val OAUTH_CIMD_ENABLED = "security.oauth-cimd-enabled"
        const val TWO_FACTOR_ENFORCEMENT = "security.two-factor-enforcement"
    }
}
