package dev.yona.web.api

import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 외부용 REST API가 함께 쓰는 요청 처리 도우미이다. v1.6 Play의 `request().body().asJson()`, `UserApi.isAuthored`, `getAuthorizedUser`에 해당한다.
 */
@Component
class ApiRequests(private val users: UserRepository) {
    private val mapper = JsonMapper.builder().build()

    /** JSON 본문이다. 내용 형식이 JSON이 아니거나 읽을 수 없으면 `null`이다 (Play는 이때 `asJson()`이 `null`이다). */
    fun json(request: HttpServletRequest): JsonNode? {
        if (request.contentType?.contains("json", ignoreCase = true) != true) return null
        return try { mapper.readTree(request.inputStream) } catch (e: Exception) { null }
    }

    /**
     * 이 요청을 **토큰으로 인증한** 사용자이다. 세션 로그인은 보지 않는다(v1.6 `isAuthored`가 헤더만 본다). 외부 API(`/-_-api/v1`)는 인증 필터가 토큰만 인정하고
     * 범위를 검사하므로 여기서는 필터가 채운 인증 정보에서 사용자를 읽는다(추가 개발 항목 X-5, `decisions.md` D-36). 토큰 인증이 아니면 `null`이다.
     */
    fun tokenUser(request: HttpServletRequest): User? {
        val authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication as? dev.yona.security.YonaAuthentication ?: return null
        if (authentication.scopes == null) return null
        return users.findById(authentication.principal.userId).orElse(null)
    }

    /** `{"message": 문장}` 본문의 응답이다. */
    fun message(status: HttpStatus, text: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to text))
}
