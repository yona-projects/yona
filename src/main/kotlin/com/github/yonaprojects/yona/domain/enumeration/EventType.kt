package com.github.yonaprojects.yona.domain.enumeration

enum class EventType(val messageKey: String, val order: Int) {
    NEW_ISSUE("notification.type.new.issue", 1),
    NEW_POSTING("notification.type.new.posting", 2),
    NEW_PULL_REQUEST("notification.type.new.pullrequest", 3),
    ISSUE_STATE_CHANGED("notification.type.issue.state.changed", 4),
    ISSUE_ASSIGNEE_CHANGED("notification.type.issue.assignee.changed", 5),
    PULL_REQUEST_STATE_CHANGED("notification.type.pullrequest.state.changed", 6),
    NEW_COMMENT("notification.type.new.comment", 7),
    NEW_REVIEW_COMMENT("notification.type.new.simple.comment", 8),
    MEMBER_ENROLL_REQUEST("notification.type.member.enroll", 9),
    PULL_REQUEST_MERGED("notification.type.pullrequest.merged", 10),
    ISSUE_REFERRED_FROM_COMMIT("notification.type.issue.referred.from.commit", 11),
    PULL_REQUEST_COMMIT_CHANGED("notification.type.pullrequest.commit.changed", 12),
    NEW_COMMIT("notification.type.new.commit", 13),
    PULL_REQUEST_REVIEW_STATE_CHANGED("notification.type.pullrequest.review.action.changed", 14),
    ISSUE_BODY_CHANGED("notification.type.issue.body.changed", 17),
    ISSUE_REFERRED_FROM_PULL_REQUEST("notification.type.issue.referred.from.pullrequest", 16),
    REVIEW_THREAD_STATE_CHANGED("notification.type.review.state.changed", 18),
    ORGANIZATION_MEMBER_ENROLL_REQUEST("notification.organization.type.member.enroll", 19),
    COMMENT_UPDATED("notification.type.comment.updated", 20),
    ISSUE_MOVED("notification.type.issue.is.moved", 21),
    ISSUE_SHARER_CHANGED("notification.type.issue.sharer.changed", 22),
    ISSUE_LABEL_CHANGED("notification.type.issue.label.changed", 23),
    ISSUE_MILESTONE_CHANGED("notification.type.milestone.changed", 24),
    POSTING_BODY_CHANGED("notification.type.posting.body.changed", 25),
    RESOURCE_DELETED("notification.type.resource.deleted", 26),
    MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 27),
    ORGANIZATION_MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 28),
    // yona-wiki P3-15(PR 승인/변경요청 워크플로) — PullRequestService.submitReview()가 APPROVE/
    // REQUEST_CHANGES/COMMENT 판정을 남길 때마다 기록한다. PULL_REQUEST_REVIEW_STATE_CHANGED(14번,
    // addReviewer/removeReviewer의 리뷰어 참여/취소 전용)와는 별개다 — 자기등록 여부와 실제 판정은
    // 서로 다른 사건이다(설계 결정 5번). 메시지 키 "notification.type.pullrequest.reviewed"는
    // messages*.properties에 이미 존재하던 미사용 키를 그대로 재사용한다(전 로케일 파일에 이미 있음).
    PULL_REQUEST_REVIEWED("notification.type.pullrequest.reviewed", 29);

    fun isCreating(): Boolean {
        return when (this) {
            NEW_ISSUE, NEW_POSTING, NEW_PULL_REQUEST, NEW_COMMENT, NEW_REVIEW_COMMENT -> true
            else -> false
        }
    }
}
