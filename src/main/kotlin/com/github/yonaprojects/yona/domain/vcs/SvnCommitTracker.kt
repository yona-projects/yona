package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.config.git.DeployKeyAuthenticationToken
import com.github.yonaprojects.yona.domain.event.SvnPostCommitEvent
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the revisions an SVN HTTP commit created. DAV commits finish with one MERGE request; the
 * youngest revision is read before and after it under a per-repository lock, so concurrent commits
 * through this server are attributed to the request that made them. Like Git, deploy-key commits have
 * no Yona pusher and produce no post-commit event.
 */
@Component
class SvnCommitTracker(
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}") private val baseDir: String,
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SvnCommitTracker::class.java)
    private val locks = ConcurrentHashMap<String, Any>()

    fun <T> track(owner: String, name: String, action: () -> T): T {
        val directory = File(baseDir, "$owner/$name").absoluteFile
        synchronized(locks.computeIfAbsent(directory.path) { Any() }) {
            val before = youngest(directory)
            val result = action()
            val after = youngest(directory)
            if (before != null && after != null && after > before) publish(owner, name, before + 1, after)
            return result
        }
    }

    private fun publish(owner: String, name: String, from: Long, to: Long) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || authentication is AnonymousAuthenticationToken || authentication is DeployKeyAuthenticationToken) return
        val user = userRepository.findByLoginId(authentication.name).orElse(null) ?: return
        val project = projectService.findByOwnerAndName(owner, name) ?: return
        eventPublisher.publishEvent(SvnPostCommitEvent(project, user, from, to))
    }

    private fun youngest(directory: File): Long? {
        if (!File(directory, "format").isFile) return null
        return try {
            FSRepositoryFactory.setup()
            val repository = SVNRepositoryFactory.create(SVNURL.fromFile(directory))
            try { repository.latestRevision } finally { repository.closeSession() }
        } catch (e: Exception) {
            logger.warn("Could not read the youngest revision of {}/{}", directory.parentFile?.name, directory.name)
            null
        }
    }
}
