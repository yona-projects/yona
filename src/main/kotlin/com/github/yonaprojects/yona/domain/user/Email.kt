package com.github.yonaprojects.yona.domain.user

import jakarta.persistence.*

@Entity
@Table(name = "email")
class Email(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var email: String = "",

    var valid: Boolean = false,

    var token: String? = null
) {
    @Transient
    var confirmUrl: String? = null

    fun validate(inputToken: String): Boolean {
        return if (inputToken == token) {
            this.valid = true
            true
        } else {
            false
        }
    }
}
