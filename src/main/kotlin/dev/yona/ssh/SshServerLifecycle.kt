package dev.yona.ssh

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * 내장 SSH 서버(Apache MINA SSHD)의 시작과 종료이다(추가 개발 항목 X-3, `decisions.md` D-66). `yona.ssh.enabled=true`일 때만 만들어지고, 꺼져 있으면 포트를 열지 않는다.
 *
 * 인증은 공개키뿐이다(비밀번호, 키보드 입력, 호스트 기반 인증은 열지 않는다). 접속 사용자 이름은 모두 `yona.ssh.user`(기본 `git`)이고, 키의 지문으로 사용자나 배포 키를 알아낸다([SshAccess]).
 * 셸, SFTP, 포트 전달은 열지 않으며 저장소 명령(`git-upload-pack`, `git-receive-pack`)만 [SshVcsHandler]가 처리한다. 호스트 키는 데이터 디렉터리의
 * `ssh/ssh_host_ed25519_key`에 처음 시작할 때 만들어 두고 계속 쓴다(재시작해도 같아야 클라이언트가 호스트 키 변경 경고를 내지 않는다).
 */
@Component
@ConditionalOnProperty(prefix = "yona.ssh", name = ["enabled"], havingValue = "true")
class SshServerLifecycle(
    private val properties: YonaProperties,
    private val dataDirectory: DataDirectory,
    private val access: SshAccess,
    private val handlers: List<SshVcsHandler>
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(javaClass)
    private var server: SshServer? = null
    private var executor: ExecutorService? = null

    /** 실제로 받는 포트이다. 설정이 0이면 운영체제가 고른 포트이다. 시작하기 전에는 0이다. */
    val boundPort: Int get() = server?.port ?: 0

    /** 호스트 키의 지문(`SHA256:...`)이다. 사용자가 처음 접속할 때 확인하는 값이며 시작하기 전에는 `null`이다. */
    val hostKeyFingerprint: String? get() = fingerprint

    @Volatile private var fingerprint: String? = null

    override fun isRunning(): Boolean = server?.isStarted == true

    override fun start() {
        val ssh = properties.ssh
        val provider = hostKeyProvider(ssh)
        val sshServer = SshServer.setUpDefaultServer().apply {
            port = ssh.port
            if (ssh.bindAddress.isNotBlank()) host = ssh.bindAddress
            keyPairProvider = provider
            userAuthFactories = listOf(UserAuthPublicKeyFactory.INSTANCE)
            publickeyAuthenticator = PublickeyAuthenticator { username, key, session -> authenticate(ssh.user, username, key, session) }
            commandFactory = CommandFactory { channel, line -> RepositoryCommand(line, channel) }
            forwardingFilter = RejectAllForwardingFilter.INSTANCE
        }
        executor = Executors.newCachedThreadPool(NamedThreads("yona-ssh"))
        sshServer.start()
        server = sshServer
        fingerprint = provider.loadKeys(null).firstOrNull()?.let { KeyUtils.getFingerPrint(it.public) }
        log.info("SSH server started on port {} (host key {})", sshServer.port, fingerprint)
    }

    override fun stop() {
        server?.stop(true)
        executor?.shutdownNow()
        server = null
        executor = null
    }

    private fun hostKeyProvider(ssh: YonaProperties.Ssh): SimpleGeneratorHostKeyProvider {
        val path = ssh.hostKeyPath.ifBlank { File(dataDirectory.root(), "ssh/$HOST_KEY_NAME").path }
        File(path).absoluteFile.parentFile.mkdirs()
        return SimpleGeneratorHostKeyProvider(File(path).toPath()).apply {
            if (SecurityUtils.isEDDSACurveSupported()) {
                algorithm = SecurityUtils.EDDSA
            } else {
                algorithm = KeyUtils.RSA_ALGORITHM
                keySize = 3072
            }
        }
    }

    private fun authenticate(expectedUser: String, username: String, key: java.security.PublicKey, session: org.apache.sshd.server.session.ServerSession): Boolean {
        if (username != expectedUser) return false
        val principal = access.authenticate(KeyUtils.getFingerPrint(key)) ?: return false
        session.setAttribute(PRINCIPAL, principal)
        return true
    }

    /** 명령 하나를 별도 스레드에서 실행한다. 인가를 통과하면 [SshVcsHandler]가 프로토콜을 처리한다. */
    private inner class RepositoryCommand(private val line: String, private val channel: ChannelSession) : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private lateinit var error: OutputStream
        private lateinit var callback: ExitCallback
        private var task: Future<*>? = null

        override fun setInputStream(input: InputStream) { this.input = input }
        override fun setOutputStream(out: OutputStream) { this.output = out }
        override fun setErrorStream(err: OutputStream) { this.error = err }
        override fun setExitCallback(callback: ExitCallback) { this.callback = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            task = checkNotNull(executor).submit {
                var code = 1
                try {
                    code = execute()
                } catch (e: Throwable) {
                    log.error("SSH command failed", e)
                    runCatching { error.write("ERROR: Internal error.\n".toByteArray()); error.flush() }
                } finally {
                    callback.onExit(code)
                }
            }
        }

        override fun destroy(channel: ChannelSession) { task?.cancel(true) }

        private fun execute(): Int {
            val principal = channel.session.getAttribute(PRINCIPAL) ?: return fail("ERROR: Not authenticated.")
            val (handler, request) = handlers.firstNotNullOfOrNull { h -> h.parse(line)?.let { h to it } } ?: return fail("ERROR: Invalid command.")
            return when (val result = access.authorize(principal, request)) {
                SshAuthorization.NotFound -> fail("ERROR: Repository not found.")
                is SshAuthorization.Denied -> fail(result.message)
                is SshAuthorization.Allowed -> handler.execute(result.project, request, line, result.sender, SshStreams(input, output, error))
            }
        }

        private fun fail(message: String): Int {
            error.write("$message\n".toByteArray()); error.flush()
            return 1
        }
    }

    private class NamedThreads(private val prefix: String) : java.util.concurrent.ThreadFactory {
        private val index = AtomicInteger()
        override fun newThread(r: Runnable): Thread = Thread(r, "$prefix-${index.incrementAndGet()}").apply { isDaemon = true }
    }

    companion object {
        const val HOST_KEY_NAME = "ssh_host_ed25519_key"

        private val PRINCIPAL = AttributeRepository.AttributeKey<SshPrincipal>()
    }
}
