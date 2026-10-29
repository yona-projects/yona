package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Service
import java.util.Date

/**
 * 브랜치 한 줄이다. v1.6 `playRepository.GitBranch`에 해당한다.
 *
 * @property name 참조 전체 이름(`refs/heads/master`, `refs/tags/v1`)
 * @property shortName 화면에 보여 줄 이름. `refs/heads/` 접두어를 뗀다(태그는 그대로)
 * @property headCommitId 머리 커밋 전체 번호
 * @property headCommitShortId 머리 커밋 7자리 번호
 * @property committerDate 머리 커밋의 커밋 시각
 * @property pullRequest 이 브랜치에서 나간 가장 최근 풀 리퀘스트. 없으면 `null`
 */
data class BranchInfo(
    val name: String, val shortName: String, val headCommitId: String, val headCommitShortId: String, val committerDate: Date, val pullRequest: PullRequest?
)

/**
 * 브랜치 목록과 삭제를 맡는다. v1.6 `GitRepository.getBranches`, `getHeadBranch`, `deleteBranch`에 해당한다(목록은 브랜치만, D-30).
 */
@Service
class BranchService(private val repositories: RepositoryService, private val pullRequests: PullRequestRepository) {

    /**
     * `refs/heads/` 아래의 브랜치만 머리 커밋이 최근인 순서로 모은다.
     *
     * v1.6 `getBranches`는 태그와 원격 참조(`refs/remotes/` 아래)까지 "브랜치"로 모았다. 태그와 원격 참조는 브랜치 화면과 풀 리퀘스트의
     * 출발, 도착 목록에서 의미가 없어(`decisions.md` D-30) 이 함수의 결과에서 뺀다. 커밋을 읽기 전에 거르므로 커밋이 아닌 대상을
     * 가리키는 태그가 있어도 실패하지 않는다. 태그와 원격 참조를 포함한 이름은 [RepositoryService.refNames]를 쓴다.
     */
    fun branches(project: Project): List<BranchInfo> = refs(project, listOf(Constants.R_HEADS))

    /** [prefixes]로 시작하는 참조를 머리 커밋이 최근인 순서로 모은다. 커밋을 가리키지 않는 참조는 건너뛴다. */
    private fun refs(project: Project, prefixes: List<String>): List<BranchInfo> = repositories.openRepository(project).use { repository ->
        RevWalk(repository).use { walk ->
            repository.refDatabase.refs.filter { ref -> prefixes.any { ref.name.startsWith(it) } && ref.objectId != null }
                .mapNotNull { ref -> runCatching { info(project, ref.name, walk.parseCommit(ref.objectId)) }.getOrNull() }
                .sortedByDescending { it.committerDate }
        }
    }

    /** 기본 브랜치(`HEAD`가 가리키는 브랜치)이다. 저장소가 비어 `HEAD`를 풀 수 없으면 `null`이다. */
    fun headBranch(project: Project): BranchInfo? = repositories.openRepository(project).use { repository ->
        val name = repository.findRef(Constants.HEAD)?.target?.name ?: return null
        val id = repository.resolve(name) ?: return null
        RevWalk(repository).use { info(project, name, it.parseCommit(id)) }
    }

    /** 브랜치를 지운다(병합 여부와 관계없이 강제로). */
    fun delete(project: Project, branchName: String) {
        repositories.openRepository(project).use { repository ->
            Git(repository).branchDelete().setBranchNames(branchName).setForce(true).call()
        }
    }

    private fun info(project: Project, name: String, commit: RevCommit): BranchInfo = BranchInfo(
        name = name, shortName = name.removePrefix(Constants.R_HEADS), headCommitId = commit.name, headCommitShortId = commit.abbreviate(7).name(),
        committerDate = commit.committerIdent.`when`, pullRequest = latestPullRequest(project, name)
    )

    /** 포크한 프로젝트이면 원본으로 보낸 풀 리퀘스트도 찾는다 (v1.6 `PullRequest.findTheLatestOneFrom`). */
    private fun latestPullRequest(project: Project, branch: String): PullRequest? {
        val ids = listOfNotNull(project.id, project.originalProject?.id)
        return pullRequests.findFirstByFromProjectIdAndFromBranchAndToProjectIdInOrderByNumberDesc(requireNotNull(project.id), branch, ids)
    }
}
