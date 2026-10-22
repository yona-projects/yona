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
    private val repositories: RepositoryService,
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
    fun codeThreads(pullRequest: PullRequest, commitId: String?): List<CodeCommentThread> {
        val all = threads.findByPullRequestId(requireNotNull(pullRequest.id)).filterIsInstance<CodeCommentThread>()
        if (commitId != null) return all.filter { it.commitId == commitId }
        val from = pullRequest.mergedCommitIdFrom
        val to = pullRequest.mergedCommitIdTo
        return repositories.openRepository(requireNotNull(pullRequest.toProject)).use { repository ->
            all.filter { thread ->
                when {
                    thread.prevCommitId.isNullOrEmpty() -> false
                    from == thread.prevCommitId && to == thread.commitId -> true
                    else -> noChangesBetween(repository, from, thread.prevCommitId, thread.path) && noChangesBetween(repository, to, thread.commitId, thread.path)
                }
            }
        }
    }

    /** 변경 화면 아래에 모아 보이는 파일 단위 스레드이다. [commitId]가 있으면 그 커밋의 것만이다. */
    @Transactional(readOnly = true)
    fun nonRangedThreads(pullRequest: PullRequest, commitId: String?): List<NonRangedCodeCommentThread> =
        threads.findByPullRequestId(requireNotNull(pullRequest.id)).filterIsInstance<NonRangedCodeCommentThread>().filter { commitId == null || commitId == it.commitId }

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
        return repositories.openRepository(requireNotNull(pullRequest.toProject)).use { repository ->
            try {
                !noChangesBetween(repository, from, thread.prevCommitId, path) || !noChangesBetween(repository, to, thread.commitId, path)
            } catch (e: MissingObjectException) {
                true
            }
        }
    }

    /** 스레드가 [pullRequest]의 현재 커밋 목록에 있는 커밋에 달린 것이면 `true`이다. */
    fun currentCommitIds(pullRequest: PullRequest): List<String> =
        commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), PullRequestCommitState.CURRENT).mapNotNull { it.commitId }

    private fun noChangesBetween(repository: Repository, rev1: String?, rev2: String?, path: String?): Boolean =
        blobId(repository, rev1, path.orEmpty().removePrefix("/")) == blobId(repository, rev2, path.orEmpty().removePrefix("/"))

    private fun blobId(repository: Repository, rev: String?, path: String): ObjectId? {
        require(!rev.isNullOrEmpty()) { "rev must not be empty" }
        val tree = RevWalk(repository).use { it.parseTree(repository.resolve(rev)) }
        return TreeWalk.forPath(repository, path, tree)?.use { it.getObjectId(0) }
    }
}
