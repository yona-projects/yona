package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommit
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.DiffLineType
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.vcs.GitCommit
import java.time.Instant

// yona-wiki P3-02 Step8.7 2번(2026-09-01 실서버 골든패스 수동검증 중 발견, 심각도 높음) —
// IssueRestApiController/PullRequestApiController/SearchRestApiController가 JPA 엔티티
// (Issue/PullRequest/Project)를 가공 없이 그대로 반환하는데, User.projectUsers
// (@OneToMany mappedBy="user") <-> ProjectUser.user(@ManyToOne)가 양방향 연관관계라
// Jackson이 "이슈->project->projectUsers[]->user->projectUsers[]->user->..."로 무한
// 순환 직렬화한다(실측: curl로 60KB 넘는 깨진 채로 끊긴 JSON 확인, 서버가 open-in-view라
// 응답 작성 시점까지 세션이 열려있어 lazy 컬렉션이 실제로 초기화되며 재현됨).
//
// ProjectRestApiController.toProjectNode()가 이미 쓰고 있는 "엔티티를 그대로 반환하지 않고
// 필요한 필드만 담은 응답 모델로 변환" 패턴을 Issue/PullRequest에도 그대로 적용한다. 필드
// 이름은 yona-cli(internal/api/issue.go, pr.go, cmd/issue.go, cmd/pr.go)가 실제로 읽는
// 키(number/title/state/body/fromBranch/toBranch/fromProject.owner/fromProject.name 등)와
// 정확히 같은 camelCase를 유지한다 — CLI는 map[string]interface{}로 느슨하게 파싱하므로
// 서버가 이 키들을 계속 내려주기만 하면 CLI 코드 변경이 필요 없다.
//
// User를 이슈/PR 응답에 중첩해야 하는 지점(작성자/담당자/리뷰어/받는사람)에는 항상 이 최소
// 참조 DTO만 쓴다 — User 엔티티를 통째로 넣으면 projectUsers 등 백레퍼런스 컬렉션이 다시
// 따라와 같은 순환 문제가 재발한다.
data class UserRefResponse(
    val id: Long?,
    val loginId: String,
    val name: String
)

fun User.toRefResponse() = UserRefResponse(id = id, loginId = loginId, name = name)

data class AssigneeResponse(
    val id: Long?,
    val userId: Long?,
    val loginId: String?,
    val name: String?
)

fun Assignee.toResponse() = AssigneeResponse(id = id, userId = user.id, loginId = user.loginId, name = user.name)

data class IssueLabelResponse(
    val id: Long?,
    val name: String,
    val color: String,
    val categoryId: Long?,
    val category: String?
)

fun IssueLabel.toResponse() = IssueLabelResponse(
    id = id,
    name = name,
    color = color,
    categoryId = category.id,
    category = category.name
)

// Issue/PullRequest 응답 안에 toProject/fromProject를 중첩할 때 쓰는 최소 프로젝트 참조 DTO.
// yona-cli cmd/pr.go의 planCheckout()("yona pr checkout")이 pr["fromProject"]["owner"]/
// ["name"]을 그대로 읽으므로 owner/name은 반드시 유지해야 한다.
data class ProjectRefResponse(
    val id: Long?,
    val owner: String?,
    val name: String,
    val overview: String?,
    val vcs: String?,
    val scope: String
)

fun Project.toRefResponse() = ProjectRefResponse(
    id = id,
    owner = owner,
    name = name,
    overview = overview,
    vcs = vcs,
    scope = projectScope.name
)

data class IssueResponse(
    val id: Long?,
    val number: Long?,
    val title: String,
    val body: String?,
    val state: State,
    val createdDate: Instant?,
    val updatedDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val updatedByAuthorId: Long?,
    val updatedByAuthorLoginId: String?,
    val updatedByAuthorName: String?,
    val numOfComments: Int,
    val dueDate: Instant?,
    val isDraft: Boolean,
    val weight: Int,
    val milestoneId: Long?,
    val assignee: AssigneeResponse?,
    val labels: List<IssueLabelResponse>,
    val projectId: Long?
)

