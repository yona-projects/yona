package dev.yona.domain.apitoken

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 쉼표로 이은 옛 범위 문자열을 범위 행으로 옮길 때의 규칙을 검증한다. 옛 토큰은 범위가 없었으므로 행을 만들지 않는다. */
class ApiScopeColumnMigrationTest {

    @Test
    fun `쉼표로 이은 문자열은 공백과 중복을 정리해 범위 집합이 된다`() {
        assertEquals(setOf("repo", "user"), ScopeColumnMigration.split("repo, user,,repo", legacy = false))
    }

    @Test
    fun `비었거나 널이면 빈 집합이다`() {
        assertEquals(emptySet(), ScopeColumnMigration.split(null, legacy = false))
        assertEquals(emptySet(), ScopeColumnMigration.split("  ", legacy = false))
    }

    @Test
    fun `옛 토큰 표시가 있고 문자열이 모든 범위이면 행을 만들지 않는다`() {
        val all = ApiScopes.format(ApiScopes.selectable(siteManager = true))
        assertEquals(emptySet(), ScopeColumnMigration.split(all, legacy = true))
    }

    @Test
    fun `옛 토큰 표시가 있어도 사용자가 범위를 줄인 문자열은 그대로 옮긴다`() {
        assertEquals(setOf("read:user"), ScopeColumnMigration.split("read:user", legacy = true))
    }

    @Test
    fun `모르는 범위는 옮기지 않는다`() {
        assertEquals(setOf("repo"), ScopeColumnMigration.split("repo,not_a_scope", legacy = false))
    }
}
