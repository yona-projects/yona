package com.github.yonaprojects.yona.domain.organization

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrganizationRepository : JpaRepository<Organization, Long> {
    fun findByName(name: String): Optional<Organization>
    fun findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(
        name: String,
        descr: String,
        pageable: Pageable
    ): Page<Organization>
}
