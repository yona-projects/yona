package dev.yona.domain.oauth2server

import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 조직의 OAuth 앱 정책이다(추가 개발 항목 X-39, `decisions.md` D-78). 조직 하나에 행이 하나이고 행이 없으면 제한 없음이다.
 *
 * @property organizationId 조직 번호(기본 키)
 * @property restricted 참이면 조직이 승인하지 않은 앱의 OAuth 토큰은 그 조직의 비공개 자원에 닿지 못한다
 */
@Entity
@Table(name = "organization_oauth_policy")
class OrganizationOAuthPolicy(
    @Id @Column(name = "organization_id")
    var organizationId: Long? = null,
    var restricted: Boolean? = false,
    @Column(name = "updated_date")
    var updatedDate: Date? = null,
    @Column(name = "updated_by")
    var updatedBy: Long? = null
)

/** [OrganizationOAuthPolicy] 저장소이다. */
interface OrganizationOAuthPolicyRepository : JpaRepository<OrganizationOAuthPolicy, Long> {

    /** 제한을 켠 조직의 번호들이다. */
    @Query("select p.organizationId from OrganizationOAuthPolicy p where p.restricted = true")
    fun restrictedOrganizationIds(): List<Long>
}

/** 조직이 앱에 내린 결정이다. */
enum class ApprovalState { APPROVED, DENIED }

/**
 * 조직이 앱 하나에 내린 결정이다(승인 또는 거부). 결정이 없으면 대기이다. 조직과 앱의 짝마다 한 행이다.
 *
 * @property state [ApprovalState]의 이름
 * @property decidedBy 결정한 사용자 번호
 */
@Entity
@Table(
    name = "organization_oauth_approval",
    indexes = [Index(name = "ix_org_oauth_approval_client", columnList = "client_row_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_org_oauth_approval", columnNames = ["organization_id", "client_row_id"])]
)
class OrganizationOAuthApproval(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "organization_id")
    var organizationId: Long? = null,
    @Column(name = "client_row_id")
    var clientRowId: Long? = null,
    @Column(length = 10)
    var state: String? = null,
    @Column(name = "decided_by")
    var decidedBy: Long? = null,
    @Column(name = "decided_date")
    var decidedDate: Date? = null
)

/** [OrganizationOAuthApproval] 저장소이다. */
interface OrganizationOAuthApprovalRepository : JpaRepository<OrganizationOAuthApproval, Long> {

    fun findByOrganizationIdAndClientRowId(organizationId: Long, clientRowId: Long): OrganizationOAuthApproval?

    fun findByOrganizationId(organizationId: Long): List<OrganizationOAuthApproval>

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from OrganizationOAuthApproval a where a.clientRowId = :clientRowId")
    fun deleteByClient(@Param("clientRowId") clientRowId: Long): Int
}

/** 정책 화면의 앱 한 줄이다. [state]가 `null`이면 대기, [members]는 그 앱을 승인한 조직 구성원 수이다. */
data class PolicyApp(val client: OAuthClient, val state: ApprovalState?, val members: Int)

/**
 * 조직의 OAuth 앱 정책을 정하고 판정한다(X-39, D-78). 판정은 요청마다 불리므로 조회를 가볍게 유지한다: 정책 행이 없거나 제한이 꺼져 있으면 곧바로 허용이다.
 * 그 조직이 소유한 앱은 승인 결정 없이 항상 허용이다.
 */
