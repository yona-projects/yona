package com.github.yonaprojects.yona.domain.gpgkey

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

// GitHub "Settings > SSH and GPG keys" 화면의 GPG 키 섹션과 동일한 사용자 전역 GPG 공개키.
// 로그인 인증 수단이 아니라 커밋 서명 검증 전용이다.
@Entity
@Table(name = "gpg_key")
class GpgKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    // 마스터 키의 Key ID(16진수 16자리). GitHub 화면의 "Key ID" 컬럼과 동일.
    @Column(name = "key_id", nullable = false, length = 32)
    var keyId: String = "",

    // 마스터 키 지문 — 전역적으로 유일해야 한다(같은 GPG 키를 여러 계정이 등록해 "누구 것인지"가
    // 모호해지는 것을 방지, GitHub도 동일 정책).
    @Column(name = "fingerprint", nullable = false, unique = true, length = 64)
    var fingerprint: String = "",

    @Column(name = "armored_public_key", nullable = false, columnDefinition = "TEXT")
    var armoredPublicKey: String = "",

    // 마스터 키 + 서명 서브키를 포함한 모든 구성 키의 Key ID. 커밋 서명의 issuer key id로 이
    // GpgKey를 찾기 위한 인덱스 역할(GpgKeyRepository.findByAssociatedKeyIdsContaining 참고).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gpg_key_associated_key_id", joinColumns = [JoinColumn(name = "gpg_key_id")])
    @Column(name = "key_id", length = 32)
    var associatedKeyIds: MutableSet<String> = mutableSetOf(),

    // 이 키의 UID 이메일 중 "이 계정의 인증된 이메일과 실제로 일치하는" 것만 저장한다(등록
    // 시점에 검증) — 커밋 author 이메일 매칭에는 이 목록만 쓴다. 키 자체에 다른 사람의 이메일이
    // UID로 박혀 있어도(위조 가능) 그 이메일이 이 계정 소유로 인증되지 않았다면 매칭 대상이
    // 아니므로 계정 사칭에 악용될 수 없다(보안 리뷰 반영).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gpg_key_verified_email", joinColumns = [JoinColumn(name = "gpg_key_id")])
    @Column(name = "email", length = 255)
    var verifiedEmails: MutableSet<String> = mutableSetOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
