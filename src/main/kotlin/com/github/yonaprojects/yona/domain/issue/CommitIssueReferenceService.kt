package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CommitIssueReferenceService(
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository
) {
    private val logger = LoggerFactory.getLogger(CommitIssueReferenceService::class.java)

    fun record(project: Project, sender: User, commitId: String, commitMessage: String) {
        val issueNumbers = IssueReferenceParser.findReferredIssueNumbers(commitMessage)
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number) ?: continue

            val issueEvent = IssueEvent(
                issue = issue,
                senderLoginId = sender.loginId,
                senderEmail = sender.email,
                newValue = commitId,
                created = Instant.now(),
                eventType = EventType.ISSUE_REFERRED_FROM_COMMIT
            )
            issueEventRepository.save(issueEvent)
            logger.info("[ISSUE REFER] Recorded issue #$number referred from commit $commitId")
        }
    }
}
