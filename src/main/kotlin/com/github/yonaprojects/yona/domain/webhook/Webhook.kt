package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "webhook")
class Webhook(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project? = null,

    @Column(nullable = false, length = 2000)
    var payloadUrl: String = "",

    @Column(length = 250)
    var secret: String? = null,

    @Column(nullable = false)
    var gitPush: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var webhookType: WebhookType = WebhookType.SIMPLE,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
