package com.github.yonaprojects.yona.domain.user

import jakarta.persistence.*

/**
 * yona의 models/LinkedAccount.java 대응(간소화).
 * yona는 UserCredential이라는 별도 자격증명 계층을 두고 그 아래 여러
 * LinkedAccount를 붙이는 play-authenticate 플러그인 구조였지만, yona는
 * Spring Security OAuth2가 이미 자격증명 계층을 관리하므로 User에 직접
 * (providerKey, providerUserId)를 연결하는 단순한 구조로 이식했다.
 */
@Entity
@Table(
    name = "linked_account",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider_key", "provider_user_id"])]
)
class LinkedAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "provider_key", nullable = false)
    var providerKey: String = "",

    @Column(name = "provider_user_id", nullable = false)
    var providerUserId: String = ""
)
