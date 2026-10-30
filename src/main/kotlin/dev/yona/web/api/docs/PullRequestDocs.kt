package dev.yona.web.api.docs

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로젝트 이름표(소유자와 이름)")
class ProjectRef(
    @get:Schema(description = "소유자", example = "alice") val owner: String,
    @get:Schema(description = "이름", example = "demo") val name: String
)

@Schema(description = "승인 현황")
class ApprovalState(
    @get:Schema(description = "지금 인정되는 승인 수") val count: Int,
    @get:Schema(description = "받는 브랜치의 규칙 묶음이 요구하는 승인 수") val required: Int,
    @get:Schema(description = "승인한 사람") val approvers: List<UserSummary>
)

@Schema(description = "병합 방식 하나의 상태")
class MergeMethodState(
    @get:Schema(description = "프로젝트가 이 방식을 허용하는지") val allowed: Boolean,
    @get:Schema(description = "지금 이 방식으로 병합할 수 있는지") val available: Boolean,
    @get:Schema(description = "할 수 없는 이유. `not-open`, `conflict`, `merge-commits` 등. 할 수 있으면 `null`", nullable = true) val reason: String?
)

@Schema(description = "풀 리퀘스트 한 건. 목록과 상세가 같은 모양이다")
class PullRequestDetail(
    @get:Schema(description = "받는 프로젝트 안의 번호", example = "5") val number: Long,
    @get:Schema(description = "전체 번호") val id: Long,
    @get:Schema(description = "제목") val title: String,
    @get:Schema(description = "본문(마크다운)") val body: String?,
    @get:Schema(description = "상태", allowableValues = ["open", "closed", "merged"]) val state: String,
    @get:Schema(description = "작성자(보낸 사람)") val author: UserSummary?,
    @get:Schema(description = "받는 사람") val receiver: UserSummary?,
    @get:Schema(description = "보내는 프로젝트") val fromProject: ProjectRef,
    @get:Schema(description = "받는 프로젝트") val toProject: ProjectRef,
    @get:Schema(description = "보내는 브랜치", example = "refs/heads/feature") val fromBranch: String?,
    @get:Schema(description = "받는 브랜치", example = "refs/heads/master") val toBranch: String?,
    @get:Schema(description = "만든 시각") val createdAt: String?,
    @get:Schema(description = "고친 시각") val updatedAt: String?,
    @get:Schema(description = "병합한 시각. 병합되지 않았으면 `null`", nullable = true) val mergedAt: String?,
    @get:Schema(description = "충돌하는지") @get:JsonProperty("isConflict") val isConflict: Boolean,
    @get:Schema(description = "병합 중인지") @get:JsonProperty("isMerging") val isMerging: Boolean,
    @get:Schema(description = "병합 가능 여부", allowableValues = ["clean", "conflicting", "unknown"]) val mergeable: String,
    @get:Schema(description = "웹 화면의 주소") val url: String,
    @get:Schema(description = "담당자") val assignees: List<UserSummary>,
    @get:Schema(description = "라벨") val labels: List<LabelSummary>,
    @get:Schema(description = "리뷰를 요청받은 사람") val requestedReviewers: List<UserSummary>,
    @get:Schema(description = "승인 현황") val approvals: ApprovalState,
    @get:Schema(description = "병합 기준 커밋(보내는 쪽)") val mergedCommitIdFrom: String?,
    @get:Schema(description = "병합 기준 커밋(받는 쪽)") val mergedCommitIdTo: String?,
    @get:Schema(description = "병합된 방식. 병합되지 않았으면 `null`", allowableValues = ["merge", "squash", "rebase"], nullable = true) val mergeMethod: String?,
    @get:Schema(description = "방식(`merge`, `squash`, `rebase`)마다 허용 여부와 가능 여부") val mergeMethods: Map<String, MergeMethodState>
)

@Schema(description = "풀 리퀘스트 목록 응답")
class PullRequestListResponse(
    @get:Schema(description = "풀 리퀘스트들") val items: List<PullRequestDetail>,
    page: Int, pageSize: Int, totalPages: Int, totalCount: Long
) : PageInfo(page, pageSize, totalPages, totalCount)

@Schema(description = "풀 리퀘스트의 커밋")
class PullRequestCommitNode(
    val commitId: String, val shortId: String?, val message: String?, val authorEmail: String?, val authoredAt: String?,
    @get:Schema(description = "`current`(지금 커밋), `outdated` 등") val state: String?
)

@Schema(description = "커밋 목록 응답")
class PullRequestCommitList(@get:Schema(description = "오래된 순서의 현재 커밋") val commits: List<PullRequestCommitNode>)

