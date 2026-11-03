package dev.yona.config

import org.hibernate.dialect.SQLServerDialect
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo
import org.hibernate.type.SqlTypes

/**
 * MSSQL용 Hibernate 방언이다(X-46, D-85). 기본 [SQLServerDialect]에 문자열 변환 형식 하나를 바꾼다.
 *
 * 한글이 깨지지 않도록 문자열 열을 `nvarchar`로 만들면(`hibernate.use_nationalized_character_data`), HQL의 `cast(x as string)`은 여전히
 * `varchar(max)`로 변환해 한글이 `?`가 된다. 검색에서 본문과 댓글(`cast(i.body as string)`)을 찾을 때 이 변환을 쓰므로,
 * 문자열 변환의 대상 형식을 `nvarchar(max)`로 바꾼다.
 */
class YonaSqlServerDialect : SQLServerDialect {

    constructor() : super()

    constructor(info: DialectResolutionInfo) : super(info)

    override fun castType(sqlTypeCode: Int): String = when (sqlTypeCode) {
        SqlTypes.VARCHAR, SqlTypes.LONG32VARCHAR, SqlTypes.CLOB -> "nvarchar(max)"
        else -> super.castType(sqlTypeCode)
    }
}
