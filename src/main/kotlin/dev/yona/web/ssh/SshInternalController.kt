package dev.yona.web.ssh

import dev.yona.ssh.SshAccess
import dev.yona.ssh.SshInternalSecret
import dev.yona.ssh.SshRelayTokens
import dev.yona.domain.sshkey.SshKeyException
import dev.yona.domain.sshkey.SshKeyParser
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.security.MessageDigest

/**
 * 시스템 sshd의 `AuthorizedKeysCommand` 훅이 부르는 내부 인증 주소이다(추가 개발 항목 X-3b, `decisions.md` D-67). 훅이 접속자의 공개키(`종류 본문`)를 보내면 등록된 사용자 키나 배포 키인지
 * 확인해 중계 소켓에 제시할 인증 표([SshRelayTokens])를 돌려준다. 알 수 없는 키와 잠기거나 삭제된 사용자의 키는 404이다. 응답에는 표만 있고 사용자 정보는 없다.
 *
 * 같은 호스트의 sshd가 부르는 것을 전제로 하므로 **루프백 주소에서 온 요청이고 공유 비밀 값(`X-Yona-Internal-Secret`)이 맞을 때만** 답한다(둘 중 하나라도 어긋나면 403).
 * `yona.ssh.relay.enabled`를 켜지 않으면 이 컨트롤러는 만들어지지 않는다.
 */
@Controller
@ResponseBody
@ConditionalOnProperty(prefix = "yona.ssh.relay", name = ["enabled"], havingValue = "true")
class SshInternalController(private val access: SshAccess, private val secret: SshInternalSecret, private val tokens: SshRelayTokens) {

    private val mapper = JsonMapper.builder().build()

    @PostMapping("/internal/ssh/authenticate", consumes = [MediaType.ALL_VALUE])
    fun authenticate(@RequestHeader(value = "X-Yona-Internal-Secret", required = false) given: String?, request: HttpServletRequest): ResponseEntity<Any> {
        if (!authorizedCaller(given, request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val publicKey = try {
            mapper.readTree(request.inputStream.readBytes()).get("publicKey")?.takeIf { it.isString }?.asString("")
        } catch (e: Exception) {
            null
        } ?: return ResponseEntity.badRequest().build()
        val parsed = try {
            SshKeyParser.parse(publicKey)
        } catch (e: SshKeyException) {
            return ResponseEntity.badRequest().build()
        }
        val principal = access.authenticate(parsed.fingerprint) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("principal" to tokens.issue(principal)))
    }

    private fun authorizedCaller(given: String?, request: HttpServletRequest): Boolean {
        if (given.isNullOrEmpty() || !MessageDigest.isEqual(given.toByteArray(), secret.value.toByteArray())) return false
        return try {
            InetAddress.getByName(request.remoteAddr).isLoopbackAddress
        } catch (e: Exception) {
            false
        }
    }
}
