package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.sshkey.SshCommandAuthorization
import com.github.yonaprojects.yona.domain.vcs.BranchProtectionPreReceiveHook
import com.github.yonaprojects.yona.domain.vcs.RejectPushToReservedRefsPreReceiveHook
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.PreReceiveHookChain
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import java.io.InputStream
import java.io.OutputStream

/** [SshCommandAuthorization.service]가 이 핸들러가 아는 어떤 git 서비스와도 일치하지 않을 때
 * 던진다(예: git-upload-archive — authorizeGitCommand()의 정규식은 허용하지만 실제 처리 로직은
 * 아직 없음). 두 호출부(YonaSshGitCommand, SshRelayServer) 모두 동일한 방식으로 처리해야 하므로
 * 예외 자체도 여기 공유 클래스에 둔다. */
class UnsupportedGitServiceException(service: String?) : Exception("지원하지 않는 명령입니다: $service")

/**
 * GitServletConfig(HTTPS)/YonaSshGitCommand(임베디드 MINA SSHD)가 각각 따로 구성하던 "JGit
 * UploadPack/ReceivePack을 열고, RejectPushToReservedRefsPreReceiveHook +
 * BranchProtectionPreReceiveHook을 체이닝해서 돌린다"는 로직을 한 곳으로 뽑아낸 공유 구현체.
 *
 * SSH 전송 수단(임베디드 MINA SSHD의 Command 채널 스트림 vs 새 유닉스 도메인 소켓 릴레이의
 * 소켓 스트림)이 달라도 이 클래스가 다루는 것은 순수 InputStream/OutputStream 세 개뿐이라 —
 * 두 경로 모두 이 클래스 하나만 호출하면 브랜치 보호 정책이 항상 동일하게 적용된다(이 리팩터의
 * 핵심 목적, 정책이 두 코드 경로로 갈라져 드리프트하는 것을 막는다).
 */
class GitSshProtocolHandler(
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier
) {
    /** authorization.allowed/repoDir/service는 호출부가 이미 검증했다고 가정한다(denied 케이스는
     * 호출부에서 처리) — 이 메서드는 오직 "허용된 요청을 실제로 처리"하는 데만 집중한다. */
    fun handle(authorization: SshCommandAuthorization, input: InputStream, output: OutputStream, error: OutputStream) {
        val repoDir = requireNotNull(authorization.repoDir) { "authorization.repoDir must not be null when allowed" }
        val service = authorization.service

        val repository = FileRepositoryBuilder().setGitDir(repoDir).build()
        try {
            when (service) {
                "git-upload-pack" -> UploadPack(repository).upload(input, output, error)
                "git-receive-pack" -> {
                    val receivePack = ReceivePack(repository)
                    // GitServletConfig(HTTPS)와 완전히 동일한 훅 체이닝 — refs/yobi/* 예약 ref
                    // 거부는 항상 적용, project를 알 수 없는 경우가 아니라면 브랜치 보호 규칙도
                    // 함께 검사한다. pusher는 Deploy Key로 push한 경우 null(익명 push와 동일한
                    // 의미).
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
                    receivePack.receive(input, output, error)
                }
                else -> throw UnsupportedGitServiceException(service)
            }
        } finally {
            repository.close()
        }
    }
}
