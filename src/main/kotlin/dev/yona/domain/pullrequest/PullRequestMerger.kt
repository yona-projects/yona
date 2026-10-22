package dev.yona.domain.pullrequest

import dev.yona.repository.CommitInfo
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RefSpec
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 병합을 시도한 결과이다.
 *
 * @property conflict 충돌하면 `true`
 * @property commits 받는 브랜치에 없고 보내는 브랜치에만 있는 커밋(최근 것이 앞). 풀 리퀘스트가 가져오는 커밋이다
 * @property leftParent 받는 브랜치의 머리 커밋
 * @property rightParent 보내는 브랜치의 머리 커밋
 */
data class MergeAttempt(val conflict: Boolean, val commits: List<CommitInfo>, val leftParent: ObjectId, val rightParent: ObjectId)

/**
 * 풀 리퀘스트의 병합 결과를 저장소에 반영한 것이다. [mergeCommitId]는 충돌하지 않을 때만 있다.
 */
data class MergeUpdate(val attempt: MergeAttempt, val mergeCommitId: ObjectId?)

/**
 * 풀 리퀘스트를 받는 프로젝트의 저장소에서 병합을 시도하고 실행한다. v1.6 `PullRequest.Merger`, `attemptMerge`, `updateMerge`, `merge`,
 * `fetchSourceBranch`(`app/models/PullRequest.java:356-570,872-950`)에 해당한다.
 *
 * 병합은 모두 받는 프로젝트의 저장소 안에서 일어난다. 보내는 프로젝트의 브랜치를 가져온(fetch) 뒤 JGit의 RECURSIVE 전략으로 작업 트리 없이 병합한다.
 * - 새 요청의 충돌 확인: 임시 참조 `refs/yobi/pull-check/<소유자>/<이름>/<브랜치>`로 가져와 확인한 뒤 지운다
 * - 요청이 있는 동안의 병합 결과: 보내는 브랜치를 `refs/yobi/pull/<번호>/head`, 병합 커밋을 `refs/yobi/pull/<번호>/merged`에 둔다
 *
 * 가져오기와 참조 갱신은 저장소 단위로 한 번에 하나씩만 실행한다(v1.6 주석 "locking this repository is required because of fetch and update").
 */
@Component
class PullRequestMerger(private val repositories: RepositoryService, private val identity: PullRequestIdentity) {

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    /** [pullRequest]의 보내는 브랜치를 받는 브랜치에 병합할 수 있는지 시험한다. 저장소를 바꾸지 않는다(임시 참조는 지운다). */
    fun attempt(pullRequest: PullRequest): MergeAttempt = locked(pullRequest) { repository ->
        val temp = "refs/yobi/pull-check/${pullRequest.fromProject?.owner}/${pullRequest.fromProject?.name}/${pullRequest.fromBranch}"
        fetch(repository, pullRequest, temp)
        try {
            merge(repository, requireNotNull(pullRequest.toBranch), temp).first
        } finally {
            repository.updateRef(temp).apply { isForceUpdate = true }.delete()
        }
    }

    /**
     * 보내는 브랜치를 `refs/yobi/pull/<번호>/head`로 가져와 병합하고, 충돌하지 않으면 병합 커밋을 만들어 `refs/yobi/pull/<번호>/merged`에 둔다.
     * 병합 커밋은 사이트 이름과 시스템 메일 주소로 만든다.
     */
    fun update(pullRequest: PullRequest): MergeUpdate = locked(pullRequest) { repository ->
        val head = headRef(pullRequest)
        fetch(repository, pullRequest, head)
        val (attempt, treeId) = merge(repository, requireNotNull(pullRequest.toBranch), head)
        if (attempt.conflict) return@locked MergeUpdate(attempt, null)
        val commitId = createCommit(repository, pullRequest, attempt, treeId!!, identity.system(), reuse = true)
        updateRef(repository, mergedRef(pullRequest), commitId, identity.system())
        MergeUpdate(attempt, commitId)
    }

    /**
     * 요청을 받아들인다. 보내는 브랜치를 가져와 병합하고, 충돌하지 않으면 병합 커밋을 [sender] 이름으로 만들어 받는 브랜치를 갱신한다.
     * 충돌하면 아무것도 바꾸지 않고 `null`이다.
     */
    fun merge(pullRequest: PullRequest, sender: PersonIdent): MergeUpdate? = locked(pullRequest) { repository ->
        val head = headRef(pullRequest)
        fetch(repository, pullRequest, head)
        val (attempt, treeId) = merge(repository, requireNotNull(pullRequest.toBranch), head)
        if (attempt.conflict) return@locked null
        val commitId = createCommit(repository, pullRequest, attempt, treeId!!, sender, reuse = false)
        updateRef(repository, requireNotNull(pullRequest.toBranch), commitId, sender)
        MergeUpdate(attempt, commitId)
    }

    /** [from]에서 [to]까지의 범위에 있는 커밋을 최근 것이 앞이 되게 돌려준다 (v1.6 `GitRepository.diffCommits`). */
    fun commitsBetween(pullRequest: PullRequest, from: String, to: String): List<CommitInfo> =
        repositories.openRepository(requireNotNull(pullRequest.toProject)).use { repository ->
            val sinceId = repository.resolve(from) ?: return emptyList()
            val untilId = repository.resolve(to) ?: return emptyList()
            commits(repository, sinceId, untilId)
        }

    /** 이 풀 리퀘스트 보내는 브랜치의 참조 이름이다 (v1.6 `getRefNameToFetchedSource`). */
    fun headRef(pullRequest: PullRequest) = "refs/yobi/pull/${pullRequest.id}/head"

