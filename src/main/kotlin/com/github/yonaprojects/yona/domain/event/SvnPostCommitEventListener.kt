package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommit
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import java.io.File
import java.time.Instant

// Collect SVN revisions in the same newest-first order as Git and Mercurial pushes.
@Component
class SvnPostCommitEventListener(
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}") private val baseDir: String,
    private val commitPostProcessingService: CommitPostProcessingService
) {
    private val logger = LoggerFactory.getLogger(SvnPostCommitEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleSvnPostCommitEvent(event: SvnPostCommitEvent) {
        val entries = try {
            readLog(event.project, event.fromRevision, event.toRevision)
        } catch (e: Exception) {
            logger.error("Failed to read SVN revisions r${event.fromRevision}-r${event.toRevision} of ${event.project.owner}/${event.project.name}", e)
            return
        }
        if (entries.isEmpty()) return
        val pushed = PushedVcsCommits(entries.map {
            PushedVcsCommit(
                id = it.revision.toString(),
                message = it.message ?: "",
                authorName = it.author ?: "",
                authorEmail = null,
                timestamp = it.date?.toInstant() ?: Instant.now()
            )
        }, emptyList())
        commitPostProcessingService.process(event.project, pushed, event.user)
    }

    private fun readLog(project: Project, from: Long, to: Long): List<SVNLogEntry> {
        FSRepositoryFactory.setup()
        val repository = SVNRepositoryFactory.create(SVNURL.fromFile(File(baseDir, "${project.owner}/${project.name}")))
        try {
            val entries = mutableListOf<SVNLogEntry>()
            repository.log(arrayOf(""), from, to, false, false) { entries.add(it) }
            // Newest first, like the RevWalk order used for Git pushes.
            return entries.sortedByDescending { it.revision }
        } finally {
            repository.closeSession()
        }
    }

}
