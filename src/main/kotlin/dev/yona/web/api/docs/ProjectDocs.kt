package dev.yona.web.api.docs

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로젝트에서 켜 둔 메뉴")
class ProjectMenus(
    val code: Boolean, val issue: Boolean, val pullRequest: Boolean, val review: Boolean, val milestone: Boolean, val board: Boolean, val wiki: Boolean
)

@Schema(description = "포크의 원본 프로젝트")
class OriginalProjectRef(
    @get:Schema(description = "원본의 소유자") val owner: String,
    @get:Schema(description = "원본의 이름") val name: String
)

@Schema(description = "프로젝트 한 건")
class ProjectDetail(
    @get:Schema(description = "프로젝트 번호") val id: Long,
    @get:Schema(description = "소유자(사용자 로그인 ID나 조직 이름)", example = "alice") val owner: String,
    @get:Schema(description = "프로젝트 이름", example = "demo") val name: String,
    @get:Schema(description = "설명") val overview: String?,
    @get:Schema(description = "저장소 종류", allowableValues = ["GIT", "Subversion"], example = "GIT") val vcs: String?,
    @get:Schema(description = "공개 범위", allowableValues = ["PUBLIC", "PROTECTED", "PRIVATE"], example = "PUBLIC") val scope: String?,
    @get:Schema(description = "만든 시각", example = "2026-10-30T10:15:30+0900") val createdAt: String?,
    @get:Schema(description = "웹 화면의 주소") val url: String,
    @get:Schema(description = "포크이면 원본. 아니면 `null`", nullable = true) val originalProject: OriginalProjectRef?,
    @get:Schema(description = "조직 소유이면 조직 이름. 아니면 `null`", nullable = true) val organization: String?,
    @get:Schema(description = "켜 둔 메뉴") val menus: ProjectMenus
)

@Schema(description = "프로젝트 한 건을 담은 응답(`result`)")
class ProjectResult(@get:Schema(description = "프로젝트") val result: ProjectDetail)

@Schema(description = "프로젝트 목록 응답")
class ProjectListResponse(@get:Schema(description = "프로젝트들") val projects: List<ProjectDetail>)

@Schema(description = "프로젝트 구성원")
class ProjectMember(
    @get:Schema(description = "로그인 ID") val loginId: String,
    @get:Schema(description = "이름") val name: String,
    @get:Schema(description = "역할", allowableValues = ["manager", "member"]) val role: String?
)

@Schema(description = "프로젝트 구성원 목록 응답")
class ProjectMemberList(@get:Schema(description = "구성원과 역할. 사이트 관리자 역할은 뺀다") val members: List<ProjectMember>)

@Schema(description = "담당자 후보 목록 응답")
class AssignableUserList(@get:Schema(description = "담당자나 리뷰어로 정할 수 있는 사람(최대 100명)") val users: List<UserSummary>)

@Schema(description = "프로젝트의 라벨")
class LabelDetail(
    @get:Schema(description = "라벨 번호") val id: Long,
    @get:Schema(description = "라벨 이름", example = "bug") val labelName: String,
    @get:Schema(description = "색", example = "#d73a4a") val labelColor: String,
    @get:Schema(description = "분류 이름", example = "Type") val category: String?,
    @get:Schema(description = "분류 번호") val categoryId: Long?,
    @get:Schema(description = "분류가 단일 선택(같은 분류의 라벨을 함께 붙일 수 없음)인지") @get:JsonProperty("isExclusive") val isExclusive: Boolean
)

@Schema(description = "라벨 목록 응답")
class LabelListResponse(@get:Schema(description = "분류 이름, 라벨 이름 순서의 라벨") val labels: List<LabelDetail>)

@Schema(description = "라벨 한 건을 담은 응답")
class LabelResult(@get:Schema(description = "라벨") val result: LabelDetail)

@Schema(description = "프로젝트의 마일스톤")
class MilestoneDetail(
    @get:Schema(description = "마일스톤 번호") val id: Long,
    @get:Schema(description = "제목", example = "1.0") val title: String,
    @get:Schema(description = "상태", allowableValues = ["open", "closed"]) val state: String,
    @get:Schema(description = "설명") val description: String?,
    @get:Schema(description = "마감일. 없으면 빈 문자열", example = "2026-11-30T23:59:59+0900") val dueDate: String?
)

