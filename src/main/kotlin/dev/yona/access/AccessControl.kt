package dev.yona.access

import dev.yona.security.SessionPermissions
import dev.yona.config.YonaProperties
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectTransferRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service

/**
 * 사용자가 리소스에 어떤 연산을 할 수 있는지 판단한다. v1.6 `utils.AccessControl`(`app/utils/AccessControl.java`)이다.
 *
 * 사용자 매개변수가 `null`이면 로그인하지 않은 익명 사용자이다(v1.6의 `User.anonymous`).
 *
 * 평가 순서(`isAllowed`)
 * 1. 익명 접근을 막는 사이트에서 익명이면 거부한다.
 * 2. 사이트 관리자이면 허용한다.
 * 3. 프로젝트에 속하지 않는 리소스는 [isGlobalResourceAllowed]로, 그 외에는 [isProjectResourceAllowed]로 판단한다.
 *
 * 규칙 표는 `yona-v2-wiki/subsystems/access-control.md`에 정리했다.
 */
@Service
class AccessControl(
    private val properties: YonaProperties,
    private val siteAdmins: SiteAdminRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val transfers: ProjectTransferRepository,
    private val issues: IssueRepository,
    private val issueComments: IssueCommentRepository,
    private val issueSharers: IssueSharerRepository,
    private val permissions: SessionPermissions
) {

    // --- 생성 권한 ---

    /** 전역 리소스(프로젝트, 조직, 첨부 등)는 로그인한 사용자라면 누구나 만들 수 있다. */
    fun isGlobalResourceCreatable(user: User?): Boolean = user != null

    /**
     * 프로젝트 안에 [resourceType] 리소스를 만들 권한이 있는지 판단한다.
     * 작성자 권한은 보지 않는다(만들기 전에는 작성자가 없다).
     *
     * 사이트 관리자, 조직 관리자, 프로젝트 멤버, 조직 구성원(공개/보호 프로젝트)은 무엇이든 만들 수 있다.
     * 비공개 프로젝트의 비멤버는 만들 수 없고, 공개 프로젝트의 로그인 사용자는 이슈, 게시글, 댓글, 포크,
     * 코드 댓글, 리뷰 댓글만 만들 수 있다.
     */
    fun isProjectResourceCreatable(user: User?, project: Project, resourceType: ResourceType): Boolean {
        if (user == null) return false

        if (isSiteManager(user) || isOrganizationAdmin(project.organization, user) ||
            isProjectMember(user, project) || isAllowedIfGroupMember(project, user)
        ) {
            return true
        }

        if (!project.isPublic()) return false

        return resourceType in PUBLIC_CREATABLE_TYPES
    }

    /** [container] 아래에 [resourceType] 리소스를 만들 권한이 있는지 판단한다. */
    fun isResourceCreatable(user: User?, container: Resource, resourceType: ResourceType): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false

        if (isAllowedIfAuthor(user, container) || isAllowedIfAssignee(user, container) || isAllowedIfSharer(user, container)) {
            return true
        }

        val project = if (container.type == ResourceType.PROJECT) {
            projects.findById(container.id.toLong()).orElse(null)
        } else {
            container.project
        }

        return if (project == null) isGlobalResourceCreatable(user) else isProjectResourceCreatable(user, project, resourceType)
    }

    // --- 접근 권한 ---

    /**
     * [user]가 [resource]에 [operation]을 할 수 있는지 판단한다.
     *
     * @throws IllegalStateException 프로젝트 리소스가 프로젝트를 잃었을 때
     */
    fun isAllowed(user: User?, resource: Resource, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false

        if (isSiteManager(user)) return true

        if (resource is GlobalResource) return isGlobalResourceAllowed(user, resource, operation)

        val project = resource.project ?: throw IllegalStateException("A project resource lost its project")
        return isProjectResourceAllowed(user, project, resource, operation)
    }

    /** 사이트가 익명 접근을 막고 있으면 `true`이다 (v1.6 `isAnonymousNotAllowed`). */
    fun isAnonymousNotAllowed(): Boolean = !properties.allowsAnonymousAccess

    // --- 전역 리소스 ---

    private fun isGlobalResourceAllowed(user: User?, resource: GlobalResource, operation: Operation): Boolean {
        if (operation == Operation.ASSIGN_ISSUE && resource.type == ResourceType.PROJECT) {
            val project = findProject(resource) ?: return false
            return isProjectMember(user, project) || (!project.isPrivate() && isOrganizationMember(project.organization, user))
        }

        // 임시 첨부는 올린 사람만 쓸 수 있다.
        if (resource.type == ResourceType.ATTACHMENT && resource.container?.type == ResourceType.USER) {
            return user?.id?.toString() == resource.container.id
        }

        if (operation == Operation.READ) {
            if (resource.type == ResourceType.PROJECT) {
                val project = findProject(resource) ?: return false
                return (project.isPublic() && user?.isGuest != true) ||
                    isProjectMember(user, project) ||
                    isOrganizationAdmin(project.organization, user) ||
                    isAllowedIfGroupMember(project, user)
            }
            // 프로젝트가 아닌 전역 리소스는 누구나 읽을 수 있다.
            return true
        }

        if (operation == Operation.WATCH && resource.type == ResourceType.PROJECT) {
            val project = findProject(resource) ?: return false
            return (project.isPublic() && user != null) ||
                isProjectMember(user, project) || isOrganizationAdmin(project.organization, user) ||
                isAllowedIfGroupMember(project, user)
        }

        if (operation == Operation.LEAVE && resource.type == ResourceType.PROJECT) {
            val project = findProject(resource) ?: return false
            return user != null && !project.isOwner(user) && isProjectMember(user, project)
        }

        // UPDATE, DELETE
        return when (resource.type) {
            ResourceType.USER, ResourceType.USER_AVATAR -> user?.id?.toString() == resource.id
            ResourceType.PROJECT -> {
                val projectId = resource.id.toLong()
                if (isProjectManager(user, projectId)) return true
                val project = projects.findById(projectId).orElse(null) ?: return false
                isOrganizationAdmin(project.organization, user)
            }
            ResourceType.ORGANIZATION -> isOrganizationAdmin(resource.id.toLong(), user)
            else -> false
        }
    }

    // --- 프로젝트 리소스 ---

    private fun isProjectResourceAllowed(user: User?, project: Project, resource: Resource, operation: Operation): Boolean {
        if (isSiteManager(user)) return true

        // 프로젝트 이전 요청은 새 소유자만 수락할 수 있다.
        if (resource.type == ResourceType.PROJECT_TRANSFER) {
            if (operation != Operation.ACCEPT) return false
            val transfer = transfers.findById(resource.id.toLong()).orElse(null) ?: return false
            val destination = transfer.destination ?: return false
            val toUser = users.findByLoginId(destination)
            if (toUser != null) return user?.loginId == destination
            val receivingOrganization = organizations.findByNameIgnoreCase(destination)
            return receivingOrganization != null && isOrganizationAdmin(receivingOrganization.id, user)
        }

        if (isOrganizationAdmin(project.organization, user)) return true

        // 이슈, 게시글, 댓글의 삭제는 작성자와 관리자만 한다(추가 개발 항목 X-36, `decisions.md` D-48). 담당자나 일반 구성원은 지우지 못한다. 수정(`UPDATE`)은
        // GitHub처럼 구성원에게 그대로 열어 둔다(이슈 본문 변경 이력이 남는다).
        val privileged = isProjectManager(user, project) || isAllowedIfAuthor(user, resource)
        if (resource.type in CONTENT_TYPES && operation == Operation.DELETE) return privileged
        if (privileged || isAllowedIfAssignee(user, resource)) {
            return true
        }

        // 일부 리소스의 권한은 컨테이너를 따른다.
        if (resource.type in CONTAINER_DEPENDENT_TYPES) {
            val container = resource.container
            when (operation) {
                Operation.READ -> return container != null &&
                    (isAllowed(user, container, Operation.READ) || isAllowedIfSharer(user, container))
                Operation.UPDATE, Operation.DELETE -> return container != null && isAllowed(user, container, Operation.UPDATE)
                else -> Unit
            }
        }

        // 멤버, 조직 구성원, 비멤버, 익명에 대한 기본 규칙
        return when (operation) {
            Operation.READ ->
                (project.isPublic() && user?.isGuest != true) || isProjectMember(user, project) ||
                    isAllowedIfSharer(user, resource) || isAllowedIfGroupMember(project, user)
            Operation.UPDATE -> isProjectMember(user, project) || isAllowedIfGroupMember(project, user)
            Operation.DELETE -> resource.type != ResourceType.CODE && isProjectMember(user, project)
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN -> isProjectMember(user, project) || isAllowedIfGroupMember(project, user)
            Operation.WATCH -> (project.isPublic() && user != null) || isProjectMember(user, project) || isAllowedIfGroupMember(project, user)
            else -> false
        }
    }

    // --- 보조 규칙 ---

    /** 조직에 속한 공개 또는 보호 프로젝트는 그 조직의 구성원에게 열린다. */
    private fun isAllowedIfGroupMember(project: Project, user: User?): Boolean =
        project.hasGroup() && (project.isPublic() || project.isProtected()) && isOrganizationMember(project.organization, user)

    /** 작성자에게 권한을 주는 종류의 리소스이고 [user]가 작성자이면 `true`이다. */
    private fun isAllowedIfAuthor(user: User?, resource: Resource): Boolean =
        resource.type in AUTHOR_TYPES && resource.isAuthoredBy(user)

    /** 이슈의 공유자(부모 이슈의 공유자 포함)이거나 공유된 이슈의 댓글이면 `true`이다. */
    private fun isAllowedIfSharer(user: User?, resource: Resource): Boolean {
        val userId = user?.id
        return when (resource.type) {
            ResourceType.ISSUE_POST -> {
                val issue = issues.findById(resource.id.toLong()).orElse(null)
                val parentId = issue?.parent?.id
                (parentId != null && userId != null && issueSharers.existsByIssueIdAndUserId(parentId, userId)) ||
                    (issue?.id != null && userId != null && issueSharers.existsByIssueIdAndUserId(issue.id!!, userId))
            }
            ResourceType.ISSUE_COMMENT -> {
                val issueId = issueComments.findById(resource.id.toLong()).orElse(null)?.issue?.id
                issueId != null && isAllowedIfSharer(user, ProjectResource(issueId.toString(), ResourceType.ISSUE_POST, null))
            }
            else -> false
        }
    }

    /** 이슈의 담당자이면 `true`이다. */
    private fun isAllowedIfAssignee(user: User?, resource: Resource): Boolean {
        if (resource.type != ResourceType.ISSUE_POST) return false
        val assignee = issues.findById(resource.id.toLong()).orElseThrow().assignee
        return assignee?.user?.id != null && assignee.user?.id == user?.id
    }

    private fun findProject(resource: Resource): Project? = projects.findById(resource.id.toLong()).orElse(null)

    /** 사용자가 사이트 관리자이면 `true`이다. 현재 세션 사용자는 v1.6 `User.isSiteManager`처럼 처음 계산한 값이 유지된다 ([SessionPermissions]). */
    fun isSiteManager(user: User?): Boolean = permissions.siteManager(user?.id)

    /**
     * 프로젝트 관리자나 사이트 관리자는 [rulesets]를 우회할 수 있다(추가 개발 항목 X-12, X-34, `decisions.md` D-35 11번, D-46). 막고 있는 규칙 묶음이 모두 바이패스를 허용해야 하고
     * 바이패스를 허용하지 않은 규칙 묶음이 하나라도 있으면 우회하지 못한다. 규칙 묶음 목록이 비어 있으면 관리자 여부만 본다.
     */
    fun canBypass(user: User?, project: Project, rulesets: List<dev.yona.domain.project.Ruleset> = emptyList()): Boolean =
        user != null && rulesets.all { it.bypassAdmins == true } && (isSiteManager(user) || isProjectManager(user, project))

    /** 프로젝트에 속해 있으면(역할 무관) `true`이다 (v1.6 `User.isMemberOf(Project)`). */
    private fun isProjectMember(user: User?, project: Project): Boolean {
        val userId = user?.id ?: return false
        return projectUsers.existsByUserIdAndProjectId(userId, requireNotNull(project.id))
    }

    /** v1.6 `user.isManagerOf(project)`: 현재 세션 사용자는 처음 계산한 값이 유지된다. 프로젝트 번호로 묻는 아래 중복 정의는 정적 `ProjectUser.isManager`에 해당해 기억하지 않는다. */
    private fun isProjectManager(user: User?, project: Project): Boolean = permissions.projectManager(user?.id, project.id)

    private fun isProjectManager(user: User?, projectId: Long?): Boolean {
        val userId = user?.id ?: return false
        return projectUsers.existsByUserIdAndProjectIdAndRoleId(userId, requireNotNull(projectId), RoleType.MANAGER.id)
    }

    private fun isOrganizationAdmin(organization: Organization?, user: User?): Boolean =
        isOrganizationAdmin(organization?.id, user)

    private fun isOrganizationAdmin(organizationId: Long?, user: User?): Boolean =
        hasOrganizationRole(organizationId, user, RoleType.ORG_ADMIN)

    private fun isOrganizationMember(organization: Organization?, user: User?): Boolean =
        hasOrganizationRole(organization?.id, user, RoleType.ORG_MEMBER)

    private fun hasOrganizationRole(organizationId: Long?, user: User?, roleType: RoleType): Boolean {
        val userId = user?.id ?: return false
        if (organizationId == null) return false
        return organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(organizationId, userId, roleType.id)
    }

    private companion object {
        /** 공개 프로젝트에서 로그인한 비멤버도 만들 수 있는 리소스 종류이다. */
        val PUBLIC_CREATABLE_TYPES = setOf(
            ResourceType.ISSUE_POST, ResourceType.BOARD_POST, ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT,
            ResourceType.FORK, ResourceType.COMMIT_COMMENT, ResourceType.REVIEW_COMMENT
        )

        /** 작성자에게 읽기, 수정, 삭제 권한을 주는 리소스 종류이다. */
        val AUTHOR_TYPES = setOf(
            ResourceType.ISSUE_POST, ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT, ResourceType.BOARD_POST,
            ResourceType.COMMIT_COMMENT, ResourceType.COMMENT_THREAD, ResourceType.REVIEW_COMMENT
        )

        /** 삭제를 작성자와 관리자만 하는 리소스 종류이다(D-48, GitHub 방식): 이슈, 게시글, 이슈 댓글, 게시판 댓글. */
        val CONTENT_TYPES = setOf(ResourceType.ISSUE_POST, ResourceType.BOARD_POST, ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT)

        /** 권한이 컨테이너를 따르는 리소스 종류이다. */
        val CONTAINER_DEPENDENT_TYPES = setOf(
            ResourceType.ISSUE_STATE, ResourceType.ISSUE_ASSIGNEE, ResourceType.ISSUE_MILESTONE, ResourceType.ATTACHMENT
        )
    }
}
