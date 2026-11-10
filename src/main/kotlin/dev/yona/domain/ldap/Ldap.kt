package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.ldap.support.LdapUtils
import org.springframework.security.ldap.DefaultSpringSecurityContextSource
import org.springframework.security.ldap.SpringSecurityLdapTemplate
import javax.naming.directory.SearchControls

/**
 * LDAP 서버가 알려 준 사용자이다. v1.6 `models.support.LdapUser`(`app/models/support/LdapUser.java`)에 해당한다.
 *
 * @property displayName 표시 이름 속성의 값
 * @property email 이메일 속성의 값. 속성이 없으면 빈 문자열
 * @property loginId 로그인 이름 속성의 값
 * @property department 부서(`department` 속성). 없으면 빈 문자열
 * @property englishName 영문 이름. 읽지 않으면 빈 문자열
 */
data class LdapUser(val displayName: String, val email: String, val loginId: String, val department: String, val englishName: String) {

    /** 부서가 있으면 `이름 [부서]` 모양의 표시 이름이다 (v1.6 `getDisplayName`). */
    val displayNameWithDepartment: String get() = if (department.isNotBlank()) "$displayName [$department]" else displayName
}

/**
 * LDAP 서버에 사용자로 바인드해 인증하고 사용자 정보를 읽는다. 테스트에서는 이 인터페이스를 바꿔 끼운다.
 */
fun interface LdapDirectory {

    /**
     * 사용자로 바인드하고 기준 DN 아래에서 그 사용자를 검색한다.
     *
     * @return 찾은 사용자. 검색 결과가 없거나 둘 이상이면 `null`
     * @throws javax.naming.AuthenticationException 바인드에 실패했을 때
     * @throws javax.naming.CommunicationException 서버에 연결하지 못했을 때
     * @throws javax.naming.NamingException 그 밖의 오류
     */
    @Throws(javax.naming.NamingException::class)
    fun authenticate(username: String, password: String): LdapUser?
}

/**
 * 바인드 주체와 검색 조건을 정한다 (v1.6 `getProperUsernameGuessing`, `searchFilter`). 이름에 `@`가 있으면 이메일로 보고
 * 그대로 주체로 쓰며 이메일 속성으로 찾고, 없으면 `<userNameProperty>=<이름>,<postfix>`를 주체로 하고 로그인 속성으로 찾는다.
 */
class LdapQueries(
    private val userNameProperty: String,
    private val distinguishedNamePostfix: String,
    private val loginProperty: String,
    private val emailProperty: String
) {
    /** 바인드 주체이다. */
    fun principalOf(username: String): String = if (username.contains("@")) username else "$userNameProperty=${escapeDnValue(username)},$distinguishedNamePostfix"

    /** 검색에 쓸 속성 이름이다. */
    fun filterAttributeOf(username: String): String = if (username.contains("@")) emailProperty else loginProperty

    companion object {
        /** RFC 4514: DN 값의 구분자(`,`, `+`, `"`, `\`, `<`, `>`, `;`, `=`)와 앞의 `#`, 앞뒤 공백을 `\`로 이스케이프한다. */
        fun escapeDnValue(value: String): String = buildString {
            value.forEachIndexed { i, c ->
                if (c in ",+\"\\<>;=" || (i == 0 && (c == '#' || c == ' ')) || (i == value.length - 1 && c == ' ')) append('\\')
                append(c)
            }
        }
    }
}

