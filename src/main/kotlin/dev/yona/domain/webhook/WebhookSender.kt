package dev.yona.domain.webhook

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Webhook
import dev.yona.domain.project.WebhookThread
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date

/** 구글 챗 스레드로 묶을 리소스이다. */
data class ThreadTarget(val type: ResourceType, val id: String)

/**
 * 웹훅 주소로 본문을 POST한다. v1.6 `Webhook.sendRequest`(`app/models/Webhook.java:560-640`)에 해당한다.
 *
 * 헤더는 `Content-Type: application/json; charset=utf-8`(Play WS가 문자열 본문에 붙이는 값), `User-Agent: Yobi-Hookshot`이고 토큰(`secret`)이 있으면 `Authorization: token <토큰> `(끝에 공백이 붙는다)이다.
 * 응답은 기다리지 않고(`yona.background-async=false`이면 기다린다) 2xx가 아니면 상태와 본문을 로그로 남긴다. 연결하지 못해도 로그만 남긴다.
 * 구글 챗 형식은 응답이 2xx이고 이 리소스의 스레드가 아직 없으면 응답의 `thread.name`을 스레드로 저장해 다음 알림을 같은 스레드로 보낸다.
 */
@Component
class WebhookSender(
    private val properties: YonaProperties,
    private val threads: WebhookThreadRepository,
    private val webhooks: WebhookRepository,
    transactionManager: PlatformTransactionManager,
    private val metrics: dev.yona.observability.CollaborationMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build()
    private val transaction = TransactionTemplate(transactionManager)
    private val mapper = JsonMapper.builder().build()

    /** `Authorization` 머리글 값이다. v1.6은 끝에 공백이 붙어 서버가 잘라 내야 했다(R-45-3). */
    internal fun authorizationValue(secret: String): String = "token $secret"

    /** [webhook]에 [payload]를 보낸다. [target]이 있으면 응답에서 스레드를 저장한다. */
    fun send(webhook: Webhook, payload: String, target: ThreadTarget? = null) {
        log.info(payload)
        val url = webhook.payloadUrl.orEmpty()
        val webhookId = requireNotNull(webhook.id)
        try {
            val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json; charset=utf-8").header("User-Agent", "Yobi-Hookshot").POST(HttpRequest.BodyPublishers.ofString(payload))
            if (!webhook.secret.isNullOrBlank()) builder.header("Authorization", authorizationValue(webhook.secret.orEmpty()))
            val request = builder.build()
            if (properties.backgroundAsync) {
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
                    if (error != null) { metrics.webhookUnreachable(); log.info("[Webhook] Request failed at given payload URL: {}", url) } else handle(webhookId, payload, response, target)
                }
            } else {
                handle(webhookId, payload, client.send(request, HttpResponse.BodyHandlers.ofString()), target)
            }
        } catch (e: Exception) {
            metrics.webhookUnreachable()
            log.info("[Webhook] Request failed at given payload URL: {}", url)
        }
    }

    private fun handle(webhookId: Long, payload: String, response: HttpResponse<String>, target: ThreadTarget?) {
        val status = response.statusCode()
        metrics.webhook(status)
        if (status < 200 || status >= 300) {
            // v1.6는 두 번째 값으로 응답 본문이 아니라 상태 문구(getStatusText)를 기록한다
            log.info("[Webhook] Request responded code  {}: {}", status, statusText(status))
            log.info("[Webhook] Request payload: {}", payload)
            return
        }
        if (target == null) return
        try {
            transaction.executeWithoutResult {
                if (threads.findFirstByWebhookIdAndResourceTypeAndResourceId(webhookId, target.type, target.id) == null) {
                    val name = mapper.readTree(response.body()).path("thread").path("name").asString("")
                    threads.save(WebhookThread(webhook = webhookReference(webhookId), resourceType = target.type, resourceId = target.id, threadId = name, createdAt = Date()))
                }
            }
        } catch (e: Exception) {
            log.info("[Webhook] Failed to keep the thread of the webhook {}", webhookId, e)
        }
    }

    private fun webhookReference(id: Long): Webhook = webhooks.getReferenceById(id)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val REQUEST_TIMEOUT_SECONDS = 120L
    }

    /** HTTP 상태 문구이다 (예: `Not Found`). 알 수 없는 코드는 빈 문자열이다 (v1.6 `WSResponse.getStatusText`). */
    private fun statusText(status: Int): String = org.springframework.http.HttpStatus.resolve(status)?.reasonPhrase.orEmpty()
}
