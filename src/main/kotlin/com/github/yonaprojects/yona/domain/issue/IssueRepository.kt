package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant

@Repository
interface IssueRepository : JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {
    fun findByProject(project: Project): List<Issue>
    fun countByProject(project: Project): Long
    fun findByProject(project: Project, pageable: Pageable): Page<Issue>
    fun findByProjectAndState(project: Project, state: State, pageable: Pageable): Page<Issue>
    fun findByProjectAndState(project: Project, state: State): List<Issue>
    fun countByProjectAndState(project: Project, state: State): Long
    fun findByProjectIn(projects: List<Project>, pageable: Pageable): Page<Issue>
    fun findByProjectInAndState(projects: List<Project>, state: State, pageable: Pageable): Page<Issue>

    // yona organization/group_issue_search_partial.scala.html의 Issue.countIssuesBy(organization, ...)
    // 대응 — 열림/닫힘 상태 탭 배지 카운트.
    fun countByProjectInAndState(projects: List<Project>, state: State): Long
    fun findByProjectAndNumber(project: Project, number: Long): Issue?
    fun findByMilestone(milestone: Milestone): List<Issue>
    fun findByMilestoneAndState(milestone: Milestone, state: State): List<Issue>
    fun findByAuthorId(authorId: Long): List<Issue>

    // yona models/support/IssueSearchCondition.java의 setAssigneeIfExists()의
    // eq("assignee.user.id", assigneeId) 대응.
    fun findByAssignee_UserId(userId: Long): List<Issue>

    // yona Issue.java의 findRecentlyIssuesByDaysAgo(user, days) 대응 — 작성자 또는
    // 담당자인 이슈 중 최근 daysAgo일 안에 갱신된 것만, updatedDate desc/state asc 순으로 반환한다.
    // assignee는 LEFT JOIN으로 명시해야 한다(암묵적 경로 탐색은 INNER JOIN으로 컴파일돼 담당자 없는
    // 이슈까지 결과에서 사라진다 — searchIssues()와 동일한 이유).
    @Query("""
        SELECT i FROM Issue i
        LEFT JOIN i.assignee a
        LEFT JOIN a.user au
        WHERE (i.authorId = :userId OR au.id = :userId)
          AND i.updatedDate >= :since
        ORDER BY i.updatedDate DESC, i.state ASC
    """)
    fun findRecentlyByUser(@Param("userId") userId: Long, @Param("since") since: Instant): List<Issue>

    // yona Search.java의 issuesEL()의 "(Project && Keyword) || (Author && Keyword) ||
    // (Assignee && Keyword)" 대응 — 프로젝트 접근권한과 무관하게 본인이 작성했거나
    // 담당자로 지정된 이슈는 항상 검색에 노출된다(equalsUserTemplate()가 익명 사용자는 건너뛰므로
    // userId가 null이면 이 두 분기는 자연히 무효화된다). assignee는 LEFT JOIN으로 명시해야 한다 —
    // 암묵적 경로 탐색(i.assignee.user.id)은 Hibernate가 INNER JOIN으로 컴파일해, OR로 묶은
    // 다른 분기가 매치돼야 할 담당자 없는 이슈까지 통째로 결과에서 사라지게 만든다.
    // JPQL/Criteria 대신 네이티브 쿼리를 쓰는 이유: Hibernate 7.2.x(버전 12~24.Final 전부 동일)의
    // PostgreSQL SQL AST 변환기가 LIKE 술어를 `like_escape(pattern, escapechar)` 함수 호출로
    // 합성하는데, 같은 쿼리 안에 LIKE가 2개 이상이면(JPQL/Criteria 무관, OR/AND 무관, ESCAPE
    // 명시 여부 무관) 두 번째 이후 like_escape 호출의 인자 타입을 bigint로 잘못 추론해
    // "function pg_catalog.like_escape(bigint, unknown) does not exist"로 항상 실패한다
    // (MariaDB에서는 발생하지 않음 — Postgres SQL AST 변환기 고유 버그). 순수 ANSI SQL LIKE는
    // 지원 대상 5개 DB에서 문법이 동일해 네이티브 쿼리로 우회하면 이 버그를 피할 수 있다.
    // 네이티브 쿼리는 JPQL과 달리 빈 컬렉션을 "IN ()"으로 그대로 내보내 SQL 문법 오류가 나므로
    // (JPQL은 Hibernate가 1=0으로 자동 치환해줬음), searchIssues/countSearchIssues에서 대신
    // 존재할 수 없는 sentinel ID(-1)로 치환해 호출한다.
    @Query(
        value = """
            SELECT i.* FROM issue i
            LEFT JOIN assignee a ON a.id = i.assignee_id
            LEFT JOIN n4user au ON au.id = a.user_id
            WHERE (LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
              AND (i.project_id IN :projectIds
                   OR (:userId IS NOT NULL AND i.author_id = :userId)
                   OR (:userId IS NOT NULL AND au.id = :userId))
        """,
        countQuery = """
            SELECT COUNT(*) FROM issue i
            LEFT JOIN assignee a ON a.id = i.assignee_id
            LEFT JOIN n4user au ON au.id = a.user_id
            WHERE (LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
              AND (i.project_id IN :projectIds
                   OR (:userId IS NOT NULL AND i.author_id = :userId)
                   OR (:userId IS NOT NULL AND au.id = :userId))
        """,
        nativeQuery = true
    )
    fun searchIssuesQuery(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?,
        pageable: Pageable
    ): Page<Issue>

