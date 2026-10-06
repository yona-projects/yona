package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.UserMenu
import com.github.yonaprojects.yona.domain.user.UserMenuService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// <yona-usermenu> 웹 컴포넌트가 슬라이드 사이드바 데이터를 JSON으로 가져가는 엔드포인트.
@RestController
class UserMenuApiController(
    private val userRepository: UserRepository,
    private val userMenuService: UserMenuService
) {
    @GetMapping("/-_-api/v1/usermenu")
    fun usermenu(authentication: Authentication?): ResponseEntity<UserMenu> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(userMenuService.load(user))
    }
}
