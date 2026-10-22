package dev.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository

/** 조직 구성원 저장소이다. */
interface OrganizationUserRepository : JpaRepository<OrganizationUser, Long> {

    /** 사용자가 조직에서 지정한 역할을 가졌는지 확인한다 (v1.6 `User.isMemberOf(org, roleType)`). */
    fun existsByOrganizationIdAndUserIdAndRoleId(organizationId: Long, userId: Long, roleId: Long): Boolean
}
