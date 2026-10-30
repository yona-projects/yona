package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

/*
 * OpenAPI 문서(로드맵 X-31, `decisions.md` D-50)가 응답과 요청 본문의 모양을 밝히려고 쓰는 문서용 모델이다. 처리기가 JSON을 직접 읽고 쓰므로
 * 이 클래스들은 실행 중에는 쓰이지 않고 문서에만 나타난다. 실제 JSON과 같은 키 이름을 쓰고, 값이 없으면 키가 빠지는 항목은 선택 항목으로 적는다.
 */

/** 이슈와 댓글 등에 붙는 사용자 정보이다. */
@Schema(description = "사용자 정보")
class UserBrief(
    @get:Schema(description = "로그인 ID", example = "alice") val loginId: String,
    @get:Schema(description = "이름", example = "앨리스") val name: String,
    @get:Schema(description = "이메일. 없으면 빈 문자열", example = "alice@example.com") val email: String? = null
)

/** 로그인 ID와 이름만 담는 사용자 정보이다(이메일은 없다). */
@Schema(description = "사용자 요약(로그인 ID와 이름)")
class UserSummary(
    @get:Schema(description = "로그인 ID", example = "alice") val loginId: String,
    @get:Schema(description = "이름", example = "앨리스") val name: String
)

@Schema(description = "첨부 파일")
class AttachmentInfo(
    @get:Schema(description = "첨부 번호") val id: Long,
    @get:Schema(description = "파일 이름", example = "screenshot.png") val name: String,
    @get:Schema(description = "내용의 해시") val hash: String?,
    @get:Schema(description = "붙은 곳의 종류", example = "ISSUE_POST") val containerType: String,
    @get:Schema(description = "MIME 형식", example = "image/png") val mimeType: String?,
    @get:Schema(description = "바이트 수") val size: Long,
    @get:Schema(description = "붙은 곳의 번호") val containerId: String,
    @get:Schema(description = "올린 시각(밀리초 단위 유닉스 시각)") val createdDate: Long?,
    @get:Schema(description = "올린 사람의 로그인 ID") val ownerLoginId: String?
)

@Schema(description = "라벨 이름과 색과 분류")
class LabelBrief(
    @get:Schema(description = "라벨 이름", example = "bug") val labelName: String,
    @get:Schema(description = "색(CSS 색 값)", example = "#d73a4a") val labelColor: String,
    @get:Schema(description = "분류 이름", example = "Type") val category: String?
)

@Schema(description = "여러 건을 한꺼번에 만든 결과의 한 항목. 만든 자원의 주소를 담는다")
class CreatedLocation(
    @get:Schema(description = "HTTP 상태", example = "201") val status: Int,
    @get:Schema(description = "만든 자원의 주소", example = "/alice/demo/issue/3") val location: String
)

@Schema(description = "성공했지만 본문 없이 번호만 알리는 응답")
class NumberResponse(@get:Schema(description = "번호", example = "7") val number: Long)

@Schema(description = "쪽 나누기 정보")
open class PageInfo(
    @get:Schema(description = "현재 쪽(1부터)", example = "1") val page: Int,
    @get:Schema(description = "쪽 크기", example = "15") val pageSize: Int,
    @get:Schema(description = "전체 쪽 수", example = "3") val totalPages: Int,
    @get:Schema(description = "전체 결과 수", example = "42") val totalCount: Long
)
