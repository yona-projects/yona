package dev.yona.web.error

import dev.yona.web.layout.LayoutModelFactory
import jakarta.persistence.PersistenceException
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 처리하지 못한 예외를 v1.6의 오류 화면으로 바꾼다. `Global.onHandlerNotFound`, `onError`, `onBadRequest`
 * (`app/Global.java:385-411`)에 해당한다.
 *
 * - 일치하는 주소가 없으면 404 화면. 필수 파라미터가 없거나 타입이 맞지 않으면 400 화면.
 * - 그 밖의 처리하지 못한 예외는 500 화면. 쿼리 제한 시간을 넘긴 경우에는 전용 문구(`error.timeout`)를 쓴다.
 *   v1.6은 운영 모드에서만 이 화면을 보여 주고 개발 모드에서는 Play의 개발용 화면을 보여 준다. v2는 항상
 *   이 화면을 보여 주고 원인은 로그에 남긴다.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalErrorHandler(private val layoutModelFactory: LayoutModelFactory, private val siteSecret: dev.yona.web.welcome.SiteSecretInterceptor) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ViewResultException::class)
    fun viewResult(e: ViewResultException, request: HttpServletRequest): ModelAndView = withLayout(e.view, request)

    /**
     * 일치하는 처리기가 없을 때이다. GET이고 경로가 `/`로 끝나면(경로가 `/`뿐인 경우는 제외) 마지막 슬래시를 뗀 경로로 `303` 이동한다.
     * 쿼리 문자열은 넣지 않는다. 그 밖에는 404 화면이다. v1.6 `routes` 맨 아래의 포괄 라우트(`Application.removeTrailer`)와 같다.
     */
    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    fun notFound(request: HttpServletRequest, response: HttpServletResponse): Any {
        val path = request.requestURI
        // Play는 HEAD를 GET 라우트로 보내므로 HEAD도 GET처럼 다룬다
        val readOnly = request.method == "GET" || request.method == "HEAD"
        // 비밀 키를 정하기 전에는 포괄 라우트의 액션도 Global.onRequest가 가로채 설정 화면(또는 재시작 안내)을 낸다. 끝 슬래시 이동도 하지 않는다
        if (readOnly) siteSecret.pendingScreen(request)?.let {
            if (!response.isCommitted) response.reset()
            return withLayout(it, request)
        }
        if (readOnly && path.length > 1 && path.endsWith("/")) {
            log.debug("Trailing slash removed and redirected: {} to {}", path, path.dropLast(1))
            return RedirectSupport.see(request, response, path.dropLast(1))
        }
        // v1.6의 `Cache-Control: no-cache`는 맨 아래 포괄 라우트(GET)의 액션이 붙인다. 다른 방식의 라우트 없음은 액션 밖이라 붙지 않는다.
        // 주소는 있으나 방식이 맞지 않는 GET은 매핑 단계에서 예외가 나 인터셉터가 헤더를 붙이지 못하므로 여기서 붙인다
        if (readOnly) response.setHeader("Cache-Control", "no-cache") else if (!response.isCommitted) response.reset()
        return withLayout(ErrorViews.notFound(), request)
    }

    /**
     * 주소는 있으나 요청 방식이 맞지 않을 때이다. Play는 방식이 맞는 라우트만 보므로 같은 주소의 다른 방식 라우트(예: `DELETE code/:branch/`)는 GET에
     * 영향을 주지 않는다. 그래서 GET이면 일치하는 처리기가 없을 때와 똑같이 끝 슬래시를 떼어 이동하거나 404 화면이다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotSupported(e: HttpRequestMethodNotSupportedException, request: HttpServletRequest, response: HttpServletResponse): Any {
        log.warn("지원하지 않는 요청 방식: {}", e.message)
        return notFound(request, response)
    }

    /** 필수 요청 파라미터가 없거나 경로 변수, 파라미터의 타입이 맞지 않으면 400 화면이다 (v1.6 `Global.onBadRequest`). */
    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class, BindFailureException::class)
    fun badRequest(e: Exception, request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
        log.warn("잘못된 요청: {}", e.message)
        // Play는 라우트 바인더 실패를 기본 액션 밖(`Global.onBadRequest`)에서 처리하므로 `Cache-Control: no-cache`가 붙지 않는다
        if (!response.isCommitted) response.reset()
        return withLayout(ErrorViews.badRequest(), request)
    }

    @ExceptionHandler(Exception::class)
    fun internalServerError(e: Exception, request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
        log.error("요청을 처리하지 못했습니다.", e)
        // v1.6 `Global.onError`는 기본 액션(Cache-Control: no-cache를 붙인다) 밖에서 오류 화면을 만든다. 이미 붙은 헤더를 없앤다.
        if (!response.isCommitted) response.reset()
        // v1.6는 예외 자체의 메시지만 봤다. Play가 원인의 메시지를 감싸 전달했기 때문에, 여기서는 원인의 메시지도 함께 본다.
        val cause = e.cause
        val timedOut = cause is PersistenceException &&
            (e.message?.contains("timed out") == true || cause.message?.contains("timed out") == true)
        return withLayout(
            ErrorViews.internalServerError(if (timedOut) "error.timeout" else "error.internalServerError"), request
        )
    }

    /** 예외 처리기가 그린 화면에는 컨트롤러 어드바이스가 적용되지 않으므로 공통 모델 값을 직접 채운다. */
    private fun withLayout(view: ModelAndView, request: HttpServletRequest): ModelAndView = view.also { mav ->
        // 화면이 직접 채운 값(예: 같은 응답에서 바로 보여 줄 flashMessages)은 덮어쓰지 않는다.
        layoutModelFactory.attributes(request).forEach { (name, value) -> if (!mav.model.containsKey(name)) mav.addObject(name, value) }
    }
}
