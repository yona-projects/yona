package com.github.yonaprojects.yona.domain.pullrequest

import java.time.Instant

// PR "conversation" 탭에 상태변경/병합/리뷰완료 이력(PullRequestEvent)만 시간순으로 보여준다.
// 댓글 스레드까지 함께 병합해 보여주던 kind 판별 필드(thread/kind)는 legacy 원본 범위를 넘어선
// 확장이라 제거했다 — 댓글은 legacy와 동일하게 "changes" 탭의 diff 인라인에서만 노출된다.
data class PullRequestTimelineItem(
    val date: Instant,
    val event: PullRequestEvent
)
