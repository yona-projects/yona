package com.github.yonaprojects.yona.mcp

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

// MCP 도구(@Tool) 메서드에서 던지는 오류. Spring AI의 MethodToolCallback은 도구 메서드가 던진
// 예외를 잡아 CallToolResult(isError=true, 메시지 그대로)로 변환해 MCP 클라이언트에 돌려주므로
// (Spring AI Tool Calling 공식 문서), 별도의 예외 처리 인프라를 새로 만들 필요가 없다.
class McpToolException(message: String) : RuntimeException(message)

// IssueRestApiController/PullRequestApiController와 동일한 얇은 어댑터 원칙: 기존
// IssueController/PullRequestController/CommentController가 반환하는 ResponseEntity<T>
// (REST API와 완전히 동일한 반환값)를 MCP 도구의 반환값으로 그대로 풀어주거나, 실패
// 상태코드를 사람이 읽을 수 있는 McpToolException으로 변환한다 — 신규 비즈니스 로직 없음.
fun <T : Any> ResponseEntity<T>.unwrapForMcp(notFoundMessage: String = "요청한 리소스를 찾을 수 없습니다."): T {
    if (statusCode.is2xxSuccessful) {
        return body ?: throw McpToolException("서버가 빈 응답을 반환했습니다.")
    }
    return when (statusCode) {
        HttpStatus.NOT_FOUND -> throw McpToolException(notFoundMessage)
        HttpStatus.FORBIDDEN -> throw McpToolException("이 작업을 수행할 권한이 없습니다.")
        HttpStatus.UNAUTHORIZED -> throw McpToolException("인증이 필요합니다.")
        else -> {
            val errorMessage = (body as? Map<*, *>)?.get("error")?.toString() ?: body?.toString()
            throw McpToolException("요청이 실패했습니다($statusCode)${errorMessage?.let { ": $it" } ?: ""}")
        }
    }
}

// addReviewer/removeReviewer처럼 본문이 없는(ResponseEntity<Unit>) 동작용.
// 반환값이 필요 없는 도구(성공 여부만 중요)에 쓴다.
fun ResponseEntity<*>.requireSuccessForMcp(notFoundMessage: String = "요청한 리소스를 찾을 수 없습니다.") {
    if (statusCode.is2xxSuccessful) return
    when (statusCode) {
        HttpStatus.NOT_FOUND -> throw McpToolException(notFoundMessage)
        HttpStatus.FORBIDDEN -> throw McpToolException("이 작업을 수행할 권한이 없습니다.")
        HttpStatus.UNAUTHORIZED -> throw McpToolException("인증이 필요합니다.")
        else -> throw McpToolException("요청이 실패했습니다($statusCode)")
    }
}
