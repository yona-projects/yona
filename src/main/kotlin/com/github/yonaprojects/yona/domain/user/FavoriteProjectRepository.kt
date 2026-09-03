package com.github.yonaprojects.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FavoriteProjectRepository : JpaRepository<FavoriteProject, Long> {
    fun findByUserIdAndProjectId(userId: Long, projectId: Long): Optional<FavoriteProject>
    fun findByUserId(userId: Long): List<FavoriteProject>
    fun findByProjectId(projectId: Long): List<FavoriteProject>
}
