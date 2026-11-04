package dev.yona.config

import org.hibernate.JDBCException
import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.dialect.TimeZoneSupport
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.spi.SQLExceptionConversionDelegate
import org.hibernate.type.SqlTypes
import java.sql.SQLException

/**
 * Cubrid 11.4용 Hibernate 방언이다(X-46, D-85). 기본 [CUBRIDDialect]에 두 가지를 보탠다.
 *
 * - **불리언을 `smallint`로 저장한다.** 기본 방언은 `bit`(`bit(1)`)를 쓰는데, Cubrid는 `bit` 열을 숫자 `1`과 비교하는 조회와 숫자 기본값(`default 0`)을 받지 않는다.
 *   v1.6의 MariaDB도 불리언을 숫자 열(`tinyint(1)`)로 두었으므로 `smallint`가 그 동작에 가깝다.
 * - **긴 문자열을 `string`으로 저장한다.** 아래 [columnType]을 보라.
 * - **시각을 시간대 기능 없이 바인딩한다.** 기본 방언은 `Instant`를 `datetimetz`와 `OffsetDateTime` 바인딩으로 다루는데 Cubrid 드라이버의 `setObject`가 이를 거부한다.
 * - **제약 위반을 오류 코드로 분류한다.** 아래 [buildSQLExceptionConversionDelegate]를 보라.
 * - **예약어 `size`를 따옴표로 감싼다.** `attachment.size` 열이 Cubrid 예약어와 겹친다. 이 방언의 예약어 목록에 더해 두면
 *   `hibernate.auto_quote_keyword`가 켜진 때 열 이름을 따옴표로 감싼다.
 */
class YonaCubridDialect : CUBRIDDialect {

    constructor() : super()

    constructor(info: DialectResolutionInfo) : super(info)

    override fun getPreferredSqlTypeCodeForBoolean(): Int = SqlTypes.SMALLINT

    /**
     * 긴 문자열(`@Column(length = LONG_TEXT)`)을 `clob`이 아닌 `string`(`varchar(1073741823)`)으로 저장한다. Cubrid의 `clob`은 별도 파일에 저장하는 진짜 LOB이라
     * `where`절에서 문자열과 비교할 수 없고(`'=' operator is not defined on types clob and char`), 다른 데이터베이스의 `text`와 같게 쓰려면 `string`이 맞다.
     */
    override fun columnType(sqlTypeCode: Int): String = when (sqlTypeCode) {
        SqlTypes.LONG32VARCHAR, SqlTypes.CLOB -> "string"
        else -> super.columnType(sqlTypeCode)
    }

    override fun registerDefaultKeywords() {
        super.registerDefaultKeywords()
        registerKeyword("SIZE")
    }

    override fun getTimeZoneSupport(): TimeZoneSupport = TimeZoneSupport.NONE

    /**
     * Cubrid 드라이버는 제약 위반에도 SQLState를 주지 않고 벤더 오류 코드만 준다. 기본 방언은 SQLState로 분류하므로 이 오류가 `GenericJDBCException`이 되어
     * 다른 데이터베이스와 달리 `DataIntegrityViolationException`으로 번역되지 않는다. 풀 리퀘스트 번호 채번 충돌 재시도처럼 이 예외 형식에 기대는 곳이 있어,
     * NOT NULL 위반(-631)과 유니크 위반(-670)만 [ConstraintViolationException]으로 바꾼다. 연결 끊김 같은 다른 오류는 건드리지 않는다.
     */
    override fun buildSQLExceptionConversionDelegate(): SQLExceptionConversionDelegate = SQLExceptionConversionDelegate { sqlException: SQLException, message: String, sql: String? ->
        val kind = when (sqlException.errorCode) {
            NOT_NULL_VIOLATION -> ConstraintViolationException.ConstraintKind.NOT_NULL
            UNIQUE_VIOLATION -> ConstraintViolationException.ConstraintKind.UNIQUE
            else -> null
        }
        kind?.let { ConstraintViolationException(message, sqlException, sql.orEmpty(), it, violatedConstraintNameExtractor.extractConstraintName(sqlException)) } as JDBCException?
    }

    private companion object {
        const val NOT_NULL_VIOLATION = -631
        const val UNIQUE_VIOLATION = -670
    }
}
