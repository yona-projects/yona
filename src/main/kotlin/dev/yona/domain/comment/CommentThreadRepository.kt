package dev.yona.domain.comment

import dev.yona.domain.enumeration.ThreadState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** 코드 리뷰 스레드 저장소이다. */
interface CommentThreadRepository : JpaRepository<CommentThread, Long> {

    /** 프로젝트에서 지정한 상태의 스레드 개수를 센다 (v1.6 `CommentThread.countReviewsBy` 기본 조건은 OPEN). */
    fun countByProjectIdAndState(projectId: Long, state: ThreadState): Long

    /** 프로젝트의 커밋에 달린 코드 범위 스레드를 최근 순으로 조회한다. 풀 리퀘스트의 스레드도 포함한다. */
    @Query("select t from CodeCommentThread t where t.project.id = :projectId and t.commitId = :commitId order by t.createdDate desc")
    fun findCodeThreads(projectId: Long, commitId: String): List<CodeCommentThread>

    /** 프로젝트의 커밋에 달린 파일 단위 스레드를 최근 순으로 조회한다. 풀 리퀘스트의 스레드도 포함한다. */
    @Query("select t from NonRangedCodeCommentThread t where t.project.id = :projectId and t.commitId = :commitId order by t.createdDate desc")
    fun findNonRangedThreads(projectId: Long, commitId: String): List<NonRangedCodeCommentThread>

    /** 프로젝트를 가리지 않고 커밋에 달린 코드 범위 스레드를 조회한다. */
    @Query("select t from CodeCommentThread t where t.commitId = :commitId")
    fun findCodeThreadsOfCommit(commitId: String): List<CodeCommentThread>

    /** 프로젝트를 가리지 않고 커밋에 달린 파일 단위 스레드를 조회한다. */
    @Query("select t from NonRangedCodeCommentThread t where t.commitId = :commitId")
    fun findNonRangedThreadsOfCommit(commitId: String): List<NonRangedCodeCommentThread>

    /** 풀 리퀘스트가 없는 프로젝트의 커밋 코드 범위 스레드이다. */
    @Query("select t from CodeCommentThread t where t.project.id = :projectId and t.commitId = :commitId and t.pullRequest is null")
    fun findCodeThreadsNotOnPullRequest(projectId: Long, commitId: String): List<CodeCommentThread>

    /** 풀 리퀘스트가 없는 프로젝트의 커밋 파일 단위 스레드이다. */
    @Query("select t from NonRangedCodeCommentThread t where t.project.id = :projectId and t.commitId = :commitId and t.pullRequest is null")
    fun findNonRangedThreadsNotOnPullRequest(projectId: Long, commitId: String): List<NonRangedCodeCommentThread>

    /** 풀 리퀘스트에 달린 스레드 수이다 (v1.6 `PullRequest.commentThreads.size`). */
    fun countByPullRequestId(pullRequestId: Long): Long

    /** 풀 리퀘스트에 달린 스레드 중 [state]인 것의 수이다 (v1.6 `PullRequest.countCommentThreadsByState`). */
    fun countByPullRequestIdAndState(pullRequestId: Long, state: ThreadState): Long

    /** 풀 리퀘스트에 달린 스레드를 모두 조회한다. */
    fun findByPullRequestId(pullRequestId: Long): List<CommentThread>
}
