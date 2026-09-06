package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Paths

/**
 * yona-wiki P3-03 Step5 — 윈도우 SSH 폴백. 시스템 OpenSSH의 AuthorizedKeysCommand 훅(Step4, 리눅스/
 * 맥 전용 — sshd_config를 고쳐야 해서 윈도우에서는 쓸 수 없음)을 대체해, 이 애플리케이션 프로세스가
 * JVM 안에서 직접 별도 포트(기본 2222)에 SSH 서버를 띄운다. 시스템 sshd/포트 22와는 완전히
 * 무관하다 — 호스트 시스템을 전혀 건드리지 않는다.
 *
 * `yona.ssh.mina.enabled`(기본 "auto" — os.name에 "windows"가 포함되면 자동 활성화, "true"/"false"로
 * 강제 지정 가능. 통합테스트가 리눅스 CI에서도 이 경로를 실제로 검증해야 하므로 "true"로 강제한다)로
 * 제어한다.
 */
@Component
final class YonaMinaSshServer(
    private val sshAuthService: SshAuthService,
    // 코디네이터 push 전 리뷰(2026-09-07) — YonaSshGitCommand가 BranchProtectionPreReceiveHook을
    // HTTPS 경로와 동일하게 체이닝하는 데 필요하다.
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — YonaSshGitCommand가 BranchProtectionPreReceiveHook의
    // require_signed_commits 검사에 필요한 GpgSignatureVerifier를 HTTPS 경로와 동일하게 전달한다.
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    @Value("\${yona.ssh.mina.enabled:auto}")
    private val enabledSetting: String,
    @Value("\${yona.ssh.mina.port:2222}")
    private val configuredPort: Int,
    @Value("\${yona.ssh.mina.host-key-path:\${yona.data:data}/ssh/host_ed25519_key}")
    private val hostKeyPath: String
) {
    private val logger = LoggerFactory.getLogger(YonaMinaSshServer::class.java)

    private var server: SshServer? = null

    // 실제 바인딩된 포트(configuredPort=0이면 OS가 임의 포트를 골라준다 — 테스트가 이 값을 읽어
    // 그 포트로 접속한다).
    var boundPort: Int = -1
        private set

    val isEnabled: Boolean
        get() = when (enabledSetting.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> System.getProperty("os.name", "").lowercase().contains("win")
        }

    @PostConstruct
    fun start() {
        if (!isEnabled) {
            logger.info("YonaMinaSshServer disabled (yona.ssh.mina.enabled={}, os.name={})", enabledSetting, System.getProperty("os.name"))
            return
        }

        val hostKeyFile = File(hostKeyPath)
        hostKeyFile.parentFile?.mkdirs()

        val sshServer = SshServer.setUpDefaultServer()
        sshServer.port = configuredPort
        sshServer.keyPairProvider = SimpleGeneratorHostKeyProvider(Paths.get(hostKeyFile.absolutePath))
        sshServer.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { _, key, session ->
            val fingerprint = KeyUtils.getFingerPrint(key)
            val principal = sshAuthService.authenticateByFingerprint(fingerprint)
            if (principal != null) {
                session.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
                true
            } else {
                false
            }
        }
        sshServer.commandFactory = org.apache.sshd.server.command.CommandFactory { channel, command ->
            val principal = channel.session.getAttribute(PRINCIPAL_ATTRIBUTE)
                ?: throw java.io.IOException("인증되지 않은 세션입니다.")
            YonaSshGitCommand(
                command, principal, sshAuthService, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
            )
        }

        sshServer.start()
        boundPort = sshServer.port
        server = sshServer
        logger.info("YonaMinaSshServer started on port {}", boundPort)
    }

    @PreDestroy
    fun stop() {
        server?.stop(true)
        server = null
    }

    companion object {
        private val PRINCIPAL_ATTRIBUTE: AttributeRepository.AttributeKey<com.github.yonaprojects.yona.domain.sshkey.SshAuthPrincipal> =
            AttributeRepository.AttributeKey()
    }
}