/**
 * [LdapDirectory]의 스프링 시큐리티 LDAP 구현이다(로드맵 X-43, `decisions.md` D-132, D-133). v1.6 `utils.LdapService.authenticate`(`app/utils/LdapService.java:41-130`)에 해당한다.
 *
 * 접속은 [DefaultSpringSecurityContextSource]가, 검색은 스프링 시큐리티가 `BindAuthenticator`에서 쓰는 검색 도우미
 * ([SpringSecurityLdapTemplate.searchForSingleEntryInternal])가 맡는다.
 *
 * **1. 먼저 검색해 DN을 찾고 그 DN으로 바인드한다**(D-133). 검색은 조회용 계정([YonaProperties.Ldap.bindDn])이 있으면 그 계정으로, 없으면 익명으로 한다. 검색 조건은 입력에 `@`가 있으면
 * 이메일 속성, 없으면 로그인 속성이다. DN을 만들지 않고 찾으므로 `CN`이 로그인 ID와 달라도 되고 이메일로도 로그인된다. 찾은 사용자가 둘 이상이면 `null`이다.
 * 바인드한 연결로 그 사용자의 속성을 읽는다.
 *
 * **2. 익명 검색이 안 되면 이전 방식으로 되돌아간다**(v1.6 방식, D-132). 조회용 계정이 없고 서버가 익명 검색을 막았거나 사용자를 찾지 못했을 때, 입력으로 바인드 주체를 만들어
 * 그 사용자로 바인드한 뒤 그 연결로 검색한다. 기존 설치는 설정을 바꾸지 않아도 이전과 같이 동작한다. 조회용 계정이 있으면 되돌아가지 않는다(설정 오류를 숨기지 않는다).
 *
 * 연결과 읽기, 검색 제한 시간은 각각 5초이다. 검색 조건의 사용자 입력은 매개변수(`{0}`)로 넘겨 JNDI가 이스케이프하고, 바인드 주체의 DN 값은 [LdapQueries]가 이스케이프한다
 * (v1.6은 이스케이프 없이 이어 붙이고 연결 제한 시간만 있었다, R-60-2, R-60-10).
 *
 * 예외는 JNDI 예외로 알린다. 스프링 LDAP는 JNDI 예외를 감싸 던지므로 원인을 꺼내 다시 던진다([LdapAuthService]가 `AuthenticationException`, `CommunicationException`으로 가린다).
 * 조회용 계정의 바인드 실패는 사용자의 잘못된 비밀번호로 오해하지 않도록 `AuthenticationException`이 아닌 일반 [javax.naming.NamingException]으로 알린다.
 */
class SpringLdapDirectory(private val config: YonaProperties.Ldap, private val protocol: String) : LdapDirectory {

    private val queries = LdapQueries(config.userNameProperty, config.distinguishedNamePostfix, config.loginProperty, config.emailProperty)

    /** 검색 제한 시간(밀리초)이다. v1.6은 연결 제한 시간만 있어 응답하지 않는 서버가 로그인 요청 스레드를 붙잡았다(R-60-10). */
    val searchTimeLimitMillis: Int get() = TIMEOUT

    /** 접속 환경에 더하는 JNDI 속성이다. 연결과 읽기 제한 시간을 둔다. */
    val baseEnvironment: Map<String, String> = mapOf(
        "com.sun.jndi.ldap.connect.timeout" to TIMEOUT.toString(),
        "com.sun.jndi.ldap.read.timeout" to TIMEOUT.toString()
    )

    private val contextSource = DefaultSpringSecurityContextSource("$protocol://${config.host}:${config.port}").apply {
        setBaseEnvironmentProperties(baseEnvironment)
        isAnonymousReadOnly = true
        afterPropertiesSet()
    }

    /** 사용자를 찾은 결과이다. */
    private sealed interface Lookup {
        /** 한 명을 찾았다. [dn]은 바인드할 DN이다. */
        data class Found(val dn: String) : Lookup

        /** 둘 이상이라 사용자를 정할 수 없다. */
        data object Ambiguous : Lookup

        /** 조회용 계정이 있는데 사용자를 찾지 못했다. */
        data object Missing : Lookup

        /** 조회용 계정이 없고 익명 검색이 되지 않았거나 사용자를 찾지 못했다. 이전 방식으로 확인한다. */
        data object Unavailable : Lookup
    }

    override fun authenticate(username: String, password: String): LdapUser? = when (val found = lookup(username)) {
        is Lookup.Found -> authenticateAs(found.dn, password)
        Lookup.Ambiguous, Lookup.Missing -> null
        Lookup.Unavailable -> bindThenSearch(username, password)
    }

    private val serviceAccount: Boolean get() = config.bindDn.isNotBlank()

    /** 조회용 계정으로 바인드할 수 있는지 확인한다(설정 화면의 연결 확인). 바인드에 실패하면 JNDI 예외를 던진다. */
    fun checkServiceAccount() {
        val context = try {
            contextSource.getContext(config.bindDn, config.bindPassword)
        } catch (e: org.springframework.ldap.NamingException) {
            throw unwrap(e)
        }
        LdapUtils.closeContext(context)
    }

