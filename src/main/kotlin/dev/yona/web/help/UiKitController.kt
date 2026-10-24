package dev.yona.web.help

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 개발자용 UI 견본 페이지(`GET /_UIKit`)이다. v1.6 `Application.UIKit`(`app/controllers/Application.java:132-134`)에 해당한다.
 *
 * 독립 HTML 문서이다. v1.6처럼 로그인 검사가 없어 사이트가 익명 접근을 막아도 열린다.
 */
@Controller
class UiKitController {

    @GetMapping("/_UIKit")
    fun uiKit(): ModelAndView = ModelAndView("help/uikit")
}
