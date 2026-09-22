package com.github.yonaprojects.yona.architecture.fixtures

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// RestControllerReturnTypeArchTest의 탐지 로직이 실제로 위반을 잡아내는지 확인하기 위한
// 자체 검증용 픽스처다. 어떤 @ComponentScan/@Configuration에도 등록되지 않아 실제 서버에
// 절대 배포/실행되지 않는다(테스트 소스셋에만 존재) — 진짜 취약점이 아니다.
@RestController
class LeakyDemoController {
    @GetMapping("/__never_registered__/leaky-demo")
    fun leak(): User = User()
}