    /** 조회용 계정(없으면 익명)으로 [username]의 항목을 찾는다. 서버에 연결하지 못하면 통신 예외를 그대로 던진다. */
    private fun lookup(username: String): Lookup {
        val context = try {
            if (serviceAccount) contextSource.getContext(config.bindDn, config.bindPassword) else contextSource.readOnlyContext
        } catch (e: org.springframework.ldap.NamingException) {
            return failedLookup(unwrap(e))
        }
        try {
            val controls = SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE; timeLimit = TIMEOUT; returningAttributes = arrayOf("1.1") }
            val entry = try {
                SpringSecurityLdapTemplate.searchForSingleEntryInternal(context, controls, config.baseDn, "(${queries.filterAttributeOf(username)}={0})", arrayOf<Any>(username))
            } catch (e: IncorrectResultSizeDataAccessException) {
                return if (e.actualSize > 1) Lookup.Ambiguous else if (serviceAccount) Lookup.Missing else Lookup.Unavailable
            }
            return Lookup.Found(entry.nameInNamespace)
        } catch (e: javax.naming.NamingException) {
            return failedLookup(e)
        } finally {
            LdapUtils.closeContext(context)
        }
    }

    /** 검색 단계의 실패를 정한다. 서버에 연결하지 못한 것은 그대로 알리고, 조회용 계정의 실패는 설정 오류이고, 익명 검색의 실패는 이전 방식으로 되돌아간다. */
    private fun failedLookup(cause: javax.naming.NamingException): Lookup {
        if (cause is javax.naming.CommunicationException) throw cause
        if (serviceAccount) throw javax.naming.NamingException("LDAP 조회용 계정으로 사용자를 찾지 못했습니다: ${cause.message}").also { it.initCause(cause) }
        return Lookup.Unavailable
    }

    /** 찾은 [dn]으로 바인드하고, 바인드한 연결로 그 사용자의 속성을 읽는다. 비밀번호가 틀리면 인증 예외이다. */
    private fun authenticateAs(dn: String, password: String): LdapUser {
        val context = try {
            contextSource.getContext(dn, password)
        } catch (e: org.springframework.ldap.NamingException) {
            throw unwrap(e)
        }
        try {
            return toUser(context.getAttributes(javax.naming.ldap.LdapName(dn)))
        } finally {
            LdapUtils.closeContext(context)
        }
    }

    /** v1.6 방식이다. 입력으로 바인드 주체를 만들어 그 사용자로 바인드하고, 바인드한 연결로 기준 DN 아래에서 그 사용자를 찾는다. */
    private fun bindThenSearch(username: String, password: String): LdapUser? {
        val context = try {
            contextSource.getContext(queries.principalOf(username), password)
        } catch (e: org.springframework.ldap.NamingException) {
            throw unwrap(e)
        }
        try {
            val controls = SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE; timeLimit = TIMEOUT }
            // 일치하는 항목은 하나여야 한다. 없거나 둘 이상이면 스프링이 던지는 예외를 null로 바꾼다
            val entry = try {
                SpringSecurityLdapTemplate.searchForSingleEntryInternal(context, controls, config.baseDn, "(${queries.filterAttributeOf(username)}={0})", arrayOf<Any>(username))
            } catch (e: IncorrectResultSizeDataAccessException) {
                return null
            }
            return toUser(entry.attributes)
        } finally {
            LdapUtils.closeContext(context)
        }
    }

    private fun toUser(attributes: javax.naming.directory.Attributes): LdapUser {
        fun text(name: String): String = attributes.get(name)?.get()?.toString().orEmpty()
        return LdapUser(
            displayName = text(config.displayNameProperty),
            email = text(config.emailProperty),
            loginId = text(config.loginProperty),
            department = text("department"),
            englishName = if (config.englishNameAttributeName.isNotBlank()) text(config.englishNameAttributeName) else ""
        )
    }

    /** 스프링 LDAP가 감싼 JNDI 예외를 꺼낸다. 원인이 JNDI 예외가 아니면 같은 메시지의 일반 JNDI 예외로 바꾼다. */
    private fun unwrap(e: org.springframework.ldap.NamingException): javax.naming.NamingException =
        e.cause as? javax.naming.NamingException ?: javax.naming.NamingException(e.message).also { it.initCause(e) }

    private companion object {
        const val TIMEOUT = 5000
    }
}

/**
 * 인증할 때마다 지금 적용되는 LDAP 설정([LdapSettingsService])으로 [SpringLdapDirectory]를 만들어 쓴다. 관리자가 화면에서 설정을 바꾸면 재시작 없이 반영된다.
 */
class DynamicLdapDirectory(private val settings: LdapSettingsService) : LdapDirectory {
    override fun authenticate(username: String, password: String): LdapUser? = SpringLdapDirectory(settings.effective(), settings.protocol()).authenticate(username, password)
}

/** [LdapDirectory]를 설정에서 만든다. */
@Configuration
class LdapConfiguration {

    @Bean
    fun ldapDirectory(settings: LdapSettingsService, registry: io.micrometer.core.instrument.MeterRegistry): LdapDirectory =
        dev.yona.observability.MeteredLdapDirectory(DynamicLdapDirectory(settings), registry)
}
