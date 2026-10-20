package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * One commit whose message may reference issues. [sender] is the Yona account credited for the
 * reference (the pusher for pushes to Yona, an administrator-confirmed account or nobody for commits
 * imported from another server). A null [created] keeps the processing time of each event.
 */
data class CommitReference(
    val commitId: String,
    val message: String,
    val sender: User?,
    val created: Instant? = null
)

@Service
class CommitIssueReferenceService(
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository
) {
    private val logger = LoggerFactory.getLogger(CommitIssueReferenceService::class.java)

    fun record(project: Project, commit: CommitReference) {
        val issueNumbers = IssueReferenceParser.findReferredIssueNumbers(commit.message)
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number) ?: continue

            val issueEvent = IssueEvent(
                issue = issue,
                senderLoginId = commit.sender?.loginId,
                senderEmail = commit.sender?.email,
                newValue = commit.commitId,
                created = commit.created ?: Instant.now(),
                eventType = EventType.ISSUE_REFERRED_FROM_COMMIT
            )
            issueEventRepository.save(issueEvent)
            logger.info("[ISSUE REFER] Recorded issue #$number referred from commit ${commit.commitId}")
        }
    }
}
