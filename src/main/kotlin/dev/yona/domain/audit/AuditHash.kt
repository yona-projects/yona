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

    /**
     * 이 기록이 가져야 할 해시이다. 내용 해시가 있는 기록(v2)은 `SHA-256(이전 해시 + 내용 해시)`이라 개인정보를 비운 뒤에도 같다. 옛(v1) 기록은 모든 필드로 계산한다.
     */
    fun of(log: AuditLog): String = log.contentHash?.let { chain(log.prevHash, it) } ?: legacy(log)

    /** v2의 사슬 해시이다: 이전 해시와 내용 해시를 이은 SHA-256이다. */
    fun chain(prevHash: String, contentHash: String): String = sha256(listOf("v2", prevHash, contentHash).joinToString(SEPARATOR.toString()))

    /**
     * 기록의 내용(`prev_hash`와 `hash`, `content_hash`, `redacted_at`을 뺀 모든 필드)의 해시이다. 기록할 때 한 번 정해 저장한다. 개인정보를 비운 기록은 내용을 다시 계산할 수 없고
     * (보관 파일에는 비우기 전 내용이 있다) 저장된 값을 쓴다.
     */
    fun contentOf(log: AuditLog): String {
        val fields = listOf(
            "c2", log.id, log.createdMillis, log.actorType.name, log.actorId, log.actorLogin, log.action, log.outcome.name, log.targetType, log.targetId, log.targetLabel,
            log.organizationId, log.projectId, log.ip, log.userAgent, log.requestId, log.tokenId, log.tokenScopes, log.details
        )
        return sha256(fields.joinToString(SEPARATOR.toString()) { it?.toString().orEmpty() })
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** 내용 해시가 없는 옛(v1) 기록의 해시이다: [log]의 `hash`를 뺀 모든 필드로 계산한다. */
    private fun legacy(log: AuditLog): String {
        val fields = listOf(
            VERSION, log.id, log.createdMillis, log.actorType.name, log.actorId, log.actorLogin, log.action, log.outcome.name, log.targetType, log.targetId, log.targetLabel,
            log.organizationId, log.projectId, log.ip, log.userAgent, log.requestId, log.tokenId, log.tokenScopes, log.details, log.prevHash
        )
        val canonical = fields.joinToString(SEPARATOR.toString()) { it?.toString().orEmpty() }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
