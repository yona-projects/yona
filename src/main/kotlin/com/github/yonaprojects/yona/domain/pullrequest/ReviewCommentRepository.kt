package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewCommentRepository : JpaRepository<ReviewComment, Long> {
    fun findByThreadIdOrderByCreatedDateAsc(threadId: Long): List<ReviewComment>

    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueCommentRepository.searchIssueComments() 주석
    // 참고 (Postgres Hibernate 7.2.x 버그 — LIKE 1개여도 다른 bigint 파라미터와 혼재하면 실패).
    @Query(
        value = """
            SELECT rc.* FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE LOWER(rc.contents) LIKE LOWER(:keyword)
              AND (t.project_id IN :projectIds OR (:userId IS NOT NULL AND rc.author_id = :userId))
        """,
        countQuery = """
            SELECT COUNT(*) FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE LOWER(rc.contents) LIKE LOWER(:keyword)
              AND (t.project_id IN :projectIds OR (:userId IS NOT NULL AND rc.author_id = :userId))
        """,
        nativeQuery = true
    )
    fun searchReviewCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?, pageable: Pageable): Page<ReviewComment>

    fun searchReviewComments(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<ReviewComment> =
        searchReviewCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId, pageable.toSnakeCaseSort())

    @Query(
        value = """
            SELECT COUNT(*) FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE LOWER(rc.contents) LIKE LOWER(:keyword)
              AND (t.project_id IN :projectIds OR (:userId IS NOT NULL AND rc.author_id = :userId))
        """,
        nativeQuery = true
    )
    fun countSearchReviewCommentsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?): Int

    fun countSearchReviewComments(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchReviewCommentsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId)

    @Query(
        value = """
            SELECT rc.* FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE t.project_id = :#{#project.id} AND LOWER(rc.contents) LIKE LOWER(:keyword)
        """,
        countQuery = """
            SELECT COUNT(*) FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE t.project_id = :#{#project.id} AND LOWER(rc.contents) LIKE LOWER(:keyword)
        """,
        nativeQuery = true
    )
    fun searchReviewCommentsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<ReviewComment>

    fun searchReviewCommentsInProject(project: Project, keyword: String, pageable: Pageable): Page<ReviewComment> =
        searchReviewCommentsInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = """
            SELECT COUNT(*) FROM review_comment rc
            JOIN comment_thread t ON t.id = rc.thread_id
            WHERE t.project_id = :#{#project.id} AND LOWER(rc.contents) LIKE LOWER(:keyword)
        """,
        nativeQuery = true
    )
    fun countSearchReviewCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
