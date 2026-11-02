package dev.yona.ssh

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 시스템 sshd 연동의 중계 소켓 서버이다(추가 개발 항목 X-3b, `decisions.md` D-67). 시스템 OpenSSH sshd가 훅([yona-ssh-auth.sh](../../../../../tools/ssh/yona-ssh-auth.sh))이 만든 강제 명령으로
 * 세션을 열면, 그 명령이 `socat`으로 이 유닉스 도메인 소켓에 연결해 클라이언트의 입출력을 그대로 이어 붙인다. 그러면 저장소 접근, 인가, 푸시 훅은 모두 이미 떠 있는 이 서버
 * 안에서 처리되어 내장 SSH 서버([SshServerLifecycle])와 HTTP와 똑같이 적용된다. sshd를 실행하는 계정은 저장소 파일을 읽고 쓸 필요가 없다.
 *
 * **핸드셰이크**: 연결하면 클라이언트가 개행으로 끝나는 세 줄을 먼저 보낸다. 1) 훅이 받아 둔 인증 표([SshRelayTokens]), 2) 클라이언트 주소(`SSH_CLIENT`의 첫 값, 감사 기록용, 비어 있을 수 있다),
 * 3) 클라이언트가 요청한 원본 명령(`SSH_ORIGINAL_COMMAND`). 그 뒤는 순수 바이트 중계이다.
 *
 * 표가 맞지 않거나 명령을 처리하지 못하면 프로토콜을 시작하기 전에 git의 오류 줄(pkt-line `ERR <사유>`)을 쓰고 닫는다. git 클라이언트는 이를 `fatal: remote error: <사유>`로 보여 준다.
 * 소켓은 이 서버를 실행하는 계정의 그룹(`rw-rw----`)만 연결할 수 있다. 소켓 파일 경로를 쓸 수 없으면 서버는 계속 시작하고 이 기능만 끈 채 경고를 남긴다.
 */
@Component
@ConditionalOnProperty(prefix = "yona.ssh.relay", name = ["enabled"], havingValue = "true")
class SshRelayServer(
    private val properties: YonaProperties,
    private val dataDirectory: DataDirectory,
    private val tokens: SshRelayTokens,
    private val runner: SshCommandRunner
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(javaClass)
    private var channel: ServerSocketChannel? = null
    private var acceptor: Thread? = null
    private var executor: ExecutorService? = null
    @Volatile private var running = false

    /** 소켓 파일의 절대 경로이다. 설정(`yona.ssh.relay.socket-path`)이 없으면 데이터 디렉터리의 `ssh/relay.sock`이다. 훅 스크립트가 다른 작업 디렉터리에서 실행되므로 상대 경로는 절대 경로로 바꾼다. */
    val socketPath: String get() = File(properties.ssh.relay.socketPath.ifBlank { File(dataDirectory.root(), "ssh/relay.sock").path }).absolutePath


    override fun isRunning(): Boolean = running

    override fun start() {
        try {
            val path = File(socketPath).absoluteFile.toPath()
            Files.createDirectories(path.parent)
            Files.deleteIfExists(path)
            val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            server.bind(UnixDomainSocketAddress.of(path))
            // 소켓 파일의 기본 권한은 프로세스의 umask에 좌우되므로 소유자와 그룹만 읽고 쓰게 고정한다(같은 그룹의 sshd 계정이 연결한다)
            runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-rw----")) }
            channel = server
            executor = Executors.newCachedThreadPool(NamedThreads("yona-ssh-relay"))
            running = true
            acceptor = Thread({ acceptLoop(server) }, "yona-ssh-relay-acceptor").apply { isDaemon = true; start() }
            log.info("SSH relay listening on {}", path)
        } catch (e: Exception) {
            log.warn("Failed to start the SSH relay at {}; system sshd integration is disabled", socketPath, e)
            running = false
        }
    }

    override fun stop() {
        running = false
        runCatching { channel?.close() }
        executor?.shutdownNow()
        runCatching { Files.deleteIfExists(File(socketPath).toPath()) }
        channel = null
        executor = null
    }

    private fun acceptLoop(server: ServerSocketChannel) {
        while (running) {
            val client = try {
                server.accept()
            } catch (e: AsynchronousCloseException) {
                return
            } catch (e: IOException) {
                if (running) log.warn("SSH relay accept failed; continuing", e)
                continue
            }
            // 연결마다 따로 처리해 연결 하나가 깨져도 받는 쪽은 계속 산다
            runCatching { checkNotNull(executor).execute { handle(client) } }.onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(client: SocketChannel) {
        client.use { socket ->
            val input = Channels.newInputStream(socket)
            val output = Channels.newOutputStream(socket)
            try {
                val tokenLine = readLine(input) ?: return
                val address = readLine(input)
                val command = readLine(input)
                if (address == null || command == null) return reject(output, "Invalid handshake.")
                val principal = tokens.verify(tokenLine) ?: return reject(output, "Invalid credentials.")
                runner.run(principal, command, SshStreams(input, output, OutputStream.nullOutputStream()), address.takeIf { it.isNotBlank() }) { reject(output, it.removePrefix("ERROR: ")) }
                output.flush()
            } catch (e: Exception) {
                log.warn("SSH relay connection failed", e)
                runCatching { reject(output, "Internal error.") }
            }
        }
    }

    /** git의 오류 줄(`ERR <사유>`)을 pkt-line으로 쓴다. 프로토콜이 시작되기 전에만 쓴다. */
    private fun reject(output: OutputStream, message: String) {
        val body = "ERR $message".toByteArray()
        output.write("%04x".format(body.size + 4).toByteArray())
        output.write(body)
        output.flush()
    }

    /**
     * 개행까지 한 줄을 바이트 단위로 읽는다. 이후의 입력은 프로토콜 바이트이므로 버퍼를 둔 읽기로 미리 삼키면 안 된다. 아무것도 읽지 못하고 끝나면 `null`이다.
     * 줄이 [MAX_LINE]바이트를 넘으면 잘못된 연결로 보고 `null`이다.
     */
    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() > 0) buffer.toString(Charsets.UTF_8) else null
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8)
            if (buffer.size() >= MAX_LINE) return null
            buffer.write(b)
        }
    }

    private class NamedThreads(private val prefix: String) : java.util.concurrent.ThreadFactory {
        private val index = AtomicInteger()
        override fun newThread(r: Runnable): Thread = Thread(r, "$prefix-${index.incrementAndGet()}").apply { isDaemon = true }
    }

    private companion object {
        const val MAX_LINE = 8192
    }
}
