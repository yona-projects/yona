package dev.yona.domain.pullrequest

import dev.yona.domain.vcs.VcsType
import dev.yona.repository.CommitInfo
import dev.yona.repository.hg.HgCommitReader
import dev.yona.repository.hg.HgPullRefs
import dev.yona.repository.hg.HgRepositories
import dev.yona.repository.hg.HgRevisions
import io.github.search5.hg4j.api.BookmarkCommand
import io.github.search5.hg4j.api.GraftInMemory
import io.github.search5.hg4j.api.MergeCommitCommand
import io.github.search5.hg4j.api.TreeMergeCommand
import io.github.search5.hg4j.util.NodeIdUtil
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Mercurial 저장소의 풀 리퀘스트 병합 구현이다([PullRequestBackend], X-7 8b와 8c, `decisions.md` D-86, D-87). Git의 [GitPullRequestBackend]와 같은 일을 한다.
 *
 * - **가져오기**: 보내는 프로젝트(포크)의 체인지셋을 받는 저장소로 가져와 secret 단계로 두고 예약 이름표 `yona/pull/<번호>/head`로 가리킨다([HgPullRefs]).
 * - **병합 가능 여부**: 저장소를 쓰지 않는 hg4j `TreeMergeCommand`로 계산한다. 파일 이름 바꿈과 복사는 따라가고, 폴더 이름 바꿈과 한쪽 삭제와 다른 쪽
 *   수정과 파일 단위 병합 도구가 필요한 병합은 충돌로 본다(`mercurial-plan.md` 4.2절).
 * - **병합 커밋**: hg4j `MergeCommitCommand`로 부모가 둘인 체인지셋을 만든다(p1은 받는 쪽 머리, p2는 보내는 쪽 머리, Git의 `--no-ff`처럼 한쪽이
 *   다른 쪽의 조상이어도 만든다). 요청이 있는 동안에는 secret이고 예약 이름표 `…/merged`가 가리키며, 받아들이면 공개 단계로 올리고 받는 이름표를 옮긴다.
 * - **스쿼시**는 hg4j `MergeCommitCommand.setSquash`(부모가 받는 머리 하나뿐), **리베이스**는 `GraftInMemory`(작업 파일 없이 그래프트)로 한다.
 *
 * 가져오기와 참조 갱신은 받는 프로젝트 단위로 한 번에 하나씩만 실행한다.
 */
