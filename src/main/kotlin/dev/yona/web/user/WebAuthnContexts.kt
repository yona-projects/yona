package dev.yona.web.user

import dev.yona.domain.twofactor.WebAuthnContext
import jakarta.servlet.http.HttpServletRequest

/** 요청에서 WebAuthn이 쓰는 이 사이트의 신원(호스트 이름과 출처)을 정한다. 역방향 프록시 뒤에서는 서버가 전달 헤더를 따르도록 설정되어 있어야 브라우저가 보는 주소와 같아진다. */
object WebAuthnContexts {

    fun of(request: HttpServletRequest): WebAuthnContext {
        val port = request.serverPort
        val default = (request.scheme == "http" && port == 80) || (request.scheme == "https" && port == 443)
        return WebAuthnContext(request.serverName, "${request.scheme}://${request.serverName}${if (default) "" else ":$port"}")
    }
}
