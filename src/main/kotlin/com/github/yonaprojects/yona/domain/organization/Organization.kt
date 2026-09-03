package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "organization")
class Organization(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var name: String = "",

    var created: Instant? = null,

    @Column(name = "descr", length = 255)
    var descr: String? = null,

    @OneToMany(mappedBy = "organization", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projects: MutableList<Project> = mutableListOf(),

    @OneToMany(mappedBy = "organization", cascade = [CascadeType.ALL], orphanRemoval = true)
    var organizationUsers: MutableList<OrganizationUser> = mutableListOf(),

    @ManyToMany(mappedBy = "enrolledOrganizations")
    var enrolledUsers: MutableList<User> = mutableListOf()
)
