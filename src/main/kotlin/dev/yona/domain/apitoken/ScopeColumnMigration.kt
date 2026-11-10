package dev.yona.domain.apitoken

/**
 * 쉼표로 이은 옛 범위 문자열(`api_token.scopes`, `device_authorization.scopes`)을 범위 행으로 옮기는 규칙이다(`decisions.md` D-126).
 *
 * 이전 빌드는 옛 토큰(v1.6 사용자 토큰)에 고를 수 있는 모든 범위를 적어 두었다. v1.6 토큰에는 범위가 없었으므로, 옛 토큰 표시가 있고
 * 모든 범위가 적힌 문자열은 범위 행을 만들지 않는다(범위 제한 없음). 사용자가 줄여 저장한 범위는 그대로 옮긴다.
 */
object ScopeColumnMigration {

    /**
     * [raw]를 범위 집합으로 읽는다. 공백과 중복은 정리하고 모르는 범위는 버린다. 순서는 적힌 순서이다.
     * [legacy]이고 고를 수 있는 모든 범위를 가졌다면 빈 집합이다.
     */
    fun split(raw: String?, legacy: Boolean): Set<String> {
        val known = ApiScopes.selectable(siteManager = true).toSet()
        val parsed = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && it in known }.toCollection(LinkedHashSet())
        return if (legacy && parsed.containsAll(known)) emptySet() else parsed
    }
}
