package dev.yona.domain.audit

import dev.yona.domain.LONG_TEXT
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/** 감사 기록의 행위자 유형이다. */
enum class AuditActorType { USER, TOKEN, SYSTEM, ANONYMOUS, DEPLOY_KEY }

/** 감사 기록의 결과이다. `DENIED`는 권한이나 범위가 부족해 거부된 것이다. */
enum class AuditOutcome { SUCCESS, FAILURE, DENIED }

/**
 * 감사 기록 한 건이다(추가 개발 항목 X-9, `decisions.md` D-62). 추가 전용이라 한 번 쓰면 고치거나 지우지 않는다(보존 기간이 지난 기록을 옮기는 [AuditRetention] 예외).
 * 기록마다 이전 기록의 해시를 포함한 SHA-256 해시를 가져 사슬을 이룬다([AuditHash]).
 *
 * 번호는 DB가 매기지 않고 [AuditChainHead]를 잠근 채로 이어 붙인다(그래서 사슬의 순서가 커밋 순서이다). 직접 번호를 매기므로 저장은 `EntityManager.persist`로 한다. 시각은 DB 시각 정밀도 차이가 해시를 깨지 않게 에폭 밀리초(`created_at`)로 둔다.
 *
 * @property actorType 행위자 유형
 * @property actorId 행위자 사용자 번호(시스템이나 익명이면 `null`)
 * @property actorLogin 행위자 로그인 ID. 로그인 실패는 시도한 ID이다
 * @property action `범주.조치` 형식의 조치 이름(예: `project.member_added`)
 * @property targetType 대상 종류(`project`, `organization`, `user`, `token` 등)
 * @property targetId 대상 식별자(번호나 이름)
 * @property targetLabel 사람이 읽을 대상 이름(`소유자/프로젝트`)
 * @property organizationId 소속 조직 번호(조직 소유자의 조회 범위를 정한다)
 * @property projectId 소속 프로젝트 번호
 * @property ip 요청의 원격 주소
 * @property userAgent 요청의 `User-Agent`(500자까지)
 * @property requestId 요청 번호. 같은 요청에서 남긴 기록이 함께 쓴다
 * @property tokenId 개인용 API 토큰으로 한 일이면 그 토큰 번호
 * @property tokenScopes 그 토큰의 범위(쉼표로 이은 값)
 * @property details 상세(JSON 글). 비밀 값은 가린다([AuditDetails])
 * @property prevHash 이전 기록의 해시(첫 기록은 0 64개)
 * @property hash 이 기록의 해시. 내용 해시가 있는 기록(v2)은 `SHA-256(이전 해시 + 내용 해시)`이다([AuditHash])
 * @property contentHash 개인정보 필드를 비운 뒤에도 사슬이 이어지게 따로 둔 내용의 해시(`decisions.md` D-63). 처음 쓸 때 정한다. 옛(v1) 기록은 `null`이다
 * @property redactedAt 보존 기간(`yona.audit-git-retention-days`)이 지나 개인정보 필드를 비운 시각(에폭 밀리초). 비우지 않았으면 `null`이다
 */
@Entity
@Table(
    name = "audit_log",
    indexes = [
        Index(name = "ix_audit_log_created", columnList = "created_at"),
        Index(name = "ix_audit_log_actor", columnList = "actor_id"),
        Index(name = "ix_audit_log_action", columnList = "action"),
        Index(name = "ix_audit_log_project", columnList = "project_id"),
        Index(name = "ix_audit_log_org", columnList = "organization_id")
    ]
)
class AuditLog(
    @Id
    var id: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdMillis: Long = 0,
    @Column(name = "actor_type", nullable = false, length = 16)
    var actorType: AuditActorType = AuditActorType.SYSTEM,
    @Column(name = "actor_id")
    var actorId: Long? = null,
    @Column(name = "actor_login", length = 255)
    var actorLogin: String? = null,
    @Column(nullable = false, length = 100)
    var action: String = "",
    @Column(nullable = false, length = 16)
    var outcome: AuditOutcome = AuditOutcome.SUCCESS,
    @Column(name = "target_type", length = 50)
    var targetType: String? = null,
    @Column(name = "target_id", length = 255)
    var targetId: String? = null,
    @Column(name = "target_label", length = 255)
    var targetLabel: String? = null,
    @Column(name = "organization_id")
    var organizationId: Long? = null,
    @Column(name = "project_id")
    var projectId: Long? = null,
    @Column(length = 64)
    var ip: String? = null,
    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null,
    @Column(name = "request_id", length = 64)
    var requestId: String? = null,
    @Column(name = "token_id")
    var tokenId: Long? = null,
    @Column(name = "token_scopes", length = 255)
    var tokenScopes: String? = null,
    @Column(length = LONG_TEXT)
    var details: String? = null,
    @Column(name = "prev_hash", nullable = false, length = 64)
    var prevHash: String = AuditHash.GENESIS,
    @Column(nullable = false, length = 64)
    var hash: String = "",
    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,
    @Column(name = "redacted_at")
    var redactedAt: Long? = null
)

/**
 * 해시 사슬의 끝이다(한 행, 번호 1). 기록할 때 이 행을 비관적 잠금으로 잡아 사슬을 한 줄로 만들고, 마지막 기록의 번호와 해시를 둔다.
 *
 * @property lastId 마지막 기록 번호(기록이 없으면 보존으로 지운 마지막 번호, 아니면 0)
 * @property lastHash 마지막 기록의 해시
 * @property prunedThroughId 보존 기간이 지나 옮기고 지운 기록의 마지막 번호. 이 뒤부터 사슬을 검증한다
 * @property prunedHash 지운 마지막 기록의 해시. 남은 첫 기록의 이전 해시가 이 값이어야 한다(지운 적이 없으면 0 64개)
 */
@Entity
@Table(name = "audit_chain_head")
class AuditChainHead(
    @Id
    var id: Long = 1,
    @Column(name = "last_id", nullable = false)
    var lastId: Long = 0,
    @Column(name = "last_hash", nullable = false, length = 64)
    var lastHash: String = AuditHash.GENESIS,
    @Column(name = "pruned_through_id", nullable = false)
    var prunedThroughId: Long = 0,
    @Column(name = "pruned_hash", nullable = false, length = 64)
    var prunedHash: String = AuditHash.GENESIS
)

/** [AuditLog] 저장소이다. 조회 조건은 [AuditQueryService]가 명세로 만든다. */
interface AuditLogRepository : JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** [id]보다 큰 번호의 기록을 번호순으로 읽는다(사슬 검증과 보존 처리가 쪽으로 나눠 읽는다). */
    fun findByIdGreaterThanOrderByIdAsc(id: Long, pageable: Pageable): List<AuditLog>

    /** [millis]보다 오래된 기록을 번호순으로 읽는다. */
    fun findByCreatedMillisLessThanOrderByIdAsc(millis: Long, pageable: Pageable): List<AuditLog>

    /** 보존 기간이 지났지만 아직 비우지 않은 [prefix] 범주의 기록을 번호순으로 읽는다(비움 대상). */
    fun findByActionStartingWithAndCreatedMillisLessThanAndRedactedAtIsNullAndContentHashIsNotNullOrderByIdAsc(prefix: String, millis: Long, pageable: Pageable): List<AuditLog>
}

/** [AuditChainHead] 저장소이다. */
interface AuditChainHeadRepository : JpaRepository<AuditChainHead, Long> {

    /** 사슬의 끝을 잠그고 읽는다. 커밋이나 롤백까지 다른 기록자가 기다린다. 행이 없으면 `null`이다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.id = 1")
    fun lockHead(): AuditChainHead?
}
