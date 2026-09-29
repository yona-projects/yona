package com.github.yonaprojects.yona.config

import org.hibernate.JDBCException
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.dialect.TimeZoneSupport
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder
import org.hibernate.engine.jdbc.env.spi.NameQualifierSupport
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.spi.SQLExceptionConversionDelegate
import org.hibernate.tool.schema.extract.internal.InformationExtractorJdbcDatabaseMetaDataImpl
import org.hibernate.tool.schema.extract.spi.ExtractionContext
import org.hibernate.tool.schema.extract.spi.ExtractionContext.ResultSetProcessor
import org.hibernate.tool.schema.extract.spi.InformationExtractor
import org.hibernate.type.SqlTypes
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.sql.Types

/**
 * CUBRID JDBC 드라이버(11.3.2.0053)에 맞게 커뮤니티 방언의 타입 바인딩과
 * 스키마 메타데이터 식별자 처리를 보완한다.
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

    // CUBRID returns lowercase table/FK names; Hibernate's uppercase fallback recreates existing objects.
    override fun buildIdentifierHelper(builder: IdentifierHelperBuilder, metadata: DatabaseMetaData?): IdentifierHelper {
        builder.applyIdentifierCasing(metadata)
        builder.setUnquotedCaseStrategy(IdentifierCaseStrategy.LOWER)
        builder.applyReservedWords(keywords)
        builder.setNameQualifierSupport(nameQualifierSupport)
        return builder.build()
    }

    override fun getNameQualifierSupport(): NameQualifierSupport = NameQualifierSupport.SCHEMA

    // JDBC ignores schema arguments. Its native owner.table argument scopes table/column patterns and key lookups.
    override fun getInformationExtractor(context: ExtractionContext): InformationExtractor =
        object : InformationExtractorJdbcDatabaseMetaDataImpl(context) {
            private fun ownerQualified(schema: String?, table: String): String {
                val owner = schema ?: extractionContext.defaultSchema?.text ?: jdbcDatabaseMetaData.userName
                check(!owner.isNullOrBlank()) { "Cannot determine the CUBRID metadata owner" }
                return "$owner.$table"
            }

            override fun <T> processTableResultSet(
                catalog: String?, schemaPattern: String?, tableNamePattern: String?,
                types: Array<out String>?, processor: ResultSetProcessor<T>,
            ): T = super.processTableResultSet(
                catalog, schemaPattern, ownerQualified(schemaPattern, tableNamePattern ?: "%"), types, processor,
            )

            override fun <T> processColumnsResultSet(
                catalog: String?, schemaPattern: String?, tableNamePattern: String?,
                columnNamePattern: String?, processor: ResultSetProcessor<T>,
            ): T = super.processColumnsResultSet(
                catalog, schemaPattern, ownerQualified(schemaPattern, tableNamePattern ?: "%"), columnNamePattern, processor,
            )

            override fun <T> processPrimaryKeysResultSet(
                catalog: String?, schema: String?, table: Identifier, processor: ResultSetProcessor<T>,
            ): T = super.processPrimaryKeysResultSet(catalog, schema, ownerQualified(schema, table.text), processor)

            override fun <T> processPrimaryKeysResultSet(
                catalog: String?, schema: String?, table: String?, processor: ResultSetProcessor<T>,
            ): T = super.processPrimaryKeysResultSet(catalog, schema, table?.let { ownerQualified(schema, it) }, processor)

            override fun <T> processIndexInfoResultSet(
                catalog: String?, schema: String?, table: String?, unique: Boolean, approximate: Boolean,
                processor: ResultSetProcessor<T>,
            ): T = super.processIndexInfoResultSet(
                catalog, schema, table?.let { ownerQualified(schema, it) }, unique, approximate, processor,
            )

            override fun <T> processImportedKeysResultSet(
                catalog: String?, schema: String?, table: String?, processor: ResultSetProcessor<T>,
            ): T = super.processImportedKeysResultSet(catalog, schema, table?.let { ownerQualified(schema, it) }, processor)

            override fun <T> processCrossReferenceResultSet(
                parentCatalog: String?, parentSchema: String?, parentTable: String,
                foreignCatalog: String?, foreignSchema: String?, foreignTable: String,
                processor: ResultSetProcessor<T>,
            ): T = super.processCrossReferenceResultSet(
                parentCatalog, parentSchema, ownerQualified(parentSchema, parentTable),
                foreignCatalog, foreignSchema, ownerQualified(foreignSchema, foreignTable), processor,
            )
        }

    /**
     * CUBRID JDBC 드라이버(11.3.2.0053)는 NOT NULL 제약 위반 시 SQLState를 아예 안 주고
     * (getSQLState() == null) 벤더 고유 errorCode만 준다. org.hibernate.community.
     * dialect.CUBRIDDialect는 buildSQLExceptionConversionDelegate()를 오버라이드하지 않아
     * (부모 Dialect 기본 구현이 null 반환) SQLState 기반 표준 분류에 의존하는데, SQLState가
     * 없으니 분류가 실패해 org.hibernate.exception.GenericJDBCException으로 떨어지고
     * Spring이 이를 org.springframework.orm.jpa.JpaSystemException으로 감싼다(MariaDB/
     * Postgres/MySQL/SQL Server는 모두 SQLState 23502를 정상 반환해 표준 분류가 동작하고
     * DataIntegrityViolationException으로 번역된다).
     *
     * errorCode -631("SQL statement violated NOT NULL constraint.")과 -670("Operation would have
     * caused one or more unique constraint violations.")만 좁게 매칭해 ConstraintViolationException
     * (NOT_NULL / UNIQUE)으로 명시 변환한다 — 연결 끊김 등 다른 종류의 SQLException까지 잘못
     * 분류하지 않도록 이 코드들에만 한정한다. 이렇게 분류되면 Spring이 DataIntegrityViolation
     * Exception으로 번역해 나머지 4개 DB와 동일한 예외 타입을 던지게 된다. PR 번호 채번 충돌 재시도
     * (PullRequestController.createPullRequest)가 바로 이 예외 타입에 의존한다.
     */
    override fun buildSQLExceptionConversionDelegate(): SQLExceptionConversionDelegate =
        object : SQLExceptionConversionDelegate {
            override fun convert(
                sqlException: SQLException,
                message: String,
                sql: String
            ): JDBCException? {
                val kind = when (sqlException.errorCode) {
                    CUBRID_NOT_NULL_VIOLATION_ERROR_CODE -> ConstraintViolationException.ConstraintKind.NOT_NULL
                    CUBRID_UNIQUE_VIOLATION_ERROR_CODE -> ConstraintViolationException.ConstraintKind.UNIQUE
                    else -> return null
                }
                return ConstraintViolationException(
                    message,
                    sqlException,
                    sql,
                    kind,
                    getViolatedConstraintNameExtractor().extractConstraintName(sqlException)
                )
            }
        }

    private companion object {
        // CUBRID JDBC 드라이버(11.3.2.0053)가 NOT NULL 제약 위반 시 실제로 던지는 errorCode.
        // (SQLState는 null이라 쓸 수 없다.)
        const val CUBRID_NOT_NULL_VIOLATION_ERROR_CODE = -631

        // 유니크 제약 위반(ER_BTREE_UNIQUE_FAILED). 메시지에도 "unique constraint violations"가 실린다.
        const val CUBRID_UNIQUE_VIOLATION_ERROR_CODE = -670
    }
}
