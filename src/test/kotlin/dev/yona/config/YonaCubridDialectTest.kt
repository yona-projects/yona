package dev.yona.config

import org.hibernate.dialect.TimeZoneSupport
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Cubrid 드라이버가 SQLState를 주지 않아 생기는 예외 분류와 시각 바인딩 설정(X-46, D-85)을 확인한다. */
class YonaCubridDialectTest {

    private val dialect = YonaCubridDialect()

    @Test
    fun `시각은 시간대 기능 없이 일반 타임스탬프로 바인딩한다`() {
        assertEquals(TimeZoneSupport.NONE, dialect.timeZoneSupport)
    }

    @Test
    fun `NOT NULL 위반 오류 코드는 제약 위반 예외로 바꾼다`() {
        val converted = dialect.buildSQLExceptionConversionDelegate().convert(SQLException("not null", null, -631), "insert", "sql")
        assertIs<ConstraintViolationException>(converted)
        assertEquals(ConstraintViolationException.ConstraintKind.NOT_NULL, converted.kind)
    }

    @Test
    fun `유니크 위반 오류 코드는 제약 위반 예외로 바꾸고 다른 오류는 건드리지 않는다`() {
        val unique = dialect.buildSQLExceptionConversionDelegate().convert(SQLException("unique", null, -670), "insert", "sql")
        assertIs<ConstraintViolationException>(unique)
        assertEquals(ConstraintViolationException.ConstraintKind.UNIQUE, unique.kind)
        assertNull(dialect.buildSQLExceptionConversionDelegate().convert(SQLException("lost", null, -21003), "select", "sql"))
    }
}
