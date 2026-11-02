package dev.yona.domain.sso

import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.LinkedAccount
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자에게 연결된 외부 계정(SSO와 소셜 로그인)을 보고 해제한다(추가 개발 항목 X-16, `decisions.md` D-80 18, 22번). 본인의 해제는 SSO 전용 모드가 꺼져 있을 때만 받는다
 * (전용 모드에서는 로컬 로그인이 막혀 해제하면 잠긴다). 사이트 관리자는 다른 사용자의 SSO 연결을 해제할 수 있다. 해제는 `auth.sso_unlinked`로 감사 기록에 남는다.
 */
@Service
class LinkedAccountService(
    private val credentials: UserCredentialRepository,
    private val linked: LinkedAccountRepository,
    private val siteSettings: SiteSettingService,
    private val audit: AuditRecorder
) {

    /** 화면에 보일 연결 하나이다. [subject]는 가려 놓은 식별자이다. */
    data class Row(val id: Long, val providerKey: String, val sso: Boolean, val subject: String)

    /** 해제 결과이다. */
    enum class Result { UNLINKED, NOT_FOUND, SSO_ONLY }

    /** [user]의 연결 목록이다. */
    @Transactional(readOnly = true)
    fun list(user: User): List<Row> = accountsOf(user).map { Row(requireNotNull(it.id), it.providerKey.orEmpty(), isSso(it), mask(it.providerUserId.orEmpty())) }

    /** [user] 본인이 연결 [accountId]를 해제한다. 본인의 연결이 아니면 [Result.NOT_FOUND], SSO 전용 모드이면 [Result.SSO_ONLY]이다. */
    @Transactional
    fun unlinkOwn(user: User, accountId: Long): Result {
        val account = accountsOf(user).firstOrNull { it.id == accountId } ?: return Result.NOT_FOUND
        if (siteSettings.ssoOnly()) return Result.SSO_ONLY
        remove(user, account, "self", user)
        return Result.UNLINKED
    }

    /** 사이트 관리자 [admin]이 [target]의 SSO 연결을 모두 해제한다(퇴사자 처리, 잘못 연결된 계정 정정). 소셜 로그인 연결은 두고, 해제한 개수를 돌려준다. */
    @Transactional
    fun unlinkAllSso(target: User, admin: User): Int = accountsOf(target).filter { isSso(it) }.onEach { remove(target, it, "admin", admin) }.size

    /** [user]에게 SSO 연결이 있는지이다(관리자 목록의 해제 단추를 보일지 정한다). */
    @Transactional(readOnly = true)
    fun hasSso(user: User): Boolean = accountsOf(user).any { isSso(it) }

    private fun accountsOf(user: User): List<LinkedAccount> =
        credentials.findByUserId(requireNotNull(user.id)).flatMap { linked.findByUserCredentialId(requireNotNull(it.id)) }

    private fun isSso(account: LinkedAccount) = account.providerKey?.startsWith("sso-") == true

    private fun remove(owner: User, account: LinkedAccount, by: String, actor: User) {
        linked.delete(account)
        audit.record(
            AuditEvent(
                "auth.sso_unlinked", targetType = "user", targetId = owner.id?.toString(), targetLabel = owner.loginId,
                details = mapOf("by" to by, "provider" to account.providerKey), actor = AuditActor(AuditActorType.USER, actor.id, actor.loginId)
            )
        )
    }

    /** 식별자를 일부만 보이게 가린다. `발급자|subject` 형태는 subject만 쓴다. */
    private fun mask(id: String): String {
        val value = id.substringAfterLast('|')
        return if (value.length <= 6) "•••" else value.take(3) + "•••" + value.takeLast(2)
    }
}
