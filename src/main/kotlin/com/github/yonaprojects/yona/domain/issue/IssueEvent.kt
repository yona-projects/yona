package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import jakarta.persistence.*
import java.time.Instant

/**
 * yona의 models/IssueEvent.java 대응(최소 스키마).
 * 이슈에 대한 변경 이력 한 건을 나타낸다.
 */
@Entity
@Table(name = "issue_event")
class IssueEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue,

    var senderLoginId: String? = null,
    var senderEmail: String? = null,

    @Column(length = 1_000_000)
    var oldValue: String? = null,

    @Column(length = 1_000_000)
    var newValue: String? = null,

    var created: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var eventType: EventType = EventType.ISSUE_REFERRED_FROM_COMMIT
)
