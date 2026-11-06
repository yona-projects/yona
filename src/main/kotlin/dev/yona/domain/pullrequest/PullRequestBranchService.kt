package dev.yona.domain.pullrequest

import dev.yona.repository.VcsBackends
import org.springframework.stereotype.Service

/**
 * 병합된 풀 리퀘스트의 보내는 브랜치를 지우거나 되살린다. v1.6 `GitRepository.canDeleteFromBranch`, `deleteFromBranch`, `canRestoreBranch`,
 * `restoreBranch`(`app/playRepository/GitRepository.java:1263-1365`)에 해당한다. 저장소를 종류와 무관한 계약([VcsBackends])으로 읽으므로 Git과 Mercurial이
 * 같이 쓴다(Mercurial은 이름표가 브랜치이다, D-86).
 *
 * 보내는 브랜치는 받는 프로젝트의 저장소가 그 머리 커밋을 이미 가지고 있고(병합됨) 보내는 저장소의 현재(HEAD) 브랜치가 아닐 때만 지울 수 있다.
 * 지울 때 머리 커밋 번호를 요청의 `lastCommitId`에 남기고, 되살릴 때 그 커밋에서 같은 이름의 브랜치를 다시 만든다.
 */
@Service
class PullRequestBranchService(private val backends: VcsBackends) {

    /** 보내는 브랜치를 지울 수 있으면 `true`이다. */
    fun canDelete(pullRequest: PullRequest): Boolean {
        val from = requireNotNull(pullRequest.fromProject)
        val to = requireNotNull(pullRequest.toProject)
        val branch = pullRequest.fromBranch
        val refs = backends.refs(from.vcs)
        val head = refs.branches(from).firstOrNull { it.name == branch } ?: return false
        if (branch == refs.headBranch(from)?.name) return false
        // 삭제를 막는 규칙 묶음이 있는 브랜치는 지우지 않는다(`decisions.md` D-46, A-251). 이 경로는 관리자 우회를 두지 않는다
        if (from.rulesetsFor(branch).any { it.pushViolation(dev.yona.domain.vcs.RefUpdateType.DELETE) != null }) return false
        return backends.commits(to.vcs).commit(to, head.headCommitId) != null
    }

    /** 보내는 브랜치를 지우고 지운 머리 커밋 번호를 돌려준다. 지울 수 없으면 `null`이다. */
    fun delete(pullRequest: PullRequest): String? {
        if (!canDelete(pullRequest)) return null
        val from = requireNotNull(pullRequest.fromProject)
        val refs = backends.refs(from.vcs)
        val last = refs.branches(from).first { it.name == pullRequest.fromBranch }.headCommitId
        refs.deleteBranch(from, requireNotNull(pullRequest.fromBranch))
        return last
    }

    /** 보내는 브랜치가 없고 지울 때 남긴 커밋 번호가 있으면 `true`이다. */
    fun canRestore(pullRequest: PullRequest): Boolean {
        val from = requireNotNull(pullRequest.fromProject)
        return pullRequest.lastCommitId != null && backends.refs(from.vcs).branches(from).none { it.name == pullRequest.fromBranch }
    }

    /** 지워진 보내는 브랜치를 마지막 커밋에서 다시 만든다. 되살릴 수 없으면 아무것도 하지 않는다. */
    fun restore(pullRequest: PullRequest) {
        if (!canRestore(pullRequest)) return
        val from = requireNotNull(pullRequest.fromProject)
        backends.refs(from.vcs).createBranch(from, pullRequest.fromBranch.orEmpty().replace("refs/heads/", ""), requireNotNull(pullRequest.lastCommitId))
    }
}
