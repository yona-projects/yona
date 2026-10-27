package dev.yona.web.svn

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping

/**
 * `POST /!svn-fake/sevice/`이다. v1.6 `routes`가 `SvnApp.service`의 역방향 주소를 만들려고 둔 가짜 경로인데, 호출하면 `SvnApp.service`가 실행된다.
 * 첫 구간을 버리고 `sevice`를 소유자, 빈 문자열을 프로젝트 이름으로 읽으므로 프로젝트가 없어 본문 없는 `404`이다.
 */
@Controller
class SvnFakeServiceController {

    @PostMapping("/!svn-fake/sevice/")
    fun service(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
