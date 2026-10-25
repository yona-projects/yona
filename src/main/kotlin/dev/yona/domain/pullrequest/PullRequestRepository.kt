package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

/** 풀 리퀘스트 저장소이다. */
interface PullRequestRepository : JpaRepository<PullRequest, Long>, JpaSpecificationExecutor<PullRequest> {

    /** 받는 프로젝트와 번호로 풀 리퀘스트를 조회한다 (v1.6 `PullRequest.findOne`). */
    fun findByToProjectIdAndNumber(projectId: Long, number: Long): PullRequest?

    /** 받는 프로젝트가 받은 풀 리퀘스트를 [pageable]의 정렬과 크기로 조회한다 (v1.6 `PullRequest.findRecentlyReceived`). */
    fun findByToProjectId(projectId: Long, pageable: org.springframework.data.domain.Pageable): List<PullRequest>

    /** 받는 프로젝트가 받은 지정한 상태의 풀 리퀘스트를 [pageable]의 정렬과 크기로 조회한다 (v1.6 `PullRequest.findRecentlyReceivedOpen`). */
    fun findByToProjectIdAndState(projectId: Long, state: State, pageable: org.springframework.data.domain.Pageable): List<PullRequest>

    /** 받는 프로젝트 기준으로 지정한 상태의 풀 리퀘스트 개수를 센다 (v1.6 `PullRequest.countOpenedPullRequests`). */
    fun countByToProjectIdAndState(projectId: Long, state: State): Long

    /** 보내는 프로젝트의 브랜치에서 나간 가장 최근(번호가 큰) 풀 리퀘스트이다. 받는 프로젝트는 [toProjectIds] 중 하나이다 (v1.6 `PullRequest.findTheLatestOneFrom`). */
    fun findFirstByFromProjectIdAndFromBranchAndToProjectIdInOrderByNumberDesc(projectId: Long, branch: String, toProjectIds: Collection<Long>): PullRequest?

    /** 보내는 프로젝트의 브랜치에서 나간 풀 리퀘스트 중 [states] 상태인 것이다 (v1.6 `PullRequest.findByFromProjectAndBranch`는 열림과 거절됨). */
    fun findByFromProjectIdAndFromBranchAndStateIn(projectId: Long, branch: String, states: Collection<State>): List<PullRequest>

    /** 같은 두 프로젝트의 같은 두 브랜치 사이에 [state] 상태인 요청이다 (v1.6 `findDuplicatedPullRequest`). */
    fun findFirstByFromProjectIdAndFromBranchAndToProjectIdAndToBranchAndState(
        fromProjectId: Long, fromBranch: String, toProjectId: Long, toBranch: String, state: State
    ): PullRequest?

    /**
     * 프로젝트의 브랜치와 관련된 아직 끝나지 않은 요청이다. 그 프로젝트의 브랜치에서 보낸 요청과 그 브랜치로 받는 요청 가운데 닫힘, 병합이 아닌 것이다
     * (v1.6 `PullRequest.findRelatedPullRequests`).
     */
    @org.springframework.data.jpa.repository.Query(
        "select r from PullRequest r where ((r.fromProject.id = :projectId and r.fromBranch = :branch) or (r.toProject.id = :projectId and r.toBranch = :branch)) " +
            "and r.state <> dev.yona.domain.enumeration.State.CLOSED and r.state <> dev.yona.domain.enumeration.State.MERGED order by r.id"
    )
    fun findRelated(projectId: Long, branch: String): List<PullRequest>

    /**
     * 사용자가 보낸 풀 리퀘스트 중 [from] 이후에 갱신된 것을 최근 갱신 순으로 조회한다. 같은 시각이면 상태 순이다
     * (v1.6 `PullRequest.findOpendPullRequestsByDaysAgo`. 이름과 달리 상태는 가리지 않는다).
     */
    @Query("select r from PullRequest r where r.contributor.id = :userId and r.updated >= :from order by r.updated desc, r.state asc, r.id asc")
    fun findRecentlyByContributor(userId: Long, from: java.util.Date): List<PullRequest>
}
