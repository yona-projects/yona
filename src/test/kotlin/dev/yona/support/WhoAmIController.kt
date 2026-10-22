package dev.yona.support

import dev.yona.security.CurrentUserProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 테스트에서 "이 요청을 보낸 사용자가 누구로 식별되는가"를 확인하기 위한 엔드포인트이다.
 * 테스트 소스에만 있으므로 운영 코드에는 포함되지 않는다.
 */
@RestController
class WhoAmIController(private val currentUser: CurrentUserProvider) {

    @GetMapping("/__test/whoami")
    fun whoAmI(): String = currentUser.get().user?.loginId ?: "anonymous"
}
