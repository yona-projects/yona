package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*

@Entity
@Table(name = "watch")
class Watch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    var resourceType: ResourceType,

    @Column(name = "resource_id", nullable = false)
    var resourceId: String
)
