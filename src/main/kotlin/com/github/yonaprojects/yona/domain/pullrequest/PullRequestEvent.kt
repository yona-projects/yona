package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.EventType
import jakarta.persistence.*
import java.time.Instant

/**
 * yona의 models/PullRequestEvent.java 대응(최소 스키마, draft-time 병합/취소
 * 최적화는 이식하지 않음 — 이슈 타임라인과 동일한 범위 조정 기준).
 */
@Entity
@Table(name = "pull_request_event")
class PullRequestEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    var pullRequest: PullRequest,

    var senderLoginId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var eventType: EventType = EventType.PULL_REQUEST_STATE_CHANGED,

    @Column(length = 1_000_000)
    var oldValue: String? = null,

    @Column(length = 1_000_000)
    var newValue: String? = null,

    var created: Instant = Instant.now()
)
