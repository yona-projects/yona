package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.FlashMap
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * 일회성 메시지(flash)와 함께 리다이렉트하는 도구이다.
 *
 * Play 2.3의 `redirect(...)`는 `303 See Other`를 돌려주고 `flash(키, 값)`는 다음 요청 한 번에만 보이는
 * 값을 쿠키로 전달한다. Spring의 `RedirectView`는 `302`를 쓰므로, 상태 코드를 v1.6과 같게 하려고 직접 만든다.
 * flash 값은 Spring의 `FlashMap`(세션 기반)에 저장해 다음 요청의 모델에서 읽는다.
 */
object RedirectSupport {

    /** 일회성 메시지의 키이다 (v1.6 `utils.Constants`). */
    const val WARNING = "warning"
    const val INFO = "info"
    const val SUCCESS = "success"
    const val ERROR = "error"

    /**
     * 리다이렉트 없이 다음 요청에서 한 번만 읽을 수 있는 값을 저장한다. 응답은 JSON으로 이동할 주소만 알려 주고 브라우저가
     * 그 주소로 이동하는 Ajax 흐름에서 쓴다 (v1.6 `flash(...)` 뒤 `okWithLocation`).
     */
    fun saveFlash(request: HttpServletRequest, response: HttpServletResponse, flash: Map<String, Any>) {
        val flashMap = FlashMap().apply { putAll(flash) }
        RequestContextUtils.getFlashMapManager(request)?.saveOutputFlashMap(flashMap, request, response)
        RequestContextUtils.getOutputFlashMap(request).putAll(flash)
    }

    /**
     * @param location 이동할 위치. 그대로 `Location` 헤더에 들어간다
     * @param flash 다음 요청에서 한 번만 읽을 수 있는 값
     */
    fun see(
        request: HttpServletRequest,
        response: HttpServletResponse,
        location: String,
        flash: Map<String, Any> = emptyMap()
    ): ResponseEntity<Void> {
        if (flash.isNotEmpty()) {
            val flashMap = FlashMap().apply { putAll(flash) }
            RequestContextUtils.getFlashMapManager(request)?.saveOutputFlashMap(flashMap, request, response)
            RequestContextUtils.getOutputFlashMap(request).putAll(flash)
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location).build()
    }
}
