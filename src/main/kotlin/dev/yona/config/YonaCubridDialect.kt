package dev.yona.config

import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo
import org.hibernate.type.SqlTypes

/**
 * Cubrid 11.4용 Hibernate 방언이다(X-46, D-85). 기본 [CUBRIDDialect]에 두 가지를 보탠다.
 *
 * - **불리언을 `smallint`로 저장한다.** 기본 방언은 `bit`(`bit(1)`)를 쓰는데, Cubrid는 `bit` 열을 숫자 `1`과 비교하는 조회와 숫자 기본값(`default 0`)을 받지 않는다.
 *   v1.6의 MariaDB도 불리언을 숫자 열(`tinyint(1)`)로 두었으므로 `smallint`가 그 동작에 가깝다.
 * - **예약어 `size`를 따옴표로 감싼다.** `attachment.size` 열이 Cubrid 예약어와 겹친다. 이 방언의 예약어 목록에 더해 두면
 *   `hibernate.auto_quote_keyword`가 켜진 때 열 이름을 따옴표로 감싼다.
 */
class YonaCubridDialect : CUBRIDDialect {

    constructor() : super()

    constructor(info: DialectResolutionInfo) : super(info)

    override fun getPreferredSqlTypeCodeForBoolean(): Int = SqlTypes.SMALLINT

    override fun registerDefaultKeywords() {
        super.registerDefaultKeywords()
        registerKeyword("SIZE")
    }
}