@Schema(description = "마일스톤 목록 응답")
class MilestoneListResponse(@get:Schema(description = "마감일 순서의 마일스톤") val milestones: List<MilestoneDetail>)

@Schema(description = "마일스톤 한 건을 담은 응답")
class MilestoneResult(@get:Schema(description = "마일스톤") val result: MilestoneDetail)

@Schema(description = "이슈 목록의 한 항목. 본문과 댓글은 `GET .../issues/{number}`로 읽는다")
class IssueSummary(
    @get:Schema(description = "이슈 번호") val number: Long,
    @get:Schema(description = "이슈 전체 번호") val id: Long,
    @get:Schema(description = "제목") val title: String,
    @get:Schema(description = "상태", allowableValues = ["open", "closed"]) val state: String,
    @get:Schema(description = "작성자") val author: UserSummary,
    @get:Schema(description = "담당자(한 명 이하)") val assignees: List<UserSummary>,
    @get:Schema(description = "라벨") val labels: List<LabelSummary>,
    @get:Schema(description = "마일스톤. 없으면 `null`", nullable = true) val milestone: MilestoneRef?,
    @get:Schema(description = "만든 시각") val createdAt: String,
    @get:Schema(description = "고친 시각") val updatedAt: String,
    @get:Schema(description = "마감일. 없으면 `null`", nullable = true) val dueDate: String?,
    @get:Schema(description = "댓글 수") val numOfComments: Int,
    @get:Schema(description = "웹 화면의 주소") val url: String,
    @get:Schema(description = "이슈를 닫은 시각(UTC, 초 단위). 목록 조회에서만 들어 있고 열린 이슈는 `null`", nullable = true) val closedAt: String? = null,
    @get:Schema(description = "이 작성자가 이 프로젝트에 낸 첫 이슈나 풀 리퀘스트인지. 목록 조회에서만 들어 있고 작성자를 모르면 `null`", nullable = true) val authorFirstTime: Boolean? = null
)

@Schema(description = "라벨 번호와 이름")
class LabelSummary(val id: Long, val labelName: String, val labelColor: String, val category: String?)

@Schema(description = "마일스톤 번호와 제목")
class MilestoneRef(val id: Long, val title: String)

@Schema(description = "이슈 목록 응답")
class IssueListResponse(
    @get:Schema(description = "이슈들") val items: List<IssueSummary>,
    page: Int, pageSize: Int, totalPages: Int, totalCount: Long
) : PageInfo(page, pageSize, totalPages, totalCount)

@Schema(description = "프로젝트 만들기 요청(사이트 관리자)")
class ProjectCreateRequest(
    @get:Schema(description = "프로젝트 이름", requiredMode = Schema.RequiredMode.REQUIRED) val projectName: String,
    @get:Schema(description = "설명") val projectDescription: String? = null,
    @get:Schema(description = "저장소 종류. 기본 `GIT`", allowableValues = ["GIT", "Subversion"]) val projectVcs: String? = null,
    @get:Schema(description = "공개 범위. 기본 `PRIVATE`", allowableValues = ["PUBLIC", "PROTECTED", "PRIVATE"]) val projectScope: String? = null,
    @get:Schema(description = "구성원. 이메일로 찾고 못 찾으면 건너뛴다") val members: List<ProjectMemberRequest>? = null
)

@Schema(description = "프로젝트를 만들 때 넣을 구성원")
class ProjectMemberRequest(
    @get:Schema(description = "이메일") val email: String,
    @get:Schema(description = "역할", example = "manager") val role: String? = null
)

@Schema(description = "만든 프로젝트")
class ProjectCreated(
    val id: Long, val owner: String, val name: String, val overview: String?, val vcs: String?
)

