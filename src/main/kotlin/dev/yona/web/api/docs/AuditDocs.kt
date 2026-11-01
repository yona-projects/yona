package dev.yona.web.api.docs

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "감사 로그 기록 한 건. 모든 필드는 기록 시점의 값이고 한 번 쓰면 바뀌지 않는다")
class AuditEventNode(
    @get:Schema(description = "기록 번호. 사슬의 순서이다") val id: Long,
    @get:Schema(description = "시각(UTC, ISO 8601)", example = "2026-11-01T09:30:00.123Z") val time: String,
    @get:JsonProperty("actor_type") @get:Schema(description = "행위자 유형", allowableValues = ["USER", "TOKEN", "SYSTEM", "ANONYMOUS"]) val actorType: String,
    @get:JsonProperty("actor_id") @get:Schema(description = "행위자 사용자 번호", nullable = true) val actorId: Long?,
    @get:JsonProperty("actor_login") @get:Schema(description = "행위자 로그인 ID. 로그인 실패는 시도한 ID이다", nullable = true) val actorLogin: String?,
    @get:Schema(description = "`범주.조치` 형식의 조치 이름", example = "project.member_added") val action: String,
    @get:Schema(description = "결과", allowableValues = ["SUCCESS", "FAILURE", "DENIED"]) val outcome: String,
    @get:JsonProperty("target_type") @get:Schema(description = "대상 종류", nullable = true) val targetType: String?,
    @get:JsonProperty("target_id") @get:Schema(description = "대상 식별자", nullable = true) val targetId: String?,
    @get:JsonProperty("target_label") @get:Schema(description = "사람이 읽을 대상 이름", nullable = true) val targetLabel: String?,
    @get:JsonProperty("organization_id") @get:Schema(description = "소속 조직 번호", nullable = true) val organizationId: Long?,
    @get:JsonProperty("project_id") @get:Schema(description = "소속 프로젝트 번호", nullable = true) val projectId: Long?,
    @get:Schema(description = "요청의 원격 주소", nullable = true) val ip: String?,
    @get:JsonProperty("user_agent") @get:Schema(description = "요청의 User-Agent", nullable = true) val userAgent: String?,
    @get:JsonProperty("request_id") @get:Schema(description = "요청 번호. 같은 요청에서 남긴 기록이 함께 쓴다", nullable = true) val requestId: String?,
    @get:JsonProperty("token_id") @get:Schema(description = "API 토큰으로 한 일이면 그 토큰 번호", nullable = true) val tokenId: Long?,
    @get:JsonProperty("token_scopes") @get:Schema(description = "그 토큰의 범위", nullable = true) val tokenScopes: String?,
    @get:Schema(description = "상세(JSON 글). 비밀 값은 가려져 있다", nullable = true) val details: String?,
    @get:JsonProperty("prev_hash") @get:Schema(description = "이전 기록의 해시") val prevHash: String,
    @get:Schema(description = "이 기록의 해시(SHA-256 16진수 64자)") val hash: String,
    @get:JsonProperty("content_hash") @get:Schema(description = "내용의 해시. 해시는 이전 해시와 이 값으로 만든다. 옛 기록은 `null`", nullable = true) val contentHash: String?,
    @get:JsonProperty("redacted_at") @get:Schema(description = "보존 기간이 지나 개인정보 필드를 비운 시각. 비우지 않았으면 `null`", nullable = true) val redactedAt: String?
)

@Schema(description = "감사 로그 조회 응답")
class AuditEventList(
    @get:Schema(description = "조건에 맞는 전체 건수") val total: Long,
    @get:Schema(description = "쪽 번호(1부터)") val page: Int,
    @get:Schema(description = "한 쪽의 건수") val size: Int,
    @get:Schema(description = "최신순") val events: List<AuditEventNode>
)

@Schema(description = "감사 로그 해시 사슬 검증 결과")
class AuditVerifyResult(
    @get:Schema(description = "사슬이 온전한지") val ok: Boolean,
    @get:Schema(description = "검증한 기록 수") val checked: Long,
    @get:Schema(description = "처음 어긋난 기록 번호", nullable = true) val brokenId: Long?,
    @get:Schema(description = "어긋난 이유", allowableValues = ["content", "hash", "prev_hash", "head"], nullable = true) val reason: String?
)

@Schema(description = "외부 스트리밍 대상 하나의 상태")
class AuditStreamState(
    @get:Schema(description = "대상 이름", allowableValues = ["webhook", "syslog"]) val name: String,
    @get:Schema(description = "마지막으로 보낸 기록 번호") val lastId: Long,
    @get:Schema(description = "아직 보내지 않은 기록 수") val backlog: Long,
    @get:Schema(description = "마지막으로 보낸 시각(ISO 8601)", nullable = true) val lastSentAt: String?,
    @get:Schema(description = "마지막 실패의 메시지. 최근에 성공했으면 `null`", nullable = true) val lastError: String?,
    @get:Schema(description = "마지막 실패 시각(ISO 8601)", nullable = true) val lastErrorAt: String?,
    @get:Schema(description = "연속 실패 횟수") val failures: Int
)

@Schema(description = "외부 스트리밍 상태 목록(설정된 대상만)")
class AuditStreamList(@get:Schema(description = "대상별 상태") val streams: List<AuditStreamState>)
