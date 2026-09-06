package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.domain.user.User

interface SshKeyService {
    fun listByUser(user: User): List<SshKey>

    // 실패 시 IllegalArgumentException(이름 누락, 중복 키) 또는
    // SshPublicKeyFingerprint.InvalidPublicKeyException을 던진다.
    fun create(user: User, title: String, rawPublicKey: String): SshKey

    // user 본인 소유가 아니면 아무 일도 하지 않는다(다른 사용자의 키 id를 추측해도 삭제 불가).
    fun delete(user: User, sshKeyId: Long)
}
