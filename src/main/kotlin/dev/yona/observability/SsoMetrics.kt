package dev.yona.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 엔터프라이즈 SSO 로그인의 지표이다(추가 개발 항목 X-16, `decisions.md` D-80). `yona.sso.logins{protocol, result, initiated}`: `protocol`은 `oidc`, `saml2`이고 `result`는 `success`,
 * `rejected`(계정 규칙으로 거절: 이메일 미확인, 잠김 등), `failed`(프로토콜 검증 실패: 서명, 만료 등)이다. 태그는 고정된 값만 쓰며 사용자 정보를 넣지 않는다.
 */
@Component
class SsoMetrics(private val registry: MeterRegistry) {

    fun login(protocol: String, result: String, initiated: Boolean = false) {
        Counter.builder("yona.sso.logins")
            .tags("protocol", if (protocol in PROTOCOLS) protocol else "other", "result", if (result in RESULTS) result else "other", "initiated", initiated.toString())
            .description("SSO 로그인 결과 수").register(registry).increment()
    }

    private companion object {
        val PROTOCOLS = setOf("oidc", "saml2")
        val RESULTS = setOf("success", "rejected", "failed")
    }
}
