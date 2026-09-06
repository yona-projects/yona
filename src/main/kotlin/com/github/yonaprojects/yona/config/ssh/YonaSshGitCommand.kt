package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.sshkey.SshAuthPrincipal
import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import com.github.yonaprojects.yona.domain.vcs.BranchProtectionPreReceiveHook
import com.github.yonaprojects.yona.domain.vcs.RejectPushToReservedRefsPreReceiveHook
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandDirectErrorStreamAware
import org.apache.sshd.server.command.CommandDirectInputStreamAware
import org.apache.sshd.server.command.CommandDirectOutputStreamAware
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.PreReceiveHookChain
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * yona-wiki P3-03 Step5 — 윈도우 폴백(Apache MINA SSHD)에서 실제로 git 프로토콜을 처리하는
 * Command 구현. GitServletConfig(HTTPS 경로)와 동일하게 JGit의 UploadPack/ReceivePack을 직접
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
    // 코디네이터 push 전 리뷰(2026-09-07) — GitServletConfig(HTTPS 경로)와 동일하게
    // BranchProtectionPreReceiveHook을 체이닝하기 위해 필요하다. 이 필드들이 없어
    // require_pull_request/disallow_force_push/disallow_delete/restrict_push_to 전부가
    // SSH를 통하면 우회되는 실제 보안 결함이 있었다(YonaMinaSshServerIntegrationSpec의
    // 회귀 테스트로 고정).
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — BranchProtectionPreReceiveHook이
    // require_signed_commits를 실제로 검사하는 데 필요(HTTPS 경로 GitServletConfig와 동일).
    private val gpgSignatureVerifier: GpgSignatureVerifier
) : Command, CommandDirectInputStreamAware, CommandDirectOutputStreamAware, CommandDirectErrorStreamAware {

    private val logger = LoggerFactory.getLogger(YonaSshGitCommand::class.java)

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

            val repository = FileRepositoryBuilder()
                .setGitDir(authorization.repoDir)
                .build()

            try {
                when (authorization.service) {
                    "git-upload-pack" -> {
                        val uploadPack = UploadPack(repository)
                        uploadPack.upload(inputStream, outputStream, errorStream)
                    }
                    "git-receive-pack" -> {
                        val receivePack = ReceivePack(repository)
                        // GitServletConfig(HTTPS)와 완전히 동일한 훅 체이닝 — refs/yobi/* 예약
                        // ref 거부는 항상 적용, project를 알 수 없는 경우가 아니라면(이 경로에서는
                        // authorizeGitCommand()가 성공한 시점에 이미 project가 확정돼 있으므로
                        // 사실상 항상 해당) 브랜치 보호 규칙도 함께 검사한다. pusher는
                        // Deploy Key로 push한 경우 null(익명 push와 동일한 의미 — restrict_push_to는
                        // 여전히 적용되고 admins_can_bypass는 적용되지 않음, HTTPS와 동일한 정책).
                        val preReceiveHooks = mutableListOf<PreReceiveHook>(RejectPushToReservedRefsPreReceiveHook())
                        if (authorization.project != null) {
                            preReceiveHooks.add(
                                BranchProtectionPreReceiveHook(
                                    authorization.project, authorization.pusher, protectedBranchRepository,
                                    projectUserRepository, gpgSignatureVerifier
                                )
                            )
                        }
                        receivePack.setPreReceiveHook(PreReceiveHookChain.newChain(preReceiveHooks))
                        receivePack.receive(inputStream, outputStream, errorStream)
                    }
                    else -> {
                        writeError("지원하지 않는 명령입니다: ${authorization.service}")
                        exitCallback?.onExit(1, "unsupported service")
                        return
                    }
                }
            } finally {
                repository.close()
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
