package dev.yona.web

import jakarta.servlet.ServletContext
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.thymeleaf.context.WebContext
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.web.servlet.JakartaServletWebApplication

/**
 * 템플릿 하나를 요청 문맥에서 문자열로 그린다. JSON 응답에 HTML 조각을 담거나(v1.6 `pullRequestState`의 `html`), 문자열을 이어 붙이는 도중에 조각을
 * 끼워 넣을 때 쓴다. 링크와 메시지를 쓸 수 있도록 현재 요청의 웹 문맥으로 처리하므로 요청 밖에서는 쓸 수 없다.
 */
@Component
class FragmentRenderer(private val templates: SpringTemplateEngine, private val servletContext: ServletContext) {

    /** [template]을 [model]로 그린 결과이다. */
    fun render(template: String, model: Map<String, Any?>): String {
        val attributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
        val exchange = JakartaServletWebApplication.buildApplication(servletContext).buildExchange(attributes.request, requireNotNull(attributes.response))
        return templates.process(template, WebContext(exchange, LocaleContextHolder.getLocale(), model))
    }
}
