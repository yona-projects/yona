package com.github.yonaprojects.yona.domain.user

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Hashtable
import java.util.Optional
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.Context
import javax.naming.NamingException
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

sealed class LdapAuthResult {
    data class Success(val user: LdapUser) : LdapAuthResult()
    object InvalidCredentials : LdapAuthResult()
    data class ConnectionFailed(val cause: Exception) : LdapAuthResult()
}

/**
 * yona의 utils/LdapService.java 대응. 실제 LDAP 서버로의 JNDI 바인딩만 담당하는
 * 얇은 글루 클래스 — 순수 로직은 LdapQueryBuilder로 분리되어 있다.
 * 2026-08-25: `LdapServiceSpec`이 Testcontainers(osixia/openldap)로 실제 LDAP 서버를 띄워
 * 실제 bind 경로까지 검증한다(InitialDirContext 생성자는 mockkConstructor로 가로챌 수 없어
 * 실제 서버가 필요했음) — search() 결과만 mockkConstructor로 시나리오별 오버라이드한다.
 */
@Component
class LdapService(
    @Value("\${yona.ldap.enabled:false}")
    val enabled: Boolean,
    @Value("\${yona.ldap.fallback-to-local-login:false}")
    val fallbackToLocalLogin: Boolean,
    @Value("\${yona.ldap.host:127.0.0.1}")
    private val host: String,
    @Value("\${yona.ldap.port:389}")
    private val port: String,
    @Value("\${yona.ldap.protocol:ldap}")
    private val protocol: String,
    @Value("\${yona.ldap.base-dn:}")
    private val baseDn: String,
    @Value("\${yona.ldap.dn-postfix:}")
    private val dnPostfix: String,
    @Value("\${yona.ldap.login-property:sAMAccountName}")
    private val loginProperty: String,
    @Value("\${yona.ldap.display-name-property:displayName}")
    private val displayNameProperty: String,
    @Value("\${yona.ldap.user-name-property:CN}")
    private val userNameProperty: String,
    @Value("\${yona.ldap.email-property:mail}")
    private val emailProperty: String,
    @Value("\${yona.ldap.department-property:department}")
    private val departmentProperty: String,
    @Value("\${yona.ldap.english-name-property:}")
    private val englishNameProperty: String,
    @Value("\${yona.ldap.use-email-base-login:false}")
    private val useEmailBaseLogin: Boolean,
    @Value("\${yona.ldap.guest-login-id-prefix:}")
    private val guestLoginIdPrefix: String,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(LdapService::class.java)
    private val timeoutMs = 5000

    fun authenticate(loginIdOrEmail: String, password: String): LdapAuthResult {
        val guessedIdentity = LdapQueryBuilder.guessUser(loginIdOrEmail, useEmailBaseLogin) { candidate ->
            userRepository.findByLoginId(candidate).map { it.email }.orElse(null)
        }

        val env = Hashtable<String, String>()
        env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
        env["com.sun.jndi.ldap.connect.timeout"] = timeoutMs.toString()
        env[Context.PROVIDER_URL] = "$protocol://$host:$port"
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = LdapQueryBuilder.properPrincipal(guessedIdentity, userNameProperty, dnPostfix)
        env[Context.SECURITY_CREDENTIALS] = password

        return try {
            val ctx = InitialDirContext(env)
            val searchResult = findUser(ctx, guessedIdentity)
                ?: return LdapAuthResult.InvalidCredentials

            val ldapUser = LdapQueryBuilder.parseLdapUser(
                searchResult.attributes,
                displayNameProperty, emailProperty, loginProperty, departmentProperty,
                englishNameProperty.takeIf { it.isNotBlank() }
            )
            val withGuestFlag = ldapUser.copy(
                isGuestUser = LdapQueryBuilder.isGuestUser(ldapUser.loginId, guestLoginIdPrefix)
            )
            LdapAuthResult.Success(withGuestFlag)
        } catch (e: AuthenticationException) {
            logger.warn("LDAP 인증 실패: $loginIdOrEmail")
            LdapAuthResult.InvalidCredentials
        } catch (e: CommunicationException) {
            logger.error("LDAP 서버에 연결할 수 없습니다", e)
            LdapAuthResult.ConnectionFailed(e)
        } catch (e: NamingException) {
            logger.error("LDAP 조회 중 오류가 발생했습니다", e)
            LdapAuthResult.ConnectionFailed(e)
        }
    }

    private fun findUser(ctx: InitialDirContext, identity: String): SearchResult? {
        val filterAttribute = LdapQueryBuilder.searchFilterAttribute(identity, emailProperty, loginProperty)
        val searchFilter = "($filterAttribute=$identity)"

        val searchControls = SearchControls()
        searchControls.searchScope = SearchControls.SUBTREE_SCOPE

        val results = ctx.search(baseDn, searchFilter, searchControls)
        if (!results.hasMoreElements()) {
            return null
        }
        val searchResult = results.nextElement()
        if (results.hasMoreElements()) {
            logger.warn("동일한 식별자로 여러 LDAP 사용자가 매칭되었습니다: $identity")
            return null
        }
        return searchResult
    }
}
