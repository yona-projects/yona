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

/** 프로젝트를 대상으로 하는 이벤트를 만든다. 대상 이름은 `소유자/이름`이고 프로젝트 번호와 소속 조직 번호를 담는다(조회 범위를 정한다). */
fun auditProject(action: String, project: dev.yona.domain.project.Project, details: Map<String, Any?> = emptyMap(), outcome: AuditOutcome = AuditOutcome.SUCCESS) =
    AuditEvent(action, outcome, targetType = "project", targetId = project.id?.toString(), targetLabel = "${project.owner}/${project.name}", organizationId = project.organization?.id, projectId = project.id, details = details)

/** 프로젝트 안의 사용자(구성원)를 대상으로 하는 이벤트이다. */
fun auditProjectMember(action: String, project: dev.yona.domain.project.Project, user: dev.yona.domain.user.User, details: Map<String, Any?> = emptyMap()) =
    auditProject(action, project, details).copy(targetType = "user", targetId = user.id?.toString(), targetLabel = user.loginId)

/** 조직을 대상으로 하는 이벤트를 만든다. */
fun auditOrganization(action: String, organization: dev.yona.domain.project.Organization, details: Map<String, Any?> = emptyMap()) =
    AuditEvent(action, targetType = "organization", targetId = organization.id?.toString(), targetLabel = organization.name, organizationId = organization.id, details = details)
