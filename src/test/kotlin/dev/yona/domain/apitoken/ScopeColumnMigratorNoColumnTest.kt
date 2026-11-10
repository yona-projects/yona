package dev.yona.domain.apitoken

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/** 새로 만든 DB에는 옛 범위 열이 없다. 이관은 열이 없으면 아무것도 하지 않고 오류도 내지 않는다. */
class ScopeColumnMigratorNoColumnTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migrator: ScopeColumnMigrator

    @Test
    fun `옛 범위 열이 없으면 이관할 것도 오류도 없다`() {
        assertEquals(0L, migrator.pending())
        assertEquals(0, migrator.migrate())
    }
}
