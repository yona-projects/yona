package dev.yona.observability

import dev.yona.domain.gpgkey.GpgReason
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * GPG 서명 검증의 지표이다(추가 개발 항목 X-4, `decisions.md` D-65). `yona.gpg.verifications{target, reason}`는 판정마다 센다. `target`은 `commit`이나 `tag`이고
 * `reason`은 [GpgReason]의 코드(최대 10가지)이다. 로그인 ID, 키 ID, 이메일은 태그에 넣지 않는다. `unknown_key`나 `invalid`의 급증은 키 등록 누락이나 위조 시도의 신호이다.
 */
@Component
class GpgMetrics(private val registry: MeterRegistry) {

    fun verification(target: String, reason: GpgReason) {
        Counter.builder("yona.gpg.verifications").tags("target", target, "reason", reason.code).description("GPG 서명 검증 판정 수").register(registry).increment()
    }
}
