package dev.yona.web.error

import dev.yona.web.organization.OrganizationViewContext
import dev.yona.web.project.ProjectViewContext
import dev.yona.web.user.LoginFormController
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.ModelAndView

/**
 * 오류 화면을 만드는 도우미이다. v1.6 `utils.ErrorViews`에 해당한다.
 *
 * 컨트롤러가 `return ErrorViews.notFound("error.notfound.project")`처럼 돌려주면 응답 상태와 화면이 함께 정해진다.
 * v1.6은 `forbidden(ErrorViews.Forbidden.render(키))`처럼 상태와 화면을 따로 골랐다.
 *
 * 프로젝트 화면 안에서 보여 주는 변형은 [ProjectViewContext]를, 조직 화면 안의 변형은 [OrganizationViewContext]를 함께 받는다.
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

    /**
     * 413: 폼 본문이 한도를 넘어 값을 읽지 못했다. v1.6 `ErrorViews.RequestTextEntityTooLarge`이며 상태는 컨트롤러가
     * `status(REQUEST_ENTITY_TOO_LARGE, ...)`로 따로 정했다.
     */
    fun requestTextEntityTooLarge(): ModelAndView = ModelAndView("error/request-text-entity-too-large", HttpStatus.PAYLOAD_TOO_LARGE)

    /** 500: 서버 오류이다. */
    fun internalServerError(messageKey: String = "error.internalServerError"): ModelAndView =
        view("error/internal-server-error-default", HttpStatus.INTERNAL_SERVER_ERROR, messageKey)

    /**
     * 403: 프로젝트 화면 안에서 권한이 없음을 알린다. 익명 사용자에게는 로그인 버튼을 함께 보여 준다
     * (v1.6 `ErrorViews.Forbidden.render(키, project)`).
     */
    fun forbidden(messageKey: String, project: ProjectViewContext): ModelAndView =
        view("error/forbidden", HttpStatus.FORBIDDEN, messageKey).addObject("projectView", project)

    /**
     * 404: 프로젝트 화면 안에서 대상을 찾을 수 없음을 알린다 (v1.6 `ErrorViews.NotFound.render(키, project, 종류)`).
     *
     * @param targetType 찾지 못한 리소스 종류(`issue_post`, `board_post`, `milestone`, `code`). 종류에 따라 문구, 활성 메뉴,
     *   돌아갈 목록이 달라진다. 모르는 종류이거나 `null`이면 일반 문구와 "뒤로 가기"를 쓴다
     */
    fun notFound(messageKey: String, project: ProjectViewContext, targetType: String?): ModelAndView =
        view("error/notfound", HttpStatus.NOT_FOUND, messageKey)
            .addObject("projectView", project).addObject("targetType", targetType)

    /** 400: 프로젝트 화면 안에서 잘못된 요청을 알린다. [menuType]은 활성으로 보여 줄 프로젝트 메뉴(MenuType 이름)이다. */
    fun badRequest(messageKey: String, project: ProjectViewContext, menuType: String): ModelAndView =
        view("error/badrequest", HttpStatus.BAD_REQUEST, messageKey)
            .addObject("projectView", project).addObject("menuType", menuType)

    /**
     * 403: 조직 화면 안에서 권한이 없음을 알린다. 조직 머리말과 메뉴를 함께 보여 준다
     * (v1.6 `ErrorViews.Forbidden.render(키, organization)`, `error/forbidden_organization`).
     */
    fun forbidden(messageKey: String, organization: OrganizationViewContext): ModelAndView =
        view("error/forbidden-organization", HttpStatus.FORBIDDEN, messageKey).addObject("orgView", organization)

    /**
     * 403: 익명 사용자에게 로그인 화면을 보여 주고 로그인 뒤 [returnUrl]로 돌아오게 한다.
     * v1.6 `ErrorViews.Forbidden.render(키, returnUrl)`이며 문서 제목 키는 오타(`error.fobidden`)까지 그대로 쓴다.
     */
    fun loginRequired(returnUrl: String): ModelAndView =
        LoginFormController.loginPage(returnUrl, "error.fobidden").also { it.status = HttpStatus.FORBIDDEN }

    private fun view(name: String, status: HttpStatus, messageKey: String): ModelAndView =
        ModelAndView(name, status).addObject("messageKey", messageKey)
}