@Schema(description = "프로젝트 만들기 실패 응답(400). 같은 이름이 있으면 `status` 409와 `project`(이미 있는 프로젝트)를 담고, 그 밖의 실패는 `message`만 담는다")
class ProjectCreateError(
    @get:Schema(description = "같은 이름이 있을 때 409", example = "409") val status: Int? = null,
    @get:Schema(description = "같은 이름이 있을 때 `Conflict`", example = "Conflict") val reason: String? = null,
    @get:Schema(description = "이미 있는 프로젝트") val project: ProjectCreated? = null,
    @get:Schema(description = "실패 이유") val message: String? = null
)

@Schema(description = "라벨 여러 개 만들기 요청")
class LabelsCreateRequest(@get:Schema(description = "만들 라벨들", requiredMode = Schema.RequiredMode.REQUIRED) val labels: List<LabelByName>)

@Schema(description = "라벨 만들기 결과의 한 항목. 만들었으면 `status`가 201이고 이미 있으면 409이며 `message`와 입력한 `user`(요청 항목)를 담는다")
class LabelCreatedItem(
    @get:Schema(description = "201 또는 409") val status: Int,
    @get:Schema(description = "만든 라벨 이름") val label: String?,
    @get:Schema(description = "분류 이름") val category: String?,
    @get:Schema(description = "색") val labelColor: String?,
    @get:Schema(description = "분류가 단일 선택인지") @get:JsonProperty("isExclusive") val isExclusive: Boolean?,
    @get:Schema(description = "409일 때 이유", example = "Conflict") val reason: String?,
    @get:Schema(description = "409일 때 안내") val message: String?,
    @get:Schema(description = "409일 때 요청한 항목을 그대로 되돌린 객체") val user: Map<String, Any?>?
)

@Schema(description = "마일스톤 여러 개 만들기 요청")
class MilestonesCreateRequest(@get:Schema(description = "만들 마일스톤들", requiredMode = Schema.RequiredMode.REQUIRED) val milestones: List<MilestoneCreateItem>)

@Schema(description = "만들 마일스톤 한 건")
class MilestoneCreateItem(
    @get:Schema(description = "제목. 같은 제목이 있으면 만들지 않고 안내만 돌려준다") val title: String? = null,
    @get:Schema(description = "설명") val description: String? = null,
    @get:Schema(description = "마감일. `2030-01-02`, `2030-01-02T12:00:00`, `2030-01-02T12:00:00Z` 등을 받고 그날의 마지막 초로 맞춘다", example = "2030-01-02") val due_on: String? = null,
    @get:Schema(description = "상태. `closed`이면 닫힘, 그 밖은 열림", allowableValues = ["open", "closed"]) val state: String? = null
)

@Schema(description = "마일스톤 만들기 결과의 한 항목. 만들었으면 마일스톤(`id`, `title`, `state`, `description`, `due_on`)이고, 같은 제목이 있으면 `message`와 입력 `milestone`을 담는다")
class MilestoneCreatedItem(
    val id: Long?, val title: String?, val state: String?, val description: String?,
    @get:Schema(description = "마감일. `yyyy-MM-dd'T'HH:mm:ss'Z'`(서버 시간대)") val due_on: String?,
    @get:Schema(description = "같은 제목이 있을 때 안내") val message: String?,
    @get:Schema(description = "같은 제목이 있을 때 요청한 항목을 그대로 되돌린 객체") val milestone: Map<String, Any?>?
)

@Schema(description = "라벨 수정 요청. 보낸 항목만 바꾼다")
class LabelPatchRequest(
    @get:Schema(description = "라벨 이름(255자까지). 같은 분류에 같은 이름이 있으면 409") val labelName: String? = null,
    @get:Schema(description = "색") val labelColor: String? = null,
    @get:Schema(description = "분류 이름. 없으면 단일 선택이 아닌 분류로 새로 만든다") val category: String? = null
)

@Schema(description = "마일스톤 수정 요청. 보낸 항목만 바꾼다")
class MilestonePatchRequest(
    @get:Schema(description = "제목. 바뀐 제목이 다른 마일스톤과 같으면 409") val title: String? = null,
    @get:Schema(description = "설명. `null`이면 비운다") val description: String? = null,
    @get:Schema(description = "마감일. `null`이면 지운다. 그날의 마지막 초로 맞춘다", example = "2030-01-02", nullable = true) val due_on: String? = null,
    @get:Schema(description = "상태", allowableValues = ["open", "closed"]) val state: String? = null
)

