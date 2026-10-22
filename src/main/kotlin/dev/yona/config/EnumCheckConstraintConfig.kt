package dev.yona.config

import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
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

/** [EnumCheckConstraintRemover]를 Hibernate에 등록한다. */
@Configuration(proxyBeanMethods = false)
class EnumCheckConstraintConfig {

    @Bean
    fun enumCheckConstraintCustomizer(): HibernatePropertiesCustomizer = HibernatePropertiesCustomizer { properties ->
        properties["hibernate.integrator_provider"] = IntegratorProvider { listOf(EnumCheckConstraintRemover()) }
    }
}
