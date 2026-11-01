package dev.yona.domain.gpgkey

import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 키 등록 결과이다. */
sealed interface GpgKeyAddition {
    /** 등록했다. [key]는 주 키 행이다. */
    data class Added(val key: GpgKey) : GpgKeyAddition

    /** 거절했다. [code]는 사용자에게 보일 메시지 키(`gpgKey.error.*`)이다. */
    data class Rejected(val code: String) : GpgKeyAddition
}

/**
 * GPG 공개키 등록 서비스이다(추가 개발 항목 X-4, `decisions.md` D-65). 읽기와 정책은 [GpgKeyParser]가, 저장과 소유 규칙은 여기서 맡는다.
 * 키는 사용자가 추가하고 삭제만 할 수 있다(편집 없음, D-65 8번).
 */
@Service
class GpgKeyService(private val keys: GpgKeyRepository) {

    companion object {
        /** 사용자당 키 최대 개수이다(주 키 기준). */
        const val MAX_KEYS = 50

        const val NAME_MAX = 100
    }

    /**
     * 공개키를 등록한다. 읽을 수 없거나 정책에 맞지 않으면, 이름이 100자를 넘으면, 사용자당 한도를 넘으면, 주 키나 하위 키의 지문이 이미 등록되어 있으면 거절한다.
     *
     * @param name 이름. 비우면 키의 첫 이메일(없으면 첫 사용자 ID)로 정한다
     */
    @Transactional
    fun add(user: User, name: String?, armored: String, now: Date = Date()): GpgKeyAddition {
        val given = name?.trim().orEmpty()
        if (given.length > NAME_MAX) return GpgKeyAddition.Rejected("gpgKey.error.name")
        val parsed = try {
            GpgKeyParser.parse(armored, now.toInstant())
        } catch (e: GpgKeyException) {
            return GpgKeyAddition.Rejected("gpgKey.error.${e.code}")
        }
        val userId = requireNotNull(user.id)
        if (keys.countByUserIdAndParentIsNull(userId) >= MAX_KEYS) return GpgKeyAddition.Rejected("gpgKey.error.limit")
        if (keys.existsByFingerprintIn((listOf(parsed.primary) + parsed.subkeys).map { it.fingerprint })) return GpgKeyAddition.Rejected("gpgKey.error.duplicate")
        val primary = parsed.primary
        val saved = keys.save(
            GpgKey(
                user = user, name = (given.ifEmpty { primary.emails.firstOrNull() ?: primary.userIds.first() }).take(NAME_MAX), fingerprint = primary.fingerprint, keyId = primary.keyId,
                publicKey = parsed.armored, emails = primary.emails.joinToString(","), canSign = primary.canSign, createdDate = now, expiresDate = primary.expiresAt?.let { Date.from(it) }
            )
        )
        parsed.subkeys.forEach {
            keys.save(GpgKey(user = user, parent = saved, fingerprint = it.fingerprint, keyId = it.keyId, canSign = it.canSign, createdDate = now, expiresDate = it.expiresAt?.let { at -> Date.from(at) }))
        }
        return GpgKeyAddition.Added(saved)
    }

    /** 사용자의 키(주 키 행)를 최근에 등록한 순서로 읽는다. */
    @Transactional(readOnly = true)
    fun list(user: User): List<GpgKey> = keys.findByUserIdAndParentIsNullOrderByCreatedDateDescIdDesc(requireNotNull(user.id))

    /** 사용자 자신의 주 키 행 하나를 읽는다. 없거나 다른 사용자의 것이거나 하위 키 행이면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(user: User, id: Long): GpgKey? = keys.findById(id).orElse(null)?.takeIf { it.user?.id == user.id && it.parent == null }

    /** 주 키 행의 하위 키 행들을 읽는다. */
    @Transactional(readOnly = true)
    fun subkeys(primary: GpgKey): List<GpgKey> = keys.findByParentIdOrderById(requireNotNull(primary.id))

    /** 키를 지운다. 하위 키 행도 함께 지운다. 없거나 다른 사용자의 키이거나 하위 키 행이면 지우지 않고 `false`이다. */
    @Transactional
    fun delete(user: User, id: Long): Boolean {
        val key = find(user, id) ?: return false
        keys.deleteByParentId(requireNotNull(key.id))
        keys.delete(key)
        return true
    }

    /**
     * 키 ID(주 키 또는 하위 키)로 등록된 키 행을 찾는다. 서명의 발행 키 ID로 소유자를 알아낼 때 쓴다. 8자리 짧은 ID는 충돌하므로 16자리만 받는다.
     */
    @Transactional(readOnly = true)
    fun findByKeyId(keyId: String): List<GpgKey> = if (keyId.length == 16) keys.findByKeyId(keyId.uppercase()) else emptyList()
}
