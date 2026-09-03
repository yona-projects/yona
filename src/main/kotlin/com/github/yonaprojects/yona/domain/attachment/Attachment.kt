package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "attachment")
class Attachment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var hash: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var containerType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(nullable = false)
    var containerId: String = "",

    var mimeType: String? = null,
    var size: Long? = null,

    var createdDate: Instant? = null,
    var ownerLoginId: String? = null
)