    /** 이 풀 리퀘스트 병합 결과의 참조 이름이다 (v1.6 `getNameOfRefToMerged`). */
    fun mergedRef(pullRequest: PullRequest) = "refs/yobi/pull/${pullRequest.id}/merged"

    private fun <T> locked(pullRequest: PullRequest, action: (Repository) -> T): T {
        val lock = locks.computeIfAbsent(requireNotNull(pullRequest.toProject?.id)) { ReentrantLock() }
        lock.lock()
        try {
            return repositories.openRepository(requireNotNull(pullRequest.toProject)).use(action)
        } finally {
            lock.unlock()
        }
    }

    private fun fetch(repository: Repository, pullRequest: PullRequest, destination: String) {
        val source = repositories.openRepository(requireNotNull(pullRequest.fromProject)).use { it.directory }
        Git(repository).fetch().setRemote(source.toURI().toString())
            .setRefSpecs(RefSpec().setSource(pullRequest.fromBranch).setDestination(destination).setForceUpdate(true)).call()
    }

    /** [left]와 [right] 참조를 병합한다. 충돌하지 않으면 병합 결과의 트리 번호를 함께 돌려준다. */
    private fun merge(repository: Repository, left: String, right: String): Pair<MergeAttempt, ObjectId?> {
        val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
        val leftParent = requireNotNull(repository.resolve(left)) { "Ref '$left' does not exist in Git repository '$repository'" }
        val rightParent = requireNotNull(repository.resolve(right)) { "Ref '$right' does not exist in Git repository '$repository'" }
        val merged = merger.merge(leftParent, rightParent)
        val commits = commits(repository, leftParent, rightParent)
        return MergeAttempt(!merged, commits, leftParent, rightParent) to (if (merged) merger.resultTreeId else null)
    }

    private fun commits(repository: Repository, from: ObjectId, to: ObjectId): List<CommitInfo> =
        Git(repository).log().addRange(from, to).call().map { info(it) }

    private fun info(commit: RevCommit) = CommitInfo(
        id = commit.name, shortId = commit.abbreviate(7).name(), shortMessage = commit.shortMessage, message = commit.fullMessage,
        authorName = commit.authorIdent.name, authorEmail = commit.authorIdent.emailAddress, authorDate = commit.authorIdent.`when`,
        committerName = commit.committerIdent.name, committerEmail = commit.committerIdent.emailAddress, commitTime = Date(commit.commitTime * 1000L), parentCount = commit.parentCount
    )

    /**
     * 병합 커밋을 만든다. [reuse]이고 이미 있는 병합 결과 참조가 같은 두 부모를 가지면 그 트리를 다시 쓴다(v1.6 `getMergedTreeIfReusable`).
     */
    private fun createCommit(repository: Repository, pullRequest: PullRequest, attempt: MergeAttempt, treeId: ObjectId, who: PersonIdent, reuse: Boolean): ObjectId {
        val builder = CommitBuilder()
        builder.setTreeId(if (reuse) reusableTree(repository, pullRequest, attempt) ?: treeId else treeId)
        builder.setParentIds(attempt.leftParent, attempt.rightParent)
        builder.author = who
        builder.setCommitter(who)
        builder.message = message(pullRequest, attempt.commits)
        repository.newObjectInserter().use { inserter ->
            val id = inserter.insert(builder)
            inserter.flush()
            return id
        }
    }

    private fun reusableTree(repository: Repository, pullRequest: PullRequest, attempt: MergeAttempt): ObjectId? = try {
        val id = repository.findRef(mergedRef(pullRequest))?.objectId
        if (id == null) null else RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(id)
            if (commit.parentCount == 2 && commit.getParent(0).id == attempt.leftParent && commit.getParent(1).id == attempt.rightParent) commit.tree.id else null
        }
    } catch (e: Exception) {
        null
    }

    private fun updateRef(repository: Repository, ref: String, id: ObjectId, who: PersonIdent) {
        val update = repository.updateRef(ref)
        update.setNewObjectId(id)
        update.isForceUpdate = true
        update.setRefLogIdent(who)
        update.setRefLogMessage("merged", true)
        when (val result = update.update()) {
            // v1.6은 NO_CHANGE(같은 초에 같은 병합 커밋을 다시 만든 경우)를 오류로 보았다. 결과가 같으므로 성공으로 본다 ([decisions.md] A-77).
            RefUpdate.Result.NEW, RefUpdate.Result.FAST_FORWARD, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE -> Unit
            else -> throw IllegalStateException("참조 $ref 를 갱신하지 못했다: $result")
        }
    }

    /**
     * 병합 커밋 메시지이다 (v1.6 `makeMergeCommitMessage`). 예:
     *
     * ```
     * Merge branch 'dev' of dlab/hive into 'next'
     *
     * from pull-request 10
     *
     * * dev:
     *   첫 커밋
     *
     * Reviewed-by: 이름 <메일>
     * ```
     */
    fun message(pullRequest: PullRequest, commits: List<CommitInfo>): String {
        val builder = StringBuilder("Merge branch '${Repository.shortenRefName(pullRequest.fromBranch)}'")
        if (pullRequest.fromProject?.id != pullRequest.toProject?.id) builder.append(" of ${pullRequest.fromProject?.owner}/${pullRequest.fromProject?.name}")
        if (pullRequest.toBranch == "refs/heads/master") builder.append("\n\n") else builder.append(" into '${Repository.shortenRefName(pullRequest.toBranch)}'\n\n")
        builder.append("from pull-request ${pullRequest.number}\n\n")
        builder.append("* ${Repository.shortenRefName(pullRequest.fromBranch)}:\n")
        commits.forEach { builder.append("  ${it.shortMessage}\n") }
        builder.append("\n")
        pullRequest.reviewers.forEach { builder.append("Reviewed-by: ${it.name} <${it.email}>\n") }
        return builder.toString()
    }
}
