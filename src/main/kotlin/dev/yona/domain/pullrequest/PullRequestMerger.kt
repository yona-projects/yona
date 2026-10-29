package dev.yona.domain.pullrequest

import dev.yona.repository.CommitInfo
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
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
     * 요청을 받아들인다. 보내는 브랜치를 가져와 [method] 방식으로 병합하고 받는 브랜치를 갱신한다. 새 커밋은 [sender] 이름으로 만든다.
     *
     * - [MergeMethod.MERGE]: 부모가 둘인 병합 커밋(v1.6)
     * - [MergeMethod.SQUASH]: 같은 병합 결과 트리에 부모가 받는 브랜치의 머리 하나뿐인 커밋. 작성자는 풀 리퀘스트 작성자이다
     * - [MergeMethod.REBASE]: 원래 커밋을 받는 브랜치 위에 순서대로 다시 적용한다. 메시지와 작성자는 그대로이고 커밋한 사람만 [sender]이다
     *
     * 충돌하거나(리베이스는 한 커밋에서라도), 리베이스인데 병합 커밋이 섞여 있으면 아무것도 바꾸지 않고 `null`이다.
     *
     * @param commitTitle 병합 커밋과 스쿼시 커밋의 제목. 비우면 기본 제목이다(리베이스는 쓰지 않는다)
     * @param commitMessage 병합 커밋과 스쿼시 커밋의 본문. 비우면 기본 본문이다(리베이스는 쓰지 않는다)
     */
    fun merge(
        pullRequest: PullRequest, sender: PersonIdent, method: MergeMethod = MergeMethod.MERGE, commitTitle: String? = null, commitMessage: String? = null
    ): MergeUpdate? = locked(pullRequest) { repository ->
        val head = headRef(pullRequest)
        fetch(repository, pullRequest, head)
        val (attempt, treeId) = merge(repository, requireNotNull(pullRequest.toBranch), head)
        if (attempt.conflict) return@locked null
        val toBranch = requireNotNull(pullRequest.toBranch)
        val commitId = when (method) {
            MergeMethod.MERGE -> createCommit(repository, pullRequest, attempt, treeId!!, sender, reuse = false, customTitle = commitTitle, customMessage = commitMessage)
            MergeMethod.SQUASH -> createSquash(repository, pullRequest, attempt, treeId!!, sender, commitTitle, commitMessage)
            MergeMethod.REBASE -> (replay(repository, attempt, sender) as? Replay.Done)?.tip ?: return@locked null
        }
        updateRef(repository, toBranch, commitId, sender)
        MergeUpdate(attempt, commitId)
    }

    /**
     * 리베이스 병합을 할 수 없는 이유이다. 할 수 있으면 `null`, 커밋 하나라도 충돌하면 `conflict`, 병합 커밋이 섞여 있으면 `merge-commits`이다.
     * 받는 저장소의 참조와 작업 트리는 바꾸지 않는다(임시 참조는 지운다). 커밋이 [REBASE_CHECK_LIMIT]개를 넘으면 시험하지 않고 할 수 있는 것으로 본다
     * (`decisions.md` D-41 7번).
     */
    fun rebaseBlocked(pullRequest: PullRequest): String? = locked(pullRequest) { repository ->
        val temp = "refs/yobi/pull-check/${pullRequest.fromProject?.owner}/${pullRequest.fromProject?.name}/${pullRequest.fromBranch}"
        fetch(repository, pullRequest, temp)
        try {
            val (attempt, _) = merge(repository, requireNotNull(pullRequest.toBranch), temp)
            if (attempt.commits.size > REBASE_CHECK_LIMIT) return@locked null
            when (replay(repository, attempt, null)) {
                is Replay.Done -> null
                Replay.Conflict -> "conflict"
                Replay.MergeCommits -> "merge-commits"
            }
        } finally {
            repository.updateRef(temp).apply { isForceUpdate = true }.delete()
        }
    }

    /** 리베이스 재적용의 결과이다. */
    private sealed interface Replay {
        data class Done(val tip: ObjectId) : Replay
        data object Conflict : Replay
        data object MergeCommits : Replay
    }

    /**
     * [attempt]의 커밋을 오래된 것부터 받는 브랜치 머리 위에 하나씩 다시 적용한다. [committer]가 있으면 커밋을 만들고 마지막 커밋을 돌려주며, 없으면 트리만 합쳐 가능 여부만 본다.
     */
    private fun replay(repository: Repository, attempt: MergeAttempt, committer: PersonIdent?): Replay {
        RevWalk(repository).use { walk ->
            val originals = attempt.commits.reversed().map { walk.parseCommit(repository.resolve(it.id)) }
            if (originals.any { it.parentCount != 1 }) return Replay.MergeCommits
            var tip: ObjectId = attempt.leftParent
            var tree: ObjectId = walk.parseCommit(attempt.leftParent).tree.id
            repository.newObjectInserter().use { inserter ->
                for (original in originals) {
                    val merger = V16ResolveMerger(repository)
                    merger.setBase(walk.parseCommit(original.getParent(0)).tree.id)
                    if (!merger.merge(tree, original.tree.id)) return Replay.Conflict
                    tree = merger.resultTreeId
                    if (committer != null) {
                        val builder = CommitBuilder()
                        builder.setTreeId(tree)
                        builder.setParentId(tip)
                        builder.author = original.authorIdent
                        builder.setCommitter(committer)
                        builder.message = original.fullMessage
                        tip = inserter.insert(builder)
                    }
                }
                inserter.flush()
            }
            return Replay.Done(tip)
        }
    }

    private fun createSquash(
        repository: Repository, pullRequest: PullRequest, attempt: MergeAttempt, treeId: ObjectId, who: PersonIdent, customTitle: String?, customMessage: String?
    ): ObjectId {
        val builder = CommitBuilder()
        builder.setTreeId(treeId)
        builder.setParentId(attempt.leftParent)
        val contributor = pullRequest.contributor
        builder.author = PersonIdent(contributor?.name ?: contributor?.loginId ?: who.name, contributor?.email ?: who.emailAddress)
        builder.setCommitter(who)
        val title = customTitle?.takeIf { it.isNotBlank() } ?: squashTitle(pullRequest)
        val body = customMessage?.takeIf { it.isNotBlank() } ?: defaults(pullRequest, MergeMethod.SQUASH, attempt.commits.map { it.shortMessage }).second
        builder.message = compose(title, body, pullRequest)
        repository.newObjectInserter().use { inserter ->
            val id = inserter.insert(builder)
            inserter.flush()
            return id
        }
    }

    private fun mergeTitle(pullRequest: PullRequest): String {
        val builder = StringBuilder("Merge branch '${Repository.shortenRefName(pullRequest.fromBranch)}'")
        if (pullRequest.fromProject?.id != pullRequest.toProject?.id) builder.append(" of ${pullRequest.fromProject?.owner}/${pullRequest.fromProject?.name}")
        if (pullRequest.toBranch != "refs/heads/master") builder.append(" into '${Repository.shortenRefName(pullRequest.toBranch)}'")
        return builder.toString()
    }

    /** 병합 커밋의 기본 본문이다. 끝은 줄바꿈이다(v1.6 `makeMergeCommitMessage`). [shortMessages]는 최근 것이 앞이다. */
    private fun mergeBody(pullRequest: PullRequest, shortMessages: List<String>): String {
        val builder = StringBuilder("from pull-request ${pullRequest.number}\n\n* ${Repository.shortenRefName(pullRequest.fromBranch)}:\n")
        shortMessages.forEach { builder.append("  $it\n") }
        return builder.toString()
    }

    private fun squashTitle(pullRequest: PullRequest) = "${pullRequest.title} (#${pullRequest.number})"

    /**
     * 병합 화면이 확인 칸에 미리 채울 기본 제목과 본문이다(추가 개발 항목 X-28). 비우지 않고 보내면 아무것도 보내지 않은 것과 같은 커밋 메시지가 된다.
     * 리뷰어 꼬리말은 병합할 때 붙으므로 여기에 없다. 리베이스는 메시지를 쓰지 않아 둘 다 비어 있다. [shortMessages]는 최근 것이 앞이다.
     */
    fun defaults(pullRequest: PullRequest, method: MergeMethod, shortMessages: List<String>): Pair<String, String> = when (method) {
        MergeMethod.MERGE -> mergeTitle(pullRequest) to mergeBody(pullRequest, shortMessages)
        MergeMethod.SQUASH -> squashTitle(pullRequest) to shortMessages.reversed().joinToString("\n") { "* $it" }
        MergeMethod.REBASE -> "" to ""
    }

    /** 제목, 본문, 리뷰한 사람 순서로 메시지를 만든다. */
    private fun compose(title: String, body: String, pullRequest: PullRequest): String {
        val trailer = pullRequest.reviewers.joinToString("") { "Reviewed-by: ${it.name} <${it.email}>\n" }
        return buildString {
            append(title.trim()).append("\n\n").append(body.trim()).append("\n")
            if (trailer.isNotEmpty()) append("\n").append(trailer)
        }
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
        val merger = V16RecursiveMerger(repository)
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
    private fun createCommit(
        repository: Repository, pullRequest: PullRequest, attempt: MergeAttempt, treeId: ObjectId, who: PersonIdent, reuse: Boolean,
        customTitle: String? = null, customMessage: String? = null
    ): ObjectId {
        val builder = CommitBuilder()
        builder.setTreeId(if (reuse) reusableTree(repository, pullRequest, attempt) ?: treeId else treeId)
        builder.setParentIds(attempt.leftParent, attempt.rightParent)
        builder.author = who
        builder.setCommitter(who)
        builder.message = if (customTitle.isNullOrBlank() && customMessage.isNullOrBlank()) message(pullRequest, attempt.commits) else {
            compose(
                customTitle?.takeIf { it.isNotBlank() } ?: mergeTitle(pullRequest),
                customMessage?.takeIf { it.isNotBlank() } ?: mergeBody(pullRequest, attempt.commits.map { it.shortMessage }), pullRequest
            )
        }
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
        val builder = StringBuilder(mergeTitle(pullRequest)).append("\n\n")
        builder.append(mergeBody(pullRequest, commits.map { it.shortMessage })).append("\n")
        pullRequest.reviewers.forEach { builder.append("Reviewed-by: ${it.name} <${it.email}>\n") }
        return builder.toString()
    }

    private companion object {
        /** 리베이스 가능 여부를 시험하는 커밋 수의 상한이다. 넘으면 시험하지 않는다. */
        const val REBASE_CHECK_LIMIT = 200
    }
}