fun Issue.toResponse() = IssueResponse(
    id = id,
    number = number,
    title = title,
    body = body,
    state = state,
    createdDate = createdDate,
    updatedDate = updatedDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    updatedByAuthorId = updatedByAuthorId,
    updatedByAuthorLoginId = updatedByAuthorLoginId,
    updatedByAuthorName = updatedByAuthorName,
    numOfComments = numOfComments,
    dueDate = dueDate,
    isDraft = isDraft,
    weight = weight,
    milestoneId = milestone?.id,
    assignee = assignee?.toResponse(),
    labels = labels.map { it.toResponse() },
    projectId = project.id
)

data class IssueCommentResponse(
    val id: Long?,
    val contents: String,
    val createdDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val parentCommentId: Long?,
    val issueId: Long?
)

fun IssueComment.toResponse() = IssueCommentResponse(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    parentCommentId = parentComment?.id,
    issueId = issue.id
)

// P3-30 — IssueController.getTimeline()이 List<IssueEvent>를 가공 없이 그대로 반환하고 있었다.
// IssueEvent.issue(ManyToOne, JsonIgnore 없음)를 그대로 직렬화하면 issue->project->projectUsers->
// user로 이어지는 동일한 순환/비밀번호 노출 문제가 재발한다 — issueId만 남기고 issue 자체는
// 담지 않는다.
data class IssueEventResponse(
    val id: Long?,
    val issueId: Long?,
    val eventType: EventType,
    val senderLoginId: String?,
    val oldValue: String?,
    val newValue: String?,
    val created: Instant
)

fun IssueEvent.toResponse() = IssueEventResponse(
    id = id,
    issueId = issue.id,
    eventType = eventType,
    senderLoginId = senderLoginId,
    oldValue = oldValue,
    newValue = newValue,
    created = created
)

// P3-30 — BoardController가 Posting 엔티티를 그대로 반환하고 있었다(Posting -> project ->
// projectUsers -> user 순환/비밀번호 노출, IssueResponse와 동일한 근본원인). IssueResponse와
// 같은 필드 선택 기준(연관관계 대신 id/비정규화된 author* 필드만)을 그대로 따른다.
data class PostingResponse(
    val id: Long?,
    val number: Long?,
    val title: String,
    val body: String?,
    val notice: Boolean,
    val readme: Boolean,
    val createdDate: Instant?,
    val updatedDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val updatedByAuthorId: Long?,
    val updatedByAuthorLoginId: String?,
    val updatedByAuthorName: String?,
    val numOfComments: Int,
    val labels: List<IssueLabelResponse>,
    val projectId: Long?
)

fun Posting.toResponse() = PostingResponse(
    id = id,
    number = number,
    title = title,
    body = body,
    notice = notice,
    readme = readme,
    createdDate = createdDate,
    updatedDate = updatedDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    updatedByAuthorId = updatedByAuthorId,
    updatedByAuthorLoginId = updatedByAuthorLoginId,
    updatedByAuthorName = updatedByAuthorName,
    numOfComments = numOfComments,
    labels = labels.map { it.toResponse() },
    projectId = project.id
)

// P3-30 — CommentController가 PostingComment 엔티티를 그대로 반환하고 있었다(IssueCommentResponse와
// 동일한 사유로, PostingComment.posting -> project -> projectUsers -> user 순환 위험).
data class PostingCommentResponse(
    val id: Long?,
    val contents: String,
    val createdDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val parentCommentId: Long?,
    val postingId: Long?
)

fun PostingComment.toResponse() = PostingCommentResponse(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    parentCommentId = parentComment?.id,
    postingId = posting.id
)

// P3-30 — MilestoneController가 Milestone 엔티티를 그대로 반환하고 있었다. Milestone.project는
// 이미 @JsonIgnore가 붙어 있어 이 경로만으로는 순환에 빠지지 않지만(직접 재현은 안 됨), 이 티켓의
// 완료 기준("5개 파일의 어떤 엔드포인트도 raw 엔티티를 직접 반환하지 않는다")과 다른 DTO들과의
// 일관성을 위해 동일하게 DTO로 감싼다.
data class MilestoneResponse(
    val id: Long?,
    val title: String,
    val dueDate: Instant?,
    val contents: String?,
    val state: State,
    val projectId: Long?
)

