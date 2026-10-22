package dev.yona.web.code

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import jakarta.servlet.ServletContext
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.thymeleaf.context.WebContext
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.web.servlet.JakartaServletWebApplication

/**
 * 코드 리뷰 스레드를 HTML 조각으로 그린다. diff 화면은 줄 사이에 스레드를 끼워 넣어야 하는데, 줄 HTML을 문자열로 이어 붙이는 도중이라
 * 템플릿 엔진을 직접 불러 스레드 조각(`code/partial-comment-thread.html`)을 문자열로 만든다. 요청이 없는 곳에서는 쓸 수 없다.
 */
@Component
class ThreadHtmlRenderer(
    private val templates: SpringTemplateEngine,
    private val assembler: ThreadViewAssembler,
    private val servletContext: ServletContext
) : DiffHtmlRenderer.ThreadRenderer {

    override fun render(threads: List<CodeCommentThread>): String = renderAll(threads)

    /** [threads]를 만든 시각 순서가 아니라 주어진 순서대로 그린다. */
    fun renderAll(threads: List<CommentThread>): String {
        val attributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
        val exchange = JakartaServletWebApplication.buildApplication(servletContext).buildExchange(attributes.request, requireNotNull(attributes.response))
        return threads.joinToString("") { thread ->
            val context = WebContext(exchange, LocaleContextHolder.getLocale(), mapOf("thread" to assembler.view(thread)))
            templates.process("code/thread-wrapper", context)
        }
    }
}
