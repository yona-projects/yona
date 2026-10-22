package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.issue.Label
import dev.yona.domain.user.Role
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 프로젝트이다. 포크한 프로젝트는 `originalProject`로 원본을 가리킨다. */
@Entity
@Table(
    name = "project",
    indexes = [
        Index(name = "ix_project_organization_24", columnList = "organization_id"),
        Index(name = "ix_project_originalProject_23", columnList = "original_project_id")
    ]
)
class Project(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var overview: String? = null,
    var vcs: String? = null,
    var siteurl: String? = null,
    var owner: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "last_issue_number")
    var lastIssueNumber: Long? = null,
    @Column(name = "last_posting_number")
    var lastPostingNumber: Long? = null,
    @ManyToOne @JoinColumn(name = "original_project_id")
    var originalProject: Project? = null,
    @Column(name = "last_pushed_date")
    var lastPushedDate: Date? = null,
    @Column(name = "default_reviewer_count")
    var defaultReviewerCount: Int? = null,
    @Column(name = "is_using_reviewer_count")
    var isUsingReviewerCount: Boolean? = false,
    @ManyToOne @JoinColumn(name = "organization_id")
    var organization: Organization? = null,
    @Column(name = "project_scope", length = 9)
    var projectScope: ProjectScope? = null,
    @Column(name = "previous_owner_login_id")
    var previousOwnerLoginId: String? = null,
    @Column(name = "previous_name")
    var previousName: String? = null,
    @Column(name = "previous_name_changed_time")
    var previousNameChangedTime: Long? = null,
    @Column(name = "is_code_accessible_member_only")
    var isCodeAccessibleMemberOnly: Boolean? = false,
    @ManyToMany @JoinTable(
        name = "project_label",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")],
        indexes = [
            Index(name = "fk_project_label_label_02", columnList = "label_id")
        ]
    )
    var labels: MutableSet<Label> = mutableSetOf()
) {
    /** 공개 프로젝트이면 `true`이다. */
    fun isPublic(): Boolean = projectScope == ProjectScope.PUBLIC

    /** 보호 프로젝트(같은 조직 구성원에게만 공개)이면 `true`이다. */
    fun isProtected(): Boolean = projectScope == ProjectScope.PROTECTED

    /** 비공개 프로젝트이면 `true`이다. */
    fun isPrivate(): Boolean = projectScope == ProjectScope.PRIVATE

    /** 조직에 속한 프로젝트이면 `true`이다. */
    fun hasGroup(): Boolean = organization != null

    /** 로그인 ID가 소유자 이름과 같으면(대소문자 무시) 소유자이다 (v1.6 `Project.isOwner`). */
    fun isOwner(user: User): Boolean = owner?.lowercase() == user.loginId?.lowercase()
}

/** 조직이다. */
@Entity
@Table(name = "organization")
class Organization(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var created: Date? = null,
    var descr: String? = null
)

/** 조직 구성원과 그 역할이다. */
@Entity
@Table(
    name = "organization_user",
    indexes = [
        Index(name = "ix_organization_user_organization_19", columnList = "organization_id"),
        Index(name = "ix_organization_user_role_20", columnList = "role_id"),
        Index(name = "ix_organization_user_user_18", columnList = "user_id")
    ]
)
class OrganizationUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "organization_id")
    var organization: Organization? = null,
    @ManyToOne @JoinColumn(name = "role_id")
    var role: Role? = null
)

/** 프로젝트 구성원과 그 역할이다. */
@Entity
@Table(
    name = "project_user",
    indexes = [
        Index(name = "ix_project_user_project_29", columnList = "project_id"),
        Index(name = "ix_project_user_role_30", columnList = "role_id"),
        Index(name = "ix_project_user_user_28", columnList = "user_id")
    ]
)
class ProjectUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @ManyToOne @JoinColumn(name = "role_id")
    var role: Role? = null
)

/** 프로젝트 메뉴 표시 설정이다. */
@Entity
@Table(
    name = "project_menu_setting",
    indexes = [
        Index(name = "ix_project_menu_setting_project_25", columnList = "project_id")
    ]
)
class ProjectMenuSetting(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var code: Boolean? = false,
    var issue: Boolean? = false,
    @Column(name = "pull_request")
    var pullRequest: Boolean? = false,
    var review: Boolean? = false,
    var milestone: Boolean? = false,
    var board: Boolean? = false
)

