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
    var emails: MutableList<Email> = mutableListOf(),

    // 2FA(TOTP/WebAuthn) 등록 여부 요약 캐시. 실제 등록 정보는 user_totp_credential/
    // user_webauthn_credential/user_backup_code 세 테이블에 있고(ssh_key처럼 User와 분리된
    // 1:N 테이블), 이 필드는 관리자 목록 등에서 조인 없이 빠르게 보여주기 위한 캐시일 뿐이다.
    // 로그인 게이트처럼 보안에 민감한 판단은 이 캐시가 아니라 TwoFactorService가 실제 테이블을
    // 조회해 판단한다(캐시 드리프트가 로그인 우회로 이어지지 않도록). 기존 채워진 테이블에 컬럼을
    // 추가하는 것이라 여러 DB 방언에서 안전하게 걸리도록 nullable로 두고(NOT NULL 추가 시
    // 방언별 DEFAULT 처리 차이를 피함), null은 false로 취급한다(hasTwoFactorEnabled() 참고).
    @Column(name = "is_two_factor_enabled")
    var isTwoFactorEnabled: Boolean? = false,

    // 브루트포스 방어용 자동/일시 잠금 — 기존 UserState.LOCKED(관리자가 수동으로 거는 영구
    // 잠금)와는 별개 축이다. 이 두 필드는 상태를 바꾸지 않고, 로그인 성공 시 항상 0/null로
    // 리셋된다. 로그인 게이트는 YonaAuthenticationProvider가 두 축을 순서대로(관리자 잠금
    // 우선) 확인한다.
    var failedLoginAttempts: Int = 0,
    var lockedUntil: Instant? = null
) {
    fun hasTwoFactorEnabled(): Boolean = isTwoFactorEnabled == true
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
