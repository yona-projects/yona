package dev.yona.web.layout

import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.StringTemplateResolver
import kotlin.test.assertEquals

/**
 * Thymeleaf가 값이 비었거나 null인 동적 속성을 어떻게 그리는지 고정한다.
 * v1.6 Twirl은 값이 비어도 `속성=""`를 그리므로, 템플릿은 정적 빈 속성에 `th:attrappend`로 값을 덧붙이는 방식을 쓴다.
 */
class ThymeleafEmptyAttributeTest {

    private fun render(template: String, value: Any?): String {
        val engine = SpringTemplateEngine().also { it.setTemplateResolver(StringTemplateResolver()) }
        return engine.process(template, Context().also { it.setVariable("v", value) })
    }

    @Test
    fun `th_attr은 값이 null이거나 비면 속성을 그리지 않는다`() {
        val t = """<a th:attr="data-m=${'$'}{v}">x</a>"""
        assertEquals("<a>x</a>", render(t, null))
        assertEquals("<a>x</a>", render(t, ""))
        assertEquals("""<a data-m="7">x</a>""", render(t, 7))
    }

    @Test
    fun `정적 빈 속성에 th_attrappend를 쓰면 null이어도 빈 속성이 남고 값이 있으면 채워진다`() {
        val t = """<a data-m="" th:attrappend="data-m=${'$'}{v}">x</a>"""
        assertEquals("""<a data-m="">x</a>""", render(t, null))
        assertEquals("""<a data-m="7">x</a>""", render(t, 7))
    }
}
