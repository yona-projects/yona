package dev.yona.web.user

import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 사용자 검색이다. v1.6 `UserApp.users`(`app/controllers/UserApp.java:86-123`)에 해당한다.
 * 기본 로그인 페이지 지정(`setDefaultLoginPage`)은 홈 컨트롤러가 가진다.
 */
@Controller
@AnonymousCheck
class UserApiController(
    private val users: UserRepository,
    private val avatars: AvatarService
) {

    /**
     * 구성원 추가 입력란의 자동 완성에 쓰는 검색이다. 구성원 화면(`.../members`)에서 JSON을 받겠다고 부른 요청에만 답한다.
     * 최대 10명이고 더 있으면 `Content-Range: items 10/전체`를 붙인다. 항목은 아바타, 이름, 아이디를 담은 HTML 조각(`info`)과 `loginId`이다.
     */
    @GetMapping("/users")
    fun search(@RequestParam(name = "query", defaultValue = "") query: String, request: HttpServletRequest): ResponseEntity<*> {
        val referer = request.getHeader(HttpHeaders.REFERER).orEmpty()
        val acceptsJson = request.getHeader(HttpHeaders.ACCEPT)?.let { it.contains("application/json") || it.contains("*/*") || it.contains("application/*") } ?: true
        if (!referer.endsWith("members") || !acceptsJson) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        if (query.isEmpty()) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(emptyList<Map<String, String>>())

        val page = users.search(query, PageRequest.of(0, MAX_FETCH_USERS))
        val items = page.content.map { user ->
            mapOf(
                "info" to "<img class='mention_image' src='${avatars.avatarUrl(user, MENTION_AVATAR_SIZE)}'>" +
                    "<b class='mention_name'>${user.name}</b><span class='mention_username'> @${user.loginId}</span>",
                "loginId" to user.loginId.orEmpty()
            )
        }
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (page.totalElements > MAX_FETCH_USERS) builder.header(HttpHeaders.CONTENT_RANGE, "items $MAX_FETCH_USERS/${page.totalElements}")
        return builder.body(items)
    }

    private companion object {
        /** 자동 완성 입력란이 한 번에 보여 주는 개수와 같다 (`yobi.ui.Typeaheds.js`). */
        const val MAX_FETCH_USERS = 10
        const val MENTION_AVATAR_SIZE = 64
    }
}
