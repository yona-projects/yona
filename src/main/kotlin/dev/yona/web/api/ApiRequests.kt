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
     * `Authorization: token <값>` 헤더의 토큰으로 찾은 사용자이다. 헤더가 없거나 토큰이 없거나 사용자를 못 찾으면 `null`이다.
     * 세션 로그인은 보지 않는다(v1.6 `isAuthored`가 헤더만 본다).
     */
    fun tokenUser(request: HttpServletRequest): User? {
        val header = request.getHeader("Authorization") ?: return null
        val parts = header.split("token")
        if (parts.size < 2) return null
        return users.findByToken(parts[1].replace(Regex("\\s"), ""))
    }

    /** `{"message": 문장}` 본문의 응답이다. */
    fun message(status: HttpStatus, text: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to text))
}
