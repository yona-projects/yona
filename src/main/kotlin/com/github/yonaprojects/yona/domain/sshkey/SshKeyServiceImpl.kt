package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SshKeyServiceImpl(
    private val sshKeyRepository: SshKeyRepository,
    // 보안 리뷰 지적 4번 — DeployKey(저장소 스코프)와 전역적으로 지문이 겹치지 않아야 계정/저장소
    // 사칭을 막을 수 있다(DeployKeyServiceImpl.create()도 동일하게 SshKeyRepository를 검사한다).
    private val deployKeyRepository: DeployKeyRepository
) : SshKeyService {

    @Transactional(readOnly = true)
    override fun listByUser(user: User): List<SshKey> {
        val userId = user.id ?: return emptyList()
        return sshKeyRepository.findByUserId(userId)
    }

    @Transactional
    override fun create(user: User, title: String, rawPublicKey: String): SshKey {
        require(title.isNotBlank()) { "이름은 필수입니다." }

        val parsed = SshPublicKeyFingerprint.parse(rawPublicKey)

        if (sshKeyRepository.findByFingerprint(parsed.fingerprint).isPresent) {
            throw IllegalArgumentException("이미 등록된 공개키입니다.")
        }
        if (deployKeyRepository.findByFingerprint(parsed.fingerprint).isPresent) {
            throw IllegalArgumentException("이미 등록된 공개키입니다.")
        }

        val sshKey = SshKey(
            user = user,
            title = title.trim(),
            publicKey = rawPublicKey.trim(),
            fingerprint = parsed.fingerprint,
            createdAt = Instant.now()
        )
        return sshKeyRepository.save(sshKey)
    }

    @Transactional
    override fun delete(user: User, sshKeyId: Long) {
        val sshKey = sshKeyRepository.findById(sshKeyId).orElse(null) ?: return
        if (sshKey.user?.id != user.id) return
        sshKeyRepository.delete(sshKey)
    }
}
