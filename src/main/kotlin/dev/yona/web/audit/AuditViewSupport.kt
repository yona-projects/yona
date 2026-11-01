package dev.yona.web.audit

import dev.yona.domain.audit.AuditFilter
import dev.yona.domain.audit.AuditLog
import dev.yona.domain.audit.AuditOutcome
import dev.yona.domain.audit.AuditPage
import dev.yona.domain.audit.AuditQueryService
import dev.yona.domain.audit.AuditScope
import dev.yona.web.TemplateHelper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

/** 감사 로그 표의 한 줄이다. [details]는 줄여 보이고 [detailsFull]은 마우스를 올렸을 때 보인다. */
data class AuditRow(
    val id: Long, val time: String, val actorType: String, val actor: String, val action: String, val outcome: String, val targetType: String, val target: String,
    val ip: String, val details: String, val detailsFull: String
)

/**
 * 감사 로그 화면(사이트, 조직, 사용자)이 함께 쓰는 조립 도구이다(로드맵 X-9 A6). 요청 매개변수를 [AuditFilter]로 읽고, 표와 쪽 나누기와 내려받기 링크의 값을 만든다.
 * 쪽 번호는 화면과 REST 모두 1부터이다. 기간(`from`, `to`)은 UTC 날짜(`yyyy-MM-dd`)이고 `to`는 그 날의 끝까지 포함한다.
 */
@Component
class AuditViewSupport(private val query: AuditQueryService, private val helper: TemplateHelper) {

    /** 요청 매개변수로 필터를 만든다. 모르는 값(결과 이름, 날짜 형식)은 무시한다. */
    fun filterOf(request: HttpServletRequest): AuditFilter = AuditFilter(
        actor = request.getParameter("actor"), action = request.getParameter("action"),
        outcome = request.getParameter("outcome")?.let { value -> AuditOutcome.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } },
        targetType = request.getParameter("targetType"), target = request.getParameter("target"), ip = request.getParameter("ip"),
        projectId = request.getParameter("projectId")?.toLongOrNull(),
        fromMillis = day(request.getParameter("from"))?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        toMillis = day(request.getParameter("to"))?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
    )

    private fun day(text: String?): LocalDate? = text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 필터 매개변수를 다시 이은 질의 문자열(`&` 없이 시작)이다. 쪽 나누기와 내려받기 링크가 쓴다. 비어 있는 값은 뺀다. */
    fun filterQuery(request: HttpServletRequest): String = FILTER_PARAMS.mapNotNull { name ->
        request.getParameter(name)?.takeIf { it.isNotBlank() }?.let { "$name=" + URLEncoder.encode(it.trim(), Charsets.UTF_8) }
    }.joinToString("&")

    fun rowOf(entry: AuditLog): AuditRow {
        val details = entry.details.orEmpty()
        return AuditRow(
            entry.id, helper.dateString(Date(entry.createdMillis), "yyyy-MM-dd HH:mm:ss"), entry.actorType.name, entry.actorLogin.orEmpty(), entry.action, entry.outcome.name,
            entry.targetType.orEmpty(), entry.targetLabel.orEmpty(), entry.ip.orEmpty(), if (details.length > 120) details.take(120) + "…" else details, details
        )
    }

    /**
     * 화면 모델이다. [base]는 이 화면의 경로(질의 문자열 제외)이고 내려받기는 `[base]/export`이다. 필터 입력란이 채울 값(`filter`), 표의 줄(`rows`),
     * 쪽 정보(`page`, `pages`, `total`, `size`)와 이전과 다음 쪽 링크(`prevUrl`, `nextUrl`), 내려받기 링크(`exportCsv`, `exportJsonl`, `exportJson`)를 담는다.
     */
    fun model(request: HttpServletRequest, base: String, scope: AuditScope): Map<String, Any?> {
        val filter = filterOf(request)
        val page = (request.getParameter("page")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val size = (request.getParameter("size")?.toIntOrNull() ?: AuditQueryService.DEFAULT_PAGE_SIZE).coerceIn(1, AuditQueryService.MAX_PAGE_SIZE)
        val result: AuditPage = query.search(filter, scope, page - 1, size)
        val keep = filterQuery(request)
        fun link(p: Int) = base + "?" + listOf(keep, "page=$p", "size=$size").filter { it.isNotEmpty() }.joinToString("&")
        fun export(format: String) = "$base/export?" + listOf(keep, "format=$format").filter { it.isNotEmpty() }.joinToString("&")
        return linkedMapOf(
            "base" to base,
            "rows" to result.items.map { rowOf(it) },
            "page" to page, "pages" to result.pages, "total" to result.total, "size" to size,
            "prevUrl" to if (page > 1) link(page - 1) else null,
            "nextUrl" to if (page < result.pages) link(page + 1) else null,
            "filter" to mapOf(
                "actor" to request.getParameter("actor").orEmpty(), "action" to request.getParameter("action").orEmpty(), "outcome" to (filter.outcome?.name ?: ""),
                "targetType" to request.getParameter("targetType").orEmpty(), "target" to request.getParameter("target").orEmpty(), "ip" to request.getParameter("ip").orEmpty(),
                "from" to request.getParameter("from").orEmpty(), "to" to request.getParameter("to").orEmpty()
            ),
            "outcomes" to AuditOutcome.entries.map { it.name },
            "exportCsv" to export("csv"), "exportJsonl" to export("jsonl"), "exportJson" to export("json")
        )
    }

    private companion object {
        val FILTER_PARAMS = listOf("actor", "action", "outcome", "targetType", "target", "ip", "projectId", "from", "to")
    }
}
