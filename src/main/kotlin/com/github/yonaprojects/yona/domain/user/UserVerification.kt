package com.github.yonaprojects.yona.domain.user

import jakarta.persistence.*

@Entity
@Table(name = "user_verification")
class UserVerification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var loginId: String = "",

    @Column(nullable = false)
    var verificationCode: String = "",

    @Column(nullable = false)
    var timestamp: Long = 0L
) {
    fun isValidDate(): Boolean {
        // 생성일 + 24시간 동안 유효 (1일)
        return (timestamp + 24 * 60 * 60 * 1000) > System.currentTimeMillis()
    }
}
