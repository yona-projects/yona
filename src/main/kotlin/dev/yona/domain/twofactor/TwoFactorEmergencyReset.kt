package dev.yona.domain.twofactor

import dev.yona.config.YonaProperties
import dev.yona.domain.audit.auditUser
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 긴급 해제이다(추가 개발 항목 X-2, `decisions.md` D-69). 관리자 본인까지 2단계 인증을 쓸 수 없게 되었을 때를 위해, `yona.two-factor.reset-user`에 로그인 ID를 적고 서버를 시작하면
 * 그 사용자의 2단계 인증(인증 앱, 백업 코드, 알려진 기기)을 지운다. 서버 설정을 바꿀 수 있는 사람만 쓸 수 있는 경로이다. 쓴 뒤에는 설정을 지우도록 로그로 알린다(남겨 두면 시작할 때마다 실행된다).
 */
@Component
class TwoFactorEmergencyReset(
    private val properties: YonaProperties,
    private val users: UserRepository,
    private val twoFactor: TwoFactorService,
    private val audit: AuditRecorder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val loginId = properties.twoFactor.resetUser
        if (loginId.isNotBlank() && resetUser(loginId)) log.warn("2단계 인증을 긴급 해제했다: {}. yona.two-factor.reset-user 설정을 지우십시오(남겨 두면 시작할 때마다 해제한다)", loginId)
    }

    /** [loginId] 사용자의 2단계 인증을 지운다. 사용자가 없거나 2단계 인증 데이터가 없으면 `false`이다. */
    @Transactional
    fun resetUser(loginId: String): Boolean {
        if (loginId.isBlank()) return false
        val user = users.findByLoginId(loginId.trim()) ?: return false
        if (!twoFactor.resetByEmergency(user)) return false
        return true
    }
}
