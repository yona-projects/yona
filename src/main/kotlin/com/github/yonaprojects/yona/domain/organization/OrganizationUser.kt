package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*

@Entity
@Table(name = "organization_user")
class OrganizationUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role
)
