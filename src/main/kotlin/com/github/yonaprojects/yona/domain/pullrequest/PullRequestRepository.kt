package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PullRequestRepository : JpaRepository<PullRequest, Long>, JpaSpecificationExecutor<PullRequest> {

    fun findByToProjectAndState(toProject: Project, state: State): List<PullRequest>
    fun countByToProjectAndState(toProject: Project, state: State): Long
    fun findByToProjectAndState(toProject: Project, state: State, pageable: Pageable): Page<PullRequest>
    fun findByToProject(toProject: Project): List<PullRequest>
    fun findByToProject(toProject: Project, pageable: Pageable): Page<PullRequest>
    fun findByToProjectAndNumber(toProject: Project, number: Long): PullRequest?
    fun findByToProjectInAndState(toProjects: List<Project>, state: State, pageable: Pageable): Page<PullRequest>
    // 조직 그룹 화면의 열림/닫힘 상태 탭 배지 카운트.
    fun countByToProjectInAndState(toProjects: List<Project>, state: State): Long

    // yona organization/group_pullrequest_list.scala.html의 condition.filter(제목 검색) 대응.
    //
    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueRepository.searchIssues() 주석 참고 (Postgres
    // Hibernate 7.2.x 버그 — LIKE가 다른 타입의 바인드 파라미터와 함께 있으면 실패). 네이티브
    // 쿼리는 엔티티가 아닌 ID로만 바인딩 가능하므로 `List<Project>`를 ID 리스트로 변환해 위임한다.
    @Query(
        value = "SELECT * FROM pull_request WHERE to_project_id IN :projectIds AND state = :#{#state.name()} AND (:keyword = '' OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        countQuery = "SELECT COUNT(*) FROM pull_request WHERE to_project_id IN :projectIds AND state = :#{#state.name()} AND (:keyword = '' OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        nativeQuery = true
    )
    fun searchByToProjectIdInAndStateQuery(
        @Param("projectIds") projectIds: List<Long>,
        @Param("state") state: State,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<PullRequest>

    fun searchByToProjectInAndState(projects: List<Project>, state: State, keyword: String, pageable: Pageable): Page<PullRequest> =
        searchByToProjectIdInAndStateQuery(projects.map { it.id!! }.ifEmpty { listOf(-1L) }, state, keyword, pageable.toSnakeCaseSort())

    fun findFirstByToProjectOrderByNumberDesc(toProject: Project): PullRequest?
    fun findByContributor(contributor: User): List<PullRequest>

    // legacy 메서드명(findOpendPullRequestsByDaysAgo)과 달리 실제로는 state 필터가 없다(원문
    // 그대로 이식). 최근 daysAgo일 안에 갱신된 PR만 updated desc/state asc 순으로 반환한다.
    fun findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(
        contributor: User,
        since: Instant
    ): List<PullRequest>

    // yona PullRequestApp.closedPullRequests 대응 — CLOSED와 MERGED를 모두 "닫힌 PR"로 취급한다.
    fun findByToProjectAndStateIn(
        toProject: Project,
        states: List<State>,
        pageable: Pageable
    ): Page<PullRequest>

    // yona PullRequestApp.sentPullRequests 대응 — 이 프로젝트가 출발지(fromProject)인 PR 목록.
    fun findByFromProject(
        fromProject: Project,
        pageable: Pageable
    ): Page<PullRequest>

    // yona Project.deletePullRequests()의 PullRequest.findSentPullRequests(this) 대응.
    fun findByFromProject(fromProject: Project): List<PullRequest>

    // sent 탭 뱃지에 쓰는 "보낸 PR 중 병합된 것" / "보낸 PR 전체" 카운트.
    fun countByFromProject(fromProject: Project): Long
    fun countByFromProjectAndState(fromProject: Project, state: State): Long

    // partial_search의 "보낸이" 상세검색 드롭다운에 쓰는, 이 프로젝트에 PR을 보낸 적 있는 사용자 목록.
    @Query("SELECT DISTINCT pr.contributor FROM PullRequest pr WHERE pr.toProject = :project")
    fun findDistinctContributorsByToProject(@Param("project") project: Project): List<User>

    // 이미 PR로 추적 중인 브랜치는 별도 PushedBranch 레코드를 만들지 않기 위한 존재 확인.
    fun existsByFromProjectAndFromBranch(fromProject: Project, fromBranch: String): Boolean

    // 동일한 from/to 프로젝트·브랜치 조합으로 이미 열려있는 PR이 있는지 확인한다(PR 수정 시
    // 브랜치를 재할당할 때 사용).
    fun findByFromBranchAndToBranchAndFromProjectAndToProjectAndState(
        fromBranch: String,
        toBranch: String,
        fromProject: Project,
        toProject: Project,
        state: State
    ): PullRequest?


    @Query("""
        SELECT pr FROM PullRequest pr
        WHERE ((pr.fromProject = :project AND pr.fromBranch = :branch)
           OR (pr.toProject = :project AND pr.toBranch = :branch))
           AND pr.state NOT IN ('CLOSED', 'MERGED')
    """)
    fun findRelatedPullRequests(
        @Param("project") project: Project,
        @Param("branch") branch: String
    ): List<PullRequest>

    // code/branches.html "보낸 코드" 컬럼에 쓰는, 이 브랜치에서 이 프로젝트로 보낸(포크 없는
    // 일반적인 경우) 가장 최근 PR 1건(상태 무관, open/closed/merged 다 포함).
    fun findFirstByFromProjectAndFromBranchAndToProjectOrderByNumberDesc(
        fromProject: Project,
        fromBranch: String,
        toProject: Project
    ): PullRequest?


    // `yona search prs` 대응. IssueRepository.searchIssues()/searchIssuesInProject()와 동일한
    // 패턴(Postgres Hibernate 7.2.x가 한 쿼리에 LIKE 술어가 2개 이상이면 실패하는 버그 회피를
    // 위해 네이티브 쿼리 사용) — 전역 검색은 "toProject가 접근 허용 프로젝트 목록에 있거나,
    // 내가 contributor인 PR"까지 포함한다(Issue의 author/assignee 대응 개념으로 contributor
    // 하나만 있음).
    @Query(
        value = """
            SELECT * FROM pull_request pr
            WHERE (LOWER(pr.title) LIKE LOWER(:keyword) OR LOWER(pr.body) LIKE LOWER(:keyword))
              AND (pr.to_project_id IN :projectIds
                   OR (:userId IS NOT NULL AND pr.contributor_id = :userId))
        """,
        countQuery = """
            SELECT COUNT(*) FROM pull_request pr
            WHERE (LOWER(pr.title) LIKE LOWER(:keyword) OR LOWER(pr.body) LIKE LOWER(:keyword))
              AND (pr.to_project_id IN :projectIds
                   OR (:userId IS NOT NULL AND pr.contributor_id = :userId))
        """,
        nativeQuery = true
    )
    fun searchPullRequestsQuery(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?,
        pageable: Pageable
    ): Page<PullRequest>

    fun searchPullRequests(projectIds: List<Long>, keyword: String, userId: Long?, pageable: Pageable): Page<PullRequest> =
        searchPullRequestsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId, pageable.toSnakeCaseSort())

    @Query(
        value = """
            SELECT COUNT(*) FROM pull_request pr
            WHERE (LOWER(pr.title) LIKE LOWER(:keyword) OR LOWER(pr.body) LIKE LOWER(:keyword))
              AND (pr.to_project_id IN :projectIds
                   OR (:userId IS NOT NULL AND pr.contributor_id = :userId))
        """,
        nativeQuery = true
    )
    fun countSearchPullRequestsQuery(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?
    ): Int

    fun countSearchPullRequests(projectIds: List<Long>, keyword: String, userId: Long?): Int =
        countSearchPullRequestsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, userId)

    @Query(
        value = "SELECT * FROM pull_request WHERE to_project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM pull_request WHERE to_project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchPullRequestsInProjectQuery(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<PullRequest>

    fun searchPullRequestsInProject(project: Project, keyword: String, pageable: Pageable): Page<PullRequest> =
        searchPullRequestsInProjectQuery(project, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM pull_request WHERE to_project_id = :#{#project.id} AND (LOWER(title) LIKE LOWER(:keyword) OR LOWER(body) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchPullRequestsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int

    // `gh status`의 "Assigned Pull Requests" 대응. 프로젝트 하나로 좁히지 않고 로그인 사용자
    // 전체를 대상으로 하는 집계라 IssueRepository.countByAssigneeAndState/findByAssigneeAndState와
    // 동일한 패턴(assignee.user.id로 필터)을 그대로 옮겼다. 여기선 LIKE 술어가 없어
    // IssueRepository.searchIssues()가 우회하는 Postgres Hibernate 7.2.x 네이티브 쿼리 버그
    // (LIKE 2개 이상 바인딩 시 실패)에 해당하지 않으므로 일반 JPQL로 충분하다.
    @Query("SELECT pr FROM PullRequest pr WHERE pr.assignee.user.id = :userId AND pr.state = :state")
    fun findByAssigneeUserIdAndState(
        @Param("userId") userId: Long,
        @Param("state") state: State,
        pageable: Pageable
    ): Page<PullRequest>

    @Query("SELECT COUNT(pr) FROM PullRequest pr WHERE pr.assignee.user.id = :userId AND pr.state = :state")
    fun countByAssigneeUserIdAndState(@Param("userId") userId: Long, @Param("state") state: State): Long

    // `gh status`의 "Review Requests" 대응. reviewers는 @ManyToMany(pull_request_reviewers
    // 조인 테이블)라 JOIN으로 펼쳐서 사용자 id를 대조한다.
    @Query("SELECT pr FROM PullRequest pr JOIN pr.reviewers r WHERE r.id = :userId AND pr.state = :state")
    fun findByReviewerIdAndState(
        @Param("userId") userId: Long,
        @Param("state") state: State,
        pageable: Pageable
    ): Page<PullRequest>

    @Query("SELECT COUNT(pr) FROM PullRequest pr JOIN pr.reviewers r WHERE r.id = :userId AND pr.state = :state")
    fun countByReviewerIdAndState(@Param("userId") userId: Long, @Param("state") state: State): Long
}

