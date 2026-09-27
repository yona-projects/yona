package com.github.yonaprojects.yona.config

import jakarta.persistence.Table
import org.hibernate.boot.model.naming.Identifier
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.sql.Types
import javax.sql.DataSource
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Stop before Hibernate schema update if an installation has a custom, populated-or-empty LOB mapping. */
@Component
@Profile("cubrid")
class CubridSchemaGuard(private val dataSource: DataSource) : HibernatePropertiesCustomizer {
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        dataSource.connection.use { connection ->
            val metadata = connection.metaData
            check(metadata.databaseProductName == "CUBRID") { "The cubrid mapping requires a CUBRID database" }
            val configuredSchema = Identifier.toIdentifier(hibernateProperties["hibernate.default_schema"]?.toString())
            val schema = configuredSchema?.text ?: metadata.userName
            check(!schema.isNullOrBlank()) { "Cannot determine the CUBRID application schema" }
            // The driver cannot report a default schema to Hibernate; avoid inspecting another owner's tables.
            if (configuredSchema == null) hibernateProperties["hibernate.default_schema"] = schema
            metadata.getColumns(connection.catalog, schema, "$schema.%", "%").use { columns ->
                while (columns.next()) {
                    if (!schema.equals(columns.getString("TABLE_SCHEM"), ignoreCase = true)) continue
                    val table = columns.getString("TABLE_NAME").substringAfterLast('.').lowercase()
                    val column = columns.getString("COLUMN_NAME").lowercase()
                    if (column !in OVERRIDDEN_COLUMNS[table].orEmpty()) continue
                    val type = columns.getInt("DATA_TYPE")
                    val typeName = columns.getString("TYPE_NAME").uppercase()
                    if (type == Types.BLOB || type == Types.CLOB || type == Types.NCLOB ||
                        typeName == "BLOB" || typeName == "CLOB" || typeName == "NCLOB") {
                        throw CubridLobMigrationRequired("$schema.$table", column)
                    }
                }
            }
        }
    }

    companion object {
        // The mapping file is the single list of affected fields; never scan or rewrite unrelated columns.
        private val OVERRIDDEN_COLUMNS: Map<String, Set<String>> = run {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val document = checkNotNull(CubridSchemaGuard::class.java.getResourceAsStream("/META-INF/orm-cubrid.xml"))
                .use { factory.newDocumentBuilder().parse(it) }
            val namespace = document.documentElement.namespaceURI
            val entities = document.getElementsByTagNameNS(namespace, "entity")
            (0 until entities.length).associate { index ->
                val entity = entities.item(index) as Element
                val model = Class.forName(entity.getAttribute("class"), false, CubridSchemaGuard::class.java.classLoader)
                val table = checkNotNull(model.getAnnotation(Table::class.java)).name
                val columns = entity.getElementsByTagNameNS(namespace, "column")
                table to (0 until columns.length).mapNotNull {
                    val column = columns.item(it) as Element
                    val attribute = column.parentNode as Element
                    if (attribute.getElementsByTagNameNS(namespace, "lob").length == 0) column.getAttribute("name") else null
                }.toSet()
            }
        }
    }
}

class CubridLobMigrationRequired(table: String, column: String) : IllegalStateException(
    "Existing CUBRID LOB $table.$column requires an explicit data-preserving migration; schema update was not started"
)
