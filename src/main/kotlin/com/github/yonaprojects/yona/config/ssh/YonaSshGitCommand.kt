package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.sshkey.SshAuthPrincipal
import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandDirectErrorStreamAware
import org.apache.sshd.server.command.CommandDirectInputStreamAware
import org.apache.sshd.server.command.CommandDirectOutputStreamAware
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * 윈도우 폴백(Apache MINA SSHD)에서 실제로 git 프로토콜을 처리하는 Command 구현.
 * GitServletConfig(HTTPS 경로)와 동일하게 JGit의 UploadPack/ReceivePack을 직접
 * 사용해 같은 물리 저장소(SshAuthService.authorizeGitCommand()가 계산한 repoDir)를 다룬다.
 *
 * Direct*StreamAware 마커 인터페이스를 구현하면 MINA SSHD가 자체 비동기 파이프 대신 실제
 * 채널 스트림을 그대로 넘겨준다 — UploadPack/ReceivePack은 블로킹 스트림 API를 기대하므로
 * 이 방식이 맞다.
 */
class YonaSshGitCommand(
    private val commandLine: String,
    private val principal: SshAuthPrincipal,
    private val sshAuthService: SshAuthService,
    // GitServletConfig(HTTPS 경로)와 동일하게 BranchProtectionPreReceiveHook을 체이닝하기
    // 위해 필요하다. 이 필드들이 없으면 require_pull_request/disallow_force_push/
    // disallow_delete/restrict_push_to 전부가 SSH를 통하면 우회되는 보안 결함이 생긴다
    // (YonaMinaSshServerIntegrationSpec의 회귀 테스트로 고정).
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    // BranchProtectionPreReceiveHook이 require_signed_commits를 실제로 검사하는 데 필요
    // (HTTPS 경로 GitServletConfig와 동일).
    private val gpgSignatureVerifier: GpgSignatureVerifier
) : Command, CommandDirectInputStreamAware, CommandDirectOutputStreamAware, CommandDirectErrorStreamAware {

    private val logger = LoggerFactory.getLogger(YonaSshGitCommand::class.java)

    // GitServletConfig(HTTPS)와 동일한 훅 체이닝 로직을 공유 클래스로 뽑아내, 이 MINA 경로와
    // 유닉스 도메인 소켓 릴레이(SshRelayServer) 둘 다 같은 구현을 호출하도록 한다.
    private val gitProtocolHandler = GitSshProtocolHandler(protectedBranchRepository, projectUserRepository, gpgSignatureVerifier)

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var errorStream: OutputStream? = null
    private var exitCallback: ExitCallback? = null
    private var worker: Thread? = null

    override fun setInputStream(input: InputStream) {
        inputStream = input
    }

    override fun setOutputStream(output: OutputStream) {
        outputStream = output
    }

    override fun setErrorStream(err: OutputStream) {
        errorStream = err
    }

    override fun setExitCallback(callback: ExitCallback) {
        exitCallback = callback
    }

    override fun start(channel: ChannelSession, env: Environment) {
        val thread = Thread({ run() }, "yona-ssh-git-command")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    private fun run() {
        try {
            val authorization = sshAuthService.authorizeGitCommand(principal, commandLine)
            if (!authorization.allowed || authorization.repoDir == null || authorization.service == null) {
                writeError(authorization.reason ?: "접근이 거부되었습니다.")
                exitCallback?.onExit(1, authorization.reason ?: "denied")
                return
            }

            try {
                gitProtocolHandler.handle(authorization, inputStream!!, outputStream!!, errorStream!!)
            } catch (e: UnsupportedGitServiceException) {
                writeError(e.message ?: "지원하지 않는 명령입니다.")
                exitCallback?.onExit(1, "unsupported service")
                return
            }

            outputStream?.flush()
            exitCallback?.onExit(0)
        } catch (e: Exception) {
            logger.warn("SSH git command failed: {}", commandLine, e)
            writeError("내부 오류: ${e.message}")
            exitCallback?.onExit(1, e.message ?: "internal error")
        }
    }

    private fun writeError(message: String) {
        try {
            errorStream?.write((message + "\n").toByteArray())
            errorStream?.flush()
        } catch (ignored: Exception) {
        }
    }

    override fun destroy(channel: ChannelSession) {
        worker?.interrupt()
    }
}
