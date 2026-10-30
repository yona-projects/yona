package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게시글 한 건. 첨부와 댓글이 없으면 키가 없다")
class PostDetail(
    @get:Schema(description = "프로젝트 안의 글 번호", example = "2") val number: Long,
    @get:Schema(description = "글의 전체 번호") val id: Long,
    @get:Schema(description = "제목") val title: String,
    @get:Schema(description = "자원 종류. 게시글은 항상 `BOARD_POST`", example = "BOARD_POST") val type: String,
    @get:Schema(description = "작성자") val author: UserBrief,
    @get:Schema(description = "만든 시각") val createdAt: String,
    @get:Schema(description = "고친 시각") val updatedAt: String,
    @get:Schema(description = "본문(마크다운)") val body: String?,
    @get:Schema(description = "프로젝트 소유자") val owner: String,
    @get:Schema(description = "프로젝트 이름") val projectName: String,
    @get:Schema(description = "공지인지(`GET posts/{number}`와 수정 응답에 있다)") val notice: Boolean? = null,
    @get:Schema(description = "붙은 라벨(`GET posts/{number}`와 수정 응답에 있다)") val labels: List<PostLabel>? = null,
    @get:Schema(description = "첨부 파일") val attachments: List<AttachmentInfo>? = null,
    @get:Schema(description = "댓글(오래된 순서)") val comments: List<CommentNode>? = null
)

@Schema(description = "게시글 한 건을 담은 응답")
class PostResult(@get:Schema(description = "게시글") val result: PostDetail)

@Schema(description = "게시글에 붙은 라벨")
class PostLabel(val id: Long, val labelName: String, val labelColor: String, val category: String?)

@Schema(description = "게시글 목록의 한 항목. 본문과 댓글은 `GET posts/{number}`로 읽는다")
class PostSummary(
    val number: Long, val id: Long, val title: String,
    @get:Schema(description = "공지인지. 공지도 목록에 함께 나온다") val notice: Boolean,
    val author: UserSummary, val labels: List<LabelDetail>,
    val createdAt: String, val updatedAt: String, val numOfComments: Int,
    @get:Schema(description = "웹 화면의 주소") val url: String
)

@Schema(description = "게시글 목록 응답")
class PostListResponse(
    @get:Schema(description = "글들") val items: List<PostSummary>,
    page: Int, pageSize: Int, totalPages: Int, totalCount: Long
) : PageInfo(page, pageSize, totalPages, totalCount)

@Schema(description = "게시글 부분 수정 요청. 보낸 항목만 바꾸고 공지와 README 표시는 그대로 둔다. 본문이 바뀌면 변경 이력이 쌓인다")
class PostPatchRequest(
    @get:Schema(description = "제목. 비어 있으면 400") val title: String? = null,
    @get:Schema(description = "본문. `null`이면 비운다") val body: String? = null
)

@Schema(description = "게시글 여러 개 만들기 요청(가져오기용). 알림은 보내지 않는다")
class PostsCreateRequest(@get:Schema(description = "만들 글들", requiredMode = Schema.RequiredMode.REQUIRED) val posts: List<PostCreateItem>)

@Schema(description = "만들 글 한 건")
class PostCreateItem(
    @get:Schema(description = "제목", requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @get:Schema(description = "본문") val body: String? = null,
    @get:Schema(description = "작성자. 이메일로 찾고 없으면 익명 사용자(번호 -1)로 기록한다. 생략하면 토큰 주인") val author: AuthorByEmail? = null,
    @get:Schema(description = "만든 시각(긴 형식 `yyyy-MM-dd a hh:mm:ss Z`)", example = "2026-10-30 AM 10:15:30 +0900") val createdAt: String? = null,
    @get:Schema(description = "고친 시각(같은 형식)") val updatedAt: String? = null,
    @get:Schema(description = "이 번호로 만든다(가져오기). 생략하면 다음 번호") val number: Long? = null,
    @get:Schema(description = "먼저 올린 첨부(`POST /files`)의 번호들") val temporaryUploadFiles: List<String>? = null
)

@Schema(description = "게시글 댓글 달기 요청(가져오기용). 알림은 보내지 않는다")
class PostCommentCreateRequest(
    @get:Schema(description = "댓글 내용") val body: String? = null,
    @get:Schema(description = "작성 시각(긴 형식)") val createdAt: String? = null,
    @get:Schema(description = "작성자. 생략하면 토큰 주인") val author: AuthorByEmail? = null,
    @get:Schema(description = "먼저 올린 첨부의 번호들") val temporaryUploadFiles: List<String>? = null
)
