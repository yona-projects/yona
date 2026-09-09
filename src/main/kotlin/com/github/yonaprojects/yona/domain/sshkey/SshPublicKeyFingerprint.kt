package com.github.yonaprojects.yona.domain.sshkey

import java.security.MessageDigest
import java.util.Base64

// OpenSSH authorized_keys 한 줄 형식("ssh-ed25519 AAAA... comment")을
// 파싱해 GitHub과 동일한 형태의 지문("SHA256:<base64, 패딩 없음>")을 계산한다. DeployKey(저장소
// 스코프)와 SshKey(사용자 전역) 양쪽이 동일한 파싱/지문 로직을 공유해야 "같은 공개키가 이미 등록돼
// 있는지" 교차 검사(계정 사칭 방지, 아래 SshPublicKeyRegistry 참고)를 할 수 있다.
object SshPublicKeyFingerprint {

    private val SUPPORTED_KEY_TYPES = setOf(
        "ssh-rsa", "ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521"
    )

    class InvalidPublicKeyException(message: String) : IllegalArgumentException(message)

    data class ParsedPublicKey(
        val keyType: String,
        val base64Blob: String,
        val comment: String?,
        val fingerprint: String
    )

    // 실패 시 InvalidPublicKeyException을 던진다 — 호출부(서비스 계층)가 사용자 입력 오류로
    // 변환해 폼에 에러 메시지를 표시할 수 있게 한다.
    fun parse(rawLine: String): ParsedPublicKey {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            throw InvalidPublicKeyException("공개키를 입력해주세요.")
        }
        // authorized_keys는 옵션 필드를 앞에 붙일 수 있지만(예: "command=... ssh-rsa ..."), 이
        // 화면은 사용자가 `~/.ssh/id_ed25519.pub` 등을 그대로 붙여넣는 것만 지원한다(GitHub 방식과
        // 동일 — GitHub도 옵션 필드가 붙은 키는 거부한다).
        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) {
            throw InvalidPublicKeyException("올바른 SSH 공개키 형식이 아닙니다.")
        }
        val keyType = parts[0]
        val base64Blob = parts[1]
        val comment = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

        if (keyType !in SUPPORTED_KEY_TYPES) {
            throw InvalidPublicKeyException("지원하지 않는 키 타입입니다: $keyType")
        }

        val decoded = try {
            Base64.getDecoder().decode(base64Blob)
        } catch (e: IllegalArgumentException) {
            throw InvalidPublicKeyException("공개키 인코딩이 올바르지 않습니다.")
        }
        if (decoded.isEmpty()) {
            throw InvalidPublicKeyException("공개키 데이터가 비어 있습니다.")
        }

        return ParsedPublicKey(
            keyType = keyType,
            base64Blob = base64Blob,
            comment = comment,
            fingerprint = fingerprintOf(decoded)
        )
    }

    private fun fingerprintOf(decoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }
}
