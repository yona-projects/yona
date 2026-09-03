package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IssueLabelRepository : JpaRepository<IssueLabel, Long> {
    fun findByProject(project: Project): List<IssueLabel>

    // yona IssueLabel.exists()(project.id+category+name 복합 유일성) 대응 (P1-54).
    fun findByProjectAndCategoryAndName(project: Project, category: IssueLabelCategory, name: String): IssueLabel?
    fun findByCategory(category: IssueLabelCategory): List<IssueLabel>

    @Modifying
    @Query(value = "delete from issue_issue_label where issue_label_id = :labelId", nativeQuery = true)
    fun deleteIssueMappings(@Param("labelId") labelId: Long)

    @Modifying
    @Query(value = "delete from posting_issue_label where issue_label_id = :labelId", nativeQuery = true)
    fun deletePostingMappings(@Param("labelId") labelId: Long)
}
