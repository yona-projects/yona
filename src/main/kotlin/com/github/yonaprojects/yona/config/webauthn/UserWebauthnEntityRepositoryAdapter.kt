package com.github.yonaprojects.yona.config.webauthn

import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.stereotype.Component

// Webauthn4JRelyingPartyOperations가 등록/인증 세리모니 중 사용자를 조회하는 데 쓰는 어댑터.
// findByUsername()이 항상 기존 User로 해석되므로(우리 흐름은 이미 로그인된 사용자의 등록이거나,
// 비밀번호 1차 인증을 통과한 계정의 2FA 검증뿐 — 새 계정을 여기서 만들 일이 없다) save()/delete()는
// 호출될 일이 없어 no-op으로 둔다(WebauthnUserHandle이 User.id에서 결정론적으로 파생되므로 별도
// 영속화할 것도 없다).
@Component
class UserWebauthnEntityRepositoryAdapter(
    private val userRepository: UserRepository
) : PublicKeyCredentialUserEntityRepository {

    override fun findById(id: Bytes): PublicKeyCredentialUserEntity? {
        val userId = WebauthnUserHandle.decode(id) ?: return null
        val user = userRepository.findById(userId).orElse(null) ?: return null
        return toEntity(user.id!!, user.loginId, user.name)
    }

    override fun findByUsername(username: String): PublicKeyCredentialUserEntity? {
        val user = userRepository.findByLoginId(username).orElse(null) ?: return null
        return toEntity(user.id!!, user.loginId, user.name)
    }

    override fun save(userEntity: PublicKeyCredentialUserEntity) {
        // 위 클래스 주석 참고 — 이 흐름에서는 호출되지 않는다.
    }

    override fun delete(id: Bytes) {
        // 위 클래스 주석 참고 — 이 흐름에서는 호출되지 않는다.
    }

    private fun toEntity(userId: Long, loginId: String, name: String): PublicKeyCredentialUserEntity =
        ImmutablePublicKeyCredentialUserEntity.builder()
            .id(WebauthnUserHandle.encode(userId))
            .name(loginId)
            .displayName(name)
            .build()
}
