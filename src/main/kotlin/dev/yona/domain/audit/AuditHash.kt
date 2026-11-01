package dev.yona.domain.audit

import java.security.MessageDigest

/**
 * 감사 기록의 해시이다(`decisions.md` D-62). `SHA-256(버전 + 이전 해시 + 정규화한 필드)`를 16진수 64자로 쓴다. 필드는 제어 문자(`U+001F`)로 잇고 `null`은 빈 글이다.
 * 기록의 어느 필드든 바뀌거나 앞 기록이 바뀌면 해시가 달라진다. 필드를 더하면 버전 글자를 올려 옛 기록이 옛 규칙으로 검증되게 해야 한다.
 */
object AuditHash {

    /** 사슬의 시작이다. 첫 기록의 이전 해시이다. */
    val GENESIS: String = "0".repeat(64)

    private const val VERSION = "v1"
    private const val SEPARATOR = '\u001F'

    /** [log]의 `hash`를 뺀 모든 필드로 해시를 계산한다. */
    fun of(log: AuditLog): String {
        val fields = listOf(
            VERSION, log.id, log.createdMillis, log.actorType.name, log.actorId, log.actorLogin, log.action, log.outcome.name, log.targetType, log.targetId, log.targetLabel,
            log.organizationId, log.projectId, log.ip, log.userAgent, log.requestId, log.tokenId, log.tokenScopes, log.details, log.prevHash
        )
        val canonical = fields.joinToString(SEPARATOR.toString()) { it?.toString().orEmpty() }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
