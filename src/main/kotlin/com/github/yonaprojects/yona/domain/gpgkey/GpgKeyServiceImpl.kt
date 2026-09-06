package com.github.yonaprojects.yona.domain.gpgkey

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class GpgKeyServiceImpl(
    private val gpgKeyRepository: GpgKeyRepository
) : GpgKeyService {

    @Transactional(readOnly = true)
    override fun listByUser(user: User): List<GpgKey> {
        val userId = user.id ?: return emptyList()
        return gpgKeyRepository.findByUserId(userId)
    }

    @Transactional
    override fun create(user: User, armoredPublicKey: String): GpgKey {
        val parsed = GpgPublicKeyParser.parse(armoredPublicKey)

        if (gpgKeyRepository.findByFingerprint(parsed.fingerprint).isPresent) {
            throw IllegalArgumentException("이미 등록된 GPG 키입니다.")
        }

        // 보안 리뷰 항목 — author 이메일과 GPG UID 이메일을 매칭할 때, 그 UID 이메일이 실제로
        // "이 계정 소유로 인증된" 이메일이어야 한다. 그렇지 않으면 다른 사람의 이메일을 UID로
        // 넣은 키를 등록해 그 사람 행세를 하는 커밋에 가짜 Verified 배지를 붙일 수 있다.
        val ownedVerifiedEmails = ownedVerifiedEmailsOf(user)
        val verifiedEmails = parsed.uidEmails.filter { it in ownedVerifiedEmails }.toMutableSet()
        if (verifiedEmails.isEmpty()) {
            throw IllegalArgumentException(
                "이 GPG 키의 User ID에 본인 계정의 인증된 이메일이 없습니다. " +
                    "먼저 이메일 설정에서 해당 이메일을 인증해주세요."
            )
        }

        val gpgKey = GpgKey(
            user = user,
            keyId = parsed.keyId,
            fingerprint = parsed.fingerprint,
            armoredPublicKey = armoredPublicKey.trim(),
            associatedKeyIds = parsed.associatedKeyIds.toMutableSet(),
            verifiedEmails = verifiedEmails,
            createdAt = Instant.now()
        )
        return gpgKeyRepository.save(gpgKey)
    }

    @Transactional
    override fun delete(user: User, gpgKeyId: Long) {
        val gpgKey = gpgKeyRepository.findById(gpgKeyId).orElse(null) ?: return
        if (gpgKey.user?.id != user.id) return
        gpgKeyRepository.delete(gpgKey)
    }

    private fun ownedVerifiedEmailsOf(user: User): Set<String> {
        val emails = mutableSetOf<String>()
        if (user.email.isNotBlank()) {
            emails.add(user.email.lowercase())
        }
        user.emails.filter { it.valid }.forEach { emails.add(it.email.lowercase()) }
        return emails
    }
}
