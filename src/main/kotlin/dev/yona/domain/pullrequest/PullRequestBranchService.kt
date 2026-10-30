package dev.yona.domain.pullrequest

import dev.yona.repository.RepositoryService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Service

/**
 * 병합된 풀 리퀘스트의 보내는 브랜치를 지우거나 되살린다. v1.6 `GitRepository.canDeleteFromBranch`, `deleteFromBranch`, `canRestoreBranch`,
 * `restoreBranch`(`app/playRepository/GitRepository.java:1263-1365`)에 해당한다.
 *
 * 보내는 브랜치는 받는 프로젝트의 저장소가 그 머리 커밋을 이미 가지고 있고(병합됨) 보내는 저장소의 현재(HEAD) 브랜치가 아닐 때만 지울 수 있다.
 * 지울 때 머리 커밋 번호를 요청의 `lastCommitId`에 남기고, 되살릴 때 그 커밋에서 같은 이름의 브랜치를 다시 만든다.
 */
@Service
class PullRequestBranchService(private val repositories: RepositoryService) {

    /** 보내는 브랜치를 지울 수 있으면 `true`이다. */
    fun canDelete(pullRequest: PullRequest): Boolean = repositories.openRepository(requireNotNull(pullRequest.fromProject)).use { from ->
        val current = from.fullBranch
        val branch = pullRequest.fromBranch
        val head = from.findRef(branch)?.takeIf { it.name.startsWith(Constants.R_HEADS) }?.objectId ?: return false
        if (branch == current) return false
        // 보호 규칙이 삭제를 막는 브랜치는 지우지 않는다(`decisions.md` D-45, A-251). 이 경로는 관리자 우회를 두지 않는다
        if (pullRequest.fromProject?.protectionFor(branch)?.pushViolation(org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE) != null) return false
        val commitName = RevWalk(from).use { it.parseCommit(head).name }
        repositories.openRepository(requireNotNull(pullRequest.toProject)).use { it.resolve(commitName) != null }
    }

    /** 보내는 브랜치를 지우고 지운 머리 커밋 번호를 돌려준다. 지울 수 없으면 `null`이다. */
    fun delete(pullRequest: PullRequest): String? {
        if (!canDelete(pullRequest)) return null
        return repositories.openRepository(requireNotNull(pullRequest.fromProject)).use { from ->
            val head = requireNotNull(from.resolve(pullRequest.fromBranch))
            val last = RevWalk(from).use { it.parseCommit(head).name }
            Git(from).branchDelete().setBranchNames(pullRequest.fromBranch).setForce(true).call()
            last
        }
    }

    /** 보내는 브랜치가 없고 지울 때 남긴 커밋 번호가 있으면 `true`이다. */
    fun canRestore(pullRequest: PullRequest): Boolean = repositories.openRepository(requireNotNull(pullRequest.fromProject)).use {
        it.resolve(pullRequest.fromBranch) == null && pullRequest.lastCommitId != null
    }

    /** 지워진 보내는 브랜치를 마지막 커밋에서 다시 만든다. 되살릴 수 없으면 아무것도 하지 않는다. */
    fun restore(pullRequest: PullRequest) {
        if (!canRestore(pullRequest)) return
        repositories.openRepository(requireNotNull(pullRequest.fromProject)).use { from ->
            Git(from).branchCreate().setName(pullRequest.fromBranch.orEmpty().replace("refs/heads/", "")).setStartPoint(pullRequest.lastCommitId).call()
        }
    }
}
