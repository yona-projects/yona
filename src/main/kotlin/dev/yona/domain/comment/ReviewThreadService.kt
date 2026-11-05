package dev.yona.domain.comment

import dev.yona.domain.project.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 커밋에 달린 코드 리뷰 스레드를 조회하고 센다. v1.6 `CommentThread.findByCommitId`, `count`, `countOnCommit`
 * (`app/models/CommentThread.java:75-215`)에 해당한다.
 */
@Service
class ReviewThreadService(private val threads: CommentThreadRepository) {

    /** 프로젝트의 커밋에 달린 스레드 전부이다(코드 범위, 파일 단위, 풀 리퀘스트 것 포함). 최근 만든 것이 앞이다. */
    @Transactional(readOnly = true)
    fun onCommit(project: Project, commitId: String): List<CommentThread> {
        val projectId = requireNotNull(project.id)
        return (threads.findCodeThreads(projectId, commitId) + threads.findNonRangedThreads(projectId, commitId)).sortedByDescending { it.createdDate }
    }

    /**
     * 커밋(프로젝트와 풀 리퀘스트를 가리지 않음)에 달린 스레드 수이다. [path]가 있으면 코드 범위 스레드는 그 경로의 것만 센다
     * (파일 단위 스레드는 경로와 상관없이 센다). 커밋 이력의 댓글 수에 쓴다 (v1.6 `CommentThread.count(null, commitId, path)`).
     */
    @Transactional(readOnly = true)
    fun count(commitId: String, path: String?): Int =
        threads.findCodeThreadsOfCommit(commitId).count { path == null || it.path == path } + threads.findNonRangedThreadsOfCommit(commitId).size

    /**
     * 풀 리퀘스트 없이 프로젝트의 커밋에 달린 스레드 수이다. 파일 보기의 댓글 수에 쓴다.
     * v1.6은 경로로 `"/" + 경로`를 넘겨 코드 범위 스레드는 항상 경로가 달라 세지 않고 파일 단위 스레드만 셌다. 호출하는 쪽이 앞 슬래시 없는 경로를 넘겨
     * 코드 범위 스레드도 센다(R-41).
     */
    @Transactional(readOnly = true)
    fun countOnCommit(project: Project, commitId: String, path: String?): Int {
        val projectId = requireNotNull(project.id)
        return threads.findCodeThreadsNotOnPullRequest(projectId, commitId).count { path == null || it.path == path } + threads.findNonRangedThreadsNotOnPullRequest(projectId, commitId).size
    }
}
