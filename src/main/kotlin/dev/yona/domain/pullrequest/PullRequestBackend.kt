package dev.yona.domain.pullrequest

import dev.yona.repository.CommitInfo
import dev.yona.repository.VcsPart

/**
 * 병합을 시도한 결과이다.
 *
 * @property conflict 충돌하면 `true`
 * @property commits 받는 브랜치에 없고 보내는 브랜치에만 있는 커밋(최근 것이 앞). 풀 리퀘스트가 가져오는 커밋이다
 * @property leftParent 받는 브랜치의 머리 커밋(40자 16진수)
 * @property rightParent 보내는 브랜치의 머리 커밋(40자 16진수)
 */
data class MergeAttempt(val conflict: Boolean, val commits: List<CommitInfo>, val leftParent: String, val rightParent: String)

/**
 * 풀 리퀘스트의 병합 결과를 저장소에 반영한 것이다. [mergeCommitId](40자 16진수)는 충돌하지 않을 때만 있다.
 */
data class MergeUpdate(val attempt: MergeAttempt, val mergeCommitId: String?)

/**
 * 병합 커밋을 만드는 사람이다. 저장소 종류와 무관하게 이름과 메일 주소만 담는다(Git은 `PersonIdent`, Mercurial은 `이름 <메일>` 사용자 문자열로 바꾼다).
 */
data class MergePerson(val name: String, val email: String)

/**
 * 한 종류의 저장소에서 풀 리퀘스트를 병합하는 계약이다(Mercurial 도입을 위한 8a, `decisions.md` D-86, D-87). [PullRequestMerger]가 받는 프로젝트의 `vcs`
 * 값으로 구현을 골라 쓴다. 병합은 모두 받는 프로젝트의 저장소 안에서 일어나고, 보내는 브랜치는 숨은 참조로 받는 저장소에 가져온다(Git은 `refs/yona/pull/…`,
 * Mercurial은 secret 체인지셋과 예약 이름표).
 *
 * 가져오기와 참조 갱신은 저장소 단위로 한 번에 하나씩만 실행한다(v1.6 주석 "locking this repository is required because of fetch and update").
 */
interface PullRequestBackend : VcsPart {

    /** [pullRequest]의 보내는 브랜치를 받는 브랜치에 병합할 수 있는지 시험한다. 저장소를 바꾸지 않는다(임시로 만든 것은 지운다). */
    fun attempt(pullRequest: PullRequest): MergeAttempt

    /** 보내는 브랜치를 숨은 참조로 가져와 병합하고, 충돌하지 않으면 병합 커밋을 만들어 병합 결과 참조에 둔다. 병합 커밋은 시스템 사용자로 만든다. */
    fun update(pullRequest: PullRequest): MergeUpdate

    /**
     * 요청을 받아들인다. 보내는 브랜치를 가져와 [method] 방식으로 병합하고 받는 브랜치를 갱신한다. 충돌하거나(리베이스는 한 커밋에서라도), 리베이스인데
     * 병합 커밋이 섞여 있으면 아무것도 바꾸지 않고 `null`이다.
     *
     * @param commitTitle 병합 커밋과 스쿼시 커밋의 제목. 비우면 기본 제목이다(리베이스는 쓰지 않는다)
     * @param commitMessage 병합 커밋과 스쿼시 커밋의 본문. 비우면 기본 본문이다(리베이스는 쓰지 않는다)
     */
    fun merge(pullRequest: PullRequest, sender: MergePerson, method: MergeMethod, commitTitle: String?, commitMessage: String?): MergeUpdate?

    /** 리베이스 병합을 할 수 없는 이유이다. 할 수 있으면 `null`, 커밋 하나라도 충돌하면 `conflict`, 병합 커밋이 섞여 있으면 `merge-commits`이다. */
    fun rebaseBlocked(pullRequest: PullRequest): String?

