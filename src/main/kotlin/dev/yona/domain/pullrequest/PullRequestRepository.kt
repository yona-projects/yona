package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** 풀 리퀘스트 저장소이다. */
interface PullRequestRepository : JpaRepository<PullRequest, Long>, JpaSpecificationExecutor<PullRequest> {

    /** 받는 프로젝트와 번호로 풀 리퀘스트를 조회한다 (v1.6 `PullRequest.findOne`). */
    fun findByToProjectIdAndNumber(projectId: Long, number: Long): PullRequest?

    /** 받는 프로젝트 기준으로 지정한 상태의 풀 리퀘스트 개수를 센다 (v1.6 `PullRequest.countOpenedPullRequests`). */
    fun countByToProjectIdAndState(projectId: Long, state: State): Long

    /** 보내는 프로젝트의 브랜치에서 나간 가장 최근(번호가 큰) 풀 리퀘스트이다. 받는 프로젝트는 [toProjectIds] 중 하나이다 (v1.6 `PullRequest.findTheLatestOneFrom`). */
    fun findFirstByFromProjectIdAndFromBranchAndToProjectIdInOrderByNumberDesc(projectId: Long, branch: String, toProjectIds: Collection<Long>): PullRequest?

    /** 보내는 프로젝트의 브랜치에서 나간 풀 리퀘스트 중 [states] 상태인 것이다 (v1.6 `PullRequest.findByFromProjectAndBranch`는 열림과 거절됨). */
    fun findByFromProjectIdAndFromBranchAndStateIn(projectId: Long, branch: String, states: Collection<State>): List<PullRequest>
}
