package com.github.yonaprojects.yona

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * yona가 지원해야 하는 6개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2)를 전부 동일한
 * 통합테스트 스위트(이 클래스를 상속하는 모든 Spec)로 검증한다. DB와 컨테이너의 수명은
 * IntegrationTestDatabaseConfiguration에서 Spring 컨텍스트와 함께 관리한다.
 *
 * 실행: `./gradlew test -Dyona.it.db=postgres` (기본값은 mariadb). 값: mariadb|postgres|mysql|mssql|cubrid|h2
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestDatabaseConfiguration::class)
@ContextConfiguration(initializers = [IntegrationTestEnvironmentInitializer::class])
abstract class AbstractIntegrationTest : DescribeSpec()
