package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus

import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.vcs.GitCommit

interface PullRequestService {
    /**
     * 풀 리퀘스트의 충돌 가능성을 판단하고, 임시 머지 결과를 반환합니다.
     */
    fun attemptMerge(pullRequestId: Long): PullRequestMergeResult

    /**
     * yona PullRequestApp.mergeResult()/PullRequest.attemptMerge()/getPullRequestMergeResult() 대응
     * (#178, TASK-0257). [attemptMerge]와 달리 아직 저장되지 않은(=PR로 생성되기 전) 임의의 from/to
     * 프로젝트·브랜치 조합에 대해 커밋 프리뷰 + 충돌 여부만 계산한다(부수효과 없음, DB 저장 없음) —
     * PR 생성/수정 화면에서 브랜치를 바꿀 때마다 AJAX로 호출하는 용도.
     */
    fun previewMerge(fromProject: Project, toProject: Project, fromBranch: String, toBranch: String): MergePreviewResult

    /**
     * yona actors/PullRequestActor.processPullRequestMerging() 대응 (P1-52). attemptMerge()의
     * 부수효과 없는 미리보기와 달리, 새 커밋이 발견되면 PullRequestCommit 영속화·PullRequestEvent
     * 기록·리뷰어 초기화를 수행하고([isNewPullRequest]가 false일 때만 알림도 발행), diff가 완전히
     * 사라지면 자동으로 MERGED 상태로 전환한다. createPullRequest()(PR 생성)와 관련 PR 재검사
     * (RelatedPullRequestMergeEvent) 양쪽에서 공유한다 — legacy도 PullRequestMergingActor/
     * RelatedPullRequestMergingActor 둘 다 이 메서드 하나로 수렴한다.
     */
    fun processMergeCheck(pullRequestId: Long, sender: User, isNewPullRequest: Boolean): PullRequestMergeResult

    /**
     * 풀 리퀘스트를 실제로 머지하고 커밋을 기록하며, 상태를 업데이트합니다.
     * @param pullRequestId 풀 리퀘스트 ID
     * @param updater 머지 처리를 수행하는 유저
     */
    fun merge(pullRequestId: Long, updater: User): PullRequestMergeResult

    fun getPullRequests(toProjectId: Long, state: State?): List<PullRequest>

    fun getPullRequest(toProjectId: Long, number: Long): PullRequest?

    fun createPullRequest(
        title: String,
        body: String?,
        fromProjectId: Long,
        toProjectId: Long,
        fromBranch: String,
        toBranch: String,
        contributor: User
    ): PullRequest

    // yona PullRequest.updateWith()/hasSameBranchesWith()/findDuplicatedPullRequest() +
    // PullRequestApp.editPullRequest() 대응 (P1-68). title/body뿐 아니라 from/toBranch까지
    // 재할당할 수 있으며, 브랜치가 바뀌면 동일한 from/to 프로젝트·브랜치 조합의 OPEN PR이
    // 이미 있는지 검사해 있으면 [DuplicatedPullRequestException]을 던진다. 브랜치 변경 여부와
    // 무관하게 항상 기존 ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트를 지우고 새 title/body를
    // 다시 스캔해 재생성한다(deleteIssueEvents()/addNewIssueEvents()).
    fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?,
        fromBranch: String,
        toBranch: String
    ): PullRequest

    fun changeState(
        pullRequestId: Long,
        state: State,
        updaterLoginId: String
    ): PullRequest

    fun addReviewer(pullRequestId: Long, reviewer: User)
    fun removeReviewer(pullRequestId: Long, reviewer: User)

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자 지정/해제. Issue의
    // assigneeId 갱신 로직(IssueServiceImpl.updateIssue())과 동일하게, 담당자를 바꿀 때마다 기존
    // Assignee 로우를 재사용하지 않고 새로 만든다(Assignee는 (user, project) 값 객체에 가까워
    // Issue도 동일하게 매번 새로 생성). assigneeUser가 null이면 담당자를 해제한다.
    fun setAssignee(pullRequestId: Long, assigneeUser: User?): PullRequest

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 라벨 추가/제거. 라벨은
    // 프로젝트에 이미 정의된 IssueLabel(ProjectViewController.newLabel() 등으로 관리)을 그대로
    // 참조만 한다(신규 라벨 정의 기능이 아니다).
    fun addLabel(pullRequestId: Long, labelId: Long): PullRequest
    fun removeLabel(pullRequestId: Long, labelId: Long): PullRequest

    fun getDiff(pullRequest: PullRequest): List<FileDiff>
    fun getDiff(pullRequest: PullRequest, commitId: String): List<FileDiff>

    /**
     * 병합된 PR의 원본(from) 브랜치를 삭제한다. 이후 복원할 수 있도록 삭제 직전
     * 브랜치의 head 커밋을 pullRequest.lastCommitId에 기록한다.
     */
    fun deleteFromBranch(pullRequestId: Long): PullRequest

    /**
     * deleteFromBranch()로 지워진 브랜치를 pullRequest.lastCommitId 지점으로 복원한다.
     */
    fun restoreFromBranch(pullRequestId: Long): PullRequest
}

