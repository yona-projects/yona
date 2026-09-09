package com.github.yonaprojects.yona.config

import org.hibernate.JDBCException
import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.dialect.TimeZoneSupport
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.spi.SQLExceptionConversionDelegate
import org.hibernate.type.SqlTypes
import java.sql.SQLException
import java.sql.Types

/**
 * CUBRID 지원용 커스텀 방언. org.hibernate.community.dialect.CUBRIDDialect를 그대로 쓰면
 * CUBRID JDBC 드라이버(11.3.2.0053)와 실제로 안 맞는 지점이 두 군데 있어 우회한다(둘 다 실측
 * 재현 — 방언은 지원한다고 광고하지만 드라이버가 그 바인딩을 못 받는 유형의 결함).
 *
 * 1. BOOLEAN을 CUBRID의 `bit` 타입으로 매핑하는데(getPreferredSqlTypeCodeForBoolean() ==
 *    Types.BIT), 드라이버가 이 bit 바인드 파라미터를 받아들이지 못해 "Cannot coerce host var
 *    to type bit"로 매번 INSERT/UPDATE가 실패한다(CUBRID 커뮤니티 Q&A에도 보고된 결함).
 *    bit 대신 smallint(0/1)로 매핑해 우회한다.
 * 2. getTimeZoneSupport()가 NATIVE라 Instant 컬럼이 datetimetz 타입 + OffsetDateTime 기반
 *    바인딩(TimestampUtcAsOffsetDateTimeJdbcType)으로 매핑되는데, 드라이버의
 *    PreparedStatement.setObject()가 이 바인딩을 거부한다(IllegalArgumentException, 메시지
 *    없음). NONE으로 낮춰 평범한 java.sql.Timestamp 기반 바인딩(TIMESTAMP)을 쓰게 한다.
 */
class YonaCubridDialect : CUBRIDDialect() {
    override fun getPreferredSqlTypeCodeForBoolean(): Int = Types.SMALLINT

    override fun columnType(sqlTypeCode: Int): String {
        if (sqlTypeCode == SqlTypes.BOOLEAN) {
            return "smallint"
        }
        return super.columnType(sqlTypeCode)
    }

    override fun getTimeZoneSupport(): TimeZoneSupport = TimeZoneSupport.NONE

    /**
     * CUBRID JDBC 드라이버(11.3.2.0053)는 NOT NULL 제약 위반 시 SQLState를 아예 안 주고
     * (getSQLState() == null) 벤더 고유 errorCode만 준다(실측 확인, ApiTokenSpec의
     * "expiresAt이 null이면 저장이 거부되어야 한다" 테스트로 재현). org.hibernate.community.
     * dialect.CUBRIDDialect는 buildSQLExceptionConversionDelegate()를 오버라이드하지 않아
     * (부모 Dialect 기본 구현이 null 반환) SQLState 기반 표준 분류에 의존하는데, SQLState가
     * 없으니 분류가 실패해 org.hibernate.exception.GenericJDBCException으로 떨어지고
     * Spring이 이를 org.springframework.orm.jpa.JpaSystemException으로 감싼다(MariaDB/
     * Postgres/MySQL/SQL Server는 모두 SQLState 23502를 정상 반환해 표준 분류가 동작하고
     * DataIntegrityViolationException으로 번역된다).
     *
     * errorCode -631("SQL statement violated NOT NULL constraint.")만 좁게 매칭해
     * ConstraintViolationException(NOT_NULL)으로 명시 변환한다 — 연결 끊김 등 다른 종류의
     * SQLException(errorCode가 -631이 아님)까지 잘못 분류하지 않도록 이 코드 하나에만
     * 한정한다. 이렇게 분류되면 Spring이 DataIntegrityViolationException으로 번역해 나머지
     * 4개 DB와 동일한 예외 타입을 던지게 된다.
     */
    override fun buildSQLExceptionConversionDelegate(): SQLExceptionConversionDelegate =
        object : SQLExceptionConversionDelegate {
            override fun convert(
                sqlException: SQLException,
                message: String,
                sql: String
            ): JDBCException? {
                return if (sqlException.errorCode == CUBRID_NOT_NULL_VIOLATION_ERROR_CODE) {
                    ConstraintViolationException(
                        message,
                        sqlException,
                        sql,
                        ConstraintViolationException.ConstraintKind.NOT_NULL,
                        getViolatedConstraintNameExtractor().extractConstraintName(sqlException)
                    )
                } else {
                    null
                }
            }
        }

    private companion object {
        // CUBRID JDBC 드라이버(11.3.2.0053)가 NOT NULL 제약 위반 시 실제로 던지는 errorCode.
        // (SQLState는 null이라 쓸 수 없다 — 실측 확인.)
        const val CUBRID_NOT_NULL_VIOLATION_ERROR_CODE = -631
    }
}
