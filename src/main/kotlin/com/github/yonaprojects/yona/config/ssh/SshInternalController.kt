package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * yona-wiki P3-03 Step4 — 리눅스/맥 시스템 OpenSSH의 `AuthorizedKeysCommand` 훅에서 실행되는
 * `yona internal ssh-auth`/`yona internal ssh-shell`(yona-cli, 별도 저장소)이 호출하는 내부 전용
 * 엔드포인트. **이 앱은 이 세션에서 시스템 sshd 자체를 구동/설정하지 않는다**(호스트 시스템을
 * 건드리지 말라는 제약) — 이 컨트롤러는 그 훅이 존재한다고 가정했을 때 호출할 순수 판정 API만
 * 제공하고, 유닛/통합 테스트로 이 API 자체의 동작만 검증한다.
 *
 * 보안: 이 엔드포인트는 시스템 sshd가 같은 호스트에서 로컬 프로세스로 호출하는 것을 전제로 하므로
 * (1) 루프백 주소(127.0.0.1/::1)에서 온 요청만 허용하고, (2) SshInternalSecretProvider가 관리하는
 * 공유 시크릿 헤더(X-Yona-Internal-Secret)까지 요구해 이중으로 방어한다. SecurityConfig의
 * anyRequest().permitAll() 아래에 있지만(이 코드베이스의 기존 컨벤션 — 컨트롤러 자체 인증/인가
 * 체크에 의존), 이 두 체크를 통과하지 못하면 무조건 403이다.
 */
@RestController
class SshInternalController(
    private val sshAuthService: SshAuthService,
    private val secretProvider: SshInternalSecretProvider
) {

    data class AuthenticateRequest(val publicKey: String)
    data class AuthenticateResponse(val principal: String)

    data class AuthorizeRequest(val principal: String, val command: String)
    data class AuthorizeResponse(val allowed: Boolean, val repoDir: String?, val service: String?, val reason: String?)

    @PostMapping("/internal/ssh/authenticate")
    fun authenticate(
        @RequestBody request: AuthenticateRequest,
        @RequestHeader(value = "X-Yona-Internal-Secret", required = false) secretHeader: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthenticateResponse> {
        if (!isAuthorizedCaller(secretHeader, httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val principal = sshAuthService.authenticate(request.publicKey)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(AuthenticateResponse(sshAuthService.encodePrincipal(principal)))
    }

    @PostMapping("/internal/ssh/authorize")
    fun authorize(
        @RequestBody request: AuthorizeRequest,
        @RequestHeader(value = "X-Yona-Internal-Secret", required = false) secretHeader: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthorizeResponse> {
        if (!isAuthorizedCaller(secretHeader, httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val principal = sshAuthService.resolvePrincipal(request.principal)
            ?: return ResponseEntity.ok(AuthorizeResponse(false, null, null, "알 수 없는 principal입니다."))

        val authorization = sshAuthService.authorizeGitCommand(principal, request.command)
        return ResponseEntity.ok(
            AuthorizeResponse(
                allowed = authorization.allowed,
                repoDir = authorization.repoDir?.absolutePath,
                service = authorization.service,
                reason = authorization.reason
            )
        )
    }

    private fun isAuthorizedCaller(secretHeader: String?, httpRequest: HttpServletRequest): Boolean {
        if (secretHeader.isNullOrBlank() || secretHeader != secretProvider.secret) {
            return false
        }
        return isLoopbackAddress(httpRequest.remoteAddr)
    }

    private fun isLoopbackAddress(remoteAddr: String?): Boolean {
        if (remoteAddr == null) return false
        return try {
            java.net.InetAddress.getByName(remoteAddr).isLoopbackAddress
        } catch (e: Exception) {
            false
        }
    }
}
