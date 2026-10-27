package dev.yona.config

import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory
import org.springframework.core.Ordered
import org.springframework.stereotype.Component

/**
 * `application.context`(v2 설정 `yona.application-context`)로 사이트 전체를 하위 경로에 올린다.
 *
 * v1.6(Play 2.3)은 이 키 하나가 라우터 접두어이자 링크 접두어였다. v2는 링크용 값([YonaProperties.applicationContext])과 서블릿 컨텍스트
 * 경로(`server.servlet.context-path`)가 따로 있어, 링크만 `/myroot`를 가리키고 실제 주소는 `/`가 되는 불일치가 생길 수 있다.
 * 서버의 컨텍스트 경로를 따로 정하지 않았으면 이 값을 서블릿 컨텍스트 경로로도 쓴다. 앞뒤 슬래시는 있어도 없어도 된다.
 */
@Component
class ApplicationContextCustomizer(private val properties: YonaProperties) : WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>, Ordered {

    override fun customize(factory: ConfigurableServletWebServerFactory) {
        val configured = properties.applicationContext.trim('/')
        if (configured.isEmpty() || !factory.contextPath.isNullOrEmpty()) return
        factory.contextPath = "/$configured"
    }

    /** 서버 속성(`server.servlet.context-path`)을 적용하는 기본 커스터마이저보다 나중에 실행해 그 값을 먼저 존중한다. */
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
