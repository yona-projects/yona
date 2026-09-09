package com.github.yonaprojects.yona.domain.webhook

import io.github.search5.hg4j.api.HgCommit as NativeHgCommit

// PushedCommits(JGit RevCommit 전용, 구체 클래스라 hg4j의 HgCommit을 감쌀 합성
// RevCommit을 만들 방법이 없다)와 동일한 웹훅 JSON 스키마를 Mercurial push에도 적용하기 위한
// 병렬 타입. WebhookServiceImpl.buildPushPayloadForHg()가 PushedCommits용 buildPushPayload()와
// 정확히 동일한 필드 구조(ref/commits/head_commit/sender/pusher/repository)를 만든다.
data class PushedHgCommits(
    val commits: List<NativeHgCommit>,
    val refNames: List<String>
)
