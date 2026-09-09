package com.github.yonaprojects.yona.domain.webhook

import org.eclipse.jgit.revwalk.RevCommit

// yona Webhook.sendRequestToPayloadUrl(List<RevCommit>, List<String>, User) 대응.
// 커밋은 DB 엔티티가 아니므로 WebhookServiceImpl.sendWebhook()의 resource: Any 자리에
// 이 마커 타입으로 감싸서 전달한다.
data class PushedCommits(
    val commits: List<RevCommit>,
    val refNames: List<String>
)
