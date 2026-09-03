package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "notification_event")
class NotificationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    override var title: String = "",

    override var senderId: Long? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "notification_event_n4user",
        joinColumns = [JoinColumn(name = "notification_event_id")],
        inverseJoinColumns = [JoinColumn(name = "n4user_id")]
    )
    override var receivers: MutableSet<User> = mutableSetOf(),

    override var created: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    override var resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(nullable = false)
    override var resourceId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    override var eventType: EventType = EventType.NEW_ISSUE,

    @Column(length = 1_000_000)
    var oldValue: String? = null,

    @Column(length = 1_000_000)
    var newValue: String? = null,

    @OneToOne(mappedBy = "notificationEvent", cascade = [CascadeType.ALL])
    var notificationMail: NotificationMail? = null
) : INotificationEvent
