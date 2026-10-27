package dev.yona.web

import dev.yona.web.error.BindFailureException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * 숫자(`Long`, `Int`)로 받는 경로 변수에 숫자가 아닌 값이 오면 접근 검사보다 먼저 바인더 실패로 다룬다.
 *
 * Play는 라우트를 고른 뒤 액션을 부르기 전에 경로 변수를 매개변수 형식으로 바꾸고, 바꾸지 못하면 `Global.onBadRequest`(`400`)로 응답한다.
 * 어노테이션으로 하는 접근 검사와 프로젝트 확인은 액션 안쪽이라 일어나지 않는다. 스프링은 인자 해석이 인터셉터 뒤라서, 그 사이에 숫자를 기대하는
 * 검사가 예외를 던지면 `500`이 된다. 화면은 [dev.yona.web.error.GlobalErrorHandler]가 그린다.
 */
@Component
class PathBindingInterceptor : HandlerInterceptor {

    private val names = DefaultParameterNameDiscoverer()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val handlerMethod = handler as? HandlerMethod ?: return true
        @Suppress("UNCHECKED_CAST")
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String> ?: return true
        for (parameter in handlerMethod.methodParameters) {
            val annotation = parameter.getParameterAnnotation(PathVariable::class.java) ?: continue
            val type = parameter.parameterType
            if (type != Long::class.javaPrimitiveType && type != Long::class.javaObjectType && type != Int::class.javaPrimitiveType && type != Int::class.javaObjectType) continue
            val variable = annotation.name.ifEmpty { annotation.value }.ifEmpty { parameter.also { it.initParameterNameDiscovery(names) }.parameterName.orEmpty() }
            val raw = variables[variable] ?: continue
            val valid = if (type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType) raw.toLongOrNull() != null else raw.toIntOrNull() != null
            if (!valid) throw BindFailureException("path variable $variable: $raw")
        }
        return true
    }
}
