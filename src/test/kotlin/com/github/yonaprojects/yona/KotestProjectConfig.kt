package com.github.yonaprojects.yona

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.filter.SpecFilter
import io.kotest.core.filter.SpecFilterResult
import io.kotest.extensions.spring.SpringExtension
import kotlin.reflect.KClass

object KotestProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(
        SpringExtension,
        CiSpecFilter(
            System.getProperty("yona.test.group", "all"),
            System.getProperty("yona.test.shard", "0").toInt(),
            System.getProperty("yona.test.shards", "1").toInt()
        )
    )
}

// ponytail: parallelize independent CI jobs, not specs sharing a database or filesystem.
internal class CiSpecFilter(
    private val group: String,
    private val shard: Int = 0,
    private val shards: Int = 1
) : SpecFilter {
    init {
        require(group == "all" || group == "database" || group == "other") { "Unknown test group: $group" }
        require(shards > 0 && shard in 0 until shards) { "Invalid test shard $shard/$shards" }
        require(shards == 1 || group == "database") { "Only the database suite supports sharding" }
    }

    override fun filter(kclass: KClass<*>): SpecFilterResult {
        val database = AbstractIntegrationTest::class.java.isAssignableFrom(kclass.java)
        val inGroup = when (group) {
            "database" -> database
            "other" -> !database
            else -> true
        }
        return if (inGroup && Math.floorMod(kclass.java.name.hashCode(), shards) == shard) {
            SpecFilterResult.Include
        } else {
            SpecFilterResult.Exclude("Outside CI group $group, shard $shard/$shards")
        }
    }
}
