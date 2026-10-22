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
}
