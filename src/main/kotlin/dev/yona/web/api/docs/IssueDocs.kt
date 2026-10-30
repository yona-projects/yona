package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

/** 이슈 댓글의 한 줄이다. 부모 댓글에 한 줄 댓글이 `childComments`로 묶인다. */
@Schema(description = "댓글")
class CommentNode(
    @get:Schema(description = "댓글 번호") val id: Long,
    @get:Schema(description = "종류", example = "ISSUE_COMMENT") val type: String,
    @get:Schema(description = "작성자") val author: UserBrief,
    @get:Schema(description = "작성 시각", example = "2026-10-30T10:15:30+0900") val createdAt: String,
    @get:Schema(description = "내용(마크다운)") val body: String?,
    @get:Schema(description = "첨부 파일. 없으면 키가 없다") val attachments: List<AttachmentInfo>? = null,
    @get:Schema(description = "부모 댓글에 달린 한 줄 댓글. 없으면 키가 없다") val childComments: List<CommentNode>? = null
)

@Schema(description = "이슈 이벤트(상태, 담당자, 마일스톤 등의 변경 기록)")
class IssueEventNode(
    @get:Schema(description = "이벤트 번호") val id: Long,
    @get:Schema(description = "발생 시각", example = "2026-10-30T10:15:30+0900") val createdDate: String,
    @get:Schema(description = "이벤트 종류", example = "ISSUE_STATE_CHANGED") val eventType: String?,
    @get:Schema(description = "이벤트 설명(서버 언어)") val eventDescription: String?,
    @get:Schema(description = "이전 값") val oldValue: String?,
    @get:Schema(description = "새 값") val newValue: String?,
    @get:Schema(description = "일으킨 사람") val actor: EventActor
)

@Schema(description = "이벤트를 일으킨 사람")
class EventActor(
    @get:Schema(description = "이름") val name: String?,
    @get:Schema(description = "로그인 ID") val loginId: String?,
    @get:Schema(description = "영문 이름") val englishName: String?
)

@Schema(description = "이슈 한 건. 값이 없는 항목(담당자, 라벨, 마일스톤, 마감일, 첨부, 댓글, 이벤트)은 키가 없다")
class IssueDetail(
    @get:Schema(description = "프로젝트 안의 이슈 번호", example = "3") val number: Long,
    @get:Schema(description = "이슈의 전체 번호(데이터베이스 번호)") val id: Long,
    @get:Schema(description = "제목") val title: String,
    @get:Schema(description = "자원 종류. 이슈는 항상 `ISSUE_POST`", example = "ISSUE_POST") val type: String,
    @get:Schema(description = "작성자") val author: UserBrief,
    @get:Schema(description = "만든 시각", example = "2026-10-30T10:15:30+0900") val createdAt: String,
    @get:Schema(description = "고친 시각. 고친 적이 없으면 빈 문자열") val updatedAt: String,
    @get:Schema(description = "본문(마크다운)") val body: String?,
    @get:Schema(description = "프로젝트 소유자", example = "alice") val owner: String,
    @get:Schema(description = "프로젝트 이름", example = "demo") val projectName: String,
    @get:Schema(description = "담당자(한 명)") val assignees: List<UserBrief>? = null,
    @get:Schema(description = "상태", allowableValues = ["OPEN", "CLOSED"], example = "OPEN") val state: String? = null,
    @get:Schema(description = "붙은 라벨") val labels: List<LabelBrief>? = null,
    @get:Schema(description = "마일스톤 번호") val milestoneId: Long? = null,
    @get:Schema(description = "마일스톤 제목") val milestoneTitle: String? = null,
    @get:Schema(description = "마감일. 긴 형식(`yyyy-MM-dd a hh:mm:ss Z`, 영어)", example = "2026-11-30 AM 11:59:59 +0900") val dueDate: String? = null,
    @get:Schema(description = "웹 화면의 주소", example = "https://yona.example.com/alice/demo/issue/3") val refUrl: String,
    @get:Schema(description = "첨부 파일") val attachments: List<AttachmentInfo>? = null,
    @get:Schema(description = "댓글(오래된 순서)") val comments: List<CommentNode>? = null,
    @get:Schema(description = "이슈 이벤트(오래된 순서). 이벤트가 하나도 없으면 키가 없다") val events: List<IssueEventNode>? = null
)

@Schema(description = "이슈 한 건을 담은 응답")
class IssueResult(@get:Schema(description = "이슈") val result: IssueDetail)

@Schema(description = "이슈 댓글 목록 응답")
class CommentListResult(@get:Schema(description = "오래된 순서의 댓글. 한 줄 댓글은 `childComments`로 묶인다") val result: List<CommentNode>)

@Schema(description = "댓글 하나를 고친 결과")
class CommentEditedResult(@get:Schema(description = "고친 댓글") val result: CommentEdited)

@Schema(description = "고친 댓글")
class CommentEdited(
    @get:Schema(description = "댓글 번호") val id: Long,
    @get:Schema(description = "고친 내용") val body: String?,
    @get:Schema(description = "만든 시각") val createdAt: String,
    @get:Schema(description = "작성자") val author: UserSummary
)

