package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.organization.Organization
import jakarta.persistence.*

@Entity
@Table(name = "favorite_organization")
class FavoriteOrganization(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    var organizationName: String = ""
) {
    constructor(user: User, organization: Organization) : this(
        id = null,
        user = user,
        organization = organization,
        organizationName = organization.name ?: ""
    )
}