@Schema(description = "구독자 목록 응답")
class WatchersResponse(
    @get:Schema(description = "전체 구독자 수") val totalWatchers: Int,
    @get:Schema(description = "목록에 담은 수(최대 100)") val watchersInList: Int,
    @get:Schema(description = "구독자") val watchers: List<WatcherRef>
)

@Schema(description = "구독자")
class WatcherRef(val name: String?, @get:Schema(description = "사용자 화면의 주소") val url: String)

@Schema(description = "프로젝트 내보내기. 구성원, 라벨, 이슈, 글, 마일스톤을 모두 담는다. 날짜는 긴 형식(`yyyy-MM-dd a hh:mm:ss Z`)이다")
class ProjectExport(
    val owner: String, val projectName: String, val projectDescription: String?, val projectCreatedDate: String?, val projectVcs: String?, val projectScope: String?,
    @get:Schema(description = "담당자로 지정된 적 있는 사용자") val assignees: List<UserBrief>,
    @get:Schema(description = "이슈나 글을 쓴 사용자와 풀 리퀘스트를 보낸 사용자") val authors: List<UserBrief>,
    val memberCount: Int,
    @get:Schema(description = "구성원") val members: List<ExportMember>,
    val issueCount: Int, val postCount: Int, val milestoneCount: Int,
    @get:Schema(description = "라벨") val labels: List<ExportLabel>,
    @get:Schema(description = "이슈 전체(본문과 댓글 포함)") val issues: List<IssueDetail>,
    @get:Schema(description = "글 전체(본문과 댓글 포함)") val posts: List<PostDetail>,
    @get:Schema(description = "마일스톤") val milestones: List<ExportMilestone>
)

@Schema(description = "내보내기의 구성원")
class ExportMember(val loginId: String, val name: String, val role: String?, val email: String?)

@Schema(description = "내보내기의 라벨")
class ExportLabel(val labelName: String, val labelColor: String, val category: String?, @get:JsonProperty("isExclusive") val isExclusive: Boolean?)

@Schema(description = "내보내기의 마일스톤")
class ExportMilestone(val id: Long, val title: String, val state: String?, val description: String?, val dueDate: String?)

@Schema(description = "서버 상태")
class HelloResponse(
    @get:Schema(example = "I'm alive!") val message: String,
    @get:Schema(example = "true") val ok: Boolean
)

@Schema(description = "메뉴 켜기와 끄기. 보낸 메뉴만 바꾼다. 키는 `code`, `issue`, `pullRequest`, `review`, `milestone`, `board`, `wiki`이다")
class ProjectMenuChanges(
    val code: Boolean? = null, val issue: Boolean? = null, val pullRequest: Boolean? = null, val review: Boolean? = null, val milestone: Boolean? = null, val board: Boolean? = null, val wiki: Boolean? = null
)