    /** 받는 저장소에서 [from]부터 [to]까지의 범위에 있는 커밋을 최근 것이 앞이 되게 돌려준다 (v1.6 `GitRepository.diffCommits`). */
    fun commitsBetween(pullRequest: PullRequest, from: String, to: String): List<CommitInfo>

    /** 이 풀 리퀘스트 보내는 브랜치의 숨은 참조 이름이다 (v1.6 `getRefNameToFetchedSource`). */
    fun headRef(pullRequest: PullRequest): String

    /** 이 풀 리퀘스트 병합 결과의 숨은 참조 이름이다 (v1.6 `getNameOfRefToMerged`). */
    fun mergedRef(pullRequest: PullRequest): String
}

/**
 * 병합 커밋 메시지를 만드는 글이다. 저장소 종류와 무관하다(v1.6 `makeMergeCommitMessage`).
 */
object MergeMessages {

    /** 브랜치 참조 이름의 짧은 이름이다(`refs/heads/dev` → `dev`). */
    fun shortName(ref: String?): String = ref.orEmpty().removePrefix("refs/heads/").removePrefix("refs/tags/").removePrefix("refs/remotes/")

    fun mergeTitle(pullRequest: PullRequest): String {
        val builder = StringBuilder("Merge branch '${shortName(pullRequest.fromBranch)}'")
        if (pullRequest.fromProject?.id != pullRequest.toProject?.id) builder.append(" of ${pullRequest.fromProject?.owner}/${pullRequest.fromProject?.name}")
        if (pullRequest.toBranch != "refs/heads/master") builder.append(" into '${shortName(pullRequest.toBranch)}'")
        return builder.toString()
    }

    /** 병합 커밋의 기본 본문이다. 끝은 줄바꿈이다. [shortMessages]는 최근 것이 앞이다. */
    fun mergeBody(pullRequest: PullRequest, shortMessages: List<String>): String {
        val builder = StringBuilder("from pull-request ${pullRequest.number}\n\n* ${shortName(pullRequest.fromBranch)}:\n")
        shortMessages.forEach { builder.append("  $it\n") }
        return builder.toString()
    }

    fun squashTitle(pullRequest: PullRequest) = "${pullRequest.title} (#${pullRequest.number})"

    /**
     * 병합 화면이 확인 칸에 미리 채울 기본 제목과 본문이다(추가 개발 항목 X-28). 리뷰어 꼬리말은 병합할 때 붙으므로 여기에 없다. 리베이스는 메시지를 쓰지
     * 않아 둘 다 비어 있다. [shortMessages]는 최근 것이 앞이다.
     */
    fun defaults(pullRequest: PullRequest, method: MergeMethod, shortMessages: List<String>): Pair<String, String> = when (method) {
        MergeMethod.MERGE -> mergeTitle(pullRequest) to mergeBody(pullRequest, shortMessages)
        MergeMethod.SQUASH -> squashTitle(pullRequest) to shortMessages.reversed().joinToString("\n") { "* $it" }
        MergeMethod.REBASE -> "" to ""
    }

    /** 제목, 본문, 리뷰한 사람 순서로 메시지를 만든다. */
    fun compose(title: String, body: String, pullRequest: PullRequest): String {
        val trailer = pullRequest.reviewers.joinToString("") { "Reviewed-by: ${it.name} <${it.email}>\n" }
        return buildString {
            append(title.trim()).append("\n\n").append(body.trim()).append("\n")
            if (trailer.isNotEmpty()) append("\n").append(trailer)
        }
    }

    /** 기본 병합 커밋 메시지이다 (v1.6 `makeMergeCommitMessage`). 제목, 본문, 리뷰어 꼬리말 순서이다. */
    fun message(pullRequest: PullRequest, commits: List<CommitInfo>): String {
        val builder = StringBuilder(mergeTitle(pullRequest)).append("\n\n")
        builder.append(mergeBody(pullRequest, commits.map { it.shortMessage })).append("\n")
        pullRequest.reviewers.forEach { builder.append("Reviewed-by: ${it.name} <${it.email}>\n") }
        return builder.toString()
    }
}
