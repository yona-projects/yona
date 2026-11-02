package dev.yona.ssh

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.springframework.stereotype.Component
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 시스템 sshd 연동에서 훅 스크립트와 서버가 나누는 공유 비밀 값이다(추가 개발 항목 X-3b, `decisions.md` D-67). 설정(`yona.ssh.relay.internal-secret`)에 있으면 그 값을 쓰고,
 * 없으면 파일(`yona.ssh.relay.internal-secret-path`, 기본 `<데이터>/ssh/internal-secret`)에 한 번 만들어 두고 계속 쓴다. 만든 파일은 소유자만 읽고 쓸 수 있다
 * (훅 스크립트는 운영자가 같은 값을 따로 둔다. 가이드 참고).
 */
@Component
class SshInternalSecret(private val properties: YonaProperties, private val dataDirectory: DataDirectory) {

    /** 비밀 값이다. 처음 읽을 때 정하고 이후에는 같은 값을 돌려준다. */
    val value: String by lazy {
        val relay = properties.ssh.relay
        relay.internalSecret.ifBlank { loadOrCreate(File(relay.internalSecretPath.ifBlank { File(dataDirectory.root(), "ssh/internal-secret").path })) }
    }

    private fun loadOrCreate(file: File): String {
        if (file.isFile) return file.readText().trim()
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(generated)
        // 같은 호스트의 다른 계정이 읽으면 내부 인증 주소를 흉내 낼 수 있으므로 소유자만 읽고 쓰게 한다
        runCatching { java.nio.file.Files.setPosixFilePermissions(file.toPath(), setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)) }
        return generated
    }
}

/**
 * 훅이 키를 인증했을 때 서버가 돌려주고, 강제 명령이 세션이 시작될 때 중계 소켓에 제시하는 인증 표이다(추가 개발 항목 X-3b, `decisions.md` D-67). 중계 소켓은 `yona` 그룹의 계정이면 누구나
 * 연결할 수 있으므로, 소켓에 적힌 사용자 번호를 그대로 믿으면 사칭할 수 있다. 그래서 `v1.<종류>.<번호>.<만료 시각(초)>.<서명>` 꼴로 만들고 서명은 공유 비밀 값의 HMAC-SHA256이다.
 * 표는 세션이 시작되는 즉시 쓰이므로 유효 시간은 짧다([VALID]).
 */
@Component
class SshRelayTokens internal constructor(private val secret: () -> String, private val clock: Clock) {

    @org.springframework.beans.factory.annotation.Autowired
    constructor(secret: SshInternalSecret) : this({ secret.value }, Clock.systemUTC())

    /** [principal]의 표를 만든다. 사용자 키와 배포 키 중 하나여야 한다. */
    fun issue(principal: SshPrincipal): String {
        val (kind, id) = when {
            principal.userId != null -> "user" to principal.userId
            principal.deployKeyId != null -> "deploy" to principal.deployKeyId
            else -> throw IllegalArgumentException("사용자 키나 배포 키여야 한다")
        }
        val body = "v1.$kind.$id.${clock.instant().plus(VALID).epochSecond}"
        return "$body.${sign(body)}"
    }

    /** 표를 확인해 주체를 돌려준다. 서명이 맞지 않거나 유효 시간이 지났거나 모양이 틀리면 `null`이다. 주체의 지문은 알 수 없어 [FINGERPRINT]이다. */
    fun verify(token: String): SshPrincipal? {
        val parts = token.split('.')
        if (parts.size != 5 || parts[0] != "v1") return null
        val body = parts.take(4).joinToString(".")
        if (!MessageDigest.isEqual(sign(body).toByteArray(), parts[4].toByteArray())) return null
        val expires = parts[3].toLongOrNull() ?: return null
        if (clock.instant().epochSecond > expires) return null
        val id = parts[2].toLongOrNull() ?: return null
        return when (parts[1]) {
            "user" -> SshPrincipal(FINGERPRINT, id, null)
            "deploy" -> SshPrincipal(FINGERPRINT, null, id)
            else -> null
        }
    }

    private fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret().toByteArray(), "HmacSHA256")) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray()))
    }

    companion object {
        /** 표의 유효 시간이다. */
        val VALID: Duration = Duration.ofMinutes(5)

        /** 표에는 지문이 없으므로 주체의 지문 자리에 넣는 값이다. */
        const val FINGERPRINT = "relay"
    }
}