// yona PullRequest.attemptMerge()가 반환하는 PullRequestMergeResult(getGitCommits()/conflicts())와
// suggestTitleAndBodyFromDiffCommit()의 title/body 두 결과를 하나로 합친 값 — 대응하는 PR 엔티티가
// 없는 프리뷰 전용 시나리오라 기존 PullRequestMergeResult(비-null PullRequest 필수)를 재사용하지
// 않고 별도 타입으로 둔다.
data class MergePreviewResult(
    val commits: List<GitCommit>,
    val conflict: Boolean,
    val suggestedTitle: String?,
    val suggestedBody: String?
)

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "리뷰어 수가 부족하여 머지할 수 없습니다.")
class LackingReviewerException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "병합되지 않은 PR은 브랜치를 삭제/복원할 수 없습니다.")
class InvalidBranchOperationException(message: String) : RuntimeException(message)

// yona PullRequestApp.editPullRequest()의 findDuplicatedPullRequest() != null 분기 대응 (P1-68).
@ResponseStatus(value = HttpStatus.CONFLICT, reason = "동일한 브랜치 조합의 풀 리퀘스트가 이미 열려 있습니다.")
class DuplicatedPullRequestException(message: String) : RuntimeException(message)

// yona errors/PullRequestException.java 대응 (P2-50). legacy MergeRefUpdate.updateRef()가 ref 갱신
// 실패(NEW/FAST_FORWARD/FORCED가 아닌 그 외 RefUpdate.Result)를 이 전용 타입으로 던지던 것과 동일하게,
// 범용 IOException 대신 병합 실패임을 타입으로 구분할 수 있게 이식한다(실제 동작 차이는 없음 — legacy도
// 호출부가 항상 catch(Exception)으로 뭉뚱그려 처리해 타입 기반 분기가 존재한 적이 없다).
class PullRequestException(message: String) : RuntimeException(message)

// yona-wiki P3-04(브랜치 보호) Step 4 — legacy에 대응 타입이 전혀 없는 신규 인프라. toBranch에
// 걸린 ProtectedBranch.restrictPushTo 제한에 걸려 merge()가 거부될 때 던진다(계획 문서의
// "PullRequestServiceImpl.merge()/processMergeCheck()에 체크 추가" 항목 참고). 403으로 응답하는
// 이유: 대상 자체(toBranch)는 존재하고 요청도 형식상 올바르지만 "이 사용자는 이 동작을 할 권한이
// 없다"는 의미가 LackingReviewerException(400, 요청 자체의 결함)보다 AccessControl 계열 거부에
// 더 가깝기 때문이다.
@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "브랜치 보호 정책에 의해 병합이 거부되었습니다.")
class BranchProtectionException(message: String) : RuntimeException(message)
