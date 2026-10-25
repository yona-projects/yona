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
    fun principalOf(username: String): String = if (username.contains("@")) username else "$userNameProperty=$username,$distinguishedNamePostfix"

    /** 검색에 쓸 속성 이름이다. */
    fun filterAttributeOf(username: String): String = if (username.contains("@")) emailProperty else loginProperty
}

/**
 * [LdapDirectory]의 JNDI 구현이다. v1.6 `utils.LdapService.authenticate`(`app/utils/LdapService.java:41-130`)에 해당한다.
 *
 * 연결 제한 시간은 5초이다. 검색 조건은 이스케이프 없이 이어 붙인다(v1.6과 같다, R-60).
 */
class JndiLdapDirectory(private val config: YonaProperties.Ldap, private val protocol: String) : LdapDirectory {

    private val queries = LdapQueries(config.userNameProperty, config.distinguishedNamePostfix, config.loginProperty, config.emailProperty)

    override fun authenticate(username: String, password: String): LdapUser? {
        val env = Hashtable<String, String>().apply {
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            put("com.sun.jndi.ldap.connect.timeout", TIMEOUT.toString())
            put(Context.PROVIDER_URL, "$protocol://${config.host}:${config.port}")
            put(Context.SECURITY_AUTHENTICATION, "simple")
            put(Context.SECURITY_PRINCIPAL, queries.principalOf(username))
            put(Context.SECURITY_CREDENTIALS, password)
        }
        val context = InitialDirContext(env)
        try {
            val controls = SearchControls().apply { searchScope = SearchControls.SUBTREE_SCOPE }
            val results = context.search(config.baseDn, "(${queries.filterAttributeOf(username)}=$username)", controls)
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

/** [LdapDirectory]를 설정에서 만든다. */
@Configuration
class LdapConfiguration {

    @Bean
    fun ldapDirectory(properties: YonaProperties): LdapDirectory = JndiLdapDirectory(properties.ldap, properties.protocol)
}
