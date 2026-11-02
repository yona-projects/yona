package dev.yona.web

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.oauth2server.ClientOwner
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.RedirectUris
import dev.yona.domain.user.User
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
class OAuthAppViewSupport(
    private val helper: TemplateHelper,
    private val oidc: OidcSettings,
    private val organizationUsers: dev.yona.domain.project.OrganizationUserRepository,
    private val organizations: dev.yona.domain.project.OrganizationRepository,
    private val users: dev.yona.domain.user.UserRepository
) {

    /** 소유자가 만든 앱의 한 줄이다. */
    data class AppRow(val id: Long, val name: String, val clientId: String, val confidential: Boolean, val redirectCount: Int, val createdText: String)

    /**
     * 앱을 넘길 수 있는 소유자 한 곳이다. [value]는 양식으로 보내는 값(`user` 또는 `org:이름`)이고 [name]은 화면에 보일 이름이다.
     */
    data class TransferTarget(val value: String, val name: String, val organization: Boolean)

    /**
     * [actor]가 [current] 소유의 앱을 넘길 수 있는 곳이다(`decisions.md` D-78 5번). 사용자 자신의 계정(앱이 지금 조직 소유일 때)과 [actor]가 **관리자인** 다른 조직이다.
     * 조직에 속하기만 한 사용자는 그 조직으로 넘길 수 없다.
     */
    fun transferTargets(actor: User, current: ClientOwner): List<TransferTarget> {
        val own = if (current.type == OAuthClient.USER) emptyList() else listOf(TransferTarget("user", actor.loginId.orEmpty(), false))
        val organizations = organizationUsers.findByUserIdAndRoleId(requireNotNull(actor.id), RoleType.ORG_ADMIN.id).mapNotNull { it.organization }
            .filter { !(current.type == OAuthClient.ORGANIZATION && it.id == current.id) }.sortedBy { it.name?.lowercase() }
            .map { TransferTarget("org:${it.name}", it.name.orEmpty(), true) }
        return own + organizations
    }

    /** 양식의 [value]가 [transferTargets] 중 하나이면 그 소유자를 돌려주고, 허용되지 않은 값이면 `null`이다. */
    fun resolveTarget(actor: User, current: ClientOwner, value: String?): ClientOwner? {
        if (value == null || transferTargets(actor, current).none { it.value == value }) return null
        if (value == "user") return ClientOwner.of(actor)
        val organization = organizationUsers.findByUserIdAndRoleId(requireNotNull(actor.id), RoleType.ORG_ADMIN.id).mapNotNull { it.organization }.firstOrNull { "org:${it.name}" == value } ?: return null
        return ClientOwner.of(organization)
    }

    /** 목록의 한 줄을 만든다. */
    fun appRow(c: OAuthClient) =
        AppRow(requireNotNull(c.id), c.name.orEmpty(), c.clientId.orEmpty(), c.confidential == true, RedirectUris.parse(c.redirectUris).size, helper.dateString(c.createdDate))

    /** 앱의 소유자 이름이다(사용자는 로그인 ID, 조직은 조직 이름, 문서로 가져온 앱은 호스트). */
    fun ownerLabel(c: OAuthClient): String = when {
        c.registration == OAuthClient.CIMD -> runCatching { java.net.URI(c.clientId.orEmpty()).host }.getOrNull().orEmpty()
        c.ownerType == OAuthClient.ORGANIZATION -> c.ownerId?.let { organizations.findById(it).orElse(null)?.name }.orEmpty()
        else -> c.ownerId?.let { users.findById(it).orElse(null)?.loginId }.orEmpty()
    }

    /** 앱의 소유자가 조직이면 `true`이다. */
    fun ownerIsOrganization(c: OAuthClient): Boolean = c.registration != OAuthClient.CIMD && c.ownerType == OAuthClient.ORGANIZATION

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
