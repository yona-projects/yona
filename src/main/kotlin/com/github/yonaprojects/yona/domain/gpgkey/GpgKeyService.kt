package com.github.yonaprojects.yona.domain.gpgkey

import com.github.yonaprojects.yona.domain.user.User

interface GpgKeyService {
    fun listByUser(user: User): List<GpgKey>

    // 실패 시 IllegalArgumentException(중복 등록, 계정 소유 인증 이메일과 UID 불일치) 또는
    // GpgPublicKeyParser.InvalidGpgKeyException을 던진다.
    fun create(user: User, armoredPublicKey: String): GpgKey

    fun delete(user: User, gpgKeyId: Long)
}
