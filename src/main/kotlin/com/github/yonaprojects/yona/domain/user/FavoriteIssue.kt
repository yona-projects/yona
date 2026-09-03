package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.issue.Issue
import jakarta.persistence.*

@Entity
@Table(name = "favorite_issue")
class FavoriteIssue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue
)

