package dev.yona.web

import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.RedirectUris
import dev.yona.oauth2server.ProtectedResources
import dev.yona.oauth2server.oidc.OidcSettings
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.ModelAndView
import java.util.Date

/**
 * 사용자 설정과 조직 설정의 OAuth 앱 화면이 같이 쓰는 도구이다(X-1, X-39). 목록의 한 줄을 만들고, OIDC 안내 주소를 구하고, 비밀 값 화면이 캐시에 남지 않게 한다.
 */
@Component
class OAuthAppViewSupport(private val helper: TemplateHelper, private val oidc: OidcSettings) {

    /** 소유자가 만든 앱의 한 줄이다. */
    data class AppRow(val id: Long, val name: String, val clientId: String, val confidential: Boolean, val redirectCount: Int, val createdText: String)

    /** 목록의 한 줄을 만든다. */
    fun appRow(c: OAuthClient) =
        AppRow(requireNotNull(c.id), c.name.orEmpty(), c.clientId.orEmpty(), c.confidential == true, RedirectUris.parse(c.redirectUris).size, helper.dateString(c.createdDate))

    /** 화면에 보일 날짜 글자이다. */
    fun dateText(date: Date?): String = helper.dateString(date)

    /**
     * OIDC로 로그인을 붙이는 앱 개발자에게 알려 줄 주소이다(X-40, D-77). OIDC가 켜져 있고 이미 만든 앱의 수정 화면일 때만 있다.
     */
    fun oidcInfo(client: OAuthClient?): Map<String, String>? {
        if (client == null || !oidc.enabled) return null
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return null
        val base = ProtectedResources.base(request)
        return linkedMapOf("issuer" to base, "discovery" to "$base/.well-known/openid-configuration")
    }

    /** 비밀 값이 든 화면이 브라우저와 중간 캐시에 남지 않게 헤더를 정한다. */
    fun noCache(view: ModelAndView, response: HttpServletResponse): ModelAndView = view.also {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }
}