@Component
class HgPullRequestBackend(
    private val repositories: HgRepositories,
    private val refs: HgPullRefs,
    private val commits: HgCommitReader,
    private val identity: PullRequestIdentity
) : PullRequestBackend {

    override val type: VcsType get() = VcsType.MERCURIAL

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    /** 병합 시험의 결과와 두 머리이다. */
    private class Merged(val conflict: Boolean, val commits: List<CommitInfo>, val left: String, val right: String)

    override fun attempt(pullRequest: PullRequest): MergeAttempt = locked(pullRequest) {
        val merged = prepare(pullRequest, refName = null)
        MergeAttempt(merged.conflict, merged.commits, merged.left, merged.right)
    }

    override fun update(pullRequest: PullRequest): MergeUpdate = locked(pullRequest) {
        val merged = prepare(pullRequest, refName = headRef(pullRequest))
        val attempt = MergeAttempt(merged.conflict, merged.commits, merged.left, merged.right)
        if (merged.conflict) return@locked MergeUpdate(attempt, null)
        val to = requireNotNull(pullRequest.toProject)
        // 이미 있는 병합 결과가 같은 두 부모를 가지면 다시 쓴다(v1.6 `getMergedTreeIfReusable`)
        val reusable = refs.resolve(owner(to), name(to), mergedRef(pullRequest))?.takeIf { parentsOf(to, it) == listOf(merged.left, merged.right) }
        val node = reusable ?: createMerge(pullRequest, merged, identity.system().let { "${it.name} <${it.email}>" })
        refs.point(owner(to), name(to), mergedRef(pullRequest), node)
        MergeUpdate(attempt, node)
    }

    override fun merge(pullRequest: PullRequest, sender: MergePerson, method: MergeMethod, commitTitle: String?, commitMessage: String?): MergeUpdate? = locked(pullRequest) {
        val merged = prepare(pullRequest, refName = headRef(pullRequest))
        if (merged.conflict) return@locked null
        val to = requireNotNull(pullRequest.toProject)
        val attempt = MergeAttempt(false, merged.commits, merged.left, merged.right)
        val node = when (method) {
            MergeMethod.MERGE -> createMerge(pullRequest, merged, user(sender), mergeMessage(pullRequest, merged, commitTitle, commitMessage, MergeMethod.MERGE))
            // 스쿼시: 같은 병합 결과 트리에 부모가 받는 머리 하나뿐인 커밋이다. 작성자는 풀 리퀘스트 작성자이다(Mercurial에는 커미터가 따로 없다)
            MergeMethod.SQUASH -> createMerge(pullRequest, merged, contributorUser(pullRequest, sender), mergeMessage(pullRequest, merged, commitTitle, commitMessage, MergeMethod.SQUASH), squash = true)
            MergeMethod.REBASE -> rebase(pullRequest, merged) ?: return@locked null
        }
        // 새 커밋과 가져온 체인지셋을 공개로 올려 `clone`, `pull`에 보이게 하고 받는 이름표를 옮긴다
        refs.publish(owner(to), name(to), node)
        repositories.open(owner(to), name(to)).use { hg ->
            BookmarkCommand(hg.repository).setBookmarkName(MergeMessages.shortName(pullRequest.toBranch)).setRevision(node).setForce(true).call()
        }
        MergeUpdate(attempt, node)
    }

    /** 병합 커밋과 스쿼시 커밋의 메시지이다. 제목과 본문을 직접 정하지 않았으면 기본 글이다([MergeMessages]). */
    private fun mergeMessage(pullRequest: PullRequest, merged: Merged, title: String?, body: String?, method: MergeMethod): String {
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            return if (method == MergeMethod.MERGE) MergeMessages.message(pullRequest, merged.commits) else {
                val defaults = MergeMessages.defaults(pullRequest, method, merged.commits.map { it.shortMessage })
                MergeMessages.compose(defaults.first, defaults.second, pullRequest)
            }
        }
        val defaults = MergeMessages.defaults(pullRequest, method, merged.commits.map { it.shortMessage })
        return MergeMessages.compose(title?.takeIf { it.isNotBlank() } ?: defaults.first, body?.takeIf { it.isNotBlank() } ?: defaults.second, pullRequest)
    }

    private fun user(person: MergePerson) = "${person.name} <${person.email}>"

    private fun contributorUser(pullRequest: PullRequest, fallback: MergePerson): String {
        val contributor = pullRequest.contributor
        return user(MergePerson(contributor?.name ?: contributor?.loginId ?: fallback.name, contributor?.email ?: fallback.email))
    }

    /**
     * 원래 체인지셋을 오래된 것부터 받는 쪽 머리 위에 하나씩 다시 적용한다(hg4j `GraftInMemory`, 작업 파일 없이). 작성자, 날짜, 메시지는 그대로이다. 새 마지막
     * 체인지셋의 노드를 돌려주고, 충돌하거나 병합 체인지셋이 섞여 있으면 아무것도 바꾸지 않고 `null`이다.
     */
    private fun rebase(pullRequest: PullRequest, merged: Merged): String? {
        if (merged.commits.any { it.parentCount != 1 }) return null
        val to = requireNotNull(pullRequest.toProject)
        return repositories.open(owner(to), name(to)).use { hg ->
            val graft = GraftInMemory(hg.repository).setTo(merged.left)
            merged.commits.reversed().forEach { graft.addRevision(it.id) }
            val result = graft.execute()
            if (result.isConflicted) null else result.nodeHexes.last()
        }
    }

    /** 리베이스를 할 수 없는 이유이다: 병합 체인지셋이 섞여 있으면 `merge-commits`, 한 체인지셋이라도 충돌하면 `conflict`, 할 수 있으면 `null`이다. */
    override fun rebaseBlocked(pullRequest: PullRequest): String? = locked(pullRequest) {
        val merged = prepare(pullRequest, refName = null)
        if (merged.commits.isEmpty() || merged.commits.size > REBASE_CHECK_LIMIT) return@locked null
        if (merged.commits.any { it.parentCount != 1 }) return@locked "merge-commits"
        val to = requireNotNull(pullRequest.toProject)
        repositories.open(owner(to), name(to)).use { hg ->
            val graft = GraftInMemory(hg.repository).setTo(merged.left).setDryRun(true)
            merged.commits.reversed().forEach { graft.addRevision(it.id) }
            if (graft.execute().isConflicted) "conflict" else null
        }
    }

    override fun commitsBetween(pullRequest: PullRequest, from: String, to: String): List<CommitInfo> = commits.range(requireNotNull(pullRequest.toProject), from, to)

    override fun headRef(pullRequest: PullRequest): String = refs.headRef(requireNotNull(pullRequest.id))

    override fun mergedRef(pullRequest: PullRequest): String = refs.mergedRef(requireNotNull(pullRequest.id))

    /**
     * 보내는 쪽 머리를 받는 저장소로 가져오고(secret, [refName]이 있으면 그 이름표로 가리킨다) 받는 쪽 머리와 병합해 본다. 병합 커밋은 만들지 않는다.
     */
    private fun prepare(pullRequest: PullRequest, refName: String?): Merged {
        val to = requireNotNull(pullRequest.toProject); val from = requireNotNull(pullRequest.fromProject)
        val fromNode = repositories.open(owner(from), name(from)).use { hg -> HgRevisions.resolve(hg, pullRequest.fromBranch) }
            ?: throw IllegalArgumentException("Ref '${pullRequest.fromBranch}' does not exist in Mercurial repository '${owner(from)}/${name(from)}'")
        refs.fetch(owner(to), name(to), owner(from), name(from), fromNode, refName)
        val toNode = repositories.open(owner(to), name(to)).use { hg -> HgRevisions.resolve(hg, pullRequest.toBranch) }
            ?: throw IllegalArgumentException("Ref '${pullRequest.toBranch}' does not exist in Mercurial repository '${owner(to)}/${name(to)}'")
        val conflict = repositories.open(owner(to), name(to)).use { hg ->
            TreeMergeCommand(hg.repository).setOurs(NodeIdUtil.fromHex(toNode)).setTheirs(NodeIdUtil.fromHex(fromNode)).call().isConflicted
        }
        return Merged(conflict, commits.range(to, toNode, fromNode), toNode, fromNode)
    }

    /** 병합 체인지셋을 만든다. 부모는 (받는 쪽 머리, 보내는 쪽 머리)이고, [squash]이면 받는 쪽 머리 하나뿐이다(hg4j `setSquash`). */
    private fun createMerge(pullRequest: PullRequest, merged: Merged, user: String, message: String = MergeMessages.message(pullRequest, merged.commits), squash: Boolean = false): String {
        val to = requireNotNull(pullRequest.toProject)
        return repositories.open(owner(to), name(to)).use { hg ->
            val result = MergeCommitCommand(hg.repository).setP1(merged.left).setP2(merged.right).setUser(user).setMessage(message).setAllowAncestorMerge(true).setSquash(squash).call()
            check(!result.isConflicted) { "병합 시험은 충돌이 아니었는데 병합 커밋에서 충돌했다: ${result.conflicts}" }
            requireNotNull(result.nodeHex)
        }
    }

    private fun parentsOf(project: dev.yona.domain.project.Project, node: String): List<String> = repositories.open(owner(project), name(project)).use { hg ->
        val changelog = HgRevisions.changelog(hg)
        val rev = dev.yona.repository.hg.HgChangesets.revOf(changelog, node)
        if (rev < 0) emptyList() else dev.yona.repository.hg.HgChangesets.read(changelog, rev)?.parents.orEmpty().mapNotNull { dev.yona.repository.hg.HgChangesets.read(changelog, it)?.node }
    }

    private fun owner(project: dev.yona.domain.project.Project) = requireNotNull(project.owner)

    private fun name(project: dev.yona.domain.project.Project) = requireNotNull(project.name)

    private fun <T> locked(pullRequest: PullRequest, action: () -> T): T {
        val lock = locks.computeIfAbsent(requireNotNull(pullRequest.toProject?.id)) { ReentrantLock() }
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    private companion object {
        /** 리베이스 가능 여부를 시험하는 커밋 수의 상한이다. 넘으면 시험하지 않는다(Git과 같다, D-41 7번). */
        const val REBASE_CHECK_LIMIT = 200
    }
}