/** 프로젝트 이전 요청이다. */
@Entity
@Table(
    name = "project_transfer",
    indexes = [
        Index(name = "ix_project_transfer_project_27", columnList = "project_id"),
        Index(name = "ix_project_transfer_sender_26", columnList = "sender_id")
    ]
)
class ProjectTransfer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "sender_id")
    var sender: User? = null,
    var destination: String? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var requested: Date? = null,
    @Column(name = "confirm_key")
    var confirmKey: String? = null,
    var accepted: Boolean? = false,
    @Column(name = "new_project_name")
    var newProjectName: String? = null
)

/** 최근 푸시된 브랜치이다. */
@Entity
@Table(
    name = "project_pushed_branch",
    indexes = [
        Index(name = "ix_project_pushed_branch_project_39", columnList = "project_id")
    ]
)
class PushedBranch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "pushed_date")
    var pushedDate: Date? = null,
    var name: String? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null
)

/** 사용자가 최근 방문한 프로젝트이다. `(user_id, project_id)`는 유일하다. */
@Entity
@Table(
    name = "recent_project",
    indexes = [
        Index(name = "fk_recent_project_project_2", columnList = "project_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_recent_project_1", columnNames = ["user_id", "project_id"])
    ]
)
class RecentProject(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id")
    var userId: Long? = null,
    var owner: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "project_name")
    var projectName: String? = null
)

/** 이슈 제목 머리말 후보와 사용 빈도이다. */
@Entity
@Table(
    name = "title_head",
    indexes = [
        Index(name = "ix_title_head_head_keyword", columnList = "head_keyword"),
        Index(name = "ix_title_head_project_id", columnList = "project_id")
    ]
)
class TitleHead(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "head_keyword")
    var headKeyword: String? = null,
    var frequency: Int? = null
)

/** 프로젝트 웹훅이다. */
@Entity
@Table(
    name = "webhook",
    indexes = [
        Index(name = "ix_webhook_git_push_only", columnList = "git_push"),
        Index(name = "ix_webhook_project_47", columnList = "project_id"),
        Index(name = "ix_webhook_webhook_type", columnList = "webhook_type")
    ]
)
class Webhook(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "payload_url", length = 2000)
    var payloadUrl: String? = null,
    @Column(length = 250)
    var secret: String? = null,
    @Column(name = "created_at")
    var createdAt: Date? = null,
    @Column(name = "git_push")
    var gitPush: Boolean? = null,
    @Column(name = "webhook_type")
    var webhookType: WebhookType? = WebhookType.DETAIL_SLACK
)

/** 웹훅 알림을 하나의 스레드로 묶기 위한 식별자이다. */
@Entity
@Table(
    name = "webhook_thread",
    indexes = [
        Index(name = "ix_webhook_thread_resource_2", columnList = "resource_type, resource_id"),
        Index(name = "ix_webhook_thread_webhook_1", columnList = "webhook_id")
    ]
)
class WebhookThread(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "webhook_id")
    var webhook: Webhook? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: dev.yona.domain.enumeration.ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null,
    @Column(name = "thread_id", length = 2000)
    var threadId: String? = null,
    @Column(name = "created_at")
    var createdAt: Date? = null
)

/**
 * v1.6 스키마에는 남아 있지만 v1.6 코드에는 대응하는 엔티티가 없는 테이블이다.
 * 방문 기록은 `recent_project`로 대체되었다. 스키마를 v1.6과 같게 유지하기 위해 매핑만 둔다.
 */
@Entity
@Table(
    name = "recently_visited_projects",
    indexes = [
        Index(name = "ix_recently_visited_projects_user_40", columnList = "user_id")
    ]
)
class RecentlyVisitedProjects(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null
)

/** [RecentlyVisitedProjects]와 같은 이유로 유지하는 방문 기록 테이블이다. */
@Entity
@Table(
    name = "project_visitation",
    indexes = [
        Index(name = "ix_project_visitation_project_31", columnList = "project_id"),
        Index(name = "ix_project_visitation_recentlyVisitedProjects_32", columnList = "recently_visited_projects_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_project_visitation_1", columnNames = ["project_id", "recently_visited_projects_id"])
    ]
)
class ProjectVisitation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    @ManyToOne @JoinColumn(name = "recently_visited_projects_id")
    var recentlyVisitedProjects: RecentlyVisitedProjects? = null,
    var visited: Date? = null
)
