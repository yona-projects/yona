package com.github.yonaprojects.yona.config.webauthn

import org.springframework.security.web.webauthn.api.Bytes
import java.nio.ByteBuffer

// Spring Security의 WebAuthn 모델은 사용자를 8~64바이트의 불투명한 "user handle"(Bytes)로
// 식별한다. yona는 이미 User.id(Long)가 있으므로 별도 매핑 테이블 없이 8바이트 빅엔디언으로
// 결정론적으로 변환한다 — User가 이미 존재하는 한 이 handle은 항상 재계산 가능하고 별도 저장이
// 필요 없다(UserWebauthnEntityRepositoryAdapter.save()가 no-op인 이유).
object WebauthnUserHandle {
    fun encode(userId: Long): Bytes = Bytes(ByteBuffer.allocate(8).putLong(userId).array())

    fun decode(bytes: Bytes): Long? {
        val raw = bytes.bytes
        if (raw.size != 8) return null
        return ByteBuffer.wrap(raw).long
    }
}
