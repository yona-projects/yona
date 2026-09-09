package com.github.yonaprojects.yona.domain.support

import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
class YonaUpdateService(
    @Value("\${yona.update.repository-url:https://github.com/yona-projects/yona.git}")
    private val repositoryUrl: String,
    @Value("\${yona.update.current-version:1.15.0}")
    private val currentVersion: String,
    // yona application.update.notification.interval 대응. 코드 레벨 fallback 기본값은
    // 1시간이지만, 실제 배포용 conf 템플릿(application.conf.default)은 6시간(21600000ms)으로
    // 오버라이드돼 있어 그 값을 기본값으로 채택한다.
    @Value("\${yona.update.interval-ms:21600000}")
    private val intervalMillis: Long = 21600000L
) {
    private val log = Logger.getLogger(YonaUpdateService::class.java.name)

    private var latestVersion: String? = null
    private var isUpdateRequired: Boolean = false
    var isWatched: Boolean = true

    fun getLatestVersion(): String? = latestVersion
    fun isUpdateRequired(): Boolean = isUpdateRequired
    fun getReleaseUrl(): String = "https://github.com/yona-projects/yona/releases/tag/v${latestVersion ?: ""}"

    // yona YobiUpdate(interval 기본값 및 설정 가능), initdelay 기본 5초 대응.
    @Scheduled(
        fixedDelayString = "\${yona.update.interval-ms:21600000}",
        initialDelayString = "\${yona.update.initial-delay-ms:5000}"
    )
    fun refreshVersionToUpdate() {
        // yona YobiUpdate.onStart()의 "interval이 0보다 클 때만 폴링을 등록한다"와 동일한 관찰 가능
        // 동작(업데이트 확인이 전혀 실행되지 않음)을 재현한다.
        if (intervalMillis <= 0) {
            log.info("Yona update check disabled (interval <= 0)")
            return
        }
        checkForUpdate()
    }

    fun checkForUpdate() {
        try {
            log.info("Fetching the latest Yona version from remote: $repositoryUrl")
            val tags = Git.lsRemoteRepository()
                .setRemote(repositoryUrl)
                .setHeads(false)
                .setTags(true)
                .call()

            var highestVersion: List<Int>? = null
            var highestVersionStr: String? = null

            for (ref in tags) {
                val rawTag = ref.name.replaceFirst("^refs/tags/", "")
                val cleanTag = if (rawTag.startsWith("v")) rawTag.substring(1) else rawTag
                val versionParts = parseVersion(cleanTag) ?: continue

                if (highestVersion == null || compareVersions(versionParts, highestVersion) > 0) {
                    highestVersion = versionParts
                    highestVersionStr = cleanTag
                }
            }

            val currentParts = parseVersion(currentVersion) ?: listOf(1, 15, 0)
            if (highestVersion != null && compareVersions(highestVersion, currentParts) > 0) {
                latestVersion = highestVersionStr
                isUpdateRequired = true
                log.info("A new Yona version is available: $latestVersion (Current: $currentVersion)")
            } else {
                latestVersion = null
                isUpdateRequired = false
                log.info("Yona is up to date (Current: $currentVersion)")
            }
        } catch (e: Exception) {
            log.warning("Failed to check for Yona updates: ${e.message}")
        }
    }

    private fun parseVersion(versionStr: String): List<Int>? {
        // "1.15.0" -> [1, 15, 0]
        val clean = versionStr.split("-").firstOrNull() ?: return null
        val parts = clean.split(".")
        if (parts.size < 2) return null
        return try {
            parts.map { it.toInt() }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun compareVersions(v1: List<Int>, v2: List<Int>): Int {
        val maxSize = maxOf(v1.size, v2.size)
        for (i in 0 until maxSize) {
            val part1 = v1.getOrNull(i) ?: 0
            val part2 = v2.getOrNull(i) ?: 0
            if (part1 != part2) {
                return part1.compareTo(part2)
            }
        }
        return 0
    }
}