@Schema(description = "변경된 파일 하나")
class PullRequestFileNode(
    val path: String?,
    @get:Schema(description = "이름이 바뀌거나 복사된 파일의 이전 경로") val oldPath: String?,
    @get:Schema(description = "변경 종류", allowableValues = ["added", "removed", "renamed", "copied", "modified"]) val status: String,
    val additions: Int, val deletions: Int,
    @get:Schema(description = "이진 파일인지") val binary: Boolean,
    @get:Schema(description = "헝크를 이은 통합 diff. 이진 파일이거나 너무 크면 `null`", nullable = true) val patch: String?,
    @get:Schema(description = "diff가 너무 커서 잘렸는지") val truncated: Boolean
)

@Schema(description = "변경 파일 목록 응답")
class PullRequestFileList(@get:Schema(description = "변경된 파일. 병합할 수 없어 변경을 알 수 없으면 빈 목록") val files: List<PullRequestFileNode>)

@Schema(description = "풀 리퀘스트의 이벤트")
class PullRequestEventNode(
    val id: Long, @get:Schema(description = "이벤트 종류", example = "PULL_REQUEST_STATE_CHANGED") val type: String?, val createdAt: String?,
    @get:Schema(description = "일으킨 사람") val sender: UserSummary?, val oldValue: String?, val newValue: String?
)

@Schema(description = "타임라인 응답")
class PullRequestTimeline(@get:Schema(description = "만든 순서의 이벤트") val events: List<PullRequestEventNode>)

@Schema(description = "리뷰 판정 하나")
class ReviewNode(
    val id: Long,
    @get:Schema(description = "판정", allowableValues = ["APPROVED", "CHANGES_REQUESTED", "COMMENTED"]) val verdict: String?,
    @get:Schema(description = "요약") val body: String?, val createdAt: String?, val author: UserSummary,
    @get:Schema(description = "해제되었는지") val dismissed: Boolean, val dismissedAt: String?, val dismissedBy: String?, val dismissedReason: String?
)

@Schema(description = "리뷰 목록 응답")
class ReviewList(@get:Schema(description = "제출한 순서의 리뷰") val reviews: List<ReviewNode>)

@Schema(description = "리뷰 댓글 하나")
class ReviewCommentNode(
    val id: Long, val body: String?, val createdAt: String?, val editedAt: String?,
    @get:Schema(description = "아직 공개되지 않은 임시 댓글인지") val pending: Boolean, val author: UserSummary
)

@Schema(description = "코드 리뷰 스레드. 줄 범위 스레드는 위치를, 변경 전체 스레드는 커밋과 경로만 담는다")
class ThreadNode(
    val id: Long, @get:Schema(description = "상태", allowableValues = ["open", "closed"]) val state: String, val createdAt: String?, val pending: Boolean,
    val commitId: String? = null, val prevCommitId: String? = null, val path: String? = null,
    @get:Schema(allowableValues = ["A", "B"]) val startSide: String? = null, val startLine: Int? = null, val startColumn: Int? = null,
    @get:Schema(allowableValues = ["A", "B"]) val endSide: String? = null, val endLine: Int? = null, val endColumn: Int? = null,
    @get:Schema(description = "스레드의 댓글") val comments: List<ReviewCommentNode>
)

@Schema(description = "리뷰 스레드 목록 응답")
class ThreadList(@get:Schema(description = "공개된 스레드와 호출한 사용자의 임시 스레드(`pending`)") val threads: List<ThreadNode>)

@Schema(description = "스레드 한 건 응답")
class ThreadResponse(val thread: ThreadNode)

