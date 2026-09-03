package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "webhook_thread")
class WebhookThread(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    var webhook: Webhook? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(nullable = false)
    var resourceId: String = "",

    @Column(nullable = false)
    var threadId: String = "",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
