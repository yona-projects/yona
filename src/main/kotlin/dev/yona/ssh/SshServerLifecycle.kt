package dev.yona.ssh

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.server.session.ServerSession
import java.net.InetSocketAddress
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
    private val handlers: List<SshVcsHandler>,
    private val failures: dev.yona.security.TokenFailureLimiter,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val boundary: dev.yona.domain.audit.AuditBoundary,
    private val metrics: dev.yona.observability.VcsMetrics,
    private val users: dev.yona.domain.user.UserRepository,
    private val deployKeys: dev.yona.domain.sshkey.DeployKeyService
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
            addSessionListener(object : SessionListener {
                override fun sessionClosed(session: Session) = reportFailedAuthentication(session)
            })
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

    /**
     * 공개키 인증이다. 같은 주소가 인증에 연달아 실패해 막혀 있으면 거부한다([dev.yona.security.TokenFailureLimiter], HTTP 토큰 인증과 같은 한도). 접속 하나에서 키를 여러 개 시도해도
     * 실패는 한 번만 센다. 접속이 인증 없이 끝나면 인증 실패로 기록한다([reportFailedAuthentication]).
     */
    private fun authenticate(expectedUser: String, username: String, key: java.security.PublicKey, session: ServerSession): Boolean {
        val address = addressOf(session)
        if (failures.isBlocked(address)) {
            if (session.setAttribute(BLOCK_LOGGED, true) == null) remoteContext(session, null) { boundary.blocked(address, CHANNEL) }
            return false
        }
        val principal = if (username == expectedUser) access.authenticate(KeyUtils.getFingerPrint(key)) else null
        if (principal == null) {
            session.setAttribute(FAILURE_REASON, if (username == expectedUser) "unknown_key" else "invalid_user")
            session.setAttribute(FAILURE_USER, username.take(255))
            if (session.setAttribute(FAILURE_COUNTED, true) == null) failures.recordFailure(address)
            return false
        }
        failures.recordSuccess(address)
        session.setAttribute(PRINCIPAL, principal)
        return true
    }

    /** 인증하지 못한 채 끝난 접속을 `auth.basic_failure`(채널 `ssh`)로 기록하고 센다. 나중에 다른 키로 인증에 성공한 접속과 막힌 접속은 기록하지 않는다. */
    private fun reportFailedAuthentication(session: Session) {
        val server = session as? ServerSession ?: return
        if (server.getAttribute(PRINCIPAL) != null) return
        val reason = server.getAttribute(FAILURE_REASON) ?: return
        remoteContext(server, null) { boundary.basicFailure(server.getAttribute(FAILURE_USER), CHANNEL, reason) }
    }

    private fun addressOf(session: Session): String = (session.remoteAddress as? InetSocketAddress)?.address?.hostAddress.orEmpty()

    /** 감사 기록에 접속 주소와 접속 종류(`ssh`)를 남기도록 문맥을 정해 [block]을 실행한다. */
    private fun <T> remoteContext(session: Session, actor: AuditActor?, block: () -> T): T =
        audit.withRemote(actor ?: AuditActor(AuditActorType.ANONYMOUS, null, null), addressOf(session), CHANNEL, block)

    /** 인증한 접속의 감사 행위자이다. 사용자 키이면 사용자, 배포 키이면 배포 키(번호와 이름)이다. */
    private fun actorOf(principal: SshPrincipal): AuditActor {
        principal.userId?.let { id -> return AuditActor(AuditActorType.USER, id, users.findById(id).orElse(null)?.loginId) }
        val id = principal.deployKeyId
        return AuditActor(AuditActorType.DEPLOY_KEY, id, id?.let { deployKeys.findName(it) })
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
            val session = channel.session
            val principal = session.getAttribute(PRINCIPAL) ?: return fail("ERROR: Not authenticated.")
            val (handler, request) = handlers.firstNotNullOfOrNull { h -> h.parse(line)?.let { h to it } } ?: return fail("ERROR: Invalid command.")
            // 푸시 훅이 남기는 감사 기록(git.push 등)에 행위자와 접속 주소가 들어가도록 이 스레드의 문맥을 정한다
            return remoteContext(session, actorOf(principal)) { run(handler, request, principal) }
        }

        /** 인가하고 실행한다. 시간과 결과를 `yona.vcs.request`로 남긴다(HTTP와 같은 지표, 결과는 `ok`, `denied`, `not_found`, `error`). */
        private fun run(handler: SshVcsHandler, request: SshRepositoryRequest, principal: SshPrincipal): Int {
            var exit = 1
            val vcs = request.vcs.lowercase()
            metrics.request(vcs, request.service, "code") { measured ->
                exit = when (val result = access.authorize(principal, request)) {
                    SshAuthorization.NotFound -> { measured.result = "not_found"; fail("ERROR: Repository not found.") }
                    is SshAuthorization.Denied -> { measured.result = "denied"; fail(result.message) }
                    is SshAuthorization.Allowed -> handler.execute(
                        result.project, request, line, result.sender, SshStreams(metrics.countedInput(vcs, "code", input), metrics.countedOutput(vcs, "code", output), error)
                    ).also { if (it != 0) measured.result = "error" }
                }
            }
            return exit
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

        /** 로그와 지표, 감사 기록의 채널 이름이다. */
        const val CHANNEL = "ssh"

        private val PRINCIPAL = AttributeRepository.AttributeKey<SshPrincipal>()
        private val FAILURE_REASON = AttributeRepository.AttributeKey<String>()
        private val FAILURE_USER = AttributeRepository.AttributeKey<String>()
        private val FAILURE_COUNTED = AttributeRepository.AttributeKey<Boolean>()
        private val BLOCK_LOGGED = AttributeRepository.AttributeKey<Boolean>()
    }
}
