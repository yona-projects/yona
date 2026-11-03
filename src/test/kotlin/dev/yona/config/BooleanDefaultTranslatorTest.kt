package dev.yona.config

import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.SQLServerDialect
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 불리언 기본값 변환(X-46, D-85)이 데이터베이스의 불리언 표기를 따르는지 확인한다. */
class BooleanDefaultTranslatorTest {

    @Test
    fun `불리언 표기가 있는 데이터베이스는 true와 false를 그대로 둔다`() {
        assertEquals("false", BooleanDefaultTranslator.translate("false", H2Dialect()))
        assertEquals("true", BooleanDefaultTranslator.translate("TRUE", H2Dialect()))
    }

    @Test
    fun `불리언 표기가 없는 데이터베이스는 숫자로 바꾼다`() {
        assertEquals("0", BooleanDefaultTranslator.translate("false", SQLServerDialect()))
        assertEquals("1", BooleanDefaultTranslator.translate("true", SQLServerDialect()))
    }

    @Test
    fun `Cubrid는 불리언을 숫자로 저장하므로 숫자로 바꾼다`() {
        assertEquals("0", BooleanDefaultTranslator.translate("false", YonaCubridDialect()))
        assertEquals("1", BooleanDefaultTranslator.translate("true", YonaCubridDialect()))
    }

    @Test
    fun `불리언이 아닌 기본값과 기본값이 없는 열은 건드리지 않는다`() {
        assertEquals("0", BooleanDefaultTranslator.translate("0", SQLServerDialect()))
        assertEquals("'merge'", BooleanDefaultTranslator.translate("'merge'", SQLServerDialect()))
        assertNull(BooleanDefaultTranslator.translate(null, SQLServerDialect()))
    }
}
