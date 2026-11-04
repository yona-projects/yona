package dev.yona.web.site

import dev.yona.web.error.ErrorViews
import dev.yona.domain.site.SiteMailService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 사이트 관리의 메일 쓰기와 대량 메일이다. v1.6 `SiteApp.writeMail`, `sendMail`, `massMail`, `mailList`
 * (`app/controllers/SiteApp.java:87-123, 277-313`)에 해당한다. 사이트 관리자만 쓴다.
 *
 * 메일 보내기(`POST /sites/mail`)는 이동하지 않고 같은 응답으로 메일 쓰기 화면을 성공 안내와 함께 다시 그린다.
 * 대량 메일은 서버가 보내지 않는다. 받는 사람 이메일 목록(`POST /sites/mailList`)만 JSON으로 알려 주고 화면의 스크립트가
 * 사용자의 메일 프로그램(`mailto:`)을 연다.
 */
@Controller
// v1.6 SiteApp: @With(SiteManagerAuthAction) 다음 @AnonymousCheck 순서라 익명은 관리자 검사의 403을 먼저 받는다
@SiteManagerOnly
@AnonymousCheck
class SiteMailController(private val service: SiteMailService) {

    @GetMapping("/sites/mail")
    fun writeMail(@RequestParam(required = false) errorMessage: String?, @RequestParam(defaultValue = "false") sended: Boolean): ModelAndView = mailView(errorMessage, sended)

    @PostMapping("/sites/mail")
    fun sendMail(request: HttpServletRequest): ModelAndView {
        // v1.6은 본문이 비었거나 주소가 올바르지 않으면 메일 라이브러리의 예외로 500이었다(R-54-1). 잘못된 요청이므로 400이다
        try {
            service.send(
                from = request.getParameter("from").orEmpty(), to = request.getParameter("to").orEmpty(),
                subject = request.getParameter("subject").orEmpty(), body = request.getParameter("body").orEmpty()
            )
        } catch (e: IllegalArgumentException) {
            return ErrorViews.badRequest()
        }
        return mailView(null, true)
    }

    @GetMapping("/sites/massmail")
    fun massMail(): ModelAndView = ModelAndView("site/mass-mail").addObject("title", "title.massMail")

    /**
     * 받는 사람 이메일 목록이다. `all=true`이면 모든 사용자, 폼에 `all`이 있으나 `true`가 아니면 없음, 없으면 폼의 모든 값을
     * `소유자/이름`으로 보고 그 프로젝트들의 구성원이다. `Accept`가 JSON을 받지 않으면 406이다.
     */
    @PostMapping("/sites/mailList")
    fun mailList(request: HttpServletRequest): ResponseEntity<Any> {
        if (!acceptsJson(request.getHeader(HttpHeaders.ACCEPT))) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()
        val form = request.parameterMap
        val emails: Collection<String> = when {
            form.isEmpty() -> emptyList()
            "all" in form -> if (form["all"]?.firstOrNull() == "true") service.allEmails() else emptyList()
            // v1.6는 폼 본문을 `HashMap`에 담아 `values()`로 훑으므로 프로젝트 순서는 파라미터 이름의 해시 순서이다
            // v1.6은 없는 프로젝트나 `/` 없는 이름이면 배열 범위 오류와 널 참조로 500이었다(R-54-4). 잘못된 요청이므로 400이다
            else -> try {
                service.projectEmails(HashMap<String, Array<String>>().also { map -> form.forEach { (k, v) -> map[k] = v } }.values.mapNotNull { it.firstOrNull() })
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(emails.toList())
    }

    private fun acceptsJson(accept: String?): Boolean =
        accept.isNullOrBlank() || MediaType.parseMediaTypes(accept).any { it.isCompatibleWith(MediaType.APPLICATION_JSON) }

    private fun mailView(errorMessage: String?, sended: Boolean) = ModelAndView("site/mail")
        .addObject("title", "title.sendMail")
        .addObject("notConfiguredItems", service.notConfiguredItems())
        .addObject("sender", service.defaultSender())
        .addObject("errorMessage", errorMessage)
        .addObject("sended", sended)
}
