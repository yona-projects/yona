package dev.yona.support

import dev.yona.web.access.AnonymousCheck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `@AnonymousCheck`의 동작을 확인하기 위한 테스트 전용 엔드포인트이다. 테스트 소스에만 있다.
 */
@RestController
class AnonymousCheckTestControllers {

    @AnonymousCheck
    @GetMapping("/__test/anon/default")
    fun default(): String = "ok"

    @AnonymousCheck(requiresLogin = true)
    @GetMapping("/__test/anon/requires-login")
    fun requiresLogin(): String = "ok"

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @GetMapping("/__test/anon/requires-login-flash")
    fun requiresLoginWithFlash(): String = "ok"

    @GetMapping("/__test/anon/unchecked")
    fun unchecked(): String = "ok"
}
