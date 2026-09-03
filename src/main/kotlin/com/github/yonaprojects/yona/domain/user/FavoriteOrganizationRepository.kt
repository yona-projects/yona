package com.github.yonaprojects.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FavoriteOrganizationRepository : JpaRepository<FavoriteOrganization, Long> {
    fun findByUserIdAndOrganizationId(userId: Long, organizationId: Long): Optional<FavoriteOrganization>
    fun findByUserId(userId: Long): List<FavoriteOrganization>
    fun findByOrganizationId(organizationId: Long): List<FavoriteOrganization>
}
