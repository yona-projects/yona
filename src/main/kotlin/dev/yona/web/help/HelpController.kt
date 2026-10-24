package dev.yona.web.help

import dev.yona.web.access.AnonymousCheck
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 도움말 목차(`GET /_help`)이다. v1.6 `HelpApp.help`(`app/controllers/HelpApp.java:28-33`)와 `app/views/help/toc.scala.html`에 해당한다.
 *
 * 질문과 답변은 언어와 관계없이 한국어 본문이 템플릿에 들어 있다. 사이트가 익명 접근을 막으면 로그인 폼으로 보낸다.
 */
@Controller
@AnonymousCheck
class HelpController {

    @GetMapping("/_help")
    fun help(): ModelAndView = ModelAndView("help/toc").addObject("title", "title.help")

}
