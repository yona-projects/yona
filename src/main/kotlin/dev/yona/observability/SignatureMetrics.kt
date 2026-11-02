package dev.yona.observability

import dev.yona.domain.gpgkey.GpgReason
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 서명 검증의 지표이다(추가 개발 항목 X-4, `decisions.md` D-65). `yona.signature.verifications{target, reason, format}`는 판정마다 센다. `target`은 `commit`이나 `tag`이고
 * `reason`은 [GpgReason]의 코드(최대 10가지)이며 `format`은 서명 형식(`gpg`, `ssh`)이다. SSH 서명(D-68)도 이 지표에 세며 `format`으로 나누어 볼 수 있다. 로그인 ID, 키 ID, 이메일은 태그에 넣지 않는다. `unknown_key`나 `invalid`의 급증은 키 등록 누락이나 위조 시도의 신호이다.
 */
@Component
class SignatureMetrics(private val registry: MeterRegistry) {

    fun verification(target: String, reason: GpgReason, format: String = "gpg") {
        Counter.builder("yona.signature.verifications").tags("target", target, "reason", reason.code, "format", format).description("서명 검증 판정 수").register(registry).increment()
    }
}
