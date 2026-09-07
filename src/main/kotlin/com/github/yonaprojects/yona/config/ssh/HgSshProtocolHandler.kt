package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.sshkey.SshCommandAuthorization
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.lib.HgRepository
import io.github.search5.hg4j.transport.HgSshWireServer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * yona-wiki P3-18/P3-12 — hg4j의 [HgSshWireServer](JGit UploadPack/ReceivePack에 대응하는 Hg SSH
 * 와이어 프로토콜 서버, `../hg4j`)를 이 애플리케이션의 소켓 릴레이([SshRelayServer])에 연결하는
 * 얇은 어댑터. [GitSshProtocolHandler]와 대칭 — 인가는 이미 [SshAuthService.authorizeHgCommand]가
 * 끝낸 상태로 받는다.
 *
 * **브랜치 보호 훅 자리(TODO)**: Mercurial 쪽 브랜치 보호(require_pull_request 등,
 * `ProtectedBranchRepository` 기반) 정책은 아직 존재하지 않는다(P3-12 2라운드 과제). 이 클래스는
 * `registerPreChangegroupHook`을 등록하는 지점만 만들어 두되, git의 `BranchProtectionPreReceiveHook`에
 * 대응하는 새 Hg 전용 정책 로직은 이 작업(P3-18) 범위에서 만들지 않는다 — 여기서는 오직
 * `authorizeHgCommand()`가 이미 계산해둔 쓰기 권한(멤버십/읽기전용 Deploy Key)만 push(unbundle)
 * 시점에 강제한다. git의 `git-upload-pack`/`git-receive-pack`처럼 명령줄만 보고 read/write를
 * 미리 구분할 수 없는 게 Hg SSH 와이어 프로토콜의 구조적 특성(한 세션 안에서 pull/push 모두
 * 가능)이라, 실제 쓰기 권한 게이트는 여기(pre-changegroup hook)에 있을 수밖에 없다.
 */
class HgSshProtocolHandler {
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
            server.handleConnection(input, output)
        } finally {
            repository.close()
        }
    }
}