fun Milestone.toResponse() = MilestoneResponse(
    id = id,
    title = title,
    dueDate = dueDate,
    contents = contents,
    state = state,
    projectId = project.id
)

data class PullRequestResponse(
    val id: Long?,
    val number: Long?,
    val title: String,
    val body: String?,
    val state: State,
    val toProject: ProjectRefResponse,
    val fromProject: ProjectRefResponse,
    val toBranch: String,
    val fromBranch: String,
    val contributor: UserRefResponse,
    val receiver: UserRefResponse?,
    val created: Instant?,
    val updated: Instant?,
    val received: Instant?,
    val isConflict: Boolean?,
    val isMerging: Boolean?,
    val lastCommitId: String?,
    val assignee: AssigneeResponse?,
    val labels: List<IssueLabelResponse>,
    val reviewers: List<UserRefResponse>
)

fun PullRequest.toResponse() = PullRequestResponse(
    id = id,
    number = number,
    title = title,
    body = body,
    state = state,
    toProject = toProject.toRefResponse(),
    fromProject = fromProject.toRefResponse(),
    toBranch = toBranch,
    fromBranch = fromBranch,
    contributor = contributor.toRefResponse(),
    receiver = receiver?.toRefResponse(),
    created = created,
    updated = updated,
    received = received,
    isConflict = isConflict,
    isMerging = isMerging,
    lastCommitId = lastCommitId,
    assignee = assignee?.toResponse(),
    labels = labels.map { it.toResponse() },
    reviewers = reviewers.map { it.toRefResponse() }
)

// yona-wiki P3-15(PR 승인/변경요청 워크플로) — PullRequestReview는 reviewer(User)를 직접 참조하고
// 있어 다른 엔티티들과 동일한 순환 직렬화 위험(User<->ProjectUser)이 있다 — 그대로 반환하지 않고
// 이 DTO로 변환한다.
data class PullRequestReviewResponse(
    val id: Long?,
    val state: PullRequestReview.ReviewState,
    val body: String?,
    val createdDate: Instant,
    val reviewerId: Long?,
    val reviewerLoginId: String?,
    val reviewerName: String?
)

fun PullRequestReview.toResponse() = PullRequestReviewResponse(
    id = id,
    state = state,
    body = body,
    createdDate = createdDate,
    reviewerId = reviewer.id,
    reviewerLoginId = reviewer.loginId,
    reviewerName = reviewer.name
)

data class ReviewCommentResponse(
    val id: Long?,
    val contents: String,
    val createdDate: Instant,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val threadId: Long?
)

fun ReviewComment.toResponse() = ReviewCommentResponse(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = author?.id,
    authorLoginId = author?.loginId,
    authorName = author?.name,
    threadId = thread?.id
)

data class GitCommitResponse(
    val id: String,
    val shortId: String,
    val message: String?,
    val authorName: String?,
    val authorEmail: String?
)

fun GitCommit.toResponse() = GitCommitResponse(
    id = getId(),
    shortId = getShortId(),
    message = getMessage(),
    authorName = getAuthorName(),
    authorEmail = getAuthorEmail()
)

data class PullRequestCommitResponse(
    val id: Long?,
    val commitId: String,
    val commitShortId: String,
    val commitMessage: String,
    val authorEmail: String?,
    val authorDate: Instant?,
    val created: Instant?
)

fun PullRequestCommit.toResponse() = PullRequestCommitResponse(
    id = id,
    commitId = commitId,
    commitShortId = commitShortId,
    commitMessage = commitMessage,
    authorEmail = authorEmail,
    authorDate = authorDate,
    created = created
)

// PullRequestMergeResult.conflicts()는 Kotlin에서 "get"/"is" 접두사가 없는 일반 메서드라
// Jackson 빈 컨벤션상 프로퍼티로 노출되지 않는다 — 그대로 반환하면 CLI의
// cmd/pr.go newPRMergeCmd()가 항상 result["conflicts"]를 못 찾아(타입 단언 실패) 충돌 여부를
// 절대 감지하지 못하는 별도의 잠재 버그가 있었다(순환 직렬화 문제와 별개). DTO로 옮기면서
// "conflicts" 필드를 명시적으로 채워 이 문제도 함께 해소한다.
data class PullRequestMergeResultResponse(
    val conflicts: Boolean,
    val pullRequest: PullRequestResponse,
    val gitCommits: List<GitCommitResponse>,
    val newCommits: List<PullRequestCommitResponse>
)

