package dev.yona.domain.audit

import dev.yona.domain.user.User
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 경계에서 일어나는 감사 이벤트(로그인, 토큰 인증, 접근 거부)를 만든다(추가 개발 항목 X-9 A3, `decisions.md` D-62). 모두 별도 트랜잭션([AuditRecorder.recordIndependent])이라
 * 바깥 요청이 실패하거나 롤백해도 남고, 기록하지 못해도 요청을 막지 않는다. 비밀 값(비밀번호, 토큰 값)은 어떤 필드에도 넣지 않는다. 시도한 로그인 ID만 남긴다.
 */
@Component
class AuditBoundary(private val recorder: AuditRecorder) {

    /** 주소마다 마지막으로 차단 이벤트를 남긴 시각이다. 막힌 주소가 요청을 계속 보내도 기록이 쏟아지지 않게 한다. */
    private val blockedLogged = ConcurrentHashMap<String, Long>()

    fun loginSuccess(user: User, method: String) = recorder.recordIndependent(
        AuditEvent(
            "auth.login_success", targetType = "user", targetId = user.id?.toString(), targetLabel = user.loginId, details = mapOf("method" to method),
            actor = AuditActor(AuditActorType.USER, user.id, user.loginId)
        )
    )

    /** 로그인 실패이다. 이유는 `invalid_credentials`(없는 사용자와 틀린 비밀번호를 같게 보여 존재 여부를 드러내지 않는다), `locked`, `deleted`, `locked_after_authentication`이다. */
    fun loginFailure(loginKey: String?, reason: String, denied: Boolean) = recorder.recordIndependent(
        AuditEvent(
            "auth.login_failure", if (denied) AuditOutcome.DENIED else AuditOutcome.FAILURE, targetType = "user", targetLabel = loginKey?.take(255),
            details = mapOf("reason" to reason), actor = AuditActor(AuditActorType.ANONYMOUS, null, loginKey?.take(255))
        )
    )

    /** 로그인한 사용자의 로그아웃이다. 로그인하지 않았으면 남기지 않는다. */
    fun logout(user: User?) {
        user ?: return
        recorder.recordIndependent(AuditEvent("auth.logout", targetType = "user", targetId = user.id?.toString(), targetLabel = user.loginId, actor = AuditActor(AuditActorType.USER, user.id, user.loginId)))
    }

    /** API 토큰이 맞지 않아 인증하지 못했다. 토큰 값은 남기지 않는다. */
    fun tokenFailure(channel: String) = recorder.recordIndependent(
        AuditEvent("auth.token_failure", AuditOutcome.FAILURE, details = mapOf("channel" to channel), actor = AuditActor(AuditActorType.ANONYMOUS, null, null))
    )

    /** git HTTP 같은 Basic 인증의 실패이다. [reason]은 `invalid_credentials`나 `invalid_token`이다. */
    fun basicFailure(loginId: String?, channel: String, reason: String) = recorder.recordIndependent(
        AuditEvent(
            "auth.basic_failure", AuditOutcome.FAILURE, targetType = "user", targetLabel = loginId?.take(255),
            details = mapOf("channel" to channel, "reason" to reason), actor = AuditActor(AuditActorType.ANONYMOUS, null, loginId?.take(255))
        )
    )

    /** 인증 실패가 쌓여 주소가 막혔다. 같은 주소는 1분에 한 번만 남긴다. */
    fun blocked(address: String, channel: String, now: Long = System.currentTimeMillis()) {
        if (blockedLogged.size > MAX_TRACKED) blockedLogged.entries.removeIf { now - it.value >= BLOCK_LOG_INTERVAL_MILLIS }
        val last = blockedLogged[address]
        if (last != null && now - last < BLOCK_LOG_INTERVAL_MILLIS) return
        blockedLogged[address] = now
        recorder.recordIndependent(AuditEvent("auth.token_blocked", AuditOutcome.DENIED, details = mapOf("channel" to channel), actor = AuditActor(AuditActorType.ANONYMOUS, null, null)))
    }

    /** 응답이 403이었다. 대상에는 질의 문자열을 넣지 않는다(질의에 비밀 값이 있을 수 있다). */
    fun accessDenied(method: String, path: String, status: Int, via: String) = recorder.recordIndependent(
        AuditEvent("access.denied", AuditOutcome.DENIED, targetType = "request", targetLabel = "$method $path".take(255), details = mapOf("status" to status, "method" to method, "path" to path, "via" to via))
    )

    /** 토큰 범위가 모자라 외부 API 요청이 거부되었다. 같은 요청을 [accessDenied]로 또 남기지 않게 표시한다. */
    fun apiScopeDenied(method: String, path: String, message: String) = recorder.recordIndependent(
        AuditEvent("access.api_scope_denied", AuditOutcome.DENIED, targetType = "request", targetLabel = "$method $path".take(255), details = mapOf("status" to 403, "method" to method, "path" to path, "reason" to message))
    )

    companion object {
        /** 요청 속성이다. 이미 구체적인 거부 이벤트를 남겼다는 표시이며 접근 거부 필터가 건너뛴다. */
        const val RECORDED_ATTRIBUTE = "dev.yona.domain.audit.DenialRecorded"
        private const val BLOCK_LOG_INTERVAL_MILLIS = 60_000L
        private const val MAX_TRACKED = 1000
    }
}
