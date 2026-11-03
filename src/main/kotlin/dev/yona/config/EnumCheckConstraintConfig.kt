package dev.yona.config

import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.jpa.boot.spi.IntegratorProvider
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * enum 컬럼에 Hibernate가 자동으로 붙이는 CHECK 제약을 스키마 생성 직전에 제거한다.
 *
 * v1.6은 20.sql에서 모든 enum CHECK 제약을 지워, 새 enum 값을 추가해도 DB를 바꾸지 않게 했다.
 * Hibernate 6.2 이후는 enum 타입 속성에 `check (col in (...))`을 만들며 변환기를 써도 막을 수 없다.
 * 이 프로젝트의 CHECK 제약은 전부 enum에서 나온 것이므로 모두 제거해도 v1.6 스키마와 같아진다.
 */
class EnumCheckConstraintRemover : Integrator {

    override fun integrate(metadata: Metadata, bootstrapContext: BootstrapContext, sessionFactory: SessionFactoryImplementor) {
        metadata.collectTableMappings().forEach { table ->
            table.columns.forEach { column -> column.clearCheckConstraints() }
            table.clearCheckConstraints()
        }
    }

    /**
     * Hibernate는 CHECK 제약 목록을 수정할 수 없는 목록으로 노출하므로, 목록을 담은 필드를 직접 비운 목록으로 바꾼다.
     * 대상 필드 이름이 Hibernate 버전에 따라 달라지면 스키마 테스트가 즉시 실패해 알려 준다.
     */
    private fun Any.clearCheckConstraints() {
        val field = javaClass.getDeclaredField("checkConstraints")
        field.isAccessible = true
        field.set(this, mutableListOf<Any>())
    }
}

/**
 * 열 기본값의 불리언 리터럴(`true`, `false`)을 데이터베이스가 쓰는 표기로 바꾼다(X-46, D-85).
 *
 * 엔티티는 `@ColumnDefault("false")`처럼 불리언 기본값을 쓰는데, MSSQL과 Cubrid에는 불리언 리터럴이 없어 이 문장으로 `create table`이 실패한다.
 * 방언의 `toBooleanValueString`이 알려 주는 표기(`0`, `1` 또는 `true`, `false`)로 바꾸므로 기존 데이터베이스의 DDL은 달라지지 않는다.
 */
object BooleanDefaultTranslator {

    /** [value]가 `true` 또는 `false`(대소문자 무시)이면 [dialect]의 표기로 바꾸고, 그 밖의 값은 그대로 돌려준다. */
    fun translate(value: String?, dialect: Dialect): String? = when (value?.lowercase()) {
        "true" -> dialect.toBooleanValueString(true)
        "false" -> dialect.toBooleanValueString(false)
        else -> value
    }
}

/** 스키마 생성 직전에 모든 열의 불리언 기본값을 [BooleanDefaultTranslator]로 바꾼다. */
class BooleanDefaultIntegrator : Integrator {

    override fun integrate(metadata: Metadata, bootstrapContext: BootstrapContext, sessionFactory: SessionFactoryImplementor) {
        val dialect = sessionFactory.jdbcServices.dialect
        metadata.collectTableMappings().forEach { table ->
            table.columns.forEach { column -> column.defaultValue = BooleanDefaultTranslator.translate(column.defaultValue, dialect) }
        }
    }
}

/**
 * Cubrid가 받지 않는 DDL을 다듬는다. 외래 키의 `on update cascade`를 지우고 열 구성이 중복되는 인덱스를 지운다(X-46, D-85).
 *
 * v1.6의 일부 외래 키는 `on update cascade`를 갖지만 Cubrid는 갱신 규칙으로 `no action`, `restrict`, `set null`만 받는다.
 * 기본 키 값을 바꾸는 코드가 없으므로 지워도 동작은 같다. Cubrid가 아닌 데이터베이스에는 아무것도 하지 않는다.
 */
class CubridDdlAdjuster : Integrator {

    override fun integrate(metadata: Metadata, bootstrapContext: BootstrapContext, sessionFactory: SessionFactoryImplementor) {
        if (sessionFactory.jdbcServices.dialect !is CUBRIDDialect) return
        metadata.collectTableMappings().forEach { table ->
            table.foreignKeyCollection.forEach { key ->
                key.keyDefinition?.let { key.keyDefinition = it.replace(UPDATE_CASCADE, "").trim() }
            }
            removeNullablePairUniqueKeys(table)
            removeIndexesDuplicatingUniqueKeys(table)
        }
    }

    /**
     * 한쪽 열이 비어 있을 수 있는 짝(`recent_issue`의 `user_id`와 `issue_id`, `user_id`와 `posting_id`)의 유니크 키를 지운다. MySQL과 MariaDB, PostgreSQL, H2는 열 하나라도
     * `NULL`이면 유니크 검사를 하지 않지만 Cubrid는 `{2, NULL}`을 값으로 보고 같은 사용자의 두 번째 글(`issue_id`가 `NULL`) 기록을 막는다.
     * 이 테이블은 기록 전에 같은 대상을 지우므로(`RecentIssueService`) 유니크 키가 없어도 중복되지 않는다. 같은 열 구성의 일반 인덱스는 그대로 둔다.
     */
    private fun removeNullablePairUniqueKeys(table: org.hibernate.mapping.Table) {
        if (table.name != "recent_issue") return
        val field = org.hibernate.mapping.Table::class.java.getDeclaredField("uniqueKeys")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(table) as MutableMap<String, org.hibernate.mapping.UniqueKey>).keys.removeIf { it.startsWith("uq_recent_issue_") }
    }

    /**
     * 유니크 키와 열 구성이 같은 일반 인덱스를 지운다. v1.6은 `(user_id, issue_id)` 같은 열에 인덱스와 유니크 키를 따로 두었지만
     * Cubrid는 열 구성이 같은 인덱스를 둘 만들면 `already defined` 오류를 낸다. 유니크 키가 같은 조회를 받쳐 주므로 동작은 같다.
     */
    private fun removeIndexesDuplicatingUniqueKeys(table: org.hibernate.mapping.Table) {
        val uniqueColumns = table.uniqueKeys.values.map { key -> key.columns.map { it.name } }.toSet()
        val duplicated = table.indexes.values.filter { index -> index.columns.map { it.name } in uniqueColumns }
        if (duplicated.isEmpty()) return
        // Hibernate는 인덱스 맵을 수정할 수 없는 맵으로 노출하므로 맵을 담은 필드를 직접 고친다.
        val field = org.hibernate.mapping.Table::class.java.getDeclaredField("indexes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val indexes = field.get(table) as MutableMap<String, org.hibernate.mapping.Index>
        duplicated.forEach { indexes.remove(it.name) }
    }

    private companion object {
        val UPDATE_CASCADE = Regex("\\s+on update cascade", RegexOption.IGNORE_CASE)
    }
}

/** [EnumCheckConstraintRemover]와 [BooleanDefaultIntegrator]를 Hibernate에 등록한다. */
@Configuration(proxyBeanMethods = false)
class EnumCheckConstraintConfig {

    @Bean
    fun enumCheckConstraintCustomizer(): HibernatePropertiesCustomizer = HibernatePropertiesCustomizer { properties ->
        properties["hibernate.integrator_provider"] = IntegratorProvider { listOf(EnumCheckConstraintRemover(), BooleanDefaultIntegrator(), CubridDdlAdjuster()) }
    }
}
