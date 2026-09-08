package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.sshkey.SshAuthPrincipal
import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import com.github.yonaprojects.yona.domain.sshkey.SshCommandAuthorization
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * yona-wiki P3-18 — SSH forced command가 실제 git/hg 바이너리를 exec하는 대신, 이미 떠 있는
 * yona 메인 JVM에 얇은 바이트 릴레이로 연결해 인프로세스 JGit/hg4j 로직(HTTPS 경로와 완전히
 * 동일한 브랜치 보호 훅 체이닝)을 그대로 타도록 하는 유닉스 도메인 소켓 리스너.
 *
 * **핸드셰이크 프로토콜**(단순한 내부 전용 프로토콜, 공개 스펙 아님 — 상세 설계는
 * `docs/parity/tickets/p3-18.md` 참고):
 * 1. 클라이언트가 연결하면 개행으로 구분된 정확히 두 줄을 먼저 보낸다:
 *    - 1번째 줄: [SshAuthService.encodePrincipal]이 만드는 인코딩된 principal 문자열
 *      (`sshkey:123` / `deploykey:45`).
 *    - 2번째 줄: 원본 `SSH_ORIGINAL_COMMAND` 문자열 그대로(git이면
 *      `git-upload-pack '/owner/project.git'`, Hg면 real hg가 실제로 보내는
 *      `hg -R '/owner/project' serve --stdio`).
 * 2. 그 두 줄을 다 읽고 나면, 이 소켓은 순수 양방향 바이트 릴레이가 된다 — 이후 남은
 *    InputStream/소켓의 OutputStream을 그대로 [GitSshProtocolHandler]/[HgSshProtocolHandler]에
 *    넘겨 UploadPack/ReceivePack/HgSshWireServer가 직접 그 스트림을 읽고 쓴다.
 *
 * 인가가 거부되거나 명령을 해석할 수 없으면 `ERR <이유>\n` 한 줄을 쓰고 연결을 닫는다 — 그
 * 이후로도 리스너 자체(accept 루프)는 계속 살아있어 다음 연결을 받는다(악의적이거나 깨진
 * 연결 하나가 리스너 전체를 죽여서는 안 된다).
 */
