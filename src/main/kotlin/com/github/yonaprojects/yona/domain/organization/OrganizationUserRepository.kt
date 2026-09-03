package com.github.yonaprojects.yona.domain.organization

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OrganizationUserRepository : JpaRepository<OrganizationUser, Long> {
    fun findByOrganizationId(organizationId: Long): List<OrganizationUser>
    fun findByOrganizationIdAndUserId(organizationId: Long, userId: Long): Optional<OrganizationUser>
    fun existsByOrganizationIdAndUserId(organizationId: Long, userId: Long): Boolean
    fun deleteByOrganizationIdAndUserId(organizationId: Long, userId: Long)
    fun countByOrganizationIdAndRoleId(organizationId: Long, roleId: Long): Long
    fun findByOrganizationIdAndRoleId(organizationId: Long, roleId: Long): List<OrganizationUser>
    fun findByUserIdAndRoleId(userId: Long, roleId: Long): List<OrganizationUser>
    fun findByUserId(userId: Long): List<OrganizationUser>
}