fun PullRequestMergeResult.toResponse() = PullRequestMergeResultResponse(
    conflicts = conflicts(),
    pullRequest = pullRequest.toResponse(),
    gitCommits = gitCommits.map { it.toResponse() },
    newCommits = newCommits.map { it.toResponse() }
)

// TASK-0419(P3-02 10라운드) — `GET .../pull-requests/{number}/diff`(PullRequestController.getDiff())가
// FileDiff 엔티티를 가공 없이 그대로 반환하던 문제. FileDiff.a/b는 org.eclipse.jgit.diff.RawText,
// editList는 EditList(Edit 리스트), oldMode/newMode는 FileMode인데 전부 일반 Jackson 빈 컨벤션에
// 맞는 getter가 없는 JGit 내부 타입이다 — 그대로 직렬화하면 a/b가 rawContent(byte[] 필드 하나만
// base64로 노출)만 덜렁 나오고 editList/hunks는 사실상 못 쓰는 내부 표현이 그대로 노출된다(실측
// 확인). yona-cli(internal/api/pr.go GetPullRequestDiff())는 이미 pathA/pathB/changeType만
// 신뢰하도록 방어적으로 작성돼 있었지만, 그마저도 Jackson이 a/b/editList 직렬화 도중 문제를
// 일으키면 응답 전체가 깨질 위험이 있었다.
//
// 대신 pathA/pathB/changeType 등 단순 필드만 노출하고, JGit RawText/EditList를 그대로 넘기는 대신
// FileDiff.getHunks()(이미 이 클래스가 순수 데이터 구조인 DiffLine 목록으로 계산해주는 값)를
// 이용해 서버가 직접 GNU unified diff 형식의 텍스트(patch)를 만들어 내려준다 — 클라이언트가 JGit
// 타입을 전혀 몰라도 사람이 읽을 수 있는 diff를 그대로 보여줄 수 있다(yona-cli 쪽 변경 없이도
// 이미 pathA/pathB/changeType으로 첫 줄을 렌더링하던 동작은 그대로 유지된다).
data class FileDiffResponse(
    val pathA: String?,
    val pathB: String?,
    val changeType: String?,
    val commitA: String?,
    val commitB: String?,
    val isBinaryA: Boolean,
    val isBinaryB: Boolean,
    val hasError: Boolean,
    val patch: String?
)

fun FileDiff.toResponse(): FileDiffResponse {
    return FileDiffResponse(
        pathA = pathA,
        pathB = pathB,
        changeType = changeType?.name,
        commitA = commitA,
        commitB = commitB,
        isBinaryA = isBinaryA,
        isBinaryB = isBinaryB,
        hasError = hasError(),
        patch = if (isBinaryA || isBinaryB) null else buildUnifiedDiffPatch(this)
    )
}

// yona-cli/cmd/pr.go의 `git diff` 관례(공백=컨텍스트, -=삭제, +=추가)를 그대로 따르는 최소
// unified diff 텍스트. FileDiff.getHunks()가 이미 begin/end 라인 번호와 DiffLineType까지
// 계산해두므로 여기서는 형식만 맞춰 문자열로 조립한다.
private fun buildUnifiedDiffPatch(diff: FileDiff): String? {
    val hunks = diff.getHunks() ?: return null
    if (hunks.isEmpty()) return ""

    val sb = StringBuilder()
    for (hunk in hunks) {
        val countA = hunk.endA - hunk.beginA
        val countB = hunk.endB - hunk.beginB
        sb.append("@@ -${hunk.beginA + 1},$countA +${hunk.beginB + 1},$countB @@\n")
        for (line in hunk.lines) {
            val prefix = when (line.kind) {
                DiffLineType.ADD -> "+"
                DiffLineType.REMOVE -> "-"
                DiffLineType.CONTEXT -> " "
            }
            sb.append(prefix).append(line.content).append("\n")
        }
    }
    return sb.toString()
}
