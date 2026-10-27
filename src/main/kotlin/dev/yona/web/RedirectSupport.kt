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

    /** flash 안에서 값을 넣은 순서를 `\u0001`로 이어 적어 두는 보조 키이다. 화면에 나오지 않는다. v1.6 flash 쿠키가 순서를 지키는 것과 맞춘다. */
    const val ORDER = "\u0000order"

    /** 확인 대화상자로 보여 줄 일회성 메시지의 제목과 설명 키이다 (v1.6 `Constants.TITLE`, `DESCRIPTION`). */
    const val TITLE = "title"
    const val DESCRIPTION = "description"

    /**
     * 리다이렉트 없이 다음 요청에서 한 번만 읽을 수 있는 값을 저장한다. 응답은 JSON으로 이동할 주소만 알려 주고 브라우저가
     * 그 주소로 이동하는 Ajax 흐름에서 쓴다 (v1.6 `flash(...)` 뒤 `okWithLocation`).
     */
    fun saveFlash(request: HttpServletRequest, response: HttpServletResponse, flash: Map<String, Any>) {
        val carried = carriedOver(request)
        val flashMap = FlashMap().apply { putAll(carried); putAll(flash) }
        val order = LinkedHashSet<String>()
        (carried[ORDER] as? String)?.split('\u0001')?.filter { it in carried }?.let { order.addAll(it) }
        order.addAll(carried.keys.filter { it != ORDER })
        order.addAll(flash.keys)
        flashMap[ORDER] = order.joinToString("\u0001")
        RequestContextUtils.getFlashMapManager(request)?.saveOutputFlashMap(flashMap, request, response)
        RequestContextUtils.getOutputFlashMap(request).putAll(flashMap)
    }

    /**
     * 앞 요청이 남긴 값이다. v1.6의 flash는 쿠키 하나라서, 새 값을 심는 응답은 앞 요청이 남긴 값을 그대로 이어 받아 다시 심는다.
     * 새 값을 심지 않는 응답에서는 값이 사라진다.
     */
    private fun carriedOver(request: HttpServletRequest): Map<String, Any> =
        RequestContextUtils.getInputFlashMap(request)?.mapNotNull { (k, v) -> v?.let { k to it } }?.toMap().orEmpty()

    /**
     * @param location 이동할 위치. ASCII 바깥의 글자는 퍼센트 인코딩해 `Location` 헤더에 넣는다([dev.yona.util.UrlPaths.encodeLocation])
     * @param flash 다음 요청에서 한 번만 읽을 수 있는 값
     */
    fun see(
        request: HttpServletRequest,
        response: HttpServletResponse,
        location: String,
        flash: Map<String, Any> = emptyMap()
    ): ResponseEntity<Void> {
        if (flash.isNotEmpty()) {
            saveFlash(request, response, flash)
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, dev.yona.util.UrlPaths.encodeLocation(location)).build()
    }
}
