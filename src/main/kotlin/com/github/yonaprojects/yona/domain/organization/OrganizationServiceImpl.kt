package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.user.LoginIdFormatValidator
import com.github.yonaprojects.yona.domain.user.ReservedWordsValidator
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class OrganizationServiceImpl(
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    // yona FavoriteOrganization.java:38-46 updateFavoriteOrganization() 대응 (P2-19). [GL-models_FavoriteOrganization-007]
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository
) : OrganizationService {

    override fun findByName(name: String): Organization? {
        return organizationRepository.findByName(name).orElse(null)
    }

    @Transactional
    override fun createOrganization(organization: Organization): Organization {
        organization.created = Instant.now()
        return organizationRepository.save(organization)
    }

    override fun isNameExist(name: String): Boolean {
        return organizationRepository.findByName(name).isPresent
    }

    @Transactional
    override fun createOrganization(name: String, descr: String?, creatorId: Long): Organization {
        // yona models/Organization.java:42 @Constraints.Pattern(User.LOGIN_ID_PATTERN) 대응 (P1-108).
        if (!LoginIdFormatValidator.isValid(name)) {
            throw IllegalArgumentException("Organization name format is invalid: $name")
        }
        if (isNameExist(name)) {
            throw IllegalArgumentException("Organization name already exists: $name")
        }
        if (userRepository.findByLoginId(name).isPresent) {
            throw IllegalArgumentException("User with this name already exists: $name")
        }
        // yona utils/ReservedWordsValidator.java 대응 (P2-01).
        if (ReservedWordsValidator.isReserved(name)) {
            throw IllegalArgumentException("Organization name is a reserved word: $name")
        }

        val creator = userRepository.findById(creatorId)
            .orElseThrow { IllegalArgumentException("User with ID $creatorId not found") }

        val organization = Organization(
            name = name,
            descr = descr,
            created = Instant.now()
        )
        val savedOrg = organizationRepository.save(organization)

        val adminRole = roleRepository.findById(RoleType.ORG_ADMIN.roleType)
            .orElseThrow { IllegalStateException("ORG_ADMIN role not found") }

        val orgUser = OrganizationUser(
            organization = savedOrg,
            user = creator,
            role = adminRole
        )
        organizationUserRepository.save(orgUser)

        return savedOrg
    }

    @Transactional
    override fun updateOrganizationSettings(orgId: Long, name: String, descr: String?, updaterId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        if (organization.name != name) {
            if (isNameExist(name) || userRepository.findByLoginId(name).isPresent) {
                throw IllegalArgumentException("Target name already exists: $name")
            }
            organization.name = name
        }
        organization.descr = descr
        organizationRepository.save(organization)

    // yona FavoriteOrganization.java:38-46 updateFavoriteOrganization() 대응 (P2-19) — 조직명이 [GL-models_FavoriteOrganization-007]
        // 바뀌면 즐겨찾기 목록에 저장된 비정규화 organizationName도 함께 갱신한다(그대로 두면 즐겨찾기
        // 화면에 옛 조직명이 남는다).
        favoriteOrganizationRepository.findByOrganizationId(orgId).forEach {
            it.organizationName = organization.name
            favoriteOrganizationRepository.save(it)
        }
    }

    @Transactional
    override fun addOrganizationMember(orgId: Long, userLoginId: String, roleId: Long, updaterId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        val targetUser = userRepository.findByLoginId(userLoginId)
            .orElseThrow { IllegalArgumentException("User with login ID $userLoginId not found") }

        // yona OrganizationApp.validateForAddMember()의 게스트 계정 거부 대응 (P1-17)
        if (targetUser.isGuest) {
            throw IllegalArgumentException("게스트 계정은 조직 멤버로 추가할 수 없습니다.")
        }

        if (organizationUserRepository.existsByOrganizationIdAndUserId(orgId, targetUser.id!!)) {
            throw IllegalArgumentException("User is already a member of this organization")
        }

        val role = roleRepository.findById(roleId)
            .orElseThrow { IllegalArgumentException("Role with ID $roleId not found") }

        val orgUser = OrganizationUser(
            organization = organization,
            user = targetUser,
            role = role
        )
        organizationUserRepository.save(orgUser)

        // 만약 가입 대기자 목록에 해당 유저가 있었다면 제거
        if (targetUser.enrolledOrganizations.contains(organization)) {
            targetUser.cancelEnroll(organization)
            userRepository.save(targetUser)
        }

        // 가입 완료 알림 생성
        val notiEvent = NotificationEvent(
            title = "${organization.name} 조직에 멤버로 추가되었습니다.",
            senderId = updaterId,
            receivers = mutableSetOf(targetUser),
            created = Instant.now(),
            resourceType = ResourceType.ORGANIZATION,
            resourceId = orgId.toString(),
            eventType = EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT,
            newValue = "ACCEPT",
            oldValue = "REQUEST"
        )
        notificationEventRecorder.record(notiEvent)
    }

    @Transactional
    override fun removeOrganizationMember(orgId: Long, userId: Long, removerId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        val targetOrgUser = organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .orElseThrow { IllegalArgumentException("User is not a member of this organization") }

        // 마지막 관리자(ORG_ADMIN)인 경우 삭제 불가
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): role.id는 타입상 Long?이지만 Role은
        // 코드베이스 전역에서 RoleType 고정 ID로만 생성·영속화돼 실제로는 결코 null이 아니다.
        if (targetOrgUser.role.id == RoleType.ORG_ADMIN.roleType) {
            val adminCount = organizationUserRepository.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.roleType)
            if (adminCount <= 1) {
                throw IllegalArgumentException("At least one admin must remain in the organization")
            }
        }

        organizationUserRepository.deleteByOrganizationIdAndUserId(orgId, userId)
    }

    @Transactional
    override fun updateOrganizationMemberRole(orgId: Long, userId: Long, newRoleId: Long, updaterId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        val targetOrgUser = organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .orElseThrow { IllegalArgumentException("User is not a member of this organization") }

        val newRole = roleRepository.findById(newRoleId)
            .orElseThrow { IllegalArgumentException("Role with ID $newRoleId not found") }

        // 관리자 강등 시, 마지막 관리자(ORG_ADMIN)가 자신뿐인지 검사
        // 테스트 커버리지 도달 불가: role.id null 분기는 위 removeOrganizationMember와 동일한 이유로 불가.
        if (targetOrgUser.role.id == RoleType.ORG_ADMIN.roleType && newRoleId != RoleType.ORG_ADMIN.roleType) {
            val adminCount = organizationUserRepository.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.roleType)
            if (adminCount <= 1) {
                throw IllegalArgumentException("At least one admin must remain in the organization")
            }
        }

        targetOrgUser.role = newRole
        organizationUserRepository.save(targetOrgUser)
    }

    @Transactional
    override fun deleteOrganization(orgId: Long, deleterId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        // 하위 프로젝트가 존재하는 경우 삭제 불가
        if (organization.projects.isNotEmpty()) {
            throw IllegalArgumentException("Cannot delete organization: projects still exist")
        }

        organizationRepository.delete(organization)
    }

    @Transactional
    override fun enroll(orgName: String, userId: Long) {
        val organization = organizationRepository.findByName(orgName)
            .orElseThrow { IllegalArgumentException("Organization not found: $orgName") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        // 이미 멤버인지 검사
        if (organizationUserRepository.existsByOrganizationIdAndUserId(organization.id!!, user.id!!)) {
            throw IllegalArgumentException("User is already a member of this organization")
        }

        // yona EnrollOrganizationApp.java 대응, Project ProjectUserServiceImpl.enroll()의 User.enrolled()
        // 가드와 동일 유형(P1-122): 이미 대기 중인 가입 신청이 있으면 조용히 무시하고 중복 알림을
        // 발생시키지 않는다.
        if (user.enrolledOrganizations.any { it.id == organization.id }) {
            return
        }

        user.enroll(organization)
        userRepository.save(user)

        // 조직 관리자들 조회
        val admins = organizationUserRepository.findByOrganizationIdAndRoleId(organization.id!!, RoleType.ORG_ADMIN.roleType)
            .map { it.user }
            .toMutableSet()

        if (admins.isNotEmpty()) {
            val notiEvent = NotificationEvent(
                title = "${user.name}(@${user.loginId})님이 ${organization.name} 조직에 가입 신청을 했습니다.",
                senderId = userId,
                receivers = admins,
                created = Instant.now(),
                resourceType = ResourceType.ORGANIZATION,
                resourceId = organization.id.toString(),
                eventType = EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST,
                newValue = "REQUEST",
                oldValue = "CANCEL"
            )
            notificationEventRecorder.record(notiEvent)
        }
    }

    @Transactional
    override fun cancelEnroll(orgName: String, userId: Long) {
        val organization = organizationRepository.findByName(orgName)
            .orElseThrow { IllegalArgumentException("Organization not found: $orgName") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        // yona EnrollOrganizationApp.java:101-104 validateForCancelEnroll()의 OrganizationUser.isGuest()
        // 가드 대응 (P1-123). 이미 조직의 정식 멤버(ORG_ADMIN/ORG_MEMBER)라면 가입 신청 취소 자체가 [GL-controllers_EnrollOrganizationApp-005]
        // 성립하지 않는다.
        if (organizationUserRepository.existsByOrganizationIdAndUserId(organization.id!!, user.id!!)) {
            throw IllegalArgumentException("User is already a member of this organization")
        }

        // yona EnrollOrganizationApp.java:82 User.enrolled(organization) 가드 대응. 실제 대기 중인
        // 가입 신청이 없으면 취소할 것도, 알릴 것도 없다(조용히 무시).
        if (user.enrolledOrganizations.none { it.id == organization.id }) {
            return
        }

        user.cancelEnroll(organization)
        userRepository.save(user)

        // 조직 관리자들 조회
        val admins = organizationUserRepository.findByOrganizationIdAndRoleId(organization.id!!, RoleType.ORG_ADMIN.roleType)
            .map { it.user }
            .toMutableSet()

        if (admins.isNotEmpty()) {
            val notiEvent = NotificationEvent(
                title = "${user.name}(@${user.loginId})님이 ${organization.name} 조직 가입 신청을 취소했습니다.",
                senderId = userId,
                receivers = admins,
                created = Instant.now(),
                resourceType = ResourceType.ORGANIZATION,
                resourceId = organization.id.toString(),
                eventType = EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST,
                newValue = "CANCEL",
                oldValue = "REQUEST"
            )
            notificationEventRecorder.record(notiEvent)
        }
    }

    @Transactional
    override fun leaveOrganization(orgId: Long, userId: Long) {
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { IllegalArgumentException("Organization with ID $orgId not found") }

        val isAdmin = organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .map { it.role.id == RoleType.ORG_ADMIN.roleType }
            .orElse(false)

        // yona OrganizationApp.java:288-309 validateForLeave() 그대로 이식 — 관리자는 이 가드를
        // 완전히 우회하고(마지막 관리자라도 탈퇴 가능), 관리자가 아니면 "조직 전체 관리자 수 == 1"일
        // 때 탈퇴 요청자와 무관하게 거부한다.
        if (!isAdmin) {
            val adminCount = organizationUserRepository.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.roleType)
            if (adminCount == 1L) {
                throw IllegalStateException("organization.member.atLeastOneAdmin")
            }
        }

        organizationUserRepository.deleteByOrganizationIdAndUserId(orgId, userId)
    }
}
