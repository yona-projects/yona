package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 여러 명 만들기 요청(사이트 관리자). 비밀번호는 무작위로 정해진다")
class UsersCreateRequest(@get:Schema(description = "만들 사용자들", requiredMode = Schema.RequiredMode.REQUIRED) val users: List<UserCreateItem>)

@Schema(description = "만들 사용자 한 명")
class UserCreateItem(val loginId: String, val name: String, val email: String)

@Schema(description = "사용자 만들기 결과의 한 항목. `status`는 201(만듦), 403(허용하지 않는 메일 도메인), 409(이미 있는 이메일이나 아이디)이다")
class UserCreatedItem(
    @get:Schema(description = "201, 403, 409") val status: Int,
    @get:Schema(example = "Created") val reason: String,
    @get:Schema(description = "실패한 이유. 만들었으면 없다") val message: String?,
    @get:Schema(description = "만든 사용자나 요청한 값") val user: UserCreatedRef
)

@Schema(description = "만든 사용자(실패하면 요청한 값, `id`는 없다)")
class UserCreatedRef(val id: Long?, val loginId: String, val name: String, val email: String)

@Schema(description = "내 이슈 목록의 한 항목")
class MyIssue(
    val id: Long, val number: Long, @get:Schema(description = "상태", example = "OPEN") val state: String?, val title: String,
    @get:Schema(description = "만든 시각") val createdDate: String, @get:Schema(description = "고친 시각") val updatedDate: String,
    val author: MyIssueUser, @get:Schema(description = "담당자. 없으면 빈 객체") val assignee: MyIssueUser?,
    val project: MyIssueProject, @get:Schema(description = "프로젝트 소유자") val owner: String,
    @get:Schema(description = "웹 화면의 주소") val refUrl: String
)

@Schema(description = "내 이슈 목록의 사용자")
class MyIssueUser(val id: Long?, val loginId: String?, val name: String?)

@Schema(description = "내 이슈 목록의 프로젝트")
class MyIssueProject(val id: Long, val name: String)

@Schema(description = "내 이슈 목록 응답")
class MyIssueList(@get:Schema(description = "최근 수정 순서의 이슈") val result: List<MyIssue>)

@Schema(description = "사용자의 활동 개수. 없는 사용자는 모두 0이다")
class UserStatistics(
    @get:Schema(description = "쓴 이슈 수") val issue: Long, @get:Schema(description = "쓴 글 수") val posting: Long,
    @get:Schema(description = "담당한 이슈 수") val assignedIssue: Long,
    @get:Schema(description = "단 이슈 댓글 수") val issueComment: Long, @get:Schema(description = "단 글 댓글 수") val postingComment: Long,
    @get:Schema(description = "추천한 이슈 수") val issueVoter: Long, @get:Schema(description = "추천한 이슈 댓글 수") val issueCommentVoter: Long
)

@Schema(description = "사이트 관리자용 사용자 한 명")
class AdminUser(
    val id: Long, @get:Schema(description = "로그인 ID") val login_id: String, val name: String?, val email: String?,
    @get:Schema(description = "상태", example = "ACTIVE") val state: String?, @get:Schema(description = "게스트인지") val is_guest: Boolean?
)

@Schema(description = "사용자 상태 변경 요청")
class UserStateRequest(
    @get:Schema(description = "새 상태(대소문자 무시). `SITE_ADMIN`으로는 바꿀 수 없다", example = "locked", requiredMode = Schema.RequiredMode.REQUIRED) val state: String
)

@Schema(description = "상태를 바꾼 사용자")
class UserStateChanged(val id: Long, val login_id: String, @get:Schema(example = "LOCKED") val state: String)

@Schema(description = "현황의 이슈 구역")
class StatusIssueSection(
    @get:Schema(description = "열린 이슈 수(읽을 수 있는 것만)") val openCount: Int,
    @get:Schema(description = "닫힌 이슈 수") val closedCount: Int,
    @get:Schema(description = "열린 이슈 중 최근 수정 순서의 항목(`limit`개까지)") val items: List<IssueSummary>
)

@Schema(description = "현황의 풀 리퀘스트 구역")
class StatusPullRequestSection(
    @get:Schema(description = "열린 풀 리퀘스트 수(읽을 수 있는 것만)") val openCount: Int,
    @get:Schema(description = "닫히거나 병합된 풀 리퀘스트 수") val closedCount: Int,
    @get:Schema(description = "열린 풀 리퀘스트 중 최근 수정 순서의 항목(`limit`개까지)") val items: List<PullRequestDetail>
)

@Schema(description = "최근 알림 한 건")
class StatusNotification(
    val id: Long, @get:Schema(description = "알림 종류", example = "NEW_COMMENT") val type: String?, val title: String?,
    @get:Schema(description = "이동할 웹 주소. 없으면 `null`", nullable = true) val url: String?, val createdAt: String?,
    @get:Schema(description = "보낸 사람. 없으면 `null`", nullable = true) val sender: UserSummary?
)

@Schema(description = "사용자 현황(`gh status`)")
class UserStatusResponse(
    @get:Schema(description = "내가 담당자인 이슈") val assignedIssues: StatusIssueSection,
    @get:Schema(description = "내가 담당자인 풀 리퀘스트") val assignedPullRequests: StatusPullRequestSection,
    @get:Schema(description = "내가 리뷰를 요청받은 풀 리퀘스트") val reviewRequests: StatusPullRequestSection,
    @get:Schema(description = "나를 멘션한 이슈(본문이나 댓글)") val mentionedIssues: StatusIssueSection,
    @get:Schema(description = "최근 알림 10건") val notifications: List<StatusNotification>
)
