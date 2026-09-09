package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostingCommentRepository : JpaRepository<PostingComment, Long> {
    fun findByPostingIdOrderByCreatedDateAsc(postingId: Long): List<PostingComment>
    fun countByPostingId(postingId: Long): Int

    // 네이티브 쿼리를 쓰는 이유는 IssueCommentRepository.searchIssueComments() 주석 참고 (Postgres
    // Hibernate 7.2.x @Lob LIKE 버그, 지금은 제거함). posting_comment.project_id(Comment 기반
    // 클래스의 denormalized 컬럼) 대신 posting을 조인해 posting.project_id를 쓰는 이유도 같은
    // 주석 참고 — denormalized 컬럼이 항상 채워진다는 보장이 없다.
    @Query(
        value = "SELECT pc.* FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE LOWER(pc.contents) LIKE LOWER(:keyword) AND (p.project_id IN :projectIds OR pc.author_id = :userId)",
        countQuery = "SELECT COUNT(*) FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE LOWER(pc.contents) LIKE LOWER(:keyword) AND (p.project_id IN :projectIds OR pc.author_id = :userId)",
        nativeQuery = true
    )
    fun searchPostingCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long, pageable: Pageable): Page<PostingComment>

    fun searchPostingComments(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<PostingComment> =
        searchPostingCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId ?: -1L, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE LOWER(pc.contents) LIKE LOWER(:keyword) AND (p.project_id IN :projectIds OR pc.author_id = :userId)",
        nativeQuery = true
    )
    fun countSearchPostingCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long): Int

    fun countSearchPostingComments(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchPostingCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId ?: -1L)

    @Query(
        value = "SELECT pc.* FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE p.project_id = :#{#project.id} AND LOWER(pc.contents) LIKE LOWER(:keyword)",
        countQuery = "SELECT COUNT(*) FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE p.project_id = :#{#project.id} AND LOWER(pc.contents) LIKE LOWER(:keyword)",
        nativeQuery = true
    )
    fun searchPostingCommentsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<PostingComment>

    fun searchPostingCommentsInProject(project: Project, keyword: String, pageable: Pageable): Page<PostingComment> =
        searchPostingCommentsInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM posting_comment pc JOIN posting p ON p.id = pc.posting_id WHERE p.project_id = :#{#project.id} AND LOWER(pc.contents) LIKE LOWER(:keyword)",
        nativeQuery = true
    )
    fun countSearchPostingCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
