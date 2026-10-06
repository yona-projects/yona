package com.github.yonaprojects.yona.config.analytics

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.nio.file.Files

class AnalyticsSettingsSpec : DescribeSpec({
    val tmp = Files.createTempDirectory("analytics-settings-spec")

    fun settings(sendUsage: Boolean = true, measurementId: String = "G-TEST", dir: String = "a") =
        AnalyticsSettings(sendUsage, measurementId, tmp.resolve("$dir/install-id").toString())

    describe("설치 식별자") {
        it("최초 한 번만 UUID를 만들어 파일에 저장하고 이후엔 같은 값을 재사용해야 한다") {
            val first = settings(dir = "persist").installId
            first shouldMatch Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            settings(dir = "persist").installId shouldBe first
        }
    }

    describe("클라이언트 태그 게이트") {
        it("send-usage가 켜져 있고 측정 ID가 있을 때만 켜져야 한다") {
            settings().clientEnabled shouldBe true
            settings(sendUsage = false).clientEnabled shouldBe false
            settings(measurementId = "").clientEnabled shouldBe false
        }
    }
})