@Service
class OAuthOrgPolicyService(
    private val policies: OrganizationOAuthPolicyRepository,
    private val approvals: OrganizationOAuthApprovalRepository,
    private val clients: OAuthClientRepository,
    private val consents: OAuthConsentRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val audit: AuditRecorder
) {

    /** 조직이 제한을 켰으면 `true`이다. */
    @Transactional(readOnly = true)
    fun restricted(organizationId: Long): Boolean = policies.findById(organizationId).orElse(null)?.restricted == true

    /** 제한을 켠 조직의 번호들이다. */
    @Transactional(readOnly = true)
    fun restrictedOrganizationIds(): Set<Long> = policies.restrictedOrganizationIds().toSet()

    /** 앱 [clientRowId]가 조직 [organizationId]의 자원을 쓸 수 있으면 `true`이다. */
    @Transactional(readOnly = true)
    fun allows(organizationId: Long, clientRowId: Long): Boolean = !denied(organizationId, clientRowId)

    /** [clientRowId] 앱이 막히는 조직의 번호들이다([organizationIds] 가운데). */
    @Transactional(readOnly = true)
    fun deniedOrganizations(clientRowId: Long, organizationIds: Collection<Long>): Set<Long> = organizationIds.filter { denied(it, clientRowId) }.toSet()

    private fun denied(organizationId: Long, clientRowId: Long): Boolean {
        if (!restricted(organizationId)) return false
        val client = clients.findById(clientRowId).orElse(null) ?: return true
        if (client.ownerType == OAuthClient.ORGANIZATION && client.ownerId == organizationId) return false
        return approvals.findByOrganizationIdAndClientRowId(organizationId, clientRowId)?.state != ApprovalState.APPROVED.name
    }

    /** [user]가 속한 조직 가운데 제한을 켰고 [clientRowId] 앱을 승인하지 않은 조직의 이름이다(승인 화면의 경고, D-78 3번). */
    @Transactional(readOnly = true)
    fun restrictedUnapprovedOrganizationNames(user: User, clientRowId: Long): List<String> {
        val restricted = restrictedOrganizationIds()
        if (restricted.isEmpty()) return emptyList()
        val mine = organizationUsers.findByUserId(requireNotNull(user.id)).mapNotNull { it.organization }.filter { it.id in restricted }
        return mine.filter { denied(requireNotNull(it.id), clientRowId) }.mapNotNull { it.name }.sortedBy { it.lowercase() }
    }

    /** 앱의 공개 클라이언트 ID이다(감사 기록용). 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun clientIdOf(clientRowId: Long): String? = clients.findById(clientRowId).orElse(null)?.clientId

    /** 제한을 켜거나 끈다. 바뀐 값이 같으면 아무것도 하지 않는다. */
    @Transactional
    fun setRestricted(organization: Organization, restricted: Boolean, actor: User, now: Date = Date()) {
        val id = requireNotNull(organization.id)
        val row = policies.findById(id).orElse(null)
        if ((row?.restricted == true) == restricted) return
        policies.save((row ?: OrganizationOAuthPolicy(organizationId = id)).also { it.restricted = restricted; it.updatedDate = now; it.updatedBy = actor.id })
        audit.record(AuditEvent("oauth.org_policy_changed", targetType = "organization", targetId = id.toString(), targetLabel = organization.name, organizationId = id, details = mapOf("restricted" to restricted), actor = actorOf(actor)))
    }

    /**
     * 조직 구성원이 승인한 적이 있는 앱이다(정책 화면의 목록). 그 조직이 소유한 앱은 뺀다. 이름과 번호 순으로 돌려준다.
     */
    @Transactional(readOnly = true)
    fun candidates(organization: Organization): List<PolicyApp> {
        val id = requireNotNull(organization.id)
        val memberIds = organizationUsers.findMembers(id).mapNotNull { it.user?.id }.toSet()
        if (memberIds.isEmpty()) return emptyList()
        val decided = approvals.findByOrganizationId(id).associate { it.clientRowId to it.state }
        return consents.findByUserIdIn(memberIds).groupBy { it.clientRowId }.mapNotNull { (clientRowId, rows) ->
            val client = clientRowId?.let { clients.findById(it).orElse(null) } ?: return@mapNotNull null
            if (client.ownerType == OAuthClient.ORGANIZATION && client.ownerId == id) return@mapNotNull null
            PolicyApp(client, decided[clientRowId]?.let { runCatching { ApprovalState.valueOf(it) }.getOrNull() }, rows.mapNotNull { it.userId }.toSet().size)
        }.sortedWith(compareBy({ it.client.name?.lowercase() }, { it.client.id }))
    }

    /**
     * 앱 [clientRowId]에 결정을 내린다. [state]가 `null`이면 결정을 지워 대기로 돌린다. 후보(조직 구성원이 승인한 적이 있는 앱)가 아니면 `false`이다.
     */
    @Transactional
    fun decide(organization: Organization, clientRowId: Long, state: ApprovalState?, actor: User, now: Date = Date()): Boolean {
        val id = requireNotNull(organization.id)
        val candidate = candidates(organization).firstOrNull { it.client.id == clientRowId } ?: return false
        val row = approvals.findByOrganizationIdAndClientRowId(id, clientRowId)
        if (state == null) row?.let { approvals.delete(it) }
        else approvals.save((row ?: OrganizationOAuthApproval(organizationId = id, clientRowId = clientRowId)).also { it.state = state.name; it.decidedBy = actor.id; it.decidedDate = now })
        audit.record(
            AuditEvent(
                "oauth.org_app_decided", targetType = "oauth_client", targetId = clientRowId.toString(), targetLabel = candidate.client.clientId, organizationId = id,
                details = mapOf("state" to (state?.name ?: "PENDING"), "name" to candidate.client.name), actor = actorOf(actor)
            )
        )
        return true
    }

    private fun actorOf(user: User) = dev.yona.domain.audit.AuditActor(dev.yona.domain.audit.AuditActorType.USER, user.id, user.loginId)
}
