package dev.yona.security

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.oauth2server.OAuthOrgPolicyService
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 조직의 OAuth 앱 정책(X-39, `decisions.md` D-78)을 요청 한 건 안에서 판정한다. OAuth 토큰으로 온 요청만 대상이고(개인용 토큰, 세션, 쿠키는 영향이 없다),
 * 제한을 켠 조직이 승인하지 않은 앱의 요청은 그 조직의 **비공개 프로젝트와 조직 정보**에 닿지 못한다. 공개 프로젝트는 읽을 수만 있고(익명과 같다) 쓰기는 막힌다.
 * 판정은 요청 속성에 한 번만 계산해 둔다.
 */
@Component
class OAuthOrgPolicyGuard(private val policy: OAuthOrgPolicyService, private val organizations: OrganizationRepository) {

    /** 이 요청이 OAuth 토큰으로 온 것이면 그 앱의 행 번호이고, 아니면 `null`이다. */
    fun clientRowId(): Long? = (SecurityContextHolder.getContext().authentication as? YonaAuthentication)?.oauthClientRowId

    /** 이 요청의 앱이 막히는 조직의 번호들이다. OAuth 토큰이 아니거나 제한한 조직이 없으면 빈 집합이다. */
    fun deniedOrganizationIds(): Set<Long> {
        val client = clientRowId() ?: return emptySet()
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        @Suppress("UNCHECKED_CAST")
        (request?.getAttribute(ATTRIBUTE) as? Set<Long>)?.let { return it }
        val restricted = policy.restrictedOrganizationIds()
        val denied = if (restricted.isEmpty()) emptySet() else policy.deniedOrganizations(client, restricted)
        request?.setAttribute(ATTRIBUTE, denied)
        return denied
    }

    /** [organization]의 정보와 설정에 이 요청의 앱이 닿을 수 없으면 `true`이다. */
    fun blocks(organization: Organization?): Boolean = organization?.id?.let { it in deniedOrganizationIds() } == true

    /** [project]가 이 요청의 앱이 막히는 조직의 것이면 `true`이다(접근 판단에서 익명과 같게 본다). */
    fun masks(project: Project?): Boolean {
        val id = project?.organization?.id ?: return false
        return clientRowId() != null && id in deniedOrganizationIds()
    }

    /** [resource]가 이 요청의 앱이 막히는 조직의 것이면 `true`이다. 조직 자원은 조직 자신이고, 그 밖의 자원은 소속 프로젝트의 조직이다. */
    fun masks(resource: dev.yona.access.Resource): Boolean {
        if (clientRowId() == null) return false
        val denied = deniedOrganizationIds()
        if (denied.isEmpty()) return false
        if (resource.type == dev.yona.domain.enumeration.ResourceType.ORGANIZATION) return resource.id.toLongOrNull()?.let { it in denied } == true
        return masks(resource.project)
    }

    /** 조직 이름으로 같은 것을 묻는다. 없는 조직이면 `false`이다. */
    fun blocksOrganizationNamed(name: String?): Boolean = name != null && deniedOrganizationIds().isNotEmpty() && blocks(organizations.findByNameIgnoreCase(name))

    /** 목록에 [project]를 보여도 되면 `true`이다. 막히는 조직의 프로젝트는 공개인 것만 보인다. */
    fun visible(project: Project): Boolean = !(project.organization?.id?.let { it in deniedOrganizationIds() } == true && project.projectScope != ProjectScope.PUBLIC)

    /** [project]에 대한 이 요청([method])을 막아야 하면 그 조직의 번호이고, 아니면 `null`이다. 막히는 조직에서는 공개 프로젝트의 읽기만 허용한다. */
    fun blockingOrganizationId(project: Project, method: String): Long? {
        val id = project.organization?.id ?: return null
        if (id !in deniedOrganizationIds()) return null
        val readsPublic = project.projectScope == ProjectScope.PUBLIC && method.uppercase() in READ_METHODS
        return if (readsPublic) null else id
    }

    /** [ownerName]이 막히는 조직의 이름이고 요청이 읽기가 아니면 그 조직의 번호이다(그 조직에 프로젝트를 만드는 것 같은 요청). */
    fun blockingOrganizationIdForOwnerWrite(ownerName: String?, method: String): Long? {
        if (ownerName == null || method.uppercase() in READ_METHODS || deniedOrganizationIds().isEmpty()) return null
        val organization = organizations.findByNameIgnoreCase(ownerName) ?: return null
        return organization.id?.takeIf { it in deniedOrganizationIds() }
    }

    /** 조직 이름으로 같은 것을 묻는다. 조직 정보와 설정은 읽기와 쓰기 모두 막는다. */
    fun blockingOrganizationIdNamed(name: String): Long? = organizations.findByNameIgnoreCase(name)?.takeIf { blocks(it) }?.id

    /** 이 요청의 앱의 공개 클라이언트 ID이다(감사 기록용). OAuth 토큰이 아니면 `null`이다. */
    fun clientIdOrNull(): String? = clientRowId()?.let { policy.clientIdOf(it) }

    companion object {
        private const val ATTRIBUTE = "dev.yona.security.OAuthOrgPolicyDenied"
        private val READ_METHODS = setOf("GET", "HEAD")
        const val MESSAGE = "this organization restricts access by OAuth apps it has not approved"
    }
}
