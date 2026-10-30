package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

// --- 이슈의 Yona 고유 기능(공유자, 가중치, 지켜보기) ---

@Schema(description = "공유자 목록 응답")
class SharerListResult(@get:Schema(description = "추가한 순서의 공유자") val result: List<UserSummary>)

@Schema(description = "공유자를 더하거나 뺄 요청. `sharers`와 `projects` 중 하나 이상이 필요하다")
class SharersRequest(
    @get:Schema(description = "사용자의 로그인 ID 목록") val sharers: List<String>? = null,
    @get:Schema(description = "프로젝트 번호 목록. 그 프로젝트의 구성원이 모두 공유자가 된다(읽을 수 있는 프로젝트만)") val projects: List<Long>? = null
)

@Schema(description = "가중치(중요도) 올리기와 내리기 요청")
class WeightRequest(@get:Schema(description = "`1`이면 올리고 `-1`이면 내린다. 그 밖의 값은 400", allowableValues = ["1", "-1"], requiredMode = Schema.RequiredMode.REQUIRED) val delta: Int)

@Schema(description = "바뀐 가중치")
class WeightValue(@get:Schema(description = "새 가중치") val weight: Int)

@Schema(description = "가중치 응답")
class WeightResult(val result: WeightValue)

@Schema(description = "지켜보는 상태")
class WatchingValue(@get:Schema(description = "지켜보고 있는지") val watching: Boolean)

@Schema(description = "지켜보기 응답")
class WatchingResult(val result: WatchingValue)

// --- 웹훅 ---

@Schema(description = "프로젝트 웹훅. 비밀 값은 돌려주지 않고 있는지만 알린다")
class HookDetail(
    @get:Schema(description = "웹훅 번호") val id: Long,
    @get:Schema(description = "알림을 보낼 주소") val payloadUrl: String,
    @get:Schema(description = "형식", allowableValues = ["SIMPLE", "DETAIL_SLACK", "DETAIL_HANGOUT_CHAT", "JSON"]) val webhookType: String,
    @get:Schema(description = "Git 푸시 이벤트도 보낼지") val gitPush: Boolean,
    @get:Schema(description = "비밀 값이 있는지") val hasSecret: Boolean,
    @get:Schema(description = "만든 시각") val createdAt: String?
)

@Schema(description = "웹훅 목록 응답")
class HookListResult(val result: List<HookDetail>)

@Schema(description = "웹훅 한 건 응답")
class HookResult(val result: HookDetail)

@Schema(description = "웹훅 만들기 요청")
class HookCreateRequest(
    @get:Schema(description = "알림을 보낼 주소(2000자까지)", requiredMode = Schema.RequiredMode.REQUIRED) val payloadUrl: String,
    @get:Schema(description = "비밀 값(250자까지). 없으면 빈 문자열") val secret: String? = null,
    @get:Schema(description = "형식. 기본 `SIMPLE`", allowableValues = ["SIMPLE", "DETAIL_SLACK", "DETAIL_HANGOUT_CHAT", "JSON"]) val webhookType: String? = null,
    @get:Schema(description = "Git 푸시 이벤트도 보낼지. 없으면 `JSON` 형식만 켠다(`JSON` 웹훅은 푸시 이벤트만 받는다)") val gitPush: Boolean? = null
)

// --- 포크 ---

@Schema(description = "포크 만들기 요청. 모두 선택이다")
class ForkRequest(
    @get:Schema(description = "포크가 놓일 소유자. 내 로그인 ID(기본)이거나 내가 관리하는 조직 이름이다. 그 밖은 403") val owner: String? = null,
    @get:Schema(description = "포크의 프로젝트 이름. 없으면 소유자에게 비어 있는 이름(원본 이름, 있으면 `이름-1`)") val name: String? = null,
    @get:Schema(description = "공개 범위. 없으면 원본을 따른다. `PROTECTED`는 조직에서만 쓴다", allowableValues = ["PUBLIC", "PROTECTED", "PRIVATE"]) val projectScope: String? = null
)

// --- 조직 ---

@Schema(description = "조직 한 건")
class OrganizationDetail(
    @get:Schema(description = "조직 이름") val name: String,
    @get:Schema(description = "설명") val description: String?,
    @get:Schema(description = "만든 시각") val createdAt: String?,
    @get:Schema(description = "토큰 주인이 볼 수 있는 프로젝트 수") val projectCount: Int,
    @get:Schema(description = "토큰 주인의 역할. 구성원일 때만 있다", allowableValues = ["admin", "member"]) val role: String? = null
)

@Schema(description = "조직 한 건 응답")
class OrganizationResult(val result: OrganizationDetail)

@Schema(description = "조직 목록 응답")
class OrganizationListResult(val result: List<OrganizationDetail>)

@Schema(description = "조직 구성원")
class OrganizationMember(
    val loginId: String, val name: String?,
    @get:Schema(description = "역할", allowableValues = ["admin", "member"]) val role: String?
)

@Schema(description = "조직 구성원 목록 응답")
class OrganizationMemberListResult(@get:Schema(description = "가입한 순서의 구성원") val result: List<OrganizationMember>)

