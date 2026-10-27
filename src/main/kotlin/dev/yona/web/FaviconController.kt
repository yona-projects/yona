package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** 루트의 `/favicon.ico`를 제공한다. v1.6 `GET /favicon.ico`는 `Assets.at("/public", "images/favicon.ico")`이므로 `/assets/images/favicon.ico`와 같은 응답이다. */
@Controller
class FaviconController(private val assets: StaticAssets) {

    @GetMapping("/favicon.ico")
    fun favicon(request: HttpServletRequest): ResponseEntity<ByteArray> = assets.serve("images/favicon.ico", request)
}
