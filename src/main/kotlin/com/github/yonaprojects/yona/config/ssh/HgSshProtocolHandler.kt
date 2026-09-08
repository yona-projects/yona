package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.sshkey.SshCommandAuthorization
import com.github.yonaprojects.yona.domain.vcs.HgBranchProtectionPrePushkeyHook
import com.github.yonaprojects.yona.domain.vcs.HgYonaPostPushkeyHook
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.lib.HgRepository
import io.github.search5.hg4j.transport.HgSshWireServer
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.ApplicationEventPublisher
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * yona-wiki P3-18/P3-12/P3-21/P3-22 — hg4j의 [HgSshWireServer](JGit UploadPack/ReceivePack에
 * 대응하는 Hg SSH 와이어 프로토콜 서버, `../hg4j`)를 이 애플리케이션의 소켓 릴레이
 * ([SshRelayServer])에 연결하는 얇은 어댑터. [GitSshProtocolHandler]와 대칭 — 인가는 이미
 * [SshAuthService.authorizeHgCommand]가 끝낸 상태로 받는다.
 *
 * **멤버십 쓰기 권한**(P3-18): git의 `git-upload-pack`/`git-receive-pack`처럼 명령줄만 보고
 * read/write를 미리 구분할 수 없는 게 Hg SSH 와이어 프로토콜의 구조적 특성(한 세션 안에서
 * pull/push 모두 가능)이라, `authorizeHgCommand()`가 이미 계산해둔 쓰기 권한(멤버십/읽기전용
 * Deploy Key)은 pre-changegroup 훅에서 강제한다.
 *
 * **브랜치 보호/push 알림(P3-21/P3-22)**: git의 `BranchProtectionPreReceiveHook`/
 * `YonaPostReceiveHook`(GitPushHooks.kt)에 정확히 대응하는 `HgBranchProtectionPrePushkeyHook`/
 * `HgYonaPostPushkeyHook`(domain/vcs/HgPushHooks.kt)을 pushkey 훅(bookmark 이동 시점 — 실제 북마크
 * 이동이 일어나는 유일한 지점, unbundle은 changeset만 반영할 뿐 어떤 ref가 움직였는지 모른다)에
 * 등록한다. `authorization.project`/`authorization.pusher`는 이미 [SshAuthService]가 이 연결
 * 하나에 대해 고정으로 계산해둔 값이라(HTTP의 `HgController`처럼 요청마다 다시 조회할 필요가
 * 없다 — SSH 연결 하나 = push 하나) 훅 생성 시점에 그대로 캡처한다. pusher는 Deploy Key로 push한
 * 경우 null(HTTPS 경로도 Deploy Key push를 익명 push와 동일하게 취급하는 것과 동일한 의미).
 */
class HgSshProtocolHandler(
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    fun handle(authorization: SshCommandAuthorization, input: InputStream, output: OutputStream) {
        val repoDir = requireNotNull(authorization.repoDir) { "authorization.repoDir must not be null when allowed" }

        val repository = HgRepository(repoDir)
        try {
            val server = HgSshWireServer(repository)
            server.registerPreChangegroupHook(
                HgHook {
                    if (!authorization.isWrite) {
                        throw IOException(authorization.reason ?: "이 저장소에 push 권한이 없습니다.")
                    }
                    true
                }
            )

            val project = authorization.project
            if (project != null) {
                server.registerPrePushkeyHook(
                    HgHook { context ->
                        HgBranchProtectionPrePushkeyHook(
                            project, authorization.pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                        ).run(context)
                    }
                )
                val pusher = authorization.pusher
                if (pusher != null) {
                    server.registerPostPushkeyHook(
                        HgHook { context ->
                            HgYonaPostPushkeyHook(
                                project, pusher, projectRepository, pullRequestRepository,
                                pushedBranchRepository, eventPublisher, meterRegistry
                            ).run(context)
                        }
                    )
                }
            }

            server.handleConnection(input, output)
        } finally {
            repository.close()
        }
    }
}
