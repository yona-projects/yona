package com.github.yonaprojects.yona.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.ui.Model
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 2단 보기(Turbo Frames) 목록의 선택 상태 URL 계산.
 * 현재 요청의 쿼리(인코딩·순서·반복 파라미터)를 그대로 보존하고 선택 파라미터만 교체/제거한다.
 *
 * 프로젝트 하나에 속한 목록은 `?selected=<번호>`를 쓰지만, 여러 프로젝트를 가로지르는 목록
 * (조직 게시판/이슈, 내 이슈, 사용자 화면)은 번호만으로 항목을 특정할 수 없으므로
 * `?<파라미터>=<type>:<owner>/<project>/<번호>` 복합 키([Key])를 쓴다.
 */
object TwoColumnSelection {
    const val DEFAULT_PARAM = "selected"

    /** 복합 선택 키. type은 화면이 정하는 상세 종류(issue, post, pull 등)다. */
    data class Key(val type: String, val owner: String, val project: String, val number: Long) {
        override fun toString() = "$type:$owner/$project/$number"
    }

    private val KEY_PATTERN = Regex("""^([A-Za-z_]+):([^/:\s]+)/([^/:\s]+)/(\d+)$""")

    /** 형식이 잘못됐거나 번호가 1 미만이면 null. 권한 확인은 호출자가 상세 로직에 위임해 수행한다. */
    fun parseKey(raw: String?): Key? {
        val match = raw?.let { KEY_PATTERN.matchEntire(it) } ?: return null
        val number = match.groupValues[4].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Key(match.groupValues[1], match.groupValues[2], match.groupValues[3], number)
    }

    fun addToModel(request: HttpServletRequest, model: Model, selected: Any?, param: String = DEFAULT_PARAM) {
        val selectionQuery = request.queryString.orEmpty().split("&")
            .filter { it.isNotEmpty() && URLDecoder.decode(it.substringBefore("="), StandardCharsets.UTF_8) != param }
            .joinToString("&")
        val clearUrl = request.requestURI + if (selectionQuery.isEmpty()) "" else "?$selectionQuery"
        model.addAttribute("selected", selected)
        model.addAttribute("selectionParam", param)
        model.addAttribute("selectionClearUrl", clearUrl)
        model.addAttribute("selectionBaseUrl", clearUrl + if (selectionQuery.isEmpty()) "?" else "&")
    }
}
