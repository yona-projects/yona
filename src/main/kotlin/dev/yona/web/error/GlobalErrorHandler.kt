package dev.yona.web.error

import dev.yona.web.layout.LayoutModelFactory
import jakarta.persistence.PersistenceException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 처리하지 못한 예외를 v1.6의 오류 화면으로 바꾼다. `Global.onHandlerNotFound`, `onError`, `onBadRequest`
 * (`app/Global.java:385-411`)에 해당한다.
 *
 * - 일치하는 주소가 없으면 404 화면.
 * - 그 밖의 처리하지 못한 예외는 500 화면. 쿼리 제한 시간을 넘긴 경우에는 전용 문구(`error.timeout`)를 쓴다.
 *   v1.6은 운영 모드에서만 이 화면을 보여 주고 개발 모드에서는 Play의 개발용 화면을 보여 준다. v2는 항상
 *   이 화면을 보여 주고 원인은 로그에 남긴다.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalErrorHandler(private val layoutModelFactory: LayoutModelFactory) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    fun notFound(request: HttpServletRequest): ModelAndView = withLayout(ErrorViews.notFound(), request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotSupported(e: HttpRequestMethodNotSupportedException, request: HttpServletRequest): ModelAndView {
        log.warn("지원하지 않는 요청 방식: {}", e.message)
        return withLayout(ErrorViews.notFound(), request)
    }

    @ExceptionHandler(Exception::class)
    fun internalServerError(e: Exception, request: HttpServletRequest): ModelAndView {
        log.error("요청을 처리하지 못했습니다.", e)
        // v1.6는 예외 자체의 메시지만 봤다. Play가 원인의 메시지를 감싸 전달했기 때문에, 여기서는 원인의 메시지도 함께 본다.
        val cause = e.cause
        val timedOut = cause is PersistenceException &&
            (e.message?.contains("timed out") == true || cause.message?.contains("timed out") == true)
        return withLayout(
            ErrorViews.internalServerError(if (timedOut) "error.timeout" else "error.internalServerError"), request
        )
    }

    /** 예외 처리기가 그린 화면에는 컨트롤러 어드바이스가 적용되지 않으므로 공통 모델 값을 직접 채운다. */
    private fun withLayout(view: ModelAndView, request: HttpServletRequest): ModelAndView =
        view.also { it.addAllObjects(layoutModelFactory.attributes(request)) }
}
