package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.role.RoleType
import jakarta.persistence.*
import java.time.Instant
import java.util.Locale

@Entity
@Table(name = "n4user")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    var englishName: String? = null,

    @Column(nullable = false, unique = true)
    var loginId: String = "",

    var password: String? = null,
    var passwordSalt: String? = null,

    @Column(nullable = false)
    var email: String = "",

    var token: String? = null,

    var rememberMe: Boolean = false,

    @Enumerated(EnumType.STRING)
    var state: UserState = UserState.ACTIVE,

    var lastStateModifiedDate: Instant? = null,

    var createdDate: Instant? = null,

    var lang: String? = null,

    var isGuest: Boolean = false,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projectUsers: MutableList<ProjectUser> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var organizationUsers: MutableList<OrganizationUser> = mutableListOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "user_enrolled_project",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")]
    )
    var enrolledProjects: MutableList<Project> = mutableListOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "user_enrolled_organization",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "organization_id")]
    )
    var enrolledOrganizations: MutableList<Organization> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var emails: MutableList<Email> = mutableListOf()
) {
    fun getPreferredLanguage(): String {
        return lang ?: Locale.getDefault().language
    }

    fun enroll(project: Project) {
        if (!enrolledProjects.contains(project)) {
            enrolledProjects.add(project)
        }
    }

    fun cancelEnroll(project: Project) {
        enrolledProjects.remove(project)
    }

    fun enroll(organization: Organization) {
        if (!enrolledOrganizations.contains(organization)) {
            enrolledOrganizations.add(organization)
        }
    }

    fun cancelEnroll(organization: Organization) {
        enrolledOrganizations.remove(organization)
    }

    fun addEmail(email: Email) {
        if (!emails.contains(email)) {
            emails.add(email)
            email.user = this
        }
    }

    fun removeEmail(email: Email) {
        emails.remove(email)
    }

    fun has(newEmail: String): Boolean {
        return emails.any { it.email == newEmail }
    }

    val isSiteManager: Boolean
        get() = state == UserState.SITE_ADMIN

    fun getDisplayName(): String {
        return name
    }

    fun getDisplayName(forCurrentUser: User): String {
        if (!englishName.isNullOrBlank() && lang != null && (forCurrentUser.lang ?: "").startsWith("en")) {
            return "$englishName ${extractDepartmentPart()}"
        }
        return name
    }

    fun getPureNameOnly(): String {
        var pureName = name
        val spliters = arrayOf("[", "(")
        for (spliter in spliters) {
            if (pureName.contains(spliter)) {
                pureName = pureName.substring(0, pureName.indexOf(spliter)).trim()
            }
        }
        return pureName
    }

    fun getPureNameOnly(targetLang: String?): String {
        if (!englishName.isNullOrBlank() && lang != null && targetLang != null && targetLang.startsWith("en")) {
            return englishName!!
        }
        var pureName = name
        val spliters = arrayOf("[", "(")
        for (spliter in spliters) {
            if (pureName.contains(spliter)) {
                pureName = pureName.substring(0, pureName.indexOf(spliter)).trim()
            }
        }
        return pureName
    }

    fun extractDepartmentPart(): String {
        var departmentName = name
        val spliters = arrayOf("[", "(")
        for (spliter in spliters) {
            if (departmentName.contains(spliter)) {
                departmentName = name.substring(name.indexOf(spliter))
                break
            }
        }
        return departmentName
    }

    @Transient
    var avatarId: Long? = null

    val avatarUrl: String
        get() = avatarUrl(64)

    fun avatarUrl(size: Int): String {
        return avatarId?.let { "/files/$it" } ?: "/assets/images/default-avatar-128.png"
    }

    fun isMemberOf(project: Project): Boolean {
        return projectUsers.any { it.project.id == project.id }
    }

    fun isManagerOf(project: Project): Boolean {
        return projectUsers.any { it.project.id == project.id && it.role.id == RoleType.MANAGER.roleType }
    }
}
