package com.github.yonaprojects.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// yona TitleHead.findByProject()/findByHeadKeyword() 대응.
@Repository
interface TitleHeadRepository : JpaRepository<TitleHead, Long> {
    fun findByProjectIdAndHeadKeywordContainingIgnoreCase(projectId: Long, query: String): List<TitleHead>
    fun findByProjectIdAndHeadKeyword(projectId: Long, headKeyword: String): TitleHead?
}
