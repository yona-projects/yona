package dev.yona.web.welcome

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping

/**
 * `POST /`이다. v1.6 `Application.fake`(`conf/routes:6`, `app/controllers/Application.java:136-139`)에 해당한다.
 *
 * 최초 기동 화면의 폼이 이 주소로 보낸다. 실제 처리는 [SiteSecretInterceptor]가 컨트롤러보다 먼저 하고, 이 경로가 있어야 요청이 컨트롤러까지
 * 오므로 둔다. 비밀 키를 정한 뒤에 호출하면 v1.6처럼 400이다("호출하지 마시오").
 */
@Controller
class FakeController {

    @PostMapping("/")
    fun fake(): ResponseEntity<Void> = ResponseEntity.badRequest().build()
}
