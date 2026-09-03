package com.github.yonaprojects.yona.domain.user

import jakarta.persistence.Embeddable

@Embeddable
class UserIdent(
    var id: Long? = null,
    var loginId: String? = null,
    var name: String? = null
) {
    constructor(user: User) : this(user.id, user.loginId, user.name)
}
