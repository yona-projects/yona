package com.github.yonaprojects.yona.domain.user

import jakarta.persistence.*

// yona models/UserSetting.java 대응 (P2-11). 로그인 후 이동할 "기본 페이지"를 사용자별로 기억한다.
@Entity
@Table(name = "user_setting")
class UserSetting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,

    var loginDefaultPage: String? = null
)
