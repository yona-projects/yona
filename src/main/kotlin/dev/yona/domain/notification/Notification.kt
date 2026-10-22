package dev.yona.domain.notification

import dev.yona.domain.LONG_TEXT
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.*
import java.util.Date

/** 알림 이벤트이다. 수신자는 `notification_event_n4user` 조인 테이블에 저장한다. */
@Entity
@Table(
    name = "notification_event",
    indexes = [
        Index(name = "ix_notification_event_created", columnList = "created")
    ]
)
class NotificationEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var title: String? = null,
    @Column(name = "sender_id")
    var senderId: Long? = null,
    var created: Date? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null,
    @Column(name = "event_type", length = 34)
    var eventType: EventType? = null,
    @Column(name = "old_value", length = LONG_TEXT)
    var oldValue: String? = null,
    @Column(name = "new_value", length = LONG_TEXT)
    var newValue: String? = null,
    @ManyToMany @JoinTable(
        name = "notification_event_n4user",
        joinColumns = [JoinColumn(name = "notification_event_id")],
        inverseJoinColumns = [JoinColumn(name = "n4user_id")],
        indexes = [
            Index(name = "fk_notification_event_n4user_n4user_02", columnList = "n4user_id")
        ]
    )
    var receivers: MutableSet<User> = mutableSetOf()
)

/** 메일로 발송할 알림 대기열이다. */
@Entity
@Table(
    name = "notification_mail",
    indexes = [
        Index(name = "ix_notification_mail_notificationEvent_17", columnList = "notification_event_id")
    ]
)
class NotificationMail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "notification_event_id")
    var notificationEvent: NotificationEvent? = null
)

/** 사용자가 프로젝트별로 설정한 알림 수신 여부이다. */
@Entity
@Table(
    name = "user_project_notification",
    indexes = [
        Index(name = "ix_user_project_notification_project_45", columnList = "project_id"),
        Index(name = "ix_user_project_notification_user_44", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_user_project_notification_1", columnNames = ["project_id", "user_id", "notification_type"])
    ]
)
class UserProjectNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "notification_type", length = 34)
    var notificationType: EventType? = null,
    var allowed: Boolean? = false
)

/** 본문이나 댓글에서 사용자를 멘션한 기록이다. */
@Entity
@Table(
    name = "mention",
    indexes = [
        Index(name = "ix_mention_resource_type", columnList = "resource_type"),
        Index(name = "ix_mention_user_15", columnList = "user_id")
    ]
)
class Mention(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null
)

/** 메일 회신으로 만들어진 리소스와 원본 메일의 연결이다. */
@Entity
@Table(
    name = "original_email",
    indexes = [
        Index(name = "ix_original_email_resource_id", columnList = "resource_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_original_email_1", columnNames = ["resource_type", "resource_id"]),
        UniqueConstraint(name = "uq_original_email_message_id", columnNames = ["message_id"])
    ]
)
class OriginalEmail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "message_id")
    var messageId: String? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null,
    @Column(name = "handled_date")
    var handledDate: Date? = null
)
