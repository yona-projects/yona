package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "위키 페이지 목록의 한 줄")
class WikiPageRef(
    @get:Schema(description = "페이지 이름. 제목의 공백이 `-`로 바뀐 값이며 경로에 쓴다", example = "My-First-Page") val name: String,
    @get:Schema(description = "표시용 제목. 이름의 `-`를 공백으로 되돌린 값", example = "My First Page") val title: String
)

@Schema(description = "위키 페이지 목록 응답")
class WikiPageList(@get:Schema(description = "제목순(대소문자 무시). `_Sidebar`와 `_Footer`는 뺀다") val pages: List<WikiPageRef>)

@Schema(description = "위키 커밋을 올린 사람")
class WikiCommitAuthor(val name: String?)

@Schema(description = "위키 커밋 한 건")
class WikiCommitNode(
    @get:Schema(description = "커밋 번호(40자)") val id: String,
    @get:Schema(description = "메시지의 첫 문단") val message: String,
    val author: WikiCommitAuthor,
    @get:Schema(description = "올린 시각(ISO 8601)") val date: String?
)

@Schema(description = "위키 페이지 한 건")
class WikiPageNode(
    val name: String,
    val title: String,
    @get:Schema(description = "마크다운 본문") val content: String,
    @get:Schema(description = "이 내용을 마지막으로 바꾼 커밋") val lastCommit: WikiCommitNode? = null
)

@Schema(description = "위키 페이지 한 건 응답")
class WikiPageResult(val page: WikiPageNode)

@Schema(description = "만들거나 고친 위키 페이지 응답")
class WikiPageRefResult(val page: WikiPageRef)

@Schema(description = "위키 페이지 이력 응답")
class WikiHistoryList(@get:Schema(description = "최신순") val history: List<WikiCommitNode>)

@Schema(description = "위키 diff 응답")
class WikiDiffResult(@get:Schema(description = "통합 diff 글. 변경이 없으면 빈 글") val diff: String)

@Schema(description = "위키 페이지 만들기 요청")
class WikiCreateRequest(
    @get:Schema(description = "제목. 200자 이하, `\\ / : * ? \" < > |`와 제어 문자 금지, `_`로 시작 금지(`_Sidebar`, `_Footer` 제외)", requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @get:Schema(description = "마크다운 본문. 없으면 빈 페이지") val content: String? = null,
    @get:Schema(description = "커밋 메시지. 없으면 `Create <이름>`") val message: String? = null
)

@Schema(description = "위키 페이지 고치기 요청. `title`과 `content` 중 하나는 있어야 한다")
class WikiUpdateRequest(
    @get:Schema(description = "새 제목. 이름이 달라지면 같은 커밋에서 이름이 바뀐다") val title: String? = null,
    @get:Schema(description = "새 본문. 없으면 본문은 그대로") val content: String? = null,
    @get:Schema(description = "커밋 메시지. 없으면 `Update <이름>` 또는 `Rename <옛 이름> to <새 이름>`") val message: String? = null
)
