package com.github.yonaprojects.yona.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HelpController {

    @GetMapping("/_help")
    fun help(model: Model): String {
        model.addAttribute("title", "도움말")
        return "help/toc"
    }

    @GetMapping("/_UIKit")
    fun uikit(): String {
        return "help/UIKit"
    }
}
