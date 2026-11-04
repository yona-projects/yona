package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Hashtable
import javax.naming.Context
import javax.naming.directory.Attribute
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

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

    /** 검색 조건이다. 사용자 입력의 특수 문자를 RFC 4515로 이스케이프한다. v1.6은 그대로 이어 붙여 검색 조건을 바꿀 수 있었다(R-60-2). */
    fun filterOf(username: String): String = "(${filterAttributeOf(username)}=${escapeFilterValue(username)})"

    companion object {
        /** RFC 4515: `\`, `*`, `(`, `)`, NUL을 `\XX` 16진수로 바꾼다. */
        fun escapeFilterValue(value: String): String = buildString {
            for (c in value) when (c) {
                '\\' -> append("\\5c"); '*' -> append("\\2a"); '(' -> append("\\28"); ')' -> append("\\29"); '\u0000' -> append("\\00")
                else -> append(c)
            }
        }

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
 * [LdapDirectory]의 JNDI 구현이다. v1.6 `utils.LdapService.authenticate`(`app/utils/LdapService.java:41-130`)에 해당한다.
 *
 * 연결, 읽기, 검색 제한 시간은 각각 5초이다. 검색 조건과 바인드 주체의 사용자 입력은 이스케이프한다(v1.6은 이스케이프 없이 이어 붙이고 연결 제한 시간만 있었다, R-60-2, R-60-10).
 */
class JndiLdapDirectory(private val config: YonaProperties.Ldap, private val protocol: String) : LdapDirectory {

    private val queries = LdapQueries(config.userNameProperty, config.distinguishedNamePostfix, config.loginProperty, config.emailProperty)

    /** 검색 제한 시간(밀리초)이다. v1.6은 연결 제한 시간만 있어 응답하지 않는 서버가 로그인 요청 스레드를 붙잡았다(R-60-10). */
    val searchTimeLimitMillis: Int get() = TIMEOUT

    /** 접속 환경이다. 연결과 읽기 제한 시간을 둔다. */
    fun environmentFor(username: String, password: String): Hashtable<String, String> = Hashtable<String, String>().apply {
        put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put("com.sun.jndi.ldap.connect.timeout", TIMEOUT.toString())
        put("com.sun.jndi.ldap.read.timeout", TIMEOUT.toString())
        put(Context.PROVIDER_URL, "$protocol://${config.host}:${config.port}")
        put(Context.SECURITY_AUTHENTICATION, "simple")
        put(Context.SECURITY_PRINCIPAL, queries.principalOf(username))
        put(Context.SECURITY_CREDENTIALS, password)
    }

    override fun authenticate(username: String, password: String): LdapUser? {
        val env = environmentFor(username, password)
        val context = InitialDirContext(env)
        try {
            val controls = SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE; timeLimit = TIMEOUT }
            val results = context.search(config.baseDn, queries.filterOf(username), controls)
            if (!results.hasMoreElements()) return null
            val found = results.nextElement() as SearchResult
            // 일치하는 항목은 하나여야 한다.
            if (results.hasMoreElements()) return null
            val attributes = found.attributes
            fun text(attribute: Attribute?): String = attribute?.get()?.toString().orEmpty()
            return LdapUser(
                displayName = text(attributes.get(config.displayNameProperty)),
                email = text(attributes.get(config.emailProperty)),
                loginId = text(attributes.get(config.loginProperty)),
                department = text(attributes.get("department")),
                englishName = if (config.englishNameAttributeName.isNotBlank()) text(attributes.get(config.englishNameAttributeName)) else ""
            )
        } finally {
            context.close()
        }
    }

    private companion object {
        const val TIMEOUT = 5000
    }
}

/**
 * 인증할 때마다 지금 적용되는 LDAP 설정([LdapSettingsService])으로 [JndiLdapDirectory]를 만들어 쓴다. 관리자가 화면에서 설정을 바꾸면 재시작 없이 반영된다.
 */
class DynamicLdapDirectory(private val settings: LdapSettingsService) : LdapDirectory {
    override fun authenticate(username: String, password: String): LdapUser? = JndiLdapDirectory(settings.effective(), settings.protocol()).authenticate(username, password)
}

/** [LdapDirectory]를 설정에서 만든다. */
@Configuration
class LdapConfiguration {

    @Bean
    fun ldapDirectory(settings: LdapSettingsService, registry: io.micrometer.core.instrument.MeterRegistry): LdapDirectory =
        dev.yona.observability.MeteredLdapDirectory(DynamicLdapDirectory(settings), registry)
}
