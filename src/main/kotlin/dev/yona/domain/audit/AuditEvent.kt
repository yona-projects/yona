package dev.yona.domain.audit

/**
 * 기록할 감사 이벤트이다. 행위자와 요청 정보(IP, 사용자 에이전트, 요청 번호, 토큰)는 [AuditRecorder]가 요청 문맥에서 채우므로 호출하는 쪽은 조치와 대상만 준다.
 *
 * @property action `범주.조치` 형식의 조치 이름(예: `project.member_added`, `auth.login_failure`)
 * @property outcome 결과(기본 성공)
 * @property details 상세. 비밀 값은 기록기가 가린다
 * @property actor 행위자를 문맥과 달리 직접 줄 때만 쓴다(로그인 실패의 시도한 ID, 시스템이 한 일)
 */
data class AuditEvent(
    val action: String,
    val outcome: AuditOutcome = AuditOutcome.SUCCESS,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val organizationId: Long? = null,
    val projectId: Long? = null,
    val details: Map<String, Any?> = emptyMap(),
    val actor: AuditActor? = null
)

/** 감사 기록의 행위자이다. [tokenId]와 [tokenScopes]는 개인용 API 토큰으로 한 일일 때만 있다. */
data class AuditActor(val type: AuditActorType, val id: Long?, val login: String?, val tokenId: Long? = null, val tokenScopes: String? = null)

/** 사용자를 대상으로 하는 이벤트를 만든다. 대상 식별자는 번호, 이름은 로그인 ID이다. */
fun auditUser(action: String, user: dev.yona.domain.user.User, details: Map<String, Any?> = emptyMap(), outcome: AuditOutcome = AuditOutcome.SUCCESS) =
    AuditEvent(action, outcome, targetType = "user", targetId = user.id?.toString(), targetLabel = user.loginId, details = details)
