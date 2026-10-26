package dev.yona.web.access

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 컨트롤러 메서드에 붙은 접근 검사 어노테이션을 v1.6과 같은 순서로 실행한다.
 *
 * ## 실행 순서 (v1.6 Play 2.3.10)
 * Play는 메서드에 붙은 액션 어노테이션(소스에 쓴 순서)을 바깥에, 클래스에 붙은 어노테이션을 안쪽에 둔다. 클래스 쪽은 최상위 부모 클래스부터
 * 해당 클래스 순서이고, 각 클래스는 소스 순서이다. 바깥에서 안쪽으로 차례로 실행하고, 하나가 막으면 안쪽은 실행하지 않는다.
 * 예를 들어 클래스에 `@AnonymousCheck`가, 메서드에 `@IsAllowed`가 있으면 `IsAllowed` → `AnonymousCheck` 순서다. 그래서 사이트가 익명 접근을 막은
 * 경우 익명은 로그인 폼(303)이 아니라 프로젝트 검사의 403을 먼저 받는다. 근거는 `play.core.j.JavaActionAnnotations`의 바이트코드와
 * v1.6 테스트(`EnrollProjectAppTest.enrollProjectNotExist`, `WatchProjectAppTest.testWatch_NoProject`)이다. 같은 어노테이션이 메서드와 클래스에
 * 모두 있으면 둘 다 실행한다(`IssueApp`의 `@AnonymousCheck`가 부모와 자식에 있는 것이 그 예다).
 *
 * 어노테이션별 단계는 [AnonymousCheckStage], [SiteManagerOnlyStage], [ProjectCheckStage]가 맡는다.
 */
@Component
class AccessChainInterceptor(
    private val anonymous: AnonymousCheckStage,
    private val siteManager: SiteManagerOnlyStage,
    private val project: ProjectCheckStage
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val handlerMethod = handler as? HandlerMethod ?: return true
        for (annotation in annotationsInPlayOrder(handlerMethod)) {
            val passed = when (annotation) {
                is AnonymousCheck -> anonymous.run(annotation, request, response)
                is SiteManagerOnly -> { siteManager.run(); true }
                else -> project.run(annotation, request, response)
            }
            if (!passed) return false
        }
        return true
    }

    companion object {
        /**
         * 바깥에서 안쪽으로의 어노테이션 순서이다: 메서드에 선언된 것(소스 순서), 그다음 최상위 부모 클래스부터 해당 클래스까지 선언된 것.
         * `getDeclaredAnnotations()`가 소스 순서를 따른다는 JVM 구현에 기대므로, 가드 테스트(`AccessChainOrderTest`)가 이를 확인한다.
         */
        fun annotationsInPlayOrder(handler: HandlerMethod): List<Annotation> {
            val classes = generateSequence<Class<*>>(handler.beanType) { it.superclass }.takeWhile { it != Any::class.java }.toList().asReversed()
            return handler.method.declaredAnnotations.toList() + classes.flatMap { it.declaredAnnotations.toList() }
        }
    }
}
