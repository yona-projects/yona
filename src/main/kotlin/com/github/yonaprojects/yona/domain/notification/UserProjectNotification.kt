package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.EventType
import jakarta.persistence.*

@Entity
@Table(
    name = "user_project_notification",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "user_id", "notification_type"])]
)
class UserProjectNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    var notificationType: EventType,

    var allowed: Boolean = true
) {
    fun toggle() {
        this.allowed = !this.allowed
    }
}
