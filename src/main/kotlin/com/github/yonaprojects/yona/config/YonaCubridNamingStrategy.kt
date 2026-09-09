package com.github.yonaprojects.yona.config

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

/**
 * CUBRID 전용 물리 네이밍 전략. Spring Boot 기본 물리 네이밍 전략
 * (org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl)을 그대로 두고
 * `spring.jpa.properties.hibernate.globally_quoted_identifiers` 전역 설정으로 예약어 충돌
 * (role 등)을 피하려 했으나, Hibernate가 모든 식별자를 미리 "이미 인용됨" 상태로 표시해버려
 * 네이밍 전략의 "이미 인용된 이름은 그대로 둔다"는 규칙에 따라 camelCase→snake_case 변환
 * 자체를 건너뛰는 부작용이 있었다(예: authorId가 author_id로 안 바뀌고 그대로
 * 남음). 그래서 전역 인용 대신, 평범하게 snake_case로 변환한 뒤 그 결과가 CUBRID 예약어와
 * 겹칠 때만 개별적으로 인용 처리한다.
 */
class YonaCubridNamingStrategy : PhysicalNamingStrategySnakeCaseImpl() {

    // https://www.cubrid.org/manual/en/11.4/sql/keyword.html (CUBRID 11.4 Reserved Words)
    private val reservedWords: Set<String> = setOf(
        "ABSOLUTE", "ACTION", "ACCESS", "ADD", "ADD_MONTHS", "AFTER", "ALL", "ALLOCATE", "ALTER", "AND",
        "ANY", "ARE", "AS", "ASC", "ASSERTION", "AT", "ATTACH", "ATTRIBUTE", "AVG", "BEFORE", "BETWEEN",
        "BIGINT", "BINARY", "BIT", "BIT_LENGTH", "BLOB", "BOOLEAN", "BOTH", "BREADTH", "BY", "CALL",
        "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CHANGE", "CHAR", "CHARACTER", "CHECK", "CLASS",
        "CLASSES", "CLOB", "COALESCE", "COLLATE", "COLLATION", "COLUMN", "COMMIT", "CONNECT",
        "CONNECT_BY_ISCYCLE", "CONNECT_BY_ISLEAF", "CONNECT_BY_ROOT", "CONNECTION", "CONSTRAINT",
        "CONSTRAINTS", "CONTINUE", "CONVERT", "CORRESPONDING", "COUNT", "CREATE", "CROSS", "CURRENT",
        "CURRENT_DATE", "CURRENT_DATETIME", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR",
        "CYCLE", "DATA", "DATA_TYPE", "DATABASE", "DATE", "DATETIME", "DAY", "DAY_HOUR", "DAY_MILLISECOND",
        "DAY_MINUTE", "DAY_SECOND", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE", "DEFAULT", "DEFERRABLE",
        "DEFERRED", "DELETE", "DEPTH", "DESC", "DESCRIBE", "DESCRIPTOR", "DIAGNOSTICS", "DIFFERENCE",
        "DISCONNECT", "DISTINCT", "DISTINCTROW", "DIV", "DO", "DOMAIN", "DOUBLE", "DUPLICATE", "DROP",
        "EACH", "ELSE", "ELSEIF", "END", "EQUALS", "ESCAPE", "EVALUATE", "EXCEPT", "EXCEPTION", "EXEC",
        "EXECUTE", "EXISTS", "EXTERNAL", "EXTRACT", "FALSE", "FETCH", "FILE", "FIRST", "FLOAT", "FOR",
        "FOREIGN", "FOUND", "FROM", "FULL", "FUNCTION", "GENERAL", "GET", "GLOBAL", "GO", "GOTO", "GRANT",
        "GROUP", "HAVING", "HOUR", "HOUR_MILLISECOND", "HOUR_MINUTE", "HOUR_SECOND", "IDENTITY", "IF",
        "IGNORE", "IMMEDIATE", "IN", "INDEX", "INDICATOR", "INHERIT", "INITIALLY", "INNER", "INOUT",
        "INPUT", "INSERT", "INT", "INTEGER", "INTERSECT", "INTERSECTION", "INTERVAL", "INTO", "IS",
        "ISOLATION", "JOIN", "KEY", "LANGUAGE", "LAST", "LEADING", "LEAVE", "LEFT", "LESS", "LEVEL",
        "LIKE", "LIMIT", "LIST", "LOCAL", "LOCAL_TRANSACTION_ID", "LOCALTIME", "LOCALTIMESTAMP", "LOOP",
        "LOWER", "MATCH", "MAX", "METHOD", "MILLISECOND", "MIN", "MINUTE", "MINUTE_MILLISECOND",
        "MINUTE_SECOND", "MOD", "MODIFY", "MODULE", "MONTH", "MULTISET", "MULTISET_OF", "NA", "NAMES",
        "NATIONAL", "NATURAL", "NCHAR", "NEXT", "NO", "NONE", "NOT", "NULL", "NULLIF", "NUMERIC", "OBJECT",
        "OCTET_LENGTH", "OF", "OFF", "ON", "ONLY", "OPTIMIZATION", "OPTION", "OR", "ORDER", "OUT", "OUTER",
        "OUTPUT", "OVERLAPS", "PARAMETERS", "PARTIAL", "POSITION", "PRECISION", "PREPARE", "PRESERVE",
        "PRIMARY", "PRIOR", "PRIVILEGES", "PROCEDURE", "QUERY", "READ", "REAL", "RECURSIVE", "REF",
        "REFERENCES", "REFERENCING", "RELATIVE", "RENAME", "REPLACE", "RESIGNAL", "RESTRICT", "RETURN",
        "RETURNS", "REVOKE", "RIGHT", "ROLE", "ROLLBACK", "ROLLUP", "ROUTINE", "ROW", "ROWNUM", "ROWS",
        "SAVEPOINT", "SCHEMA", "SCOPE", "SCROLL", "SEARCH", "SECOND", "SECOND_MILLISECOND", "SECTION",
        "SELECT", "SENSITIVE", "SEQUENCE", "SEQUENCE_OF", "SERIALIZABLE", "SESSION", "SESSION_USER", "SET",
        "SET_OF", "SETEQ", "SHARED", "SIBLINGS", "SIGNAL", "SIMILAR", "SIZE", "SMALLINT", "SOME", "SQL",
        "SQLCODE", "SQLERROR", "SQLEXCEPTION", "SQLSTATE", "SQLWARNING", "STATISTICS", "STRING",
        "SUBCLASS", "SUBSET", "SUBSETEQ", "SUBSTRING", "SUM", "SUPERCLASS", "SUPERSET", "SUPERSETEQ",
        "SYS_CONNECT_BY_PATH", "SYS_DATE", "SYS_DATETIME", "SYS_TIME", "SYS_TIMESTAMP", "SYSDATE",
        "SYSDATETIME", "SYSTEM_USER", "SYSTIME", "TABLE", "TEMPORARY", "THEN", "TIME", "TIMESTAMP",
        "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSACTION", "TRANSLATE", "TRANSLATION",
        "TRIGGER", "TRIM", "TRUE", "TRUNCATE", "UNDER", "UNION", "UNIQUE", "UNKNOWN", "UPDATE", "UPPER",
        "USAGE", "USE", "USER", "USING", "UTIME", "VALUE", "VALUES", "VARCHAR", "VARIABLE", "VARYING",
        "VCLASS", "VIEW", "WHEN", "WHENEVER", "WHERE", "WHILE", "WITH", "WITHOUT", "WORK", "WRITE", "XOR",
        "YEAR", "YEAR_MONTH", "ZONE"
    )

    private fun quoteIfReserved(identifier: Identifier?): Identifier? {
        if (identifier == null) return null
        if (identifier.text.uppercase() !in reservedWords) return identifier
        return Identifier.toIdentifier(identifier.text, true)
    }

    override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment?): Identifier? =
        quoteIfReserved(super.toPhysicalTableName(name, context))

    override fun toPhysicalColumnName(name: Identifier?, context: JdbcEnvironment?): Identifier? =
        quoteIfReserved(super.toPhysicalColumnName(name, context))
}