@Schema(description = "이슈 수정 요청(전체). 네 항목 `title`, `body`, `state`, `assignees`가 모두 필요하다(v1.6 호환). 부분 수정은 `PATCH`를 쓴다")
class IssueUpdateRequest(
    @get:Schema(description = "제목", requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @get:Schema(description = "본문", requiredMode = Schema.RequiredMode.REQUIRED) val body: String,
    @get:Schema(description = "상태. `OPEN`이면 열림, 그 밖은 닫힘", allowableValues = ["OPEN", "CLOSED"], requiredMode = Schema.RequiredMode.REQUIRED) val state: String,
    @get:Schema(description = "담당자의 이메일 목록(한 명)", requiredMode = Schema.RequiredMode.REQUIRED) val assignees: List<AssigneeByEmail>,
    @get:Schema(description = "마일스톤 제목. 없으면 마일스톤을 비운다") val milestoneTitle: String? = null
)

@Schema(description = "이메일로 사용자를 가리킨다")
class AssigneeByEmail(@get:Schema(description = "이메일", example = "alice@example.com") val email: String)

@Schema(description = "이슈 부분 수정 요청. 보낸 항목만 바꾼다. 항목이 하나라도 틀리면 아무것도 바꾸지 않고 400이다")
class IssuePatchRequest(
    @get:Schema(description = "제목. 비어 있으면 안 된다") val title: String? = null,
    @get:Schema(description = "본문. `null`이면 비운다") val body: String? = null,
    @get:Schema(description = "상태", allowableValues = ["open", "closed"]) val state: String? = null,
    @get:Schema(description = "마일스톤 번호. `null`이면 마일스톤을 지운다", nullable = true) val milestone: Long? = null,
    @get:Schema(description = "담당자의 로그인 ID 목록. 담당자는 한 명까지이고 빈 목록이면 지운다") val assignees: List<String>? = null,
    @get:Schema(description = "라벨 번호 목록. 빈 목록이면 모두 뗀다. 같은 배타 분류의 라벨은 함께 쓸 수 없다") val labels: List<Long>? = null
)

@Schema(description = "이슈 여러 개 만들기 요청(이슈 가져오기에도 쓴다)")
class IssuesCreateRequest(
    @get:Schema(description = "만들 이슈들", requiredMode = Schema.RequiredMode.REQUIRED) val issues: List<IssueCreateItem>,
    @get:Schema(description = "알림을 보낼지. 기본 `false`(가져오기용이라 보내지 않는다)") val sendNotification: Boolean? = null
)

@Schema(description = "만들 이슈 한 건")
class IssueCreateItem(
    @get:Schema(description = "제목", requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @get:Schema(description = "본문") val body: String? = null,
    @get:Schema(description = "상태. `OPEN`이면 열림, 그 밖은 닫힘", example = "OPEN") val state: String? = null,
    @get:Schema(description = "작성자. 이메일로 찾고 없으면 익명 사용자(번호 -1)로 기록한다. 생략하면 토큰 주인") val author: AuthorByEmail? = null,
    @get:Schema(description = "만든 시각(긴 형식 `yyyy-MM-dd a hh:mm:ss Z`)", example = "2026-10-30 AM 10:15:30 +0900") val createdAt: String? = null,
    @get:Schema(description = "고친 시각(같은 형식)") val updatedAt: String? = null,
    @get:Schema(description = "담당자의 이메일 목록") val assignees: List<AssigneeByEmail>? = null,
    @get:Schema(description = "마일스톤 제목. 없는 제목이면 새로 만든다") val milestoneTitle: String? = null,
    @get:Schema(description = "마감일(같은 형식)") val dueDate: String? = null,
    @get:Schema(description = "라벨 목록. 없는 라벨은 만든다") val labels: List<LabelByName>? = null,
    @get:Schema(description = "이 번호로 만든다(가져오기). 생략하면 다음 번호") val number: Long? = null,
    @get:Schema(description = "먼저 올린 첨부(`POST /files`)의 번호들") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "작성자를 이메일로 가리킨다")
class AuthorByEmail(
    @get:Schema(description = "이메일", example = "alice@example.com") val email: String,
    @get:Schema(description = "이름") val name: String? = null
)

@Schema(description = "만들거나 찾을 라벨")
class LabelByName(
    @get:Schema(description = "라벨 이름") val labelName: String,
    @get:Schema(description = "색") val labelColor: String,
    @get:Schema(description = "분류 이름") val category: String
)

@Schema(description = "이슈 댓글 달기 요청. `comment`가 있으면 토큰 주인이 다는 일반 흐름(응답은 이슈 전체), 없으면 가져오기 흐름(`body`, `author`, `createdAt`을 쓰고 응답은 주소)이다")
class IssueCommentCreateRequest(
    @get:Schema(description = "댓글 내용(일반 흐름)") val comment: String? = null,
    @get:Schema(description = "댓글 내용(가져오기 흐름)") val body: String? = null,
    @get:Schema(description = "작성자(가져오기 흐름)") val author: AuthorByEmail? = null,
    @get:Schema(description = "작성 시각(가져오기 흐름, 긴 형식)") val createdAt: String? = null,
    @get:Schema(description = "먼저 올린 첨부의 번호들(가져오기 흐름)") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "댓글 수정 요청")
class CommentBodyRequest(@get:Schema(description = "새 내용. 비어 있으면 400", requiredMode = Schema.RequiredMode.REQUIRED) val body: String)

@Schema(description = "라벨 번호 목록 요청")
class LabelIdsRequest(@get:Schema(description = "라벨 번호 목록", requiredMode = Schema.RequiredMode.REQUIRED) val labels: List<Long>)

@Schema(description = "담당자 지정 요청. Yona의 이슈는 담당자가 한 명이라 정확히 한 명을 받는다")
class AssigneesRequest(@get:Schema(description = "담당자의 로그인 ID 목록(정확히 한 명)", requiredMode = Schema.RequiredMode.REQUIRED) val assignees: List<String>)

@Schema(description = "이슈 라벨을 바꾼 결과(v1.6 호환)")
class IssueLabelsChanged(
    @get:Schema(description = "프로젝트 소유자") val id: String,
    @get:Schema(description = "붙은 라벨 수") val labels: Int
)