@Schema(description = "조직 구성원 한 명 응답")
class OrganizationMemberResult(val result: OrganizationMember)

@Schema(description = "조직 만들기 요청. 만든 사람이 조직 관리자가 된다")
class OrganizationCreateRequest(
    @get:Schema(description = "조직 이름. 로그인 ID와 같은 규칙이고 사용자나 다른 조직과 겹치면 409", requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
    @get:Schema(description = "설명") val description: String? = null
)

@Schema(description = "조직 수정 요청. 보낸 항목만 바꾼다(로고는 웹 화면에서만). 이름이 바뀌면 프로젝트 소유자와 저장소도 옮겨진다")
class OrganizationPatchRequest(
    @get:Schema(description = "새 이름") val name: String? = null,
    @get:Schema(description = "설명. `null`이면 비운다") val description: String? = null
)

@Schema(description = "구성원 더하기 요청")
class MemberAddRequest(
    @get:Schema(description = "더할 사용자의 로그인 ID", requiredMode = Schema.RequiredMode.REQUIRED) val loginId: String,
    @get:Schema(description = "역할. 기본 `member`", allowableValues = ["admin", "member"]) val role: String? = null
)

@Schema(description = "구성원 역할 바꾸기 요청")
class MemberRoleRequest(@get:Schema(description = "새 역할", allowableValues = ["admin", "member"], requiredMode = Schema.RequiredMode.REQUIRED) val role: String)

// --- 검색 ---

@Schema(description = "종류별 검색 결과 수")
class SearchCountsDoc(
    val users: Long, val projects: Long, val issues: Long, val posts: Long, val milestones: Long, val issueComments: Long, val postComments: Long, val reviews: Long
)

@Schema(description = "검색 결과 한 항목. 어떤 키가 있는지는 `searchType`에 따라 다르다. 이슈, 글, 댓글, 코드 리뷰는 `number`, `title`, `url`, `snippets`, `project`, `author`, `createdAt`, 마일스톤은 `title`, `url`, `snippets`, `project`, `dueDate`, 사용자는 `loginId`, `name`, `since`, 프로젝트는 `owner`, `name`, `overview`, `createdAt`, `originalProject`")
class SearchItem(
    @get:Schema(description = "글이나 이슈 번호. 댓글은 댓글이 달린 글의 번호") val number: Long? = null,
    @get:Schema(description = "제목") val title: String? = null,
    @get:Schema(description = "웹 화면의 주소") val url: String? = null,
    @get:Schema(description = "찾은 낱말 앞뒤의 발췌") val snippets: List<String>? = null,
    @get:Schema(description = "소속 프로젝트(`소유자/이름`)") val project: String? = null,
    @get:Schema(description = "작성자") val author: SearchAuthor? = null,
    @get:Schema(description = "만든 시각") val createdAt: String? = null,
    @get:Schema(description = "마일스톤의 마감일") val dueDate: String? = null,
    @get:Schema(description = "사용자의 로그인 ID") val loginId: String? = null,
    @get:Schema(description = "사용자나 프로젝트의 이름") val name: String? = null,
    @get:Schema(description = "사용자의 가입일") val since: String? = null,
    @get:Schema(description = "프로젝트의 소유자") val owner: String? = null,
    @get:Schema(description = "프로젝트의 설명") val overview: String? = null,
    @get:Schema(description = "프로젝트가 포크이면 원본") val originalProject: OriginalProjectRef? = null
)

@Schema(description = "검색 결과의 작성자")
class SearchAuthor(val loginId: String?, val name: String?)

@Schema(description = "검색 응답")
class SearchResponse(
    @get:Schema(description = "찾은 낱말") val keyword: String,
    @get:Schema(description = "확정된 종류. 요청이 `auto`이면 결과가 있는 첫 종류", allowableValues = ["issue", "post", "milestone", "issue_comment", "post_comment", "review", "user", "project"]) val searchType: String,
    @get:Schema(description = "종류별 결과 수") val counts: SearchCountsDoc,
    @get:Schema(description = "결과(한 쪽 20개)") val items: List<SearchItem>,
    @get:Schema(description = "현재 쪽(1부터)") val page: Int,
    @get:Schema(description = "전체 쪽 수") val totalPages: Int
)

// --- 첨부 파일 ---

@Schema(description = "첨부 파일 올리기 요청(멀티파트)")
class FileUploadRequest(
    @get:Schema(description = "올릴 파일", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED) val filePath: String,
    @get:Schema(description = "올린 사람을 이메일로 가리킨다(가져오기용)") val authorEmail: String? = null,
    @get:Schema(description = "올린 사람을 로그인 ID로 가리킨다(가져오기용)") val authorLoginId: String? = null
)

@Schema(description = "올린 첨부 파일. 값은 모두 문자열이다")
class AttachmentUploaded(
    @get:Schema(description = "첨부 번호. 글이나 댓글을 만들 때 `temporaryUploadFiles`에 담는다") val id: String,
    @get:Schema(description = "MIME 형식") val mimeType: String,
    @get:Schema(description = "파일 이름") val name: String,
    @get:Schema(description = "내려받는 주소", example = "/files/12") val url: String,
    @get:Schema(description = "바이트 수") val size: String
)
