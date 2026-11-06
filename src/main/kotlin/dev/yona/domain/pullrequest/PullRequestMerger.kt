package dev.yona.domain.pullrequest

import dev.yona.repository.CommitInfo
import dev.yona.domain.vcs.VcsType
import org.springframework.stereotype.Component

/**
 * 풀 리퀘스트를 받는 프로젝트의 저장소에서 병합을 시도하고 실행한다. v1.6 `PullRequest.Merger`, `attemptMerge`, `updateMerge`, `merge`,
 * `fetchSourceBranch`(`app/models/PullRequest.java:356-570,872-950`)에 해당한다.
 *
 * 저장소 종류마다 다른 일(보내는 브랜치 가져오기, 병합 커밋 만들기, 참조 갱신)은 [PullRequestBackend] 구현이 하고, 이 클래스는 받는 프로젝트의 `vcs` 값으로
 * 구현을 골라 넘기기만 한다(Mercurial 도입, D-86). 병합 커밋 메시지는 종류와 무관하게 [MergeMessages]가 만든다.
 */
@Component
class PullRequestMerger(backends: List<PullRequestBackend>) {

    private val backends: Map<VcsType, PullRequestBackend> = backends.associateBy { it.type }

    /**
     * 받는 프로젝트의 종류에 맞는 구현이다. 구현이 없는(풀 리퀘스트를 지원하지 않는) 종류는 지원하지 않는 VCS이다. 구현이 저장소 서비스를 쓰므로
     * 저장소 서비스가 쓰는 [dev.yona.repository.VcsBackends]에 두지 않고 여기서 직접 받는다(순환 의존을 피한다).
     */
    private fun backend(pullRequest: PullRequest): PullRequestBackend {
        val vcs = pullRequest.toProject?.vcs
        return VcsType.of(vcs)?.let { backends[it] } ?: throw UnsupportedOperationException("지원하지 않는 VCS입니다: $vcs")
    }

    /** [pullRequest]의 보내는 브랜치를 받는 브랜치에 병합할 수 있는지 시험한다. 저장소를 바꾸지 않는다. */
    fun attempt(pullRequest: PullRequest): MergeAttempt = backend(pullRequest).attempt(pullRequest)

    /** 보내는 브랜치를 숨은 참조로 가져와 병합하고, 충돌하지 않으면 병합 커밋을 만들어 병합 결과 참조에 둔다. */
    fun update(pullRequest: PullRequest): MergeUpdate = backend(pullRequest).update(pullRequest)

    /**
     * 요청을 받아들인다. 보내는 브랜치를 가져와 [method] 방식으로 병합하고 받는 브랜치를 갱신한다. 새 커밋은 [sender] 이름으로 만든다. 충돌하면 `null`이다
     * ([PullRequestBackend.merge]).
     */
    fun merge(
        pullRequest: PullRequest, sender: MergePerson, method: MergeMethod = MergeMethod.MERGE, commitTitle: String? = null, commitMessage: String? = null
    ): MergeUpdate? = backend(pullRequest).merge(pullRequest, sender, method, commitTitle, commitMessage)

    /** 리베이스 병합을 할 수 없는 이유이다. 할 수 있으면 `null`이다([PullRequestBackend.rebaseBlocked]). */
    fun rebaseBlocked(pullRequest: PullRequest): String? = backend(pullRequest).rebaseBlocked(pullRequest)

    /** 병합 화면이 확인 칸에 미리 채울 기본 제목과 본문이다(추가 개발 항목 X-28). [shortMessages]는 최근 것이 앞이다. */
    fun defaults(pullRequest: PullRequest, method: MergeMethod, shortMessages: List<String>): Pair<String, String> = MergeMessages.defaults(pullRequest, method, shortMessages)

    /** 기본 병합 커밋 메시지이다 (v1.6 `makeMergeCommitMessage`). */
    fun message(pullRequest: PullRequest, commits: List<CommitInfo>): String = MergeMessages.message(pullRequest, commits)

    /** [from]에서 [to]까지의 범위에 있는 커밋을 최근 것이 앞이 되게 돌려준다 (v1.6 `GitRepository.diffCommits`). */
    fun commitsBetween(pullRequest: PullRequest, from: String, to: String): List<CommitInfo> = backend(pullRequest).commitsBetween(pullRequest, from, to)

    /** 이 풀 리퀘스트 보내는 브랜치의 숨은 참조 이름이다 (v1.6 `getRefNameToFetchedSource`). */
    fun headRef(pullRequest: PullRequest) = backend(pullRequest).headRef(pullRequest)

    /** 이 풀 리퀘스트 병합 결과의 숨은 참조 이름이다 (v1.6 `getNameOfRefToMerged`). */
    fun mergedRef(pullRequest: PullRequest) = backend(pullRequest).mergedRef(pullRequest)
}
