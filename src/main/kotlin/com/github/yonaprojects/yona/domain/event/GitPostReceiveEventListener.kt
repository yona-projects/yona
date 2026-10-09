package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.GitService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommit
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.ReceiveCommand
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.io.IOException

@Component
class GitPostReceiveEventListener(
    private val gitService: GitService,
    private val commitPostProcessingService: CommitPostProcessingService
) {
    private val logger = LoggerFactory.getLogger(GitPostReceiveEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleGitPostReceiveEvent(event: GitPostReceiveEvent) {
        logger.info("Handling GitPostReceiveEvent asynchronously for project: ${event.project.name} by user: ${event.user.loginId}")
        
        val commits = mutableListOf<RevCommit>()
        val refNames = mutableListOf<String>()

        val repoDir = gitService.getRepositoryPath(event.project.owner ?: "", event.project.name)
        if (!repoDir.exists()) {
            logger.warn("Repository directory does not exist: ${repoDir.absolutePath}")
            return
        }

        try {
            val repository = RepositoryBuilder().setGitDir(repoDir).build()
            repository.use { repo ->
                for (command in event.commands) {
                    if (isNewOrUpdateCommand(command)) {
                        commits.addAll(parseCommitsFrom(command, repo))
                        refNames.add(command.refName)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse commits from git repository", e)
            return
        }

        logger.info("Parsed ${commits.size} commits from ref: $refNames")

        val pushed = PushedVcsCommits(commits.map { commit ->
            PushedVcsCommit(
                id = commit.name,
                message = commit.fullMessage,
                authorName = commit.authorIdent?.name ?: "",
                authorEmail = commit.authorIdent?.emailAddress ?: "",
                timestamp = commit.authorIdent?.`when`?.toInstant(),
                committerName = commit.committerIdent?.name ?: "",
                committerEmail = commit.committerIdent?.emailAddress ?: ""
            )
        }, refNames)
        commitPostProcessingService.process(event.project, pushed, event.user)
    }

    private fun isNewOrUpdateCommand(command: ReceiveCommand): Boolean {
        return command.type == ReceiveCommand.Type.CREATE ||
                command.type == ReceiveCommand.Type.UPDATE ||
                command.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD
    }

    private fun parseCommitsFrom(command: ReceiveCommand, repository: Repository): Collection<RevCommit> {
        val list = mutableListOf<RevCommit>()
        try {
            val endRange = command.newId
            val startRange = command.oldId

            RevWalk(repository).use { rw ->
                rw.markStart(rw.parseCommit(endRange))
                if (startRange.equals(ObjectId.zeroId())) {
                    list.add(rw.parseCommit(endRange))
                    return list
                } else {
                    rw.markUninteresting(rw.parseCommit(startRange))
                }

                for (rev in rw) {
                    list.add(rev)
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to walk commits in Repository", e)
        }
        return list
    }

}
