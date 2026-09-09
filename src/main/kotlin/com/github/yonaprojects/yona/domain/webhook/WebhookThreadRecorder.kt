package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// Webhook.sendRequest(payload, webhookId, resource) 대응 — Hangout Chat 응답의 thread.name을
// 파싱해 WebhookThread로 저장하는 쓰기 경로. WebhookServiceImpl.sendRequestAsync()의 HTTP 비동기
// 콜백은 원래 요청의 @Transactional 스코프 밖(별도 스레드)에서 실행되므로, 별도 Spring 빈으로
// 분리해 호출 시점에 새 트랜잭션이 열리도록 한다 — 같은 클래스 안에서 this.xxx()로 직접 호출하면
// Spring AOP 프록시를 우회해 트랜잭션이 걸리지 않는다.
@Component
class WebhookThreadRecorder(
    private val webhookThreadRepository: WebhookThreadRepository,
    private val webhookRepository: WebhookRepository
) {
    @Transactional
    fun recordThreadIfAbsent(webhookId: Long, resourceType: ResourceType, resourceId: String, threadId: String) {
        if (threadId.isBlank()) return

        val existing = webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(
            webhookId, resourceType, resourceId
        )
        if (existing != null) return

        val webhook = webhookRepository.findById(webhookId).orElse(null) ?: return

        webhookThreadRepository.save(
            WebhookThread(
                webhook = webhook,
                resourceType = resourceType,
                resourceId = resourceId,
                threadId = threadId,
                createdAt = Instant.now()
            )
        )
    }
}
