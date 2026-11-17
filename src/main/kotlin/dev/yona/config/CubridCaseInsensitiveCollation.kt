package dev.yona.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Cubrid에서 대소문자 무시 비교를 `lower()` 대신 정렬 규칙 `utf8_en_ci`로 하는 스위치이다(`decisions.md` A-630).
 *
 * 검색은 `lower(x) like :패턴`으로 쓰는데, Cubrid에서는 `lower()`가 이 질의 비용의 절반 이상이다(5,000행에서 17ms 가운데 `lower`가 약 10ms). 식에 정렬 규칙을 붙여
 * `x collate utf8_en_ci like :패턴`으로 비교하면 같은 결과를 약 4배 빠르게(21.7ms에서 5.5ms) 얻는다. 이 정렬 규칙은 DB를 UTF-8 로케일(`en_US.utf8`)로 만들었을 때만 있으므로
 * (설치 문서가 이미 그 조건을 요구한다) 시작할 때 `db_collation`에 있는지 확인해 있을 때만 켠다. 없으면 지금처럼 `lower()`로 비교한다. [YonaCubridSqlAstTranslator]가 이 값을 본다.
 */
object CubridCaseInsensitiveCollation {

    /** 정렬 규칙 이름이다. */
    const val NAME = "utf8_en_ci"

    /** `db_root.charset`의 UTF-8 값이다(ISO-8859-1은 3이다). */
    const val UTF8_CHARSET = 5

    /**
     * `like` 비교를 정렬 규칙으로 그릴지이다. 시작할 때 [CubridCollationProbe]가 한 번 정한다. 알아내기 전에는 안전하게 꺼 둔다.
     * SQL을 번역하는 순간에 읽고 하이버네이트는 같은 질의의 번역 결과를 캐시하므로, 질의가 실행된 뒤에 값을 바꿔도 이미 캐시된 질의에는 반영되지 않는다(운영에서는 시작할 때만 정하므로 문제가 없다).
     */
    @Volatile
    var enabled: Boolean = false
}

/**
 * 시작할 때 Cubrid DB의 문자 집합이 UTF-8이고 `db_collation`에 [CubridCaseInsensitiveCollation.NAME]이 있는지 조사해 스위치를 정한다.
 * Cubrid가 아니면(접속 주소가 `jdbc:cubrid:`가 아니면) 아무것도 하지 않는다.
 */
@Component
class CubridCollationProbe(private val dataSource: DataSource, private val environment: Environment) : InitializingBean {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        val url = environment.getProperty("spring.datasource.url").orEmpty().trim().lowercase()
        if (!url.startsWith("jdbc:cubrid:")) return
        val jdbc = JdbcTemplate(dataSource)
        CubridCaseInsensitiveCollation.enabled = runCatching {
            val charset = jdbc.queryForObject("select charset from db_root", Int::class.java)
            val listed = jdbc.queryForObject("select count(*) from db_collation where coll_name = ?", Int::class.java, CubridCaseInsensitiveCollation.NAME) ?: 0
            charset == CubridCaseInsensitiveCollation.UTF8_CHARSET && listed > 0
        }.getOrElse { e ->
            log.warn("Cubrid의 문자 집합과 정렬 규칙을 읽지 못해 대소문자 무시 비교를 lower()로 한다: {}", e.message)
            false
        }
        if (CubridCaseInsensitiveCollation.enabled) log.info("Cubrid 대소문자 무시 비교에 정렬 규칙 {}을 쓴다", CubridCaseInsensitiveCollation.NAME)
        else log.warn("Cubrid DB가 UTF-8이 아니어서 대소문자 무시 비교를 lower()로 한다(검색이 느리고 한글도 깨진다). 데이터베이스를 UTF-8 로케일(en_US.utf8 등)로 만들어야 한다")
    }
}
