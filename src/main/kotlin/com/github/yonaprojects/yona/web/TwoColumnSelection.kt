package com.github.yonaprojects.yona.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.ui.Model
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 2단 보기(Turbo Frames) 목록의 `?selected=<번호>` 선택 상태 URL 계산.
 * 현재 요청의 쿼리(인코딩·순서·반복 파라미터)를 그대로 보존하고 `selected`만 교체/제거한다.
 */
object TwoColumnSelection {
    fun addToModel(request: HttpServletRequest, model: Model, selected: Long?) {
        val selectionQuery = request.queryString.orEmpty().split("&")
            .filter { it.isNotEmpty() && URLDecoder.decode(it.substringBefore("="), StandardCharsets.UTF_8) != "selected" }
            .joinToString("&")
        val clearUrl = request.requestURI + if (selectionQuery.isEmpty()) "" else "?$selectionQuery"
        model.addAttribute("selected", selected)
        model.addAttribute("selectionClearUrl", clearUrl)
        model.addAttribute("selectionBaseUrl", clearUrl + if (selectionQuery.isEmpty()) "?" else "&")
    }
}
