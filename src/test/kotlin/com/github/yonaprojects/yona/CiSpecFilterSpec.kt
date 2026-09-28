package com.github.yonaprojects.yona

import com.github.yonaprojects.yona.domain.issue.IssueNumberingConcurrencyIntegrationSpec
import com.github.yonaprojects.yona.domain.site.DataBackupServicePostgresIntegrationSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.filter.SpecFilterResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CiSpecFilterSpec : DescribeSpec({
    describe("CI suite partitioning") {
        val databaseSpecs = setOf(YonaApplicationTests::class, IssueNumberingConcurrencyIntegrationSpec::class)
        val dedicatedSpecs = setOf(CiSpecFilterSpec::class, DataBackupServicePostgresIntegrationSpec::class)
        val specs = databaseSpecs + dedicatedSpecs

        it("keeps every spec in exactly one suite, including dedicated PostgreSQL tests outside the matrix") {
            specs.filter { CiSpecFilter("database").filter(it) == SpecFilterResult.Include }.toSet() shouldBe databaseSpecs
            specs.filter { CiSpecFilter("other").filter(it) == SpecFilterResult.Include }.toSet() shouldBe dedicatedSpecs
            specs.all { CiSpecFilter("all").filter(it) == SpecFilterResult.Include } shouldBe true
        }

        it("assigns each database spec to exactly one shard without admitting dedicated tests") {
            val shards = (0..1).map { shard ->
                specs.filter { CiSpecFilter("database", shard, 2).filter(it) == SpecFilterResult.Include }.toSet()
            }
            (shards[0] intersect shards[1]) shouldBe emptySet()
            (shards[0] + shards[1]) shouldBe databaseSpecs
        }

        it("rejects invalid selection rather than silently omitting tests") {
            shouldThrow<IllegalArgumentException> { CiSpecFilter("databse") }
            shouldThrow<IllegalArgumentException> { CiSpecFilter("database", 0, 0) }
            shouldThrow<IllegalArgumentException> { CiSpecFilter("database", -1, 2) }
            shouldThrow<IllegalArgumentException> { CiSpecFilter("database", 2, 2) }
            shouldThrow<IllegalArgumentException> { CiSpecFilter("other", 0, 2) }
        }
    }
})