    fun searchIssues(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<Issue> =
        searchIssuesQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId, pageable.toSnakeCaseSort())

    @Query(
        value = """
            SELECT COUNT(*) FROM issue i
            LEFT JOIN assignee a ON a.id = i.assignee_id
            LEFT JOIN n4user au ON au.id = a.user_id
            WHERE (LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
              AND (i.project_id IN :projectIds
                   OR (:userId IS NOT NULL AND i.author_id = :userId)
                   OR (:userId IS NOT NULL AND au.id = :userId))
        """,
        nativeQuery = true
    )
    fun countSearchIssuesQuery(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?
    ): Int

    fun countSearchIssues(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchIssuesQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId)

    // 아래 3개 + 이 파일 나머지 keyword 검색 메서드들은 전부 JPQL 대신 네이티브 쿼리를 쓴다 —
    // Postgres에서 Hibernate 7.2.x가 한 쿼리 안에 LIKE 술어가 2개 이상이면(title/body 둘 다
    // 검색 대상이라 1개로 인수분해 불가) `like_escape(bigint, unknown) does not exist`로 항상
    // 실패하는 버그가 있다(searchIssues()/searchIssuesQuery() 주석 참고).
    @Query(
        value = "SELECT * FROM issue WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM issue WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchIssuesInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<Issue>

    fun searchIssuesInProject(project: Project, keyword: String, pageable: Pageable): Page<Issue> =
        searchIssuesInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT * FROM issue WHERE project_id = :#{#project.id} AND state = :#{#state.name()} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM issue WHERE project_id = :#{#project.id} AND state = :#{#state.name()} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchIssuesInProjectAndStateQuery(
        @Param("project") project: Project,
        @Param("state") state: State,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<Issue>

    fun searchIssuesInProjectAndState(project: Project, state: State, keyword: String, pageable: Pageable): Page<Issue> =
        searchIssuesInProjectAndStateQuery(project, state, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM issue WHERE project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchIssuesInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int

    @Modifying
    @Query("update Issue i set i.milestone = null where i.milestone = :milestone")
    fun removeMilestoneFromIssues(@Param("milestone") milestone: Milestone)

    fun findByState(state: State, pageable: Pageable): Page<Issue>

    // 1. 내가 담당자인 이슈
    @Query(
        value = """
            SELECT i.* FROM issue i
            JOIN assignee a ON a.id = i.assignee_id
            WHERE a.user_id = :assigneeId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(*) FROM issue i
            JOIN assignee a ON a.id = i.assignee_id
            WHERE a.user_id = :assigneeId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findByAssigneeAndStateQuery(
        @Param("assigneeId") assigneeId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findByAssigneeAndState(assigneeId: Long, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findByAssigneeAndStateQuery(assigneeId, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.assignee.user.id = :assigneeId 
          AND i.state = :state
    """)
    fun countByAssigneeAndState(
        @Param("assigneeId") assigneeId: Long,
        @Param("state") state: State
    ): Long

    // 2. 내가 작성자인 이슈
    @Query(
        value = """
            SELECT * FROM issue
            WHERE author_id = :authorId AND state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(*) FROM issue
            WHERE author_id = :authorId AND state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findByAuthorIdAndStateQuery(
        @Param("authorId") authorId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findByAuthorIdAndState(authorId: Long, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findByAuthorIdAndStateQuery(authorId, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.authorId = :authorId 
          AND i.state = :state
    """)
    fun countByAuthorIdAndState(
        @Param("authorId") authorId: Long,
        @Param("state") state: State
    ): Long

    // 3. 내가 댓글을 단 이슈
    @Query(
        value = """
            SELECT DISTINCT i.* FROM issue i
            JOIN issue_comment c ON c.issue_id = i.id
            WHERE c.author_id = :commenterId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(DISTINCT i.id) FROM issue i
            JOIN issue_comment c ON c.issue_id = i.id
            WHERE c.author_id = :commenterId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findCommentedByStateQuery(
        @Param("commenterId") commenterId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findCommentedByState(commenterId: Long, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findCommentedByStateQuery(commenterId, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(DISTINCT i) FROM Issue i 
        JOIN IssueComment c ON c.issue = i
        WHERE c.authorId = :commenterId 
          AND i.state = :state
    """)
    fun countCommentedByState(
        @Param("commenterId") commenterId: Long,
        @Param("state") state: State
    ): Long

    // 4. 나를 언급한 이슈 (yona Mention.getMentioningIssueIds() 대응 — 조직/프로젝트 그룹
    // 멘션까지 포함한 실제 멘션 인덱스 테이블 기반. 이전에는 title/body/댓글 LIKE 텍스트 검색으로만
    // 근사해 그룹 멘션(@orgname, @owner/project)으로 간접 멘션된 이슈를 놓쳤다.)
    @Query(
        value = """
            SELECT * FROM issue
            WHERE id IN :mentionedIssueIds AND state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(*) FROM issue
            WHERE id IN :mentionedIssueIds AND state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findMentionedByStateQuery(
        @Param("mentionedIssueIds") mentionedIssueIds: List<Long>,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findMentionedByState(mentionedIssueIds: List<Long>, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findMentionedByStateQuery(mentionedIssueIds, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(i) FROM Issue i
        WHERE i.id IN :mentionedIssueIds AND i.state = :state
    """)
    fun countMentionedByState(
        @Param("mentionedIssueIds") mentionedIssueIds: List<Long>,
        @Param("state") state: State
    ): Long

    // 5. 내가 보관(favorite)한 이슈
    @Query(
        value = """
            SELECT i.* FROM issue i
            JOIN issue_voter v ON v.issue_id = i.id
            WHERE v.user_id = :voterId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(*) FROM issue i
            JOIN issue_voter v ON v.issue_id = i.id
            WHERE v.user_id = :voterId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findFavoriteByStateQuery(
        @Param("voterId") voterId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findFavoriteByState(voterId: Long, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findFavoriteByStateQuery(voterId, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(i) FROM Issue i
        JOIN i.voters v
        WHERE v.id = :voterId
          AND i.state = :state
    """)
    fun countFavoriteByState(
        @Param("voterId") voterId: Long,
        @Param("state") state: State
    ): Long

    // 6. 내가 공유받은 이슈
    @Query(
        value = """
            SELECT DISTINCT i.* FROM issue i
            JOIN issue_sharer s ON s.issue_id = i.id
            WHERE s.user_id = :userId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        countQuery = """
            SELECT COUNT(DISTINCT i.id) FROM issue i
            JOIN issue_sharer s ON s.issue_id = i.id
            WHERE s.user_id = :userId AND i.state = :#{#state.name()}
              AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(:keyword) OR LOWER(i.body) LIKE LOWER(:keyword))
        """,
        nativeQuery = true
    )
    fun findSharedByStateQuery(
        @Param("userId") userId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    fun findSharedByState(userId: Long, state: State, keyword: String?, pageable: Pageable): Page<Issue> =
        findSharedByStateQuery(userId, state, keyword, pageable.toSnakeCaseSort())

    @Query("""
        SELECT COUNT(DISTINCT i) FROM Issue i
        JOIN i.sharers s
        WHERE s.user.id = :userId
          AND i.state = :state
    """)
    fun countSharedByState(
        @Param("userId") userId: Long,
        @Param("state") state: State
    ): Long

    fun countByParentId(parentId: Long): Long
    fun countByParentIdAndState(parentId: Long, state: State): Long
    fun findByParentId(parentId: Long): List<Issue>
    // yona Issue.findByParentIssueIdAndState() 대응 (issue/partial_view_child*.scala.html).
    // 부모 이슈 화면에서 초안/오픈/클로즈 하위이슈를 상태별로 나눠 렌더링한다.
    fun findByParentIdAndState(parentId: Long, state: State): List<Issue>

    // yona IssueApp.findDraftIssues() 대응 (issue/partial_list_draft.scala.html).
    // 이슈 목록 첫 페이지 상단에 "작성자 본인의 초안"만 노출한다.
    fun findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(project: Project, authorLoginId: String): List<Issue>

    // yona Issue.findParentIssueByProject(project, "", 300) 대응 (issue/partial_select_subtask.scala.html).
    // 부모가 없는 이슈를 프로젝트 전체(상태 무관)에서 최신순으로 최대 300건까지 후보로 노출한다.
    fun findByProjectAndParentIsNullOrderByCreatedDateDesc(project: Project, pageable: Pageable): List<Issue>

    // yona ProjectApp.getMentionIssueList() 대응: @이슈번호 멘션 자동완성용 최근 이슈 검색.
    // 네이티브 쿼리 이유는 이 파일 위쪽 주석 참고 (Postgres Hibernate 7.2.x LIKE 2개 이상 버그).
    @Query(
        value = """
            SELECT * FROM issue
            WHERE project_id = :#{#project.id}
              AND (:query = ''
                   OR LOWER(title) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR CAST(number AS CHAR(20)) LIKE CONCAT(:query, '%'))
            ORDER BY created_date DESC
        """,
        nativeQuery = true
    )
    fun findForMention(@Param("project") project: Project, @Param("query") query: String, pageable: Pageable): List<Issue>
}


