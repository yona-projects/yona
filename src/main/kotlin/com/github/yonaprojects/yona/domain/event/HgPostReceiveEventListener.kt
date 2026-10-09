package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.vcs.HgBookmarkAncestry
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkChangeType
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.vcs.HgCommit as HgCommitAuthorParser
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommit
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.HgCommit as NativeHgCommit
import io.github.search5.hg4j.lib.HgRepository as NativeHgRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant

// Collect the commits introduced by a bookmark move, then use the shared post-processing path.
@Component
class HgPostReceiveEventListener(
    private val repositoryService: RepositoryService,
    private val commitPostProcessingService: CommitPostProcessingService
) {
    private val logger = LoggerFactory.getLogger(HgPostReceiveEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleHgPostReceiveEvent(event: HgPostReceiveEvent) {
        logger.info(
            "Handling HgPostReceiveEvent asynchronously for project: ${event.project.name} bookmark: ${event.move.name} by user: ${event.user.loginId}"
        )

        // git의 isNewOrUpdateCommand() 대응 — 삭제는 새로 알릴 커밋이 없다.
        if (event.move.changeType == HgBookmarkChangeType.DELETE) {
            return
        }

        val playRepo = repositoryService.getRepository(event.project)
        val repoDir = playRepo.getDirectory()
        if (!repoDir.exists()) {
            logger.warn("Repository directory does not exist: ${repoDir.absolutePath}")
            return
        }

        val commits: List<NativeHgCommit> = try {
            parseCommitsFrom(repoDir, event.move.oldNodeHex, event.move.newNodeHex)
        } catch (e: Exception) {
            logger.error("Failed to parse commits from Mercurial repository", e)
            return
        }

        logger.info("Parsed ${commits.size} commits from bookmark: ${event.move.name}")

        val refName = "refs/heads/${event.move.name}"
        val pushed = PushedVcsCommits(commits.map { commit ->
            val email = HgCommitAuthorParser.parseAuthorEmail(commit.author) ?: ""
            PushedVcsCommit(
                id = commit.nodeId.toHex(),
                message = commit.message ?: "",
                authorName = if (email.isNotEmpty()) commit.author.substringBefore("<").trim() else commit.author,
                authorEmail = email,
                timestamp = Instant.ofEpochSecond(commit.timestamp)
            )
        }, listOf(refName))
        commitPostProcessingService.process(event.project, pushed, event.user)
    }

    // git RevWalk(markStart(newId)/markUninteresting(oldId))와 동일한 목적 — Wire2Commands.
    // changelog()와 동일한 저수준 Revlog(00changelog.i/.d)로 실제 부모 사슬을 따라간다(hg4j 포셀린
    // HgCommit에는 부모 리비전이 노출되지 않으므로).
    private fun parseCommitsFrom(repoDir: File, oldHex: String, newHex: String): List<NativeHgCommit> {
        val nativeRepo = NativeHgRepository(repoDir)
        try {
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)
            val newRevisions = HgBookmarkAncestry.newlyIntroducedRevisions(revlog, oldHex, newHex)
            if (newRevisions.isEmpty()) return emptyList()

            return Hg.open(repoDir).use { hg ->
                val byRevision = hg.log().call().associateBy { it.revision }
                // git 쪽과 동일하게 최신 커밋이 먼저 오도록(RevWalk의 기본 순회 순서) 내림차순 정렬.
                newRevisions.sortedDescending().mapNotNull { byRevision[it] }
            }
        } finally {
            nativeRepo.close()
        }
    }

}
