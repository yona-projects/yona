package dev.yona.domain.enumeration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 서수(선언 순서)로 저장하는 열거형이 v1.6과 같은 순서인지 검증한다.
 *
 * v1.6 DB의 `issue.state`, `milestone.state`, `pull_request.state`(`int`)와 `webhook.webhook_type`(`tinyint`)에는 서수가
 * 들어 있다. 순서가 달라지면 기존 데이터의 뜻이 바뀐다(예: 닫힌 이슈가 열린 이슈로 읽힌다). 기준은 v1.6
 * `models.enumeration.State`, `WebhookType`의 선언 순서이다.
 */
class EnumOrdinalParityTest {

    @Test
    fun `State의 선언 순서는 v1_6과 같다`() {
        assertEquals(listOf("ALL", "OPEN", "CLOSED", "REJECTED", "CONFLICT", "RESOLVED", "MERGED", "DRAFT"), State.entries.map { it.name })
    }

    @Test
    fun `WebhookType의 선언 순서는 v1_6과 같다`() {
        assertEquals(listOf("SIMPLE", "DETAIL_SLACK", "DETAIL_HANGOUT_CHAT", "JSON"), WebhookType.entries.map { it.name })
    }

    @Test
    fun `v1_6이 저장한 정수 값을 같은 상태로 읽는다`() {
        val state = StateConverter()
        assertEquals(State.OPEN, state.convertToEntityAttribute(1))
        assertEquals(State.CLOSED, state.convertToEntityAttribute(2))
        assertEquals(State.MERGED, state.convertToEntityAttribute(6))
        assertEquals(1, state.convertToDatabaseColumn(State.OPEN))
        assertEquals(2, state.convertToDatabaseColumn(State.CLOSED))
        val webhook = WebhookTypeConverter()
        assertEquals(WebhookType.DETAIL_SLACK, webhook.convertToEntityAttribute(1))
        assertEquals(3.toByte(), webhook.convertToDatabaseColumn(WebhookType.JSON))
    }
}
