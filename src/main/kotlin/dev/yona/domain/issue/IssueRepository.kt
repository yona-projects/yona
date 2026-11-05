package dev.yona.domain.issue

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** 이슈 저장소이다. */
interface IssueRepository : JpaRepository<Issue, Long> {

    /** 프로젝트 안의 번호로 이슈를 조회한다 (v1.6 `Issue.findByNumber`). */
    fun findByProjectIdAndNumber(projectId: Long, number: Long): Issue?

    /** 프로젝트의 이슈를 번호 순서로 조회한다 (프로젝트 내보내기). */
    fun findByProjectIdOrderByIdAsc(projectId: Long): List<Issue>

    /**
     * 사용자가 담당자인 이슈 중 지정한 상태의 개수를 센다.
     * v1.6 `Issue.countOpenIssuesByUser`는 `assignee.user.id`와 `state = OPEN`으로 센다.
     */
    fun countByAssigneeUserIdAndState(userId: Long, state: State): Long

    /** 프로젝트에서 지정한 상태의 이슈를 조건에 따라 읽는다. 초안은 상태가 `DRAFT`이므로 `OPEN`으로 읽으면 빠진다. */
    fun findByProjectIdAndState(projectId: Long, state: State, pageable: org.springframework.data.domain.Pageable): List<Issue>

    /** 프로젝트에서 가장 최근에 만든 이슈이다. 상태와 초안을 가리지 않는다 (v1.6 `Issue.findRecentlyCreated`). */
    fun findByProjectId(projectId: Long, pageable: org.springframework.data.domain.Pageable): List<Issue>

    /** 담당자(프로젝트의 담당자 행)가 맡은 지정한 상태의 이슈 수이다. 하위 이슈도 센다 (v1.6 `Issue.countOpenIssuesByAssignee`). */
    fun countByProjectIdAndAssigneeIdAndState(projectId: Long, assigneeId: Long, state: State): Long

    /** 담당자가 없는 지정한 상태의 이슈 수이다 (v1.6 `countOpenIssuesByAssignee(project, null)`). */
    fun countByProjectIdAndAssigneeIsNullAndState(projectId: Long, state: State): Long

    /** 마일스톤이 없는 지정한 상태의 이슈 수이다 (v1.6 `countOpenIssuesByMilestone(project, null)`). */
    fun countByProjectIdAndMilestoneIsNullAndState(projectId: Long, state: State): Long

    /** 프로젝트에서 마일스톤이 [milestoneId]인 지정한 상태의 이슈 수이다 (v1.6 `Issue.countOpenIssuesByMilestone`). */
    fun countByProjectIdAndMilestoneIdAndState(projectId: Long, milestoneId: Long, state: State): Long

    /** 프로젝트에서 라벨이 붙은 지정한 상태의 이슈 수이다 (v1.6 `Issue.countOpenIssuesByLabel`). */
    @Query("select count(i) from Issue i join i.labels l where i.project.id = :projectId and l.id = :labelId and i.state = :state")
    fun countByLabel(projectId: Long, labelId: Long, state: State): Long

    /** 프로젝트에서 하위 이슈를 포함해 지정한 상태인 이슈 수이다. 담당자별 비율의 분모에 쓴다(R-68-1). */
    fun countByProjectIdAndState(projectId: Long, state: State): Long

    /** 프로젝트의 최상위 이슈(부모가 없는 이슈) 중 지정한 상태의 개수이다 (v1.6 `Issue.countIssues`). */
    fun countByProjectIdAndParentIsNullAndState(projectId: Long, state: State): Long

    /** 프로젝트의 최상위 이슈 중 초안을 뺀 개수이다 (v1.6 `Issue.countIssues(projectId, State.ALL)`). */
    fun countByProjectIdAndParentIsNullAndStateNot(projectId: Long, state: State): Long

    /** 마일스톤에 속한 이슈 중 지정한 상태의 개수이다 (v1.6 `Milestone.getNumOpenIssues`, `getNumClosedIssues`). */
    fun countByMilestoneIdAndState(milestoneId: Long, state: State): Long

    /** 마일스톤에 속한 전체 이슈 수이다 (v1.6 `Milestone.getNumTotalIssues`). */
    fun countByMilestoneId(milestoneId: Long): Long

    /** 마일스톤에 속한 모든 이슈를 번호 내림차순으로 조회한다 (v1.6 `Milestone.sortedByNumberOfIssue`). */
    fun findByMilestoneIdOrderByNumberDesc(milestoneId: Long): List<Issue>

    /** 마일스톤에 속한 이슈 중 지정한 상태인 것을 번호 순으로 조회한다 (v1.6 `Issue.findOpenIssuesByMilestone`, `findClosedIssuesByMilestone`). */
    fun findByMilestoneIdAndStateOrderById(milestoneId: Long, state: State): List<Issue>

    /** 마일스톤을 지울 때 속한 이슈의 마일스톤을 비운다. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Issue i set i.milestone = null where i.milestone.id = :milestoneId")
    fun clearMilestone(milestoneId: Long): Int

    /**
     * 사용자가 담당했거나 작성한 이슈 중 [from] 이후에 갱신된 것을 최근 갱신 순으로 조회한다. 같은 시각이면 상태 순이다
     * (v1.6 `Issue.findRecentlyIssuesByDaysAgo`).
     */
    @Query("select i from Issue i left join i.assignee a where (a.user.id = :userId or i.authorId = :userId) and i.updatedDate >= :from order by i.updatedDate desc, i.state asc, i.id asc")
    fun findRecentlyByUser(userId: Long, from: java.util.Date): List<Issue>
}
