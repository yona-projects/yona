package com.github.yonaprojects.yona.domain.pullrequest

import java.time.Instant

// yona git/partial_pull_request_event.scala.html 대응 — PR "conversation" 탭에 상태변경/병합/
// 리뷰완료 이력(PullRequestEvent)만 시간순으로 보여준다. P1-106에서 댓글 스레드까지 함께 병합해
// 보여주던 kind 판별 필드(thread/kind)는 legacy 원본 범위를 넘어선 확장이었음을 확인해 P2-39에서
// 제거했다(사용자 확인 완료) — 댓글은 legacy와 동일하게 "changes" 탭의 diff 인라인에서만 노출된다.
data class PullRequestTimelineItem(
    val date: Instant,
    val event: PullRequestEvent
)