@Schema(description = "프로젝트 만들기 요청(내 계정이나 조직). `name`만 필수이다")
class ProjectManageCreateRequest(
    @get:Schema(description = "프로젝트 이름. 영문, 숫자, `-`, `_`, `.`, 한글만 쓰고 `.`, `..`, `.git`은 쓸 수 없다. 같은 소유자에게 같은 이름(대소문자 무시)이 있으면 409", requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
    @get:Schema(description = "설명") val description: String? = null,
    @get:Schema(description = "공개 범위. 없으면 사이트 설정의 기본값", allowableValues = ["PUBLIC", "PROTECTED", "PRIVATE"]) val scope: String? = null,
    @get:Schema(description = "저장소 종류. 기본 `GIT`", allowableValues = ["GIT", "Subversion"]) val vcs: String? = null,
    @get:Schema(description = "켜 둘 메뉴. 보낸 메뉴만 기본 메뉴에서 바꾼다") val menus: ProjectMenuChanges? = null
)

@Schema(description = "프로젝트 설정 바꾸기 요청. 보낸 항목만 바꾼다")
class ProjectUpdateRequest(
    @get:Schema(description = "새 이름. 바꾸면 저장소 디렉터리도 옮긴다") val name: String? = null,
    @get:Schema(description = "설명. `null`이면 비운다") val description: String? = null,
    @get:Schema(description = "공개 범위", allowableValues = ["PUBLIC", "PROTECTED", "PRIVATE"]) val scope: String? = null,
    @get:Schema(description = "코드 접근을 구성원으로 제한할지") val isCodeAccessibleMemberOnly: Boolean? = null,
    @get:Schema(description = "새 커밋이 오면 오래된 승인을 해제할지") val isDismissingStaleApprovals: Boolean? = null,
    @get:Schema(description = "병합 커밋 방식을 허용할지") val allowMergeCommit: Boolean? = null,
    @get:Schema(description = "스쿼시 방식을 허용할지") val allowSquashMerge: Boolean? = null,
    @get:Schema(description = "리베이스 방식을 허용할지") val allowRebaseMerge: Boolean? = null,
    @get:Schema(description = "기본 병합 방식. 허용한 방식이어야 하고 허용한 방식이 하나는 있어야 한다", allowableValues = ["merge", "squash", "rebase"]) val defaultMergeMethod: String? = null,
    @get:Schema(description = "기본 브랜치(`master`나 `refs/heads/master`). Git이고 있는 브랜치여야 한다") val defaultBranch: String? = null,
    @get:Schema(description = "메뉴 켜기와 끄기. 보낸 메뉴만 바꾼다") val menus: ProjectMenuChanges? = null
)

@Schema(description = "프로젝트 구성원 더하기 요청")
class ProjectMemberAddRequest(
    @get:Schema(description = "더할 사용자의 로그인 ID", requiredMode = Schema.RequiredMode.REQUIRED) val loginId: String,
    @get:Schema(description = "역할. 기본 `member`", allowableValues = ["manager", "member"]) val role: String? = null
)

@Schema(description = "프로젝트 구성원 역할 바꾸기 요청")
class ProjectMemberRoleRequest(@get:Schema(description = "새 역할", allowableValues = ["manager", "member"], requiredMode = Schema.RequiredMode.REQUIRED) val role: String)

@Schema(description = "프로젝트 구성원 한 명 응답")
class ProjectMemberResult(val result: ProjectMember)

@Schema(description = "태그를 단 사람과 시각(주석 태그)")
class TagTagger(val name: String?, val date: String?)

@Schema(description = "태그가 가리키는 커밋(주석 태그를 모두 벗긴 끝의 커밋)")
class TagCommit(@get:Schema(description = "커밋 번호") val id: String, val date: String?)

@Schema(description = "Git 태그 한 개. 경량 태그는 `message`와 `tagger`가 없고, 커밋이 아닌 대상을 가리키면 `commit`이 없다")
class TagNode(
    @get:Schema(description = "태그 이름(`refs/tags/` 없이). 슬래시가 있을 수 있다", example = "release/1.0") val name: String,
    @get:Schema(description = "주석 태그인지") val annotated: Boolean,
    @get:Schema(description = "주석 태그의 메시지") val message: String? = null,
    @get:Schema(description = "태그를 단 사람과 시각") val tagger: TagTagger? = null,
    @get:Schema(description = "가리키는 커밋") val commit: TagCommit? = null,
    @get:Schema(description = "서명 판정. 주석 태그에만 있다") val verification: VerificationNode? = null
)

@Schema(description = "태그 목록 응답")
class TagList(@get:Schema(description = "태그 시각이 최근인 순서") val tags: List<TagNode>)

@Schema(description = "태그 한 개 응답")
class TagResult(val result: TagNode)

@Schema(description = "태그 만들기 요청")
class TagCreateRequest(
    @get:Schema(description = "태그 이름. Git 참조 이름 규칙을 지켜야 한다(공백, `..` 불가). 같은 이름이 있으면 409", requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
    @get:Schema(description = "대상. 브랜치, 태그, 커밋 번호(앞부분만 가능). 없으면 기본 브랜치의 끝", example = "master") val target: String? = null,
    @get:Schema(description = "메시지. 있으면 주석 태그(태그를 단 사람과 시각이 남는다), 없으면 경량 태그") val message: String? = null
)
