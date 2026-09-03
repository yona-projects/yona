package com.github.yonaprojects.yona.domain.issue

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "issue_sharer")
class IssueSharer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var created: Instant = Instant.now(),

    @Column(nullable = false)
    var loginId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    // Issue.sharers <-> IssueSharer.issue 순환 참조가 그대로 직렬화되면 Jackson이
    // 무한 중첩(StreamWriteConstraints 깊이 제한 초과)으로 실패한다 (P1-82에서 이슈를
    // REST 응답으로 직접 반환하는 경로에 sharers가 채워지면서 드러난 기존 결함).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue
)