@Component
class SshRelayServer(
    private val sshAuthService: SshAuthService,
    // yona-wiki P3-18 — GitSshProtocolHandler 구성에 필요(YonaMinaSshServer와 동일한 이유).
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    // yona-wiki P3-21/P3-22 — HgSshProtocolHandler의 브랜치 보호/push 알림·웹훅·PushedBranch
    // 훅 구성에 필요(GitSshProtocolHandler와 대칭).
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
    // YonaMinaSshServer의 yona.ssh.mina.enabled와 동일한 취지 — 테스트 프로파일 등 소켓 경로를
    // 쓸 수 없거나 원치 않는 환경에서 기동을 건너뛸 수 있게 한다. 운영 기본값은 활성화.
    @Value("\${yona.ssh.relay.enabled:true}")
    private val enabledSetting: Boolean,
    @Value("\${yona.ssh.relay.socket-path:/tmp/yona/ssh-relay.sock}")
    private val socketPathSetting: String
) {
    private val logger = LoggerFactory.getLogger(SshRelayServer::class.java)

    private val gitProtocolHandler = GitSshProtocolHandler(protectedBranchRepository, projectUserRepository, gpgSignatureVerifier)
    private val hgProtocolHandler = HgSshProtocolHandler(
        protectedBranchRepository, projectUserRepository, gpgSignatureVerifier,
        projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher, meterRegistry
    )

    private var serverChannel: ServerSocketChannel? = null
    private var acceptThread: Thread? = null
    @Volatile
    private var running = false

    // 실제로 리스닝 중인 소켓 경로(테스트가 이 값으로 접속한다).
    val socketPath: String get() = socketPathSetting

    val isRunning: Boolean get() = running

    @PostConstruct
    fun start() {
        if (!enabledSetting) {
            logger.info("SshRelayServer disabled (yona.ssh.relay.enabled=false)")
            return
        }

        try {
            val path = Path.of(socketPathSetting)
            path.parent?.let { Files.createDirectories(it) }
            Files.deleteIfExists(path)

            val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            channel.bind(UnixDomainSocketAddress.of(path))
            serverChannel = channel
            running = true

            // yona-wiki P3-18 — 실제 forced command(sshd_config의 Match User git 아래에서 도는
            // ssh-auth.sh의 command=)는 이 JVM과 다른 OS 계정("git")으로 실행된다(docs/guide/
            // ssh-system-sshd-setup.md Step 1 — git 계정은 yona 앱 계정과 그룹만 공유). 유닉스
            // 도메인 소켓은 connect()에 소켓 파일 자체의 쓰기 권한이 필요한데, 기본 생성 권한은
            // JVM 프로세스의 umask에 좌우돼 그룹 쓰기가 막혀 있을 수 있다 — 그러면 같은 그룹인
            // git 계정도 연결이 거부된다. 그룹 rw로 명시 고정해 이 문제를 배포 환경 umask 설정에
            // 기대지 않고 항상 재현 가능하게 만든다(소유자는 이 JVM 프로세스 계정 그대로,
            // world 권한은 부여하지 않는다).
            try {
                Files.setPosixFilePermissions(
                    path,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----")
                )
            } catch (e: UnsupportedOperationException) {
                // POSIX 권한을 지원하지 않는 파일시스템(사실상 발생하지 않음 — UNIX 도메인 소켓
                // 자체가 POSIX 전용 기능)이면 조용히 넘어간다.
            }

            val thread = Thread({ acceptLoop(channel) }, "yona-ssh-relay-acceptor")
            thread.isDaemon = true
            acceptThread = thread
            thread.start()

            logger.info("SshRelayServer listening at {}", path)
        } catch (e: Exception) {
            // yona-wiki P3-18 — 테스트 프로파일 등 소켓 경로를 쓸 수 없는 환경에서도 애플리케이션
            // 기동 자체는 절대 실패하면 안 된다(YonaMinaSshServer가 윈도우 폴백 실패를 감수하는
            // 것과 동일한 관용도). 실패하면 이 기능만 조용히 비활성 상태로 남는다.
            logger.warn("Failed to start SshRelayServer at {} — SSH socket relay disabled", socketPathSetting, e)
            running = false
        }
    }

    @PreDestroy
    fun stop() {
        running = false
        try {
            serverChannel?.close()
        } catch (ignored: Exception) {
        }
        acceptThread?.interrupt()
        try {
            Files.deleteIfExists(Path.of(socketPathSetting))
        } catch (ignored: Exception) {
        }
    }

    private fun acceptLoop(channel: ServerSocketChannel) {
        while (running) {
            val client = try {
                channel.accept()
            } catch (e: AsynchronousCloseException) {
                return
            } catch (e: IOException) {
                if (running) {
                    logger.warn("SshRelayServer accept() failed — continuing to accept further connections", e)
                }
                continue
            }

            val connectionThread = Thread({ handleConnection(client) }, "yona-ssh-relay-conn")
            connectionThread.isDaemon = true
            connectionThread.start()
        }
    }

    // 연결 하나(핸드셰이크 두 줄 읽기 + 이후 프로토콜 위임)를 처리한다. 여기서 던져지는 어떤
    // 예외도 이 스레드 밖으로(즉 accept 루프로) 새어나가지 않는다 — 스레드 자체가 격리돼 있다.
    private fun handleConnection(client: SocketChannel) {
        client.use { channel ->
            val input = Channels.newInputStream(channel)
            val output = Channels.newOutputStream(channel)
            try {
                val encodedPrincipal = readHandshakeLine(input)
                val commandLine = readHandshakeLine(input)
                if (encodedPrincipal == null || commandLine == null) {
                    writeErrorLine(output, "핸드셰이크를 읽을 수 없습니다.")
                    return
                }

                val principal = sshAuthService.resolvePrincipal(encodedPrincipal)
                if (principal == null) {
                    writeErrorLine(output, "알 수 없는 principal입니다.")
                    return
                }

                dispatch(principal, commandLine, input, output)
            } catch (e: Exception) {
                logger.warn("SSH relay connection failed", e)
                try {
                    writeErrorLine(output, "내부 오류: ${e.message}")
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun dispatch(principal: SshAuthPrincipal, commandLine: String, input: InputStream, output: OutputStream) {
        val trimmed = commandLine.trim()
        when {
            trimmed.startsWith("git-upload-pack") || trimmed.startsWith("git-receive-pack") ||
                trimmed.startsWith("git-upload-archive") -> {
                val authorization = sshAuthService.authorizeGitCommand(principal, commandLine)
                if (!isUsable(authorization)) {
                    writeErrorLine(output, authorization.reason ?: "접근이 거부되었습니다.")
                    return
                }
                handleGit(authorization, input, output)
            }
            trimmed.startsWith("hg ") -> {
                val authorization = sshAuthService.authorizeHgCommand(principal, commandLine)
                if (!isUsable(authorization)) {
                    writeErrorLine(output, authorization.reason ?: "접근이 거부되었습니다.")
                    return
                }
                hgProtocolHandler.handle(authorization, input, output)
            }
            else -> writeErrorLine(output, "지원하지 않는 명령입니다: $commandLine")
        }
    }

    private fun isUsable(authorization: SshCommandAuthorization): Boolean =
        authorization.allowed && authorization.repoDir != null

    private fun handleGit(authorization: SshCommandAuthorization, input: InputStream, output: OutputStream) {
        // yona-wiki P3-18 1라운드 한계(문서화됨) — 이 소켓은 stdin/stdout 하나뿐이라 SSH 채널의
        // 별도 stderr 채널에 해당하는 게 없다. JGit ReceivePack/UploadPack의 "메시지" 스트림으로
        // 가는 내용(진행률 등 부가 정보)은 여기서는 버린다 — ref 거부 사유 등 핵심 프로토콜
        // 응답(report-status)은 이 메시지 스트림이 아니라 주 output 스트림으로 나가므로
        // 영향받지 않는다(YonaMinaSshServerIntegrationSpec의 브랜치 보호 회귀 테스트와 동일한
        // 검증 방식이 이 릴레이 경로에도 그대로 통과하는 것으로 확인).
        val discardedMessages = ByteArrayOutputStream()
        try {
            gitProtocolHandler.handle(authorization, input, output, discardedMessages)
        } catch (e: UnsupportedGitServiceException) {
            writeErrorLine(output, e.message ?: "지원하지 않는 명령입니다.")
        }
    }

    // hg4j의 HgSshWireServer.readLine()과 동일한 방식(버퍼링 없는 바이트 단위 읽기) — 이후 남은
    // InputStream을 그대로 프로토콜 핸들러에 넘겨야 하므로, BufferedReader 등으로 감싸 미리
    // 읽어두면 안 된다(프로토콜 바이트를 먼저 삼켜버림).
    private fun readHandshakeLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var any = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (any) buffer.toString(StandardCharsets.UTF_8) else null
            }
            any = true
            if (b == '\n'.code) {
                return buffer.toString(StandardCharsets.UTF_8)
            }
            buffer.write(b)
        }
    }

    private fun writeErrorLine(output: OutputStream, message: String) {
        try {
            output.write("ERR $message\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } catch (ignored: Exception) {
        }
    }
}
