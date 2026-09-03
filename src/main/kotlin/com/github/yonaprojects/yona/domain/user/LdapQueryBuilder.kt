package com.github.yonaprojects.yona.domain.user

import javax.naming.directory.Attributes

/**
 * yona의 utils/LdapService.java에서 JNDI 연결과 뒤섞여 있던 순수 로직(사용자 식별자
 * 추측, principal 조립, 검색 필터 선택, 검색결과 파싱, 게스트 판별)을 분리했다.
 * JNDI 서버 연결 없이도 단위테스트 가능하도록 LdapService(실제 바인딩 담당)와 분리.
 */
object LdapQueryBuilder {

    fun guessUser(loginIdOrEmail: String, useEmailBaseLogin: Boolean, resolveEmail: (String) -> String?): String {
        if (!useEmailBaseLogin) {
            return loginIdOrEmail
        }
        return resolveEmail(loginIdOrEmail) ?: loginIdOrEmail
    }

    fun searchFilterAttribute(identity: String, emailProperty: String, loginProperty: String): String {
        return if (identity.contains("@")) emailProperty else loginProperty
    }

    fun properPrincipal(identity: String, userNameProperty: String, dnPostfix: String): String {
        return if (identity.contains("@")) identity else "$userNameProperty=$identity,$dnPostfix"
    }

    fun parseLdapUser(
        attributes: Attributes,
        displayNameProperty: String,
        emailProperty: String,
        loginProperty: String,
        departmentProperty: String,
        englishNameProperty: String?
    ): LdapUser {
        val englishName = englishNameProperty
            ?.takeIf { it.isNotBlank() }
            ?.let { attributeString(attributes, it) }
            ?.takeIf { it.isNotBlank() }

        return LdapUser(
            displayName = attributeString(attributes, displayNameProperty),
            email = attributeString(attributes, emailProperty),
            loginId = attributeString(attributes, loginProperty),
            department = attributeString(attributes, departmentProperty).takeIf { it.isNotBlank() },
            englishName = englishName
        )
    }

    fun isGuestUser(loginId: String, prefixConfig: String): Boolean {
        val prefixes = prefixConfig.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (prefixes.isEmpty()) {
            return false
        }
        val lowerLoginId = loginId.lowercase()
        return prefixes.any { lowerLoginId.startsWith(it) }
    }

    private fun attributeString(attributes: Attributes, name: String): String {
        val attribute = attributes.get(name) ?: return ""
        return try {
            attribute.get()?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
