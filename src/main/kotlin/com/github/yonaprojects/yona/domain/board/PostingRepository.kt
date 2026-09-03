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
interface PostingRepository : JpaRepository<Posting, Long> {
    fun findByProject(project: Project): List<Posting>
    fun countByProject(project: Project): Long
    fun findByProject(project: Project, pageable: Pageable): Page<Posting>
    fun findByProjectAndNotice(project: Project, notice: Boolean): List<Posting>
    fun findByProjectAndNotice(project: Project, notice: Boolean, pageable: Pageable): Page<Posting>
    fun findByProjectAndNumber(project: Project, number: Long): Posting?
    fun findByProjectIn(projects: List<Project>, pageable: Pageable): Page<Posting>
    fun findByProjectAndReadme(project: Project, readme: Boolean): List<Posting>

    // yona organization/group_board_list.scala.html:65-71 notices 대응 (조직 그룹, TASK-0244) —
    // 조직에서 보이는 프로젝트들의 공지 게시글 전체(페이지 무관, 1페이지에서만 상단에 노출됨).
    fun findByProjectInAndNotice(projects: List<Project>, notice: Boolean): List<Posting>

    // yona organization/group_board_list.scala.html의 param.filter(검색어) + projectNames[](프로젝트
    // 좁히기) 대응 — keyword가 빈 문자열이면 전체를 대상으로 한다.
    //
    // 아래 메서드들은 전부 JPQL 대신 네이티브 쿼리를 쓴다 — Postgres에서 Hibernate 7.2.x가 한
    // 쿼리 안에 LIKE 술어가 2개 이상이면(title/body 둘 다 검색 대상이라 1개로 인수분해 불가)
    // `like_escape(bigint, unknown) does not exist`로 항상 실패하는 버그가 있다
    // (IssueRepository.searchIssues() 주석 참고). 네이티브 쿼리는 엔티티가 아닌 ID로만 바인딩
    // 가능하므로 `List<Project>`를 받는 공개 메서드는 ID 리스트로 변환해 내부 쿼리에 위임한다.
    // notice = false/0 같은 SQL 리터럴은 DB마다 다르게 깨진다(PostgreSQL은 진짜 boolean 컬럼이라
    // 정수 리터럴과 비교 시 타입 불일치 에러, SQL Server는 TRUE/FALSE 리터럴 자체가 없음 —
    // 실측 확인). 리터럴 대신 파라미터로 바인딩하면 각 방언의 Boolean JDBC 타입 매핑을 그대로
    // 타므로 전부 호환된다.
    @Query(
        value = "SELECT * FROM posting WHERE project_id IN :projectIds AND notice = :isNotice AND (:keyword = '' OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(body) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        countQuery = "SELECT COUNT(*) FROM posting WHERE project_id IN :projectIds AND notice = :isNotice AND (:keyword = '' OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(body) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        nativeQuery = true
    )
    fun findByProjectIdInAndKeywordQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("isNotice") isNotice: Boolean, pageable: Pageable): Page<Posting>

    fun findByProjectInAndKeyword(projects: List<Project>, keyword: String, pageable: Pageable): Page<Posting> =
        findByProjectIdInAndKeywordQuery(projects.map { it.id!! }.ifEmpty { listOf(-1L) }, keyword, false, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT * FROM posting WHERE (project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))) OR (:userId IS NOT NULL AND author_id = :userId AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword)))",
        countQuery = "SELECT COUNT(*) FROM posting WHERE (project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))) OR (:userId IS NOT NULL AND author_id = :userId AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword)))",
        nativeQuery = true
    )
    fun searchPostingsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?, pageable: Pageable): Page<Posting>

    fun searchPostings(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<Posting> =
        searchPostingsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM posting WHERE (project_id IN :projectIds AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))) OR (:userId IS NOT NULL AND author_id = :userId AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword)))",
        nativeQuery = true
    )
    fun countSearchPostingsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?): Int

    fun countSearchPostings(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchPostingsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId)

    @Query(
        value = "SELECT * FROM posting WHERE project_id = :#{#project.id} AND notice = :isNotice AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM posting WHERE project_id = :#{#project.id} AND notice = :isNotice AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchPostingsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, @Param("isNotice") isNotice: Boolean, pageable: Pageable): Page<Posting>

    fun searchPostingsInProject(project: Project, keyword: String, pageable: Pageable): Page<Posting> =
        searchPostingsInProjectQuery(project, keyword, false, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM posting WHERE project_id = :#{#project.id} AND notice = :isNotice AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchPostingsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, @Param("isNotice") isNotice: Boolean): Int

    fun countSearchPostingsInProject(project: Project, keyword: String): Int =
        countSearchPostingsInProjectQuery(project, keyword, false)

    fun findAllByOrderByCreatedDateDesc(pageable: Pageable): Page<Posting>

    // yona BoardApp.SearchCondition.asExpressionList()의 labelIdSet 필터 대응 (P1-19)
    @Query(
        value = """
            SELECT DISTINCT p.* FROM posting p
            JOIN posting_issue_label pl ON pl.posting_id = p.id
            WHERE p.project_id = :#{#project.id}
              AND p.notice = :isNotice
              AND pl.issue_label_id IN :labelIds
              AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(:keyword) OR LOWER(p.body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM posting p
            JOIN posting_issue_label pl ON pl.posting_id = p.id
            WHERE p.project_id = :#{#project.id}
              AND p.notice = :isNotice
              AND pl.issue_label_id IN :labelIds
              AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(:keyword) OR LOWER(p.body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findByProjectAndLabelIdsInQuery(
        @Param("project") project: Project,
        @Param("labelIds") labelIds: List<Long>,
        @Param("keyword") keyword: String?,
        @Param("isNotice") isNotice: Boolean,
        pageable: Pageable
    ): Page<Posting>

    fun findByProjectAndLabelIdsIn(project: Project, labelIds: List<Long>, keyword: String?, pageable: Pageable): Page<Posting> =
        findByProjectAndLabelIdsInQuery(project, labelIds, keyword, false, pageable.toSnakeCaseSort())
}
