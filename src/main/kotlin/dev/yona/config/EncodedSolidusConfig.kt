package dev.yona.config

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

/**
 * 인코딩된 슬래시(`%2F`)가 든 주소를 Tomcat이 400으로 거부하지 않고 그대로 넘기게 한다.
 *
 * v1.6(Play/Netty)은 경로를 가공하지 않고 라우터에 넘겨, `/`가 든 브랜치 이름(`feature%2Fx`)이나 `code/!/src%2Fmain`처럼 `%2F`가 포함된 주소도 라우트가
 * 맞으면 처리하고 맞지 않으면 404로 답한다. Tomcat의 기본값(`reject`)은 이런 요청을 모두 400으로 막으므로 `passthrough`로 바꾼다. 슬래시가 경로 구분자로
 * 해석되지 않고 인코딩된 채로 남아, 라우트 매칭은 v1.6과 같은 조각 단위로 이루어진다. 보안 방화벽 쪽 허용은 [SecurityConfig.httpFirewall]에 있다.
 */
@Component
class EncodedSolidusConfig : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    override fun customize(factory: TomcatServletWebServerFactory) {
        factory.addConnectorCustomizers({ connector -> connector.encodedSolidusHandling = "passthrough" })
    }
}
