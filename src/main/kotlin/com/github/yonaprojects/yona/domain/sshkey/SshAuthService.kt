package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.domain.deploykey.DeployKey
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import java.io.File

// yona-wiki P3-03 Step6 — 리눅스/맥(AuthorizedKeysCommand 훅, `internal ssh-auth`/`internal
// ssh-shell`)과 윈도우 폴백(Apache MINA SSHD 임베디드 서버) 두 경로가 공유하는 공통 서비스
// 레이어. 어느 쪽도 이 인터페이스 뒤의 판정 로직을 다시 구현하지 않는다.
sealed class SshAuthPrincipal {
    data class SshKeyPrincipal(val user: User, val sshKey: SshKey) : SshAuthPrincipal()
    data class DeployKeyPrincipal(val deployKey: DeployKey) : SshAuthPrincipal()
}

data class SshCommandAuthorization(
    val allowed: Boolean,
    val isWrite: Boolean = false,
    val repoDir: File? = null,
    val service: String? = null,
    val reason: String? = null,
    // 코디네이터 push 전 리뷰(2026-09-07) — GitServletConfig(HTTPS)의
    // BranchProtectionPreReceiveHook 체이닝을 이 SSH 경로에서도 그대로 재현하려면 이 두 값이
    // 필요하다. pusher는 SshKeyPrincipal일 때만 채워지고 DeployKeyPrincipal이면 null이다 —
    // HTTPS 경로도 Deploy Key(Basic 인증)로 push할 때 pusher를 null로 취급하는 것과 동일한
    // 의미(익명 push와 동일하게 restrict_push_to는 여전히 적용되고 admins_can_bypass는
    // 적용되지 않음).
    val project: Project? = null,
    val pusher: User? = null
) {
    companion object {
        fun denied(reason: String): SshCommandAuthorization = SshCommandAuthorization(allowed = false, reason = reason)
    }
}

interface SshAuthService {
    // rawPublicKeyLine: "ssh-ed25519 AAAA... [comment]" 형식. 알 수 없는 공개키면 null.
    // 리눅스/맥 경로(`yona internal ssh-auth`, AuthorizedKeysCommand가 %t/%k로 넘겨준 값을 조합)가 쓴다.
    fun authenticate(rawPublicKeyLine: String): SshAuthPrincipal?

    // 윈도우 폴백(Apache MINA SSHD) 경로가 쓴다 — PublickeyAuthenticator 콜백은 이미 파싱된
    // java.security.PublicKey만 주므로, MINA SSHD의 KeyUtils.getFingerPrint(PublicKey)(OpenSSH
    // ssh-keygen -lf와 동일한 "SHA256:..." 포맷)로 계산한 지문을 그대로 조회 키로 쓴다 —
    // SshPublicKeyFingerprint.parse()가 raw base64 블롭에서 계산하는 지문과 완전히 동일한 값이다.
    fun authenticateByFingerprint(fingerprint: String): SshAuthPrincipal?

    // command: SSH_ORIGINAL_COMMAND 그대로("git-upload-pack 'owner/project.git'" 등).
    fun authorizeGitCommand(principal: SshAuthPrincipal, command: String): SshCommandAuthorization

    fun resolveProject(owner: String, projectName: String): Project?

    // yona-wiki P3-03 Step4 — 리눅스/맥 경로 전용. `internal ssh-auth`가 authenticate()로 얻은
    // principal을 forced command(`internal ssh-shell --principal=<encoded>`)에 opaque 문자열로
    // 심어두면, 이후 `internal ssh-shell`이 SSH_ORIGINAL_COMMAND를 처리할 때 이 문자열을 그대로
    // /internal/ssh/authorize에 되돌려줘 principal을 복원한다(매 git 명령마다 공개키를 다시 보내지
    // 않아도 됨 — sshd가 이미 그 세션의 공개키 소유를 검증했으므로 안전하다).
    fun encodePrincipal(principal: SshAuthPrincipal): String
    fun resolvePrincipal(encoded: String): SshAuthPrincipal?
}
