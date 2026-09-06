package com.github.yonaprojects.yona.config.ssh

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// yona-wiki P3-03 Step4 — 리눅스/맥 AuthorizedKeysCommand 훅(`yona internal ssh-auth`/`ssh-shell`,
// yona-cli 별도 저장소)이 이 서버의 내부 전용 엔드포인트(SshInternalController)를 호출할 때 쓰는
// 공유 시크릿. JwkKeyPairProvider(P3-07)와 동일한 패턴 — 최초 1회 생성해 yona.data 아래에 저장하고
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
        return generated
    }
}
