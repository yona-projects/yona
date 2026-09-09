package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

// GitHub의 Approve/Request changes/Comment에 대응하는,
// "PR 전체에 대한 판정" 개념. CommentThread(코드 라인별 리뷰 스레드, ThreadState는 OPEN/CLOSED뿐)와는
// 완전히 별개의 신규 엔티티다 — 기존 구조가 이미 "스레드"(코드 리뷰
// 코멘트, CommentThread/ReviewComment)와 "PR 전체 댓글"(NonRangedCodeCommentThread를 통한
// `pr comment`)을 구분해서 다루고 있어, 이 판정도 그 구분과 일관되게 PR 단위 별도 엔티티로 분리한다.
//
// 재판정 시 이력 보존(GitHub와 동일) — 같은 리뷰어가 여러 번 판정을 남기면 매번 새
// 로우를 추가한다(UPDATE가 아니라 INSERT). 타임라인에는 전부 표시되지만, require_approvals 등
// 정책 판단에는 리뷰어별 가장 최근 판정만 유효하다
// (PullRequestServiceImpl.latestDecisiveReviewByReviewer() 참고).
//
// stale 승인 자동 무효화는 구현하지 않는다 — 새 커밋이 push돼도 기존 승인 로우는
// 그대로 유효한 채로 남는다(GitHub 기본값과 동일).
@Entity
@Table(name = "pull_request_review")
class PullRequestReview(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    var pullRequest: PullRequest,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    var reviewer: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: ReviewState,

    @Column(length = 1_000_000)
    var body: String? = null,

    @Column(nullable = false)
    var createdDate: Instant = Instant.now()
) {
    // GitHub의 review event 세 가지(APPROVE/REQUEST_CHANGES/COMMENT) 그대로 — 그 이상의 세분화
    // (예: DISMISS)는 이 계획의 DoD에 없어 과도한 설계로 보류한다.
    enum class ReviewState {
        APPROVE, REQUEST_CHANGES, COMMENT
    }
}
