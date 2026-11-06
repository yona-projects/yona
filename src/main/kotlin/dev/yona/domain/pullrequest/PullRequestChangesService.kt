package dev.yona.domain.pullrequest

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.repository.RepositoryService
import dev.yona.repository.diff.DiffService
import dev.yona.repository.diff.FileDiff
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 풀 리퀘스트의 변경 내용(차이와 코드 리뷰 스레드)을 계산한다. v1.6 `PullRequest.getDiff`, `getCodeCommentThreadsForChanges`,
 * `noChangesBetween`, `CodeCommentThread.isOutdated`(`app/models/PullRequest.java:731-750,1064-1160`, `app/models/CodeCommentThread.java`)에 해당한다.
 *
 * 변경 내용은 받는 프로젝트의 저장소에서 본다. 보내는 브랜치와 병합 결과를 가져와 두었기 때문이다.
 */
@Service
class PullRequestChangesService(
    private val diffs: DiffService,
    private val backends: dev.yona.repository.VcsBackends,
    private val threads: CommentThreadRepository,
    private val commits: PullRequestCommitRepository
) {

    /**
     * 변경 내용이다. [commitId]가 없으면 병합 기준 커밋 사이의 전체 변경이고, 있으면 그 커밋 하나의 변경(첫 부모와 비교)이다.
     * 커밋을 받는 프로젝트 저장소에서 찾을 수 없으면 `null`이다.
     */
    fun diff(pullRequest: PullRequest, commitId: String?): List<FileDiff>? {
        val project = requireNotNull(pullRequest.toProject)
        if (commitId == null) {
            val from = checkNotNull(pullRequest.mergedCommitIdFrom) { "No mergedCommitIdFrom or mergedCommitIdTo" }
            val to = checkNotNull(pullRequest.mergedCommitIdTo) { "No mergedCommitIdFrom or mergedCommitIdTo" }
            return diffs.diff(project, from, to)
        }
        return diffs.commitDiff(project, commitId)
    }

    /**
     * 변경 화면에 줄 사이로 끼워 넣을 코드 범위 스레드이다. [commitId]가 있으면 그 커밋에 달린 것이다. 없으면 커밋 하나에 달린 것은 빼고,
     * 병합 기준 커밋 쌍에 달렸거나 그 파일이 그 뒤로 바뀌지 않은(최신이 아닌 것이 확실하지 않은) 것을 고른다.
     */
    @Transactional(readOnly = true)
    fun codeThreads(pullRequest: PullRequest, commitId: String?, pendingThreads: List<dev.yona.domain.comment.CommentThread> = emptyList()): List<CodeCommentThread> {
        // 임시 저장 리뷰(X-26, D-38)의 내 임시 스레드는 엔티티 조회에 보이지 않으므로 호출하는 쪽이 따로 읽어 넘긴다
        val all = (threads.findByPullRequestId(requireNotNull(pullRequest.id)) + pendingThreads).filterIsInstance<CodeCommentThread>()
        if (commitId != null) return all.filter { it.commitId == commitId }
        val from = pullRequest.mergedCommitIdFrom
        val to = pullRequest.mergedCommitIdTo
        val project = requireNotNull(pullRequest.toProject)
        return all.filter { thread ->
            when {
                thread.prevCommitId.isNullOrEmpty() -> false
                from == thread.prevCommitId && to == thread.commitId -> true
                // 커밋이 저장소에 없으면 같다고 확신할 수 없으므로 최신이 아닌 것으로 본다
                else -> try {
                    noChangesBetween(project, from, thread.prevCommitId, thread.path) && noChangesBetween(project, to, thread.commitId, thread.path)
                } catch (e: MissingCommitException) {
                    false
                }
            }
        }
    }

    /** 변경 화면 아래에 모아 보이는 파일 단위 스레드이다. [commitId]가 있으면 그 커밋의 것만이다. */
    @Transactional(readOnly = true)
    fun nonRangedThreads(pullRequest: PullRequest, commitId: String?, pendingThreads: List<dev.yona.domain.comment.CommentThread> = emptyList()): List<NonRangedCodeCommentThread> =
        (threads.findByPullRequestId(requireNotNull(pullRequest.id)) + pendingThreads).filterIsInstance<NonRangedCodeCommentThread>().filter { commitId == null || commitId == it.commitId }

    /**
     * 스레드가 지금 변경 내용과 어긋난(outdated) 것이면 `true`이다 (v1.6 `CodeCommentThread.isOutdated`). 풀 리퀘스트에 달리지 않았거나 병합 기준이 없거나
     * 위치 정보가 없으면 아니다. 커밋 하나에 단 코멘트(`prevCommitId`가 비어 있음)는 그 커밋이 현재 커밋 목록에 없으면 어긋난 것이다.
     * 그 밖에는 이전, 현재 기준 커밋과 스레드의 커밋에서 파일 내용이 달라졌는지를 본다. 객체가 없으면 어긋난 것으로 본다.
     */
    @Transactional(readOnly = true)
    fun isOutdated(thread: CodeCommentThread): Boolean {
        val pullRequest = thread.pullRequest ?: return false
        if (thread.startLine == null || thread.prevCommitId == null || thread.commitId == null) return false
        val from = pullRequest.mergedCommitIdFrom ?: return false
        val to = pullRequest.mergedCommitIdTo ?: return false
        if (thread.prevCommitId.isNullOrEmpty()) {
            return commits.findByPullRequestId(requireNotNull(pullRequest.id)).none { it.commitId == thread.commitId }
        }
        val path = thread.path.orEmpty().removePrefix("/")
        val project = requireNotNull(pullRequest.toProject)
        return try {
            !noChangesBetween(project, from, thread.prevCommitId, path) || !noChangesBetween(project, to, thread.commitId, path)
        } catch (e: MissingCommitException) {
            true
        }
    }

    /** 스레드가 [pullRequest]의 현재 커밋 목록에 있는 커밋에 달린 것이면 `true`이다. */
    fun currentCommitIds(pullRequest: PullRequest): List<String> =
        commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), PullRequestCommitState.CURRENT).mapNotNull { it.commitId }

    /** 비교할 커밋이 받는 프로젝트의 저장소에 없다. */
    private class MissingCommitException(rev: String?) : RuntimeException("commit not found: $rev")

    /** [rev1]과 [rev2] 커밋에서 [path] 파일의 내용이 같으면 `true`이다(둘 다 없어도 같다). 커밋이 저장소에 없으면 [MissingCommitException]이다. */
    private fun noChangesBetween(project: dev.yona.domain.project.Project, rev1: String?, rev2: String?, path: String?): Boolean {
        require(!rev1.isNullOrEmpty()) { "rev must not be empty" }
        require(!rev2.isNullOrEmpty()) { "rev must not be empty" }
        val commits = backends.commits(project.vcs)
        if (commits.commit(project, rev1) == null) throw MissingCommitException(rev1)
        if (commits.commit(project, rev2) == null) throw MissingCommitException(rev2)
        val code = backends.code(project.vcs)
        val file = path.orEmpty().removePrefix("/")
        val a = code.rawFile(project, rev1, file)
        val b = code.rawFile(project, rev2, file)
        return if (a == null || b == null) a == null && b == null else a.contentEquals(b)
    }
}
