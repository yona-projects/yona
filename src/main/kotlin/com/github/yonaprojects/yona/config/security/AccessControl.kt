package com.github.yonaprojects.yona.config.security

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectTransfer
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.webhook.Webhook
import com.github.yonaprojects.yona.domain.attachment.Attachment
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// 리소스 타입은 JPA 엔티티라 다형성을 가질 수 없어, `isGlobalResourceAllowed`/`isProjectResourceAllowed`를
// 리소스 타입별 `isAllowed(...)` 오버로드로 분리한다(설계 근거: docs/P1-85_PLAN.md).
// userRepository/organizationRepository는 ProjectTransfer.destination(loginId 또는 조직명) 조회 및
// isAllowedAttachment()의 컨테이너 동적 해석에, issueRepository/postingRepository/reviewCommentRepository/
// commitCommentRepository/milestoneRepository는 isAllowedAttachment()가 첨부파일의 실제 컨테이너
// 엔티티를 읽어와야 하는 데 쓰인다.
@Component
class AccessControl(
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val milestoneRepository: MilestoneRepository,
    @Value("\${yona.access.allows-anonymous-access:true}")
    private val allowsAnonymousAccess: Boolean = true,
    // 비로그인·조직 비회원에게 조직 프로젝트 목록 자체를 숨기는 사이트 전역 플래그.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {

    private fun isAnonymousNotAllowed(): Boolean = !allowsAnonymousAccess

    /**
     * Checks if a user has a permission to read a project.
     */
    fun isAllowedToReadProject(user: User?, project: Project): Boolean {
        // public 프로젝트는 비로그인 사용자나 게스트가 아니라면 기본적으로 읽기 가능
        if (project.isPublic) {
            return user == null || !user.isGuest
        }

        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }

        return user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isMemberOf(project)
            || isAllowedIfGroupMember(project, user)
    }

    /**
     * Checks if a user has a permission to create a resource of the given
     * type in the given project.
     */
    fun isProjectResourceCreatable(user: User?, project: Project, resourceType: ResourceType): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }

        // Site manager, Group admin, Project members can create anything.
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isMemberOf(project)
            || isAllowedIfGroupMember(project, user)
        ) {
            return true
        }

        // If the project is not public, nonmembers cannot create anything.
        if (!project.isPublic) {
            return false
        }

        // If the project is public, login users can create issues and posts.
        return when (resourceType) {
            ResourceType.ISSUE_POST,
            ResourceType.BOARD_POST,
            ResourceType.ISSUE_COMMENT,
            ResourceType.NONISSUE_COMMENT,
            ResourceType.FORK,
            ResourceType.COMMIT_COMMENT,
            ResourceType.REVIEW_COMMENT -> true
            else -> false
        }
    }

    /**
     * Checks if a user has a permission to update a project resource like an issue.
     */
    fun isAllowedToUpdateIssue(user: User?, project: Project, authorLoginId: String?): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
            || (authorLoginId != null && user.loginId == authorLoginId)
        ) {
            return true
        }
        return false
    }

    /**
     * Checks if a user has a permission to update a posting.
     */
    fun isAllowedToUpdatePosting(user: User?, project: Project, authorLoginId: String?): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
            || (authorLoginId != null && user.loginId == authorLoginId)
        ) {
            return true
        }
        return false
    }

    /**
     * Checks if a user has a permission to update a milestone.
     */
    fun isAllowedToUpdateMilestone(user: User?, project: Project): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
        ) {
            return true
        }
        return false
    }

    private fun isOrganizationAdmin(project: Project, user: User): Boolean {
        val organization = project.organization ?: return false
        val orgId = organization.id ?: return false
        val userId = user.id ?: return false
        return organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .map { it.role.id == RoleType.ORG_ADMIN.roleType }
            .orElse(false)
    }

    // 프로젝트에 속하지 않는 전역 리소스(예: 새 프로젝트 자체)를 생성할 때는 로그인 여부만 확인한다.
    fun isGlobalResourceCreatable(user: User?): Boolean = user != null

    // 댓글을 달 대상 Issue의 작성자/담당자/공유대상이면 프로젝트 멤버 여부와 무관하게 항상 허용되고,
    // 그 외에는 프로젝트 기준 생성권한(isProjectResourceCreatable)으로 위임한다.
    fun isIssueCommentCreatable(user: User?, project: Project, issue: Issue): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user != null) {
            val isAuthor = issue.authorId != null && issue.authorId == user.id
            val isAssignee = issue.assignee?.user?.id == user.id
            if (isAuthor || isAssignee || isAllowedIfSharer(issue, user)) return true
        }
        return isProjectResourceCreatable(user, project, ResourceType.ISSUE_COMMENT)
    }

    // 댓글을 달 대상 Posting은 isAllowedIfAssignee/isAllowedIfSharer의 분기 대상이 아니므로 작성자
    // 우회만 적용된다.
    fun isPostingCommentCreatable(user: User?, project: Project, posting: Posting): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user != null && posting.authorId != null && posting.authorId == user.id) return true
        return isProjectResourceCreatable(user, project, ResourceType.NONISSUE_COMMENT)
    }

    // 프로젝트 직접 멤버가 아니어도 조직(그룹) 소속이면 PUBLIC/PROTECTED 프로젝트에 한해 권한을 준다
    // — web 컨트롤러들의 checkReadPermission/checkWritePermission이 이 규칙을 호출할 수 있도록 공개.
    fun isAllowedIfGroupMember(project: Project, user: User): Boolean {
        val organization = project.organization ?: return false
        val hasGroup = project.organization != null
        val isPublicOrProtected = project.isPublic || project.isProtected

        if (hasGroup && isPublicOrProtected) {
            return organization.organizationUsers.any {
                it.user.id == user.id && (
                    it.role.id == RoleType.ORG_MEMBER.roleType ||
                    it.role.id == RoleType.ORG_ADMIN.roleType
                )
            }
        }
        return false
    }


    // 조직 소속 프로젝트 목록을 노출하는 화면(조직 게시판/조직 홈)에서 로그인 사용자의
    // 조직관리자/조직멤버/비회원 여부에 따라 비공개 프로젝트를 걸러낸다.
    fun getVisibleProjects(organization: Organization, user: User?): List<Project> {
        val result = when {
            isOrganizationAdmin(organization, user) || user?.isSiteManager == true ->
                organization.projects.toList()
            isOrganizationMember(organization, user) ->
                organization.projects.filter { !it.isPrivate || user?.isMemberOf(it) == true }
            else ->
                if (!hideProjectListing) {
                    organization.projects.filter {
                        (it.isPublic && user?.isGuest != true) || user?.isMemberOf(it) == true
                    }
                } else {
                    emptyList()
                }
        }
        return result.sortedBy { it.name }
    }

    // ISSUE_POST의 READ 권한 판단에서 프로젝트 멤버 여부와 무관하게, 해당 이슈의 IssueSharer로
    // 등록된 사용자(또는 대상이 하위 이슈일 경우 그 부모 이슈의 IssueSharer)에게는 READ를 허용한다.
    fun isAllowedIfSharer(issue: Issue, user: User): Boolean {
        issue.parent?.let { parent ->
            if (parent.sharers.any { it.user.id == user.id }) return true
        }
        return issue.sharers.any { it.user.id == user.id }
    }

    // 댓글 자체는 공유 대상이 아니라, 댓글이 달린 이슈가 공유돼 있으면 그 댓글도 읽을 수 있다.
    private fun isAllowedIfSharer(issueComment: IssueComment, user: User): Boolean {
        return isAllowedIfSharer(issueComment.issue, user)
    }

    // Organization을 직접 대상으로 하는 오버로드 및 nullable user 지원 — 위
    // isOrganizationAdmin(project, user)와 달리 project 경유가 아니라 organization을 직접 받는
    // 경로(ORGANIZATION 리소스, PROJECT_TRANSFER 등)에 쓰인다. "owner가 기존 조직명이면 그 조직
    // admin만 생성 가능" 가드가 컨트롤러에서 직접 호출해야 해 public으로 공개한다.
    fun isOrganizationAdmin(organization: Organization?, user: User?): Boolean {
        val orgId = organization?.id ?: return false
        val userId = user?.id ?: return false
        return organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .map { it.role.id == RoleType.ORG_ADMIN.roleType }
            .orElse(false)
    }

    // isOrganizationAdmin과 달리 ORG_MEMBER 역할만 인정한다(ORG_ADMIN은 포함하지 않음).
    private fun isOrganizationMember(organization: Organization?, user: User?): Boolean {
        val orgId = organization?.id ?: return false
        val userId = user?.id ?: return false
        return organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .map { it.role.id == RoleType.ORG_MEMBER.roleType }
            .orElse(false)
    }

    // ==================== 리소스 타입별 isAllowed(...) ====================
    // 리소스 타입은 JPA 엔티티라 다형성을 가질 수 없어 리소스 타입별 오버로드로 분리한다
    // (docs/P1-85_PLAN.md). isAnonymousNotAllowed()는 아래 모든 isAllowed(...) 오버로드 +
    // isAllowedAttachment() 시작부에 동일하게 배선했다.

    fun isAllowed(user: User?, project: Project, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true

        if (operation == Operation.ASSIGN_ISSUE) {
            return user?.isMemberOf(project) == true ||
                (!project.isPrivate && isOrganizationMember(project.organization, user))
        }

        if (operation == Operation.READ) {
            return (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                isOrganizationAdmin(project.organization, user) ||
                (user != null && isAllowedIfGroupMember(project, user))
        }

        if (operation == Operation.WATCH) {
            return (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                isOrganizationAdmin(project.organization, user) ||
                (user != null && isAllowedIfGroupMember(project, user))
        }

        if (operation == Operation.LEAVE) {
            return user != null && project.owner != user.loginId && user.isMemberOf(project)
        }

        // UPDATE, DELETE, ACCEPT, REOPEN, CLOSE 등 나머지 연산: 매니저 또는 조직 관리자만 허용
        // (yona AccessControl.java:186-192 PROJECT case 대응)
        return user?.isManagerOf(project) == true || isOrganizationAdmin(project.organization, user)
    }

    // READ는 (프로젝트가 아닌 리소스는 누구나 읽을 수 있다는 legacy 규칙에 따라) 익명 포함 항상 true,
    // 그 외 모든 연산은 조직 관리자만 허용된다.
    fun isAllowed(user: User?, organization: Organization, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (operation == Operation.READ) return true
        return isOrganizationAdmin(organization, user)
    }

    // isAllowedIfAuthor/isAllowedIfAssignee가 적용되는 리소스 타입 — 작성자 또는 담당자는 연산 종류와
    // 무관하게 항상 허용된다(legacy AccessControl.java:225-227).
    fun isAllowed(user: User?, project: Project, issue: Issue, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && issue.authorId != null && issue.authorId == user.id
        val isAssignee = user?.id != null && issue.assignee?.user?.id == user.id
        if (user?.isManagerOf(project) == true || isAuthor || isAssignee) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfSharer(issue, user)) ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, issueComment: IssueComment, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && issueComment.authorId != null && issueComment.authorId == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfSharer(issueComment, user)) ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, posting: Posting, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && posting.authorId != null && posting.authorId == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, postingComment: PostingComment, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && postingComment.authorId != null && postingComment.authorId == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    // PULL_REQUEST는 legacy isAllowedIfAuthor/isAllowedIfAssignee 스위치에 없는 타입이라(작성자/담당자
    // 자동 승격이 없다) contributor 여부는 이 중앙 함수가 아니라 각 컨트롤러의 별도 isManagerOrContributor류
    // 로직이 추가로 처리한다 — legacy도 동일하게 컨트롤러 액션 단에서 별도 체크한다(P1-85_PLAN.md 참고).
    fun isAllowed(user: User?, project: Project, pullRequest: PullRequest, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true
        if (user?.isManagerOf(project) == true) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, commitComment: CommitComment, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && commitComment.author?.id == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, commentThread: CommentThread, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && commentThread.author?.id == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    fun isAllowed(user: User?, project: Project, reviewComment: ReviewComment, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true

        val isAuthor = user?.id != null && reviewComment.author?.id == user.id
        if (user?.isManagerOf(project) == true || isAuthor) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    // MILESTONE은 isAllowedIfAuthor 대상이 아니다(legacy 스위치에 없음) — 작성자 개념이 없는 리소스.
    fun isAllowed(user: User?, project: Project, milestone: Milestone, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true
        if (user?.isManagerOf(project) == true) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    // 항목이 참조할 함수). WEBHOOK도 작성자 개념이 없는 리소스라 매니저/관리자 우회 외에는 일반 연산
    // 스위치를 그대로 따른다 — legacy 규칙상 프로젝트 멤버라면 누구나 webhook을 UPDATE할 수 있다는 점에
    // 유의(매니저 전용이 아님, legacy 원본 그대로).
    fun isAllowed(user: User?, project: Project, webhook: Webhook, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true
        if (user?.isManagerOf(project) == true) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE -> user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    // yona AccessControl.java:205-301 isProjectResourceAllowed()의 일반(엔티티 없는) 리소스 타입 케이스
    // 대응 — CODE(git 저장소 자체)처럼 yona에 전용 엔티티가 없는 리소스에 쓰인다. legacy는 DELETE에서
    // resource.getType()==CODE면 무조건 false(저장소 자체 삭제는 이 경로로 허용하지 않음 — legacy
    // AccessControl.java:264-267)로 특별 취급한다.
    fun isAllowed(user: User?, project: Project, resourceType: ResourceType, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (isOrganizationAdmin(project.organization, user)) return true
        if (user?.isManagerOf(project) == true) return true

        return when (operation) {
            Operation.READ -> (project.isPublic && user?.isGuest != true) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.UPDATE -> user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            Operation.DELETE ->
                if (resourceType == ResourceType.CODE) false else user?.isMemberOf(project) == true
            Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN ->
                user?.isMemberOf(project) == true || (user != null && isAllowedIfGroupMember(project, user))
            Operation.WATCH -> (project.isPublic && user != null) ||
                user?.isMemberOf(project) == true ||
                (user != null && isAllowedIfGroupMember(project, user))
            else -> false
        }
    }

    // 다른 모든 리소스 타입과 달리 매니저/조직관리자 우회보다도 먼저 체크되며, ACCEPT 연산만 정의돼
    // 있고 그 외는 항상 false다.
    fun isAllowed(user: User?, projectTransfer: ProjectTransfer, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true
        if (operation != Operation.ACCEPT || user == null) return false

        val destinationUser = userRepository.findByLoginId(projectTransfer.destination).orElse(null)
        return if (destinationUser != null) {
            user.loginId == projectTransfer.destination
        } else {
            val receivingOrg = organizationRepository.findByName(projectTransfer.destination).orElse(null)
            receivingOrg != null && isOrganizationAdmin(receivingOrg, user)
        }
    }

    // Attachment.containerType/containerId로부터 실제 컨테이너 엔티티를 조회해, 그 컨테이너 기준으로
    // 권한을 위임한다 — 이 함수만 유일하게 컨테이너를 동적으로 해석한다.
    //
    // "아직 실제 리소스에 붙기 전 임시 첨부"는 containerType=NOT_A_RESOURCE로 표현하며
    // (web/AttachmentController.kt), 코드리뷰 댓글 첨부의 임시 보관은 containerType=USER를 그대로
    // 쓴다(CodeReviewServiceImpl.kt) — 두 경우 모두 ownerLoginId(원 업로더)로 판별한다.
    fun isAllowedAttachment(user: User?, attachment: Attachment, operation: Operation): Boolean {
        if (isAnonymousNotAllowed() && user == null) return false
        if (user?.isSiteManager == true) return true

        if (attachment.containerType == ResourceType.USER || attachment.containerType == ResourceType.NOT_A_RESOURCE) {
            return user != null && user.loginId == attachment.ownerLoginId
        }

        if (attachment.containerType == ResourceType.USER_AVATAR) {
            // yona AccessControl.java:130-138 isGlobalResourceAllowed()의 READ 분기("PROJECT가 아닌
            // 리소스는 누구나 읽을 수 있다")가 USER_AVATAR에도 그대로 적용된다 — READ는 익명 포함 항상
            // 허용, UPDATE/DELETE만 본인 확인(:186 case USER_AVATAR: user.id.toString().equals(...)).
            if (operation == Operation.READ) return true
            val ownerId = attachment.containerId.toLongOrNull()
            return user?.id != null && user.id == ownerId
        }

        val containerId = attachment.containerId.toLongOrNull() ?: return false

        return when (attachment.containerType) {
            ResourceType.ORGANIZATION -> {
                val organization = organizationRepository.findById(containerId).orElse(null) ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, organization, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, organization, Operation.UPDATE)
                    else -> false
                }
            }
            ResourceType.ISSUE_POST -> {
                val issue = issueRepository.findById(containerId).orElse(null) ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, issue.project, issue, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, issue.project, issue, Operation.UPDATE)
                    else -> false
                }
            }
            ResourceType.BOARD_POST -> {
                val posting = postingRepository.findById(containerId).orElse(null) ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, posting.project, posting, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, posting.project, posting, Operation.UPDATE)
                    else -> false
                }
            }
            ResourceType.MILESTONE -> {
                val milestone = milestoneRepository.findById(containerId).orElse(null) ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, milestone.project, milestone, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, milestone.project, milestone, Operation.UPDATE)
                    else -> false
                }
            }
            ResourceType.COMMIT_COMMENT -> {
                val commitComment = commitCommentRepository.findById(containerId).orElse(null) ?: return false
                val project = commitComment.project ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, project, commitComment, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, project, commitComment, Operation.UPDATE)
                    else -> false
                }
            }
            ResourceType.REVIEW_COMMENT -> {
                val reviewComment = reviewCommentRepository.findById(containerId).orElse(null) ?: return false
                val project = reviewComment.thread?.project ?: return false
                when (operation) {
                    Operation.READ -> isAllowed(user, project, reviewComment, Operation.READ)
                    Operation.UPDATE, Operation.DELETE -> isAllowed(user, project, reviewComment, Operation.UPDATE)
                    else -> false
                }
            }
            // legacy도 위 목록에 없는 컨테이너 타입은 Resource.get()이 처리할 수 없어 사실상 도달하지
            // 않는 경로다 — yona에서 Attachment.containerType으로 실제 쓰이는 값(위 케이스들 + USER/
            // USER_AVATAR/NOT_A_RESOURCE)을 모두 다뤘으므로 나머지는 false로 안전하게 막는다.
            else -> false
        }
    }
}