@Schema(description = "풀 리퀘스트 만들기 요청. 경로의 프로젝트가 받는 프로젝트이다")
class PullRequestCreateRequest(
    @get:Schema(description = "제목(255자까지)", requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @get:Schema(description = "본문") val body: String? = null,
    @get:Schema(description = "보내는 프로젝트. 생략하면 받는 프로젝트 자신. 받는 프로젝트이거나 그 포크나 원본이어야 한다") val fromProject: ProjectRef? = null,
    @get:Schema(description = "보내는 브랜치(`feature`나 `refs/heads/feature`). 실제로 있어야 한다", requiredMode = Schema.RequiredMode.REQUIRED) val fromBranch: String,
    @get:Schema(description = "받는 브랜치", requiredMode = Schema.RequiredMode.REQUIRED) val toBranch: String,
    @get:Schema(description = "담당자의 로그인 ID(한 명). 받는 프로젝트를 고칠 수 있는 사람만 정한다") val assignees: List<String>? = null,
    @get:Schema(description = "라벨 번호. 받는 프로젝트의 라벨이어야 하고 고칠 수 있는 사람만 정한다") val labelIds: List<Long>? = null,
    @get:Schema(description = "먼저 올린 첨부의 번호들") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "같은 브랜치 쌍의 열린 풀 리퀘스트가 이미 있을 때의 응답(409)")
class PullRequestConflict(
    val message: String,
    @get:Schema(description = "이미 열려 있는 풀 리퀘스트") val pullRequest: PullRequestRefNode
)

@Schema(description = "풀 리퀘스트의 번호와 주소")
class PullRequestRefNode(val number: Long, val url: String)

@Schema(description = "풀 리퀘스트 수정 요청. 주어진 것만 바꾼다. 출발 프로젝트는 바꿀 수 없다")
class PullRequestPatchRequest(
    @get:Schema(description = "제목. 비울 수 없고 255자까지") val title: String? = null,
    @get:Schema(description = "본문") val body: String? = null,
    @get:Schema(description = "보내는 브랜치") val fromBranch: String? = null,
    @get:Schema(description = "받는 브랜치") val toBranch: String? = null,
    @get:Schema(description = "첨부로 더할, 먼저 올린 파일의 번호들") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "병합 요청. 모두 생략할 수 있다")
class MergeRequest(
    @get:Schema(description = "병합 방식. 생략하면 프로젝트의 기본 방식. 프로젝트가 허용하지 않은 방식은 400", allowableValues = ["merge", "squash", "rebase"]) val method: String? = null,
    @get:Schema(description = "병합 커밋이나 스쿼시 커밋의 제목. 리베이스는 무시한다") val commitTitle: String? = null,
    @get:Schema(description = "병합 커밋이나 스쿼시 커밋의 본문. 리베이스는 무시한다") val commitMessage: String? = null
)

@Schema(description = "담당자 지정 요청. 비우면 해제한다(지금은 한 명까지)")
class PullRequestAssigneesRequest(@get:Schema(description = "담당자의 로그인 ID") val assignees: List<String>)

@Schema(description = "라벨 바꾸기 요청. 둘 다 생략할 수 있다")
class PullRequestLabelsRequest(
    @get:Schema(description = "붙일 라벨 번호") val attachingLabelIds: List<Long>? = null,
    @get:Schema(description = "뗄 라벨 번호") val detachingLabelIds: List<Long>? = null
)

@Schema(description = "리뷰 요청")
class ReviewRequestRequest(@get:Schema(description = "리뷰를 요청할 사용자의 로그인 ID", requiredMode = Schema.RequiredMode.REQUIRED) val reviewer: String)

@Schema(description = "리뷰 제출 요청")
class ReviewSubmitRequest(
    @get:Schema(description = "판정", allowableValues = ["APPROVED", "CHANGES_REQUESTED", "COMMENTED"], requiredMode = Schema.RequiredMode.REQUIRED) val verdict: String,
    @get:Schema(description = "요약. 내 임시 줄 댓글이 있으면 생략할 수 있다") val body: String? = null
)

@Schema(description = "리뷰 제출 응답")
class ReviewSubmitResponse(
    @get:Schema(description = "제출한 리뷰") val review: ReviewNode?,
    @get:Schema(description = "함께 공개된 임시 줄 댓글 수") val publishedComments: Int
)

@Schema(description = "리뷰 해제 요청")
class DismissRequest(@get:Schema(description = "해제 사유", requiredMode = Schema.RequiredMode.REQUIRED) val message: String)

@Schema(description = "해제한 리뷰 응답")
class ReviewResponse(val review: ReviewNode)

@Schema(description = "줄 댓글 달기 요청. `threadId`가 있으면 그 스레드에 이어 달고, 없으면 새 스레드를 만든다(줄 범위를 모두 주면 줄 스레드, 아니면 변경 전체 스레드)")
class ReviewCommentCreateRequest(
    @get:Schema(description = "내용", requiredMode = Schema.RequiredMode.REQUIRED) val body: String,
    @get:Schema(description = "파일 경로") val path: String? = null,
    @get:Schema(description = "시작 쪽(A는 이전, B는 이후)", allowableValues = ["A", "B"]) val startSide: String? = null,
    val startLine: Int? = null, val startColumn: Int? = null,
    @get:Schema(description = "끝 쪽", allowableValues = ["A", "B"]) val endSide: String? = null,
    val endLine: Int? = null, val endColumn: Int? = null,
    @get:Schema(description = "댓글을 다는 커밋") val commitId: String? = null,
    @get:Schema(description = "이어 달 스레드 번호") val threadId: Long? = null,
    @get:Schema(description = "`true`이면 임시 댓글(리뷰 시작이나 리뷰에 추가)이다. 내 임시 스레드의 답글은 항상 임시이다") val pending: Boolean? = null,
    @get:Schema(description = "먼저 올린 첨부의 번호들") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "줄 댓글 달기 응답")
class ReviewCommentCreated(val comment: ReviewCommentNode, @get:Schema(description = "댓글이 속한 스레드 번호") val threadId: Long?)

@Schema(description = "고친 댓글 응답")
class ReviewCommentEdited(val comment: ReviewCommentNode)

@Schema(description = "댓글을 지운 응답")
class ReviewCommentDeleted(val deleted: Boolean)

@Schema(description = "임시 리뷰를 버린 응답")
class PendingReviewDiscarded(@get:Schema(description = "지운 댓글 수") val discarded: Int)
