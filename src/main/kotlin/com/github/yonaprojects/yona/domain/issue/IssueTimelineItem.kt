package com.github.yonaprojects.yona.domain.issue

import java.time.Instant

// yona Issue.getTimeline()(댓글+IssueEvent 병합 정렬) 대응. 화면(issue/view.html)이 이
// 병합 리스트를 시간순으로 순회하며 타입별로 다르게 렌더링할 수 있도록 kind로 구분한다.
data class IssueTimelineItem(
    val kind: String,
    val date: Instant,
    val comment: IssueComment? = null,
    val event: IssueEvent? = null
)
