package dev.yona.web.error

import org.springframework.http.HttpStatus
import org.springframework.web.servlet.ModelAndView

/**
 * 오류 화면을 만드는 도우미이다. v1.6 `utils.ErrorViews`에 해당한다.
 *
 * 컨트롤러가 `return ErrorViews.notFound("error.notfound.project")`처럼 돌려주면 응답 상태와 화면이 함께 정해진다.
 * v1.6은 `forbidden(ErrorViews.Forbidden.render(키))`처럼 상태와 화면을 따로 골랐다.
 *
 * 프로젝트나 조직 화면 안에서 보여 주는 변형(`render(키, project)`)은 프로젝트 화면을 옮길 때 추가한다.
 */
object ErrorViews {

    /** 404: 찾을 수 없다. [messageKey]는 메시지 키이다. */
    fun notFound(messageKey: String = "error.notfound"): ModelAndView =
        view("error/notfound-default", HttpStatus.NOT_FOUND, messageKey)

    /** 403: 권한이 없다. */
    fun forbidden(messageKey: String = "error.forbidden"): ModelAndView =
        view("error/forbidden-default", HttpStatus.FORBIDDEN, messageKey)

    /** 400: 잘못된 요청이다. */
    fun badRequest(messageKey: String = "error.badrequest"): ModelAndView =
        view("error/badrequest-default", HttpStatus.BAD_REQUEST, messageKey)

    /** 500: 서버 오류이다. */
    fun internalServerError(messageKey: String = "error.internalServerError"): ModelAndView =
        view("error/internal-server-error-default", HttpStatus.INTERNAL_SERVER_ERROR, messageKey)

    private fun view(name: String, status: HttpStatus, messageKey: String): ModelAndView =
        ModelAndView(name, status).addObject("messageKey", messageKey)
}
