package dev.yona.observability

import dev.yona.domain.ldap.LdapDirectory
import dev.yona.domain.ldap.LdapUser
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import javax.naming.AuthenticationException
import javax.naming.CommunicationException

/**
 * LDAP 인증의 시간과 결과를 재는 [LdapDirectory] 감싸개이다(로드맵 X-10 C5d). LDAP이 느리면 로그인이 멈추므로 결과별 시간이 필요하다.
 * `yona.ldap.authenticate{result}`: `success`, `not_found`(사용자 없음), `auth_failure`(잘못된 자격), `unreachable`(연결 못 함), `error`. 사용자 이름은 태그에 넣지 않는다.
 * 예외는 그대로 다시 던진다(호출하는 쪽의 폴백 동작이 바뀌지 않는다).
 */
class MeteredLdapDirectory(private val delegate: LdapDirectory, private val registry: MeterRegistry) : LdapDirectory {

    override fun authenticate(username: String, password: String): LdapUser? {
        val sample = Timer.start(registry)
        var result = "error"
        try {
            val user = delegate.authenticate(username, password)
            result = if (user == null) "not_found" else "success"
            return user
        } catch (e: AuthenticationException) {
            result = "auth_failure"
            throw e
        } catch (e: CommunicationException) {
            result = "unreachable"
            throw e
        } finally {
            sample.stop(Timer.builder("yona.ldap.authenticate").tag("result", result).description("LDAP 인증 시간").register(registry))
        }
    }
}
