package dev.yona.domain.user

import org.hibernate.annotations.ColumnDefault
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.Organization
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 사용자이다. v1.6의 테이블 이름은 `n4user`이다. */
@Entity
@Table(
    name = "n4user",
    indexes = [
        Index(name = "ix_n4user_email", columnList = "email"),
        Index(name = "ix_n4user_is_guest", columnList = "is_guest")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_n4user_1", columnNames = ["login_id"]),
        UniqueConstraint(name = "uq_n4user_token", columnNames = ["token"])
    ]
)
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    @Column(name = "login_id")
    var loginId: String? = null,
    var password: String? = null,
    @Column(name = "password_salt")
    var passwordSalt: String? = null,
    var email: String? = null,
    @Column(name = "remember_me")
    @ColumnDefault("false")
    var rememberMe: Boolean? = false,
    @Column(length = 7)
    var state: UserState? = null,
    @Column(name = "last_state_modified_date")
    var lastStateModifiedDate: Date? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    var lang: String? = null,
    var token: String? = null,
    @Column(name = "is_guest")
    @ColumnDefault("false")
    var isGuest: Boolean? = false,
    @Column(name = "english_name")
    var englishName: String? = null,
    @ManyToMany @JoinTable(
        name = "user_enrolled_project",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")],
        indexes = [
            Index(name = "fk_user_enrolled_project_project_02", columnList = "project_id")
        ]
    )
    var enrolledProjects: MutableSet<Project> = mutableSetOf(),
    @ManyToMany @JoinTable(
        name = "user_enrolled_organization",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "organization_id")],
        indexes = [
            Index(name = "fk_user_enrolled_organization_organization_02", columnList = "organization_id")
        ]
    )
    var enrolledOrganizations: MutableSet<Organization> = mutableSetOf()
)

/** 사용자의 보조 이메일이다. `(email, valid)` 조합은 유일하다. */
@Entity
@Table(
    name = "email",
    indexes = [
        Index(name = "ix_email_user_6", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_email_email_valid", columnNames = ["email", "valid"])
    ]
)
class Email(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    var email: String? = null,
    @ColumnDefault("false")
    var valid: Boolean? = false,
    var token: String? = null
)

/** 소셜 로그인과 연결된 인증 정보이다. */
@Entity
@Table(
    name = "user_credential",
    indexes = [
        Index(name = "ix_user_credential_user_id_1", columnList = "user_id")
    ]
)
class UserCredential(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "login_id")
    var loginId: String? = null,
    var email: String? = null,
    var name: String? = null,
    @ColumnDefault("false")
    var active: Boolean? = false,
    @Column(name = "email_validated")
    @ColumnDefault("false")
    var emailValidated: Boolean? = false
)

/** 소셜 로그인 제공자별 연결 계정이다. */
@Entity
@Table(
    name = "linked_account",
    indexes = [
        Index(name = "ix_linked_account_user_credential_1", columnList = "user_credential_id")
    ]
)
class LinkedAccount(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_credential_id")
    var userCredential: UserCredential? = null,
    @Column(name = "provider_user_id")
    var providerUserId: String? = null,
    @Column(name = "provider_key")
    var providerKey: String? = null
)

/** 사용자 설정이다. 현재는 로그인 후 기본 페이지만 가진다. */
@Entity
@Table(
    name = "user_setting",
    indexes = [
        Index(name = "ix_user_setting_user_1", columnList = "user_id")
    ]
)
class UserSetting(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "login_default_page")
    var loginDefaultPage: String? = null
)

/** 이메일 인증 코드이다. */
@Entity
@Table(
    name = "user_verification",
    indexes = [
        Index(name = "ix_user_verification_user_1", columnList = "user_id"),
        Index(name = "ix_user_verification_user_2", columnList = "login_id, verification_code")
    ]
)
class UserVerification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @Column(name = "login_id")
    var loginId: String? = null,
    @Column(name = "verification_code")
    var verificationCode: String? = null,
    var timestamp: Long? = null
)

/** 사이트 관리자이다. */
@Entity
@Table(
    name = "site_admin",
    indexes = [
        Index(name = "ix_site_admin_admin_42", columnList = "admin_id")
    ]
)
class SiteAdmin(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "admin_id")
    var admin: User? = null
)

/** 프로젝트나 조직 안에서 사용자가 가지는 역할이다. `id` 1~7은 `RoleType`과 일치해야 한다. */
@Entity
@Table(name = "role")
class Role(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    @ColumnDefault("false")
    var active: Boolean? = false
)
