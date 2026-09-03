package com.github.yonaprojects.yona.domain.issue

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface IssueSharerRepository : JpaRepository<IssueSharer, Long> {
    fun findByLoginIdAndIssueId(loginId: String, issueId: Long): Optional<IssueSharer>
    fun findByIssueId(issueId: Long): List<IssueSharer>
}
