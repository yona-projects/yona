package com.github.yonaprojects.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProjectUserRepository : JpaRepository<ProjectUser, Long> {
    fun existsByProjectIdAndUserLoginId(projectId: Long, loginId: String): Boolean
    fun findByProjectIdAndUserId(projectId: Long, userId: Long): Optional<ProjectUser>
    fun findByProjectId(projectId: Long): List<ProjectUser>
    fun findByUserId(userId: Long): List<ProjectUser>
    fun deleteByProjectIdAndUserId(projectId: Long, userId: Long)

    fun existsByProjectIdAndUserId(projectId: Long, userId: Long): Boolean
}
