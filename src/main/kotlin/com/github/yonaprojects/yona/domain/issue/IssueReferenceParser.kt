package com.github.yonaprojects.yona.domain.issue

/**
 * yona의 models/Issue.java ISSUE_PATTERN + IssueEvent.findReferredIssue()에서
 * 텍스트(커밋 메시지) 파싱 부분만 분리한 순수 함수.
 */
object IssueReferenceParser {
    private val issuePattern = Regex("#(\\d+)")

    fun findReferredIssueNumbers(text: String): Set<Long> {
        return issuePattern.findAll(text)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .toSet()
    }
}
