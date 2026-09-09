package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IssueCommentRepository : JpaRepository<IssueComment, Long> {
    fun findByIssueIdOrderByCreatedDateAsc(issueId: Long): List<IssueComment>

    // 네이티브 쿼리를 쓰는 진짜 이유는 domain/support/Comment.kt의 contents 필드 주석 참고 —
    // Postgres + Hibernate 7.2.x에서 @Lob String 컬럼은 LIKE가 예외 없이 조용히 0건으로 실패했다
    // (원인은 @Lob 자체였고 지금은 제거함).
    //
    // issue_comment.project_id(Comment 기반 클래스의 denormalized 컬럼)를 직접 쓰지 않고 굳이
    // issue를 조인해 issue.project_id를 쓰는 이유: issue_comment.project_id는 실제로 값이 항상
    // 채워진다는 보장이 없다(이 컬럼을 채우는 코드 경로가 없어 null인 행이 존재함). 원래
    // JPQL(ic.issue.project.id)이 관계를 통해 항상 신뢰할 수 있게 프로젝트를 구했던 것과 동일하게,
    // issue 조인을 통해 구해야 한다. userId는 null일 수 있어 매치 불가능한 sentinel(-1)로 치환한다.
    @Query(
        value = "SELECT ic.* FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE LOWER(ic.contents) LIKE LOWER(:keyword) AND (i.project_id IN :projectIds OR ic.author_id = :userId)",
        countQuery = "SELECT COUNT(*) FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE LOWER(ic.contents) LIKE LOWER(:keyword) AND (i.project_id IN :projectIds OR ic.author_id = :userId)",
        nativeQuery = true
    )
    fun searchIssueCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long, pageable: Pageable): Page<IssueComment>

    fun searchIssueComments(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<IssueComment> =
        searchIssueCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId ?: -1L, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE LOWER(ic.contents) LIKE LOWER(:keyword) AND (i.project_id IN :projectIds OR ic.author_id = :userId)",
        nativeQuery = true
    )
    fun countSearchIssueCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long): Int

    fun countSearchIssueComments(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchIssueCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId ?: -1L)

    @Query(
        value = "SELECT ic.* FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE i.project_id = :#{#project.id} AND LOWER(ic.contents) LIKE LOWER(:keyword)",
        countQuery = "SELECT COUNT(*) FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE i.project_id = :#{#project.id} AND LOWER(ic.contents) LIKE LOWER(:keyword)",
        nativeQuery = true
    )
    fun searchIssueCommentsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<IssueComment>

    fun searchIssueCommentsInProject(project: Project, keyword: String, pageable: Pageable): Page<IssueComment> =
        searchIssueCommentsInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM issue_comment ic JOIN issue i ON i.id = ic.issue_id WHERE i.project_id = :#{#project.id} AND LOWER(ic.contents) LIKE LOWER(:keyword)",
        nativeQuery = true
    )
    fun countSearchIssueCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
