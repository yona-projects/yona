package dev.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository

/** 조직 구성원 저장소이다. */
interface OrganizationUserRepository : JpaRepository<OrganizationUser, Long> {

    /** 사용자가 조직에서 지정한 역할을 가졌는지 확인한다 (v1.6 `User.isMemberOf(org, roleType)`). */
    fun existsByOrganizationIdAndUserIdAndRoleId(organizationId: Long, userId: Long, roleId: Long): Boolean

    /** 사용자가 지정한 역할을 가진 조직 구성원 행을 조회한다 (v1.6 `OrganizationUser.findByAdmin`은 관리자 역할). */
    fun findByUserIdAndRoleId(userId: Long, roleId: Long): List<OrganizationUser>

    /** 사용자가 조직에 속해 있는지 확인한다. 역할은 보지 않는다 (v1.6 `OrganizationUser.exist`). */
    fun existsByOrganizationIdAndUserId(organizationId: Long, userId: Long): Boolean

    /** 사용자의 조직 소속 행을 조회한다 (v1.6 `OrganizationUser.findByOrganizationIdAndUserId`). */
    fun findFirstByOrganizationIdAndUserId(organizationId: Long, userId: Long): OrganizationUser?

    /** 조직의 모든 구성원 행을 사용자와 함께 조회한다. 입력 순서(번호 순)이다 (v1.6 `organization.users`). */
    @org.springframework.data.jpa.repository.Query("select ou from OrganizationUser ou join fetch ou.user join fetch ou.role where ou.organization.id = :organizationId order by ou.id")
    fun findMembers(organizationId: Long): List<OrganizationUser>

    /** 사용자가 속한 조직의 구성원 행을 조직과 역할과 함께 조회한다. 조직 번호 순이다(외부 REST의 내 조직 목록, X-30a). */
    @org.springframework.data.jpa.repository.Query("select ou from OrganizationUser ou join fetch ou.organization join fetch ou.role where ou.user.id = :userId order by ou.organization.id")
    fun findByUserId(userId: Long): List<OrganizationUser>

    /** 조직에서 지정한 역할을 가진 구성원 수이다. */
    fun countByOrganizationIdAndRoleId(organizationId: Long, roleId: Long): Long
}
