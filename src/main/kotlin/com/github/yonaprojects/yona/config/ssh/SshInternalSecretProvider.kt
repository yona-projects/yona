package com.github.yonaprojects.yona.config.ssh

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// 리눅스/맥 AuthorizedKeysCommand 훅(`yona internal ssh-auth`/`ssh-shell`,
// yona-cli 별도 저장소)이 이 서버의 내부 전용 엔드포인트(SshInternalController)를 호출할 때 쓰는
// 공유 시크릿. JwkKeyPairProvider와 동일한 패턴 — 최초 1회 생성해 yona.data 아래에 저장하고
// 재시작마다 재사용한다. 이 엔드포인트는 시스템 sshd가 같은 호스트에서 로컬 프로세스로 호출하는
// 것을 전제로 하므로(설계 문서 참고), 루프백 주소 제한(SshInternalController)과 이 시크릿 둘 다
// 요구해 이중으로 방어한다.
@Component
class SshInternalSecretProvider(
    @Value("\${yona.ssh.internal-secret:}")
    private val configuredSecret: String,
    @Value("\${yona.ssh.internal-secret-path:\${yona.data:data}/ssh/internal-secret}")
    private val secretPath: String
) {
    val secret: String by lazy { configuredSecret.takeIf { it.isNotBlank() } ?: loadOrGenerate() }

    private fun loadOrGenerate(): String {
        val file = File(secretPath)
        if (file.exists()) {
            return file.readText().trim()
        }

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        file.parentFile?.mkdirs()
        file.writeText(generated)
        // 이 파일을 같은 호스트의 다른 로컬 사용자가 읽을 수
        // 있으면 SshInternalController의 루프백 제한을 우회해 임의 SSH 공개키로 인증할 수 있다.
        // 소유자 전용으로 제한한다(File API가 chmod 0600과 동일하게 동작 — Windows에서는 무시되고
        // 조용히 false를 반환할 뿐 예외는 없음, 이 파일 자체가 리눅스/맥 SSH 훅 전용이라 문제 없음).
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        return generated
    }
}
