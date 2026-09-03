package com.github.yonaprojects.yona.domain.milestone

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MilestoneRepository : JpaRepository<Milestone, Long> {
    fun findByProject(project: Project): List<Milestone>
    fun findByProject(project: Project, sort: Sort): List<Milestone>
    fun countByProject(project: Project): Long
    fun findByProjectAndState(project: Project, state: State): List<Milestone>
    fun findByProjectAndState(project: Project, state: State, sort: Sort): List<Milestone>
    fun findByProjectAndTitle(project: Project, title: String): Milestone?

    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueRepository.searchIssues() 주석 참고 (Postgres
    // Hibernate 7.2.x LIKE 2개 이상 버그 회피 — title/contents 2개 컬럼이라 1개로 인수분해 불가).
    @Query(
        value = "SELECT * FROM milestone WHERE project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM milestone WHERE project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchMilestonesQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Milestone>

    fun searchMilestones(projectIds: List<Long>, keyword: String, pageable: Pageable): Page<Milestone> =
        searchMilestonesQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM milestone WHERE project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchMilestonesQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    fun countSearchMilestones(projectIds: List<Long>, keyword: String): Int =
        countSearchMilestonesQuery(projectIds.ifEmpty { listOf(-1L) }, keyword)

    @Query(
        value = "SELECT * FROM milestone WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM milestone WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchMilestonesInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<Milestone>

    fun searchMilestonesInProject(project: Project, keyword: String, pageable: Pageable): Page<Milestone> =
        searchMilestonesInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM milestone WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(contents) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchMilestonesInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
