package com.github.yonaprojects.yona.domain.issue

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// RecentIssue.java 대응. 이슈와 게시글 방문 이력을 함께 추적하는 yona 원본 설계를 그대로 따라
// issueId/postingId 둘 다 nullable로 둔다.
@Entity
@Table(name = "recent_issue")
class RecentIssue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "user_id", nullable = false) var userId: Long = 0L,
    @Column(name = "issue_id") var issueId: Long? = null,
    @Column(name = "posting_id") var postingId: Long? = null,
    @Column(nullable = false) var title: String = "",
    @Column(nullable = false) var url: String = "",
    @Column(name = "created_date", nullable = false) var createdDate: Instant = Instant.now()
)
