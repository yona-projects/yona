package dev.yona.security

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.ProjectService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * 외부 API(`/-_-api` 아래)의 범위를 검사한다(추가 개발 항목 X-5, `decisions.md` D-36). 스프링이 고른 경로 패턴으로 [ApiScopeRules] 대응표를 찾는다.
 * 대응표에 없는 경로는 403(기본 거부), 범위가 필요한데 토큰 인증이 아니면 401, 범위가 모자라면 403이다. `public_repo`는 요청이 가리키는 프로젝트
 * (`{owner}`와 `{projectName}`)가 공개(`PUBLIC`)일 때만 채운다. 접근 검사 어노테이션보다 먼저 실행해 토큰 범위로 먼저 거른다.
 */
@Component
class ApiScopeInterceptor(private val projects: ProjectService, private val providers: List<ApiScopeRuleProvider> = emptyList()) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: return true
        if (!pattern.startsWith("/-_-api")) return true
        val requirement = ApiScopeRules.requirement(request.method, pattern)
            ?: providers.firstNotNullOfOrNull { it.rules()[request.method.uppercase() to pattern] }
            ?: return deny(response, 403, "no scope is defined for this API path")
        if (requirement == ApiRequirement.None) return true
        val scopes = (SecurityContextHolder.getContext().authentication as? YonaAuthentication)?.scopes ?: return deny(response, 401, "unauthorized request")
        @Suppress("UNCHECKED_CAST")
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
        val owner = variables?.get("owner")
        val name = variables?.get("projectName")
        val projectPublic = if (owner != null && name != null) projects.findByOwnerAndProjectName(owner, name)?.projectScope == ProjectScope.PUBLIC else null
        return if (ApiScopeRules.allows(requirement, scopes, projectPublic)) true else deny(response, 403, "the token scope does not allow this request")
    }

    private fun deny(response: HttpServletResponse, status: Int, message: String): Boolean {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("{\"message\":\"$message\"}")
        return false
    }
}
