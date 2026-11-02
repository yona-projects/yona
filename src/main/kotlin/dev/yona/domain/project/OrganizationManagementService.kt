package dev.yona.domain.project

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.misc.FavoriteOrganizationRepository
import dev.yona.domain.notification.OrganizationMemberRequestEvent
import dev.yona.domain.user.Role
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

/**
 * 조직 설정과 구성원 관리, 삭제이다. v1.6 `OrganizationApp`의 `addMember`, `editMember`, `deleteMember`, `leave`,
 * `updateOrganizationInfo`, `deleteOrganization`과 `Organization.updateWith`, `cleanEnrolledUsers`에 해당한다.
 *
 * 거부 이유는 사용자에게 보여 줄 메시지 키(`String`)로 돌려주고, 성공이면 `null`이다. v1.6가 정하는 검사 순서를 그대로 따른다.
 */
@Service
@Transactional
class OrganizationManagementService(
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val projects: ProjectRepository,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val accessControl: AccessControl,
    private val attachments: AttachmentService,
    private val favoriteOrganizations: FavoriteOrganizationRepository,
    private val repositories: RepositoryService,
    private val events: ApplicationEventPublisher,
    private val sessionUsers: dev.yona.security.SessionUserCache,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val oauthApps: dev.yona.domain.oauth2server.OAuthClientService
) {

    @PersistenceContext
    private lateinit var em: EntityManager

    /** 조직 구성원으로 고를 수 있는 역할이다(조직 관리자, 조직 멤버) (v1.6 `Role.findOrganizationRoles`). */
    @Transactional(readOnly = true)
    fun organizationRoles(): List<Role> = roles.findAllById(listOf(RoleType.ORG_ADMIN.id, RoleType.ORG_MEMBER.id)).sortedBy { it.id }

    /** 조직 구성원 행이다. 입력 순서(번호 순)이다. */
    @Transactional(readOnly = true)
    fun members(organization: Organization): List<OrganizationUser> = organizationUsers.findMembers(requireNotNull(organization.id))

    /** 조직의 가입 신청자이다. */
    @Transactional(readOnly = true)
    fun enrolledUsers(organization: Organization): List<User> = users.findEnrolledUsersOfOrganization(requireNotNull(organization.id))

    // --- 구성원 추가 ---

    /**
     * [loginId]의 사용자를 일반 구성원으로 추가한다. 검사 순서: 사용자 없음, 게스트 사용자, 조직 없음, 권한(조직 관리자), 이미 구성원.
     * 추가하면 이미 구성원인 가입 신청자의 신청을 정리하고 승인 이벤트를 알린다.
     *
     * @return 거부 이유의 메시지 키. 성공이면 `null`
     */
    fun addMember(organization: Organization?, actor: User?, loginId: String?): String? {
        val target = loginId?.let { users.findByLoginId(it) } ?: return "organization.member.unknownUser"
        if (target.isGuest == true) return "error.forbidden.to.guest.user"
        if (organization == null) return "organization.member.unknownOrganization"
        if (!accessControl.isAllowed(actor, organization.asResource(), Operation.UPDATE)) return "organization.member.needManagerRole"
        val orgId = requireNotNull(organization.id)
        if (organizationUsers.existsByOrganizationIdAndUserId(orgId, requireNotNull(target.id))) return "organization.member.alreadyMember"

        assignRole(orgId, target, roles.getReferenceById(RoleType.ORG_MEMBER.id))
        cleanEnrolledUsers(organization, actor)
        events.publishEvent(OrganizationMemberRequestEvent(organization, target, RequestState.ACCEPT, actor))
        audit.record(orgMemberEvent("org.member_added", organization, target, mapOf("role" to RoleType.ORG_MEMBER.name)))
        return null
    }

    /** 이미 구성원인데 가입 신청이 남은 사용자의 신청을 정리하고 승인 이벤트를 알린다 (v1.6 `Organization.cleanEnrolledUsers`). */
    fun cleanEnrolledUsers(organization: Organization, actor: User?) {
        val orgId = requireNotNull(organization.id)
        val memberIds = organizationUsers.findMembers(orgId).mapNotNull { it.user?.id }.toSet()
        users.findEnrolledUsersOfOrganization(orgId).filter { it.id in memberIds }.forEach { user ->
            user.enrolledOrganizations.removeIf { it.id == orgId }
            users.save(user)
            // v1.6 User.cancelEnroll(Organization): 새로 읽은 사용자 인스턴스를 서버 캐시에 넣는다
            sessionUsers.remember(requireNotNull(user.id))
            events.publishEvent(OrganizationMemberRequestEvent(organization, user, RequestState.ACCEPT, actor))
        }
    }

    // --- 역할 변경, 삭제, 탈퇴 ---

    /**
     * 구성원의 역할을 바꾼다. 검사 순서: 역할 번호, 구성원 여부, 권한, 마지막 관리자.
     * 숫자가 아닌 값만 알 수 없는 역할이다. 없는 역할 번호는 아무것도 바꾸지 않고 성공으로 답한다(v1.6, R-120).
     *
     * @return 거부 이유의 메시지 키. 성공이면 `null`
     */
    fun editMember(organization: Organization, actor: User?, userId: Long, roleIdParam: String?): String? {
        // 숫자가 아닌 값은 폼 바인딩 오류(unknownRole)이고, 값이 없으면 오류 없이 번호가 `null`이다. 번호가 `null`이면 마지막 검사나 저장에서 널 참조로 500이다
        val roleId: Long? = if (roleIdParam.isNullOrBlank()) null else roleIdParam.toLongOrNull() ?: return "organization.member.unknownRole"
        val orgId = requireNotNull(organization.id)
        if (!organizationUsers.existsByOrganizationIdAndUserId(orgId, userId)) return "organization.member.isNotAMember"
        if (!accessControl.isAllowed(actor, organization.asResource(), Operation.UPDATE)) return "organization.member.needManagerRole"
        if (isLastAdmin(orgId, userId)) {
            if (roleId == null) throw NullPointerException("role id")
            if (roleId == RoleType.ORG_MEMBER.id) return "organization.member.atLeastOneAdmin"
        }
        if (roleId == null) throw NullPointerException("role id")
        // v1.6 `OrganizationUser.assignRole`은 없는 역할 번호이면 아무것도 바꾸지 않고 넘어간다(그래도 204). 존재하는 역할이면 종류를 가리지 않고 받는다
        val before = organizationUsers.findFirstByOrganizationIdAndUserId(orgId, userId)?.role?.id
        roles.findById(roleId).orElse(null)?.let {
            assignRole(orgId, users.getReferenceById(userId), it)
            if (before != it.id) {
                val target = users.getReferenceById(userId)
                audit.record(orgMemberEvent("org.member_role_changed", organization, target, mapOf("from" to orgRoleName(before), "to" to orgRoleName(it.id))))
            }
        }
        return null
    }

    /**
     * 구성원을 내보낸다(본인 탈퇴 포함). 검사 순서: 구성원 여부, 권한(본인이면 통과), 마지막 관리자.
     *
     * @return 거부 이유의 메시지 키. 성공이면 `null`
     */
    fun removeMember(organization: Organization, actor: User?, userId: Long): String? {
        val orgId = requireNotNull(organization.id)
        if (!organizationUsers.existsByOrganizationIdAndUserId(orgId, userId)) return "organization.member.isNotAMember"
        if (!accessControl.isAllowed(actor, organization.asResource(), Operation.UPDATE) && actor?.id != userId) {
            return "organization.member.needManagerRole"
        }
        if (isLastAdmin(orgId, userId)) return "organization.member.atLeastOneAdmin"

        organizationUsers.findFirstByOrganizationIdAndUserId(orgId, userId)?.let {
            organizationUsers.delete(it)
            audit.record(orgMemberEvent("org.member_removed", organization, users.getReferenceById(userId), mapOf("role" to orgRoleName(it.role?.id), "self" to (actor?.id == userId))))
        }
        return null
    }

    /**
     * 본인이 조직에서 나간다. 조직을 고칠 수 없는 사용자이고 관리자가 한 명뿐이면 거부한다.
     * 조건이 거꾸로 쓰여 있어서 관리자는 한 명뿐이어도 나갈 수 있다([refactoring-backlog.md] R-19, v1.6 `validateForLeave`).
     *
     * @return 거부 이유의 메시지 키. 성공이면 `null`
     */
    fun leave(organization: Organization, actor: User): String? {
        val orgId = requireNotNull(organization.id)
        if (!accessControl.isAllowed(actor, organization.asResource(), Operation.LEAVE) &&
            organizationUsers.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.id) == 1L
        ) {
            return "organization.member.atLeastOneAdmin"
        }
        organizationUsers.findFirstByOrganizationIdAndUserId(orgId, requireNotNull(actor.id))?.let {
            organizationUsers.delete(it)
            audit.record(orgMemberEvent("org.member_removed", organization, actor, mapOf("role" to orgRoleName(it.role?.id), "self" to true)))
        }
        return null
    }

    // --- 설정 ---

    /** 이름을 바꿀 수 없는 이유이다. 바꾸지 않으면(대소문자까지 같으면) 검사하지 않는다. 사용자 로그인 ID나 다른 조직과 겹치면 중복이다. */
    @Transactional(readOnly = true)
    fun isDuplicateName(organization: Organization, newName: String): Boolean {
        if (organization.name == newName) return false
        return users.existsByLoginIdIgnoreCase(newName) || organizations.existsByNameIgnoreCase(newName)
    }

    /**
     * 이름과 설명을 저장한다. 이름이 바뀌면 조직의 모든 프로젝트 소유자를 바꾸고 Git 저장소 디렉터리도 옮긴다.
     * 로고를 올렸으면 이전 로고를 지우고 새로 저장한다. 즐겨찾기한 조직의 이름도 바꾼다.
     */
    fun update(organization: Organization, name: String, descr: String?, logoName: String?, logo: (() -> InputStream)?, uploader: String?) {
        val orgId = requireNotNull(organization.id)
        if (logoName != null && logo != null) {
            attachments.deleteAll(ResourceType.ORGANIZATION, orgId)
            attachments.store(logo(), logoName, ResourceType.ORGANIZATION, orgId, uploader)
        }

        if (organization.name != name) {
            projects.findByOrganizationId(orgId).forEach { project ->
                if (name != project.owner) {
                    if (!repositories.moveRepository(project, name, requireNotNull(project.name))) {
                        throw IllegalStateException("fail repository move to $name/${project.name}")
                    }
                }
                project.owner = name
                projects.save(project)
            }
        }
        val before = mapOf("name" to organization.name, "descr" to organization.descr)
        organization.name = name
        organization.descr = descr
        organizations.save(organization)
        val changes = linkedMapOf<String, Any?>().apply {
            putAll(dev.yona.domain.audit.AuditDetails.diff(before, mapOf("name" to name, "descr" to descr)))
            if (logoName != null && logo != null) put("logoChanged", true)
        }
        if (changes.isNotEmpty()) audit.record(dev.yona.domain.audit.auditOrganization("org.settings_changed", organization, changes))

        favoriteOrganizations.findByOrganizationId(orgId).forEach {
            it.organizationName = name
            favoriteOrganizations.save(it)
        }
    }

    // --- 삭제 ---

    /** 삭제 불가 이유이다. */
    enum class DeleteDenied(val messageKey: String) {
        NOT_ALLOWED("organization.member.needManagerRole"),
        HAS_PROJECTS("organization.delete.impossible.project.exist")
    }

    /** 조직을 지울 수 있는지 확인한다. 권한이 없거나 프로젝트가 남아 있으면 이유를 돌려준다. */
    @Transactional(readOnly = true)
    fun checkDeletable(organization: Organization, actor: User?): DeleteDenied? {
        if (!accessControl.isAllowed(actor, organization.asResource(), Operation.DELETE)) return DeleteDenied.NOT_ALLOWED
        if (projects.findByOrganizationId(requireNotNull(organization.id)).isNotEmpty()) return DeleteDenied.HAS_PROJECTS
        return null
    }

    /** 조직과 구성원 행, 가입 신청을 지운다. 즐겨찾기는 외래 키 규칙(`CASCADE`)으로 지워진다. */
    fun delete(organization: Organization) {
        val orgId = requireNotNull(organization.id)
        audit.record(dev.yona.domain.audit.auditOrganization("org.deleted", organization))
        // 추가 항목(X-39, D-78): 조직이 소유한 OAuth 앱과 그 앱의 인가와 승인도 지운다(토큰이 주인 없이 남지 않게 한다)
        oauthApps.deleteOwnedBy(dev.yona.domain.oauth2server.ClientOwner.of(organization))
        em.createNativeQuery("delete from user_enrolled_organization where organization_id = :id").setParameter("id", orgId).executeUpdate()
        em.createNativeQuery("delete from favorite_organization where organization_id = :id").setParameter("id", orgId).executeUpdate()
        em.createNativeQuery("delete from organization_user where organization_id = :id").setParameter("id", orgId).executeUpdate()
        em.createNativeQuery("delete from organization where id = :id").setParameter("id", orgId).executeUpdate()
        em.flush()
        em.clear()
    }

    private fun orgMemberEvent(action: String, organization: Organization, user: User, details: Map<String, Any?>) =
        dev.yona.domain.audit.auditOrganization(action, organization, details).copy(targetType = "user", targetId = user.id?.toString(), targetLabel = user.loginId)

    private fun orgRoleName(id: Long?): String? = id?.let { value -> RoleType.entries.firstOrNull { it.id == value }?.name ?: value.toString() }

    private fun isLastAdmin(orgId: Long, userId: Long): Boolean =
        organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(orgId, userId, RoleType.ORG_ADMIN.id) &&
            organizationUsers.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.id) == 1L

    private fun assignRole(orgId: Long, user: User, role: Role) {
        val existing = organizationUsers.findFirstByOrganizationIdAndUserId(orgId, requireNotNull(user.id))
        if (existing == null) {
            organizationUsers.save(OrganizationUser(user = user, organization = organizations.getReferenceById(orgId), role = role))
        } else {
            existing.role = role
            organizationUsers.save(existing)
        }
    }
}
