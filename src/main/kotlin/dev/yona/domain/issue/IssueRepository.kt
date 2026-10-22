package dev.yona.domain.issue

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 저장소이다. */
interface IssueRepository : JpaRepository<Issue, Long> {

    /** 프로젝트 안의 번호로 이슈를 조회한다 (v1.6 `Issue.findByNumber`). */
    fun findByProjectIdAndNumber(projectId: Long, number: Long): Issue?

    /**
     * 사용자가 담당자인 이슈 중 지정한 상태의 개수를 센다.
     * v1.6 `Issue.countOpenIssuesByUser`는 `assignee.user.id`와 `state = OPEN`으로 센다.
     */
    fun countByAssigneeUserIdAndState(userId: Long, state: State): Long

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
}
