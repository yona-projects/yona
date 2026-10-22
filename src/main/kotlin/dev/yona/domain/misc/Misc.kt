package dev.yona.domain.misc

import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Organization
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 첨부 파일이다. 어느 리소스에 속하는지는 `(containerType, containerId)`로 표현한다. */
@Entity
@Table(
    name = "attachment",
    indexes = [
        Index(name = "ix_attachment_container", columnList = "container_type, container_id"),
        Index(name = "ix_attachment_created_date", columnList = "created_date"),
        Index(name = "ix_attachment_owner_login_id", columnList = "owner_login_id")
    ]
)
class Attachment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var hash: String? = null,
    @Column(name = "container_type", length = 20)
    var containerType: ResourceType? = null,
    @Column(name = "mime_type")
    var mimeType: String? = null,
    var size: Long? = null,
    @Column(name = "container_id", nullable = false)
    var containerId: Long = 0,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "owner_login_id")
    var ownerLoginId: String? = null
)

/** 리소스를 구독(Watch)한 기록이다. */
@Entity
@Table(
    name = "watch",
    indexes = [
        Index(name = "ix_watch_resource_id_resource_type", columnList = "resource_id, resource_type"),
        Index(name = "ix_watch_user_46", columnList = "user_id")
    ]
)
class Watch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null
)

/** 리소스 구독을 명시적으로 해제(Unwatch)한 기록이다. */
@Entity
@Table(
    name = "unwatch",
    indexes = [
        Index(name = "ix_unwatch_resource_id_resource_type", columnList = "resource_id, resource_type"),
        Index(name = "ix_unwatch_user_43", columnList = "user_id")
    ]
)
class Unwatch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null
)

/** 즐겨찾기한 이슈이다. */
@Entity
@Table(
    name = "favorite_issue",
    indexes = [
        Index(name = "ix_favorite_issue_project_2", columnList = "issue_id"),
        Index(name = "ix_favorite_issue_user_1", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_favorite_issue_user_id_issue_id_1", columnNames = ["user_id", "issue_id"])
    ]
)
class FavoriteIssue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "issue_id")
    var issue: Issue? = null
)

/** 즐겨찾기한 조직이다. */
@Entity
@Table(
    name = "favorite_organization",
    indexes = [
        Index(name = "ix_favorite_organization_organization_2", columnList = "organization_id"),
        Index(name = "ix_favorite_organization_user_1", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_favorite_organization_user_id_organization_id_1", columnNames = ["user_id", "organization_id"])
    ]
)
class FavoriteOrganization(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "organization_id")
    var organization: Organization? = null,
    @Column(name = "organization_name")
    var organizationName: String? = null
)

/** 즐겨찾기한 프로젝트이다. */
@Entity
@Table(
    name = "favorite_project",
    indexes = [
        Index(name = "ix_favorite_project_project_2", columnList = "project_id"),
        Index(name = "ix_favorite_project_user_1", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_favorite_project_user_id_project_id_1", columnNames = ["user_id", "project_id"])
    ]
)
class FavoriteProject(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    var owner: String? = null,
    @Column(name = "project_name")
    var projectName: String? = null
)

/** 최근 본 이슈나 게시글이다. */
@Entity
@Table(
    name = "recent_issue",
    indexes = [
        Index(name = "fk_recent_issue_issue", columnList = "issue_id"),
        Index(name = "ix_recent_issue_issue_2", columnList = "user_id, issue_id"),
        Index(name = "ix_recent_issue_posting_3", columnList = "user_id, posting_id"),
        Index(name = "ix_recent_issue_user_1", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_recent_issue_user_id_issue_id_1", columnNames = ["user_id", "issue_id"]),
        UniqueConstraint(name = "uq_recent_issue_user_id_posting_id_1", columnNames = ["user_id", "posting_id"])
    ]
)
class RecentIssue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "issue_id")
    var issue: Issue? = null,
    @Column(name = "posting_id")
    var postingId: Long? = null,
    var title: String? = null,
    var url: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null
)

/** 키-값 형태의 시스템 속성이다. */
@Entity
@Table(name = "property")
class Property(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = 25)
    var name: PropertyName? = null,
    var value: String? = null
)
