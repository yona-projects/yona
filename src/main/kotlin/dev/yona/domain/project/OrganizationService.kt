package dev.yona.domain.project

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.user.LoginIdRules
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.SiteAdminRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 조직의 생성과 조회이다. v1.6 `models.Organization`, `OrganizationApp`의 생성, 목록에 해당한다.
 */
@Service
@Transactional
class OrganizationService(
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val siteAdmins: SiteAdminRepository,
    private val properties: YonaProperties
) {

    /** 조직 이름 검증에서 걸린 이유이다. [messageKey]는 사용자에게 보여 줄 메시지 키이다. */
    enum class NameError(val messageKey: String) {
        REQUIRED("error.required"),
        FORMAT("user.wrongloginId.alert"),
        RESERVED("validation.reservedWord"),
        DUPLICATE("organization.name.duplicate")
    }

    /** 이름으로 조직을 찾는다. 대소문자를 구별하지 않는다 (v1.6 `Organization.findByName`). */
    @Transactional(readOnly = true)
    fun findByName(name: String): Organization? = organizations.findByNameIgnoreCase(name)

    /**
     * 새 조직 이름을 검증한다. 형식 -> 예약어 -> 중복(사용자 로그인 ID 또는 다른 조직) 순서이다.
     * 첫 번째로 걸린 이유를 돌려주고, 통과하면 `null`이다. 오류 안내는 첫 번째 이유 하나만 보여 준다.
     */
    @Transactional(readOnly = true)
    fun validateNewName(name: String?): NameError? {
        if (name.isNullOrEmpty()) return NameError.REQUIRED
        if (!LoginIdRules.isValidFormat(name)) return NameError.FORMAT
        if (LoginIdRules.isReserved(name)) return NameError.RESERVED
        if (users.existsByLoginIdIgnoreCase(name) || organizations.existsByNameIgnoreCase(name)) return NameError.DUPLICATE
        return null
    }

    /** 조직을 만들고 만든 사람을 조직 관리자로 등록한다 (v1.6 `newOrganization`, `User.createOrganization`). */
    fun create(name: String, descr: String?, creator: User): Organization {
        val organization = organizations.save(Organization(name = name, descr = descr, created = Date()))
        organizationUsers.save(
            OrganizationUser(user = creator, organization = organization, role = roles.getReferenceById(RoleType.ORG_ADMIN.id))
        )
        return organization
    }

    /** 이름이나 설명에 [query]가 들어 있는 조직을 최신순으로 한 쪽(30개) 조회한다. [pageNum]은 1부터이다. */
    @Transactional(readOnly = true)
    fun search(query: String, pageNum: Int): Page<Organization> =
        organizations.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCaseOrderByIdDesc(query, query, PageRequest.of(pageNum - 1, PAGE_SIZE))

    /**
     * [viewer]가 볼 수 있는 조직 프로젝트를 이름순으로 돌려준다 (v1.6 `Organization.getVisibleProjects`).
     *
     * - 조직 관리자와 사이트 관리자는 모두 본다.
     * - 조직 구성원은 비공개가 아닌 프로젝트와 자신이 구성원인 프로젝트를 본다.
     * - 그 밖의 사람은 사이트가 목록을 숨기지 않을 때만, 공개 프로젝트(게스트 제외)와 자신이 구성원인 프로젝트를 본다.
     */
    @Transactional(readOnly = true)
    fun visibleProjects(organization: Organization, viewer: User?): List<Project> {
        val all = projects.findByOrganizationId(requireNotNull(organization.id))
        val orgId = requireNotNull(organization.id)
        val userId = viewer?.id
        fun isProjectMember(project: Project) = userId != null && projectUsers.existsByUserIdAndProjectId(userId, requireNotNull(project.id))

        val result = when {
            userId != null && (organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(orgId, userId, RoleType.ORG_ADMIN.id) || siteAdmins.existsByAdminId(userId)) -> all
            userId != null && organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(orgId, userId, RoleType.ORG_MEMBER.id) ->
                all.filter { !it.isPrivate() || isProjectMember(it) }
            properties.hideProjectListing -> emptyList()
            else -> all.filter { (it.isPublic() && viewer?.isGuest != true) || isProjectMember(it) }
        }
        return result.sortedBy { it.name }
    }

    companion object {
        /** 조직 목록의 쪽 크기이다 (v1.6 `findPagingList(30)`). */
        const val PAGE_SIZE = 30
    }
}
