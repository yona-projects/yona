package dev.yona.web.layout

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * 화면을 그리기 직전에 일회성 메시지(flash) 목록을 모델에 채운다.
 *
 * v1.6 `common/scripts.scala.html`은 렌더링 시점의 `Context.current().flash()`를 읽으므로, 컨트롤러가 `flash(...)`를 심은 뒤
 * 같은 요청에서 화면을 그리면 그 메시지도 바로 보인다. 컨트롤러 어드바이스는 핸들러보다 먼저 실행되어 이를 놓치므로,
 * 핸들러가 끝난 뒤(`postHandle`) 앞 요청이 남긴 값에 이번 요청이 심은 값을 더해 다시 채운다.
 */
@Component
class FlashDisplayInterceptor(private val translator: FlashMessageTranslator) : HandlerInterceptor {

    override fun postHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any, modelAndView: ModelAndView?) {
        val view = modelAndView ?: return
        val name = view.viewName
        if (name != null && (name.startsWith("redirect:") || name.startsWith("forward:"))) return
        val input = RequestContextUtils.getInputFlashMap(request).orEmpty()
        val output = RequestContextUtils.getOutputFlashMap(request)
        if (output.isEmpty()) return // 이번 요청이 심은 값이 없으면 어드바이스가 채운 값(앞 요청의 값)으로 충분하다
        view.addObject("flashMessages", translator.translate(input + output))
    }
}
