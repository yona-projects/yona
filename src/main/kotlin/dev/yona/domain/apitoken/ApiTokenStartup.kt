package dev.yona.domain.apitoken

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 서버를 시작할 때 v1.6의 사용자 토큰을 새 구조로 옮기고 오래 쓰지 않은 토큰을 지운다(추가 개발 항목 X-5, `decisions.md` D-36). 모두 멱등하다.
 * 데이터를 가져온 뒤에도 [ApiTokenService.migrateLegacyTokens]를 부른다.
 */
@Component
class ApiTokenStartup(private val service: ApiTokenService) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        service.migrateLegacyTokens()
        service.deleteUnused()
    }
}
