package dev.yona.domain.audit

import org.springframework.stereotype.Component
import java.io.OutputStream

/** 감사 로그 내보내기 형식이다. [param]은 요청의 `format` 값이다. */
enum class AuditFormat(val param: String, val contentType: String, val extension: String) {
    CSV("csv", "text/csv;charset=UTF-8", "csv"),
    JSONL("jsonl", "application/x-ndjson;charset=UTF-8", "jsonl"),
    JSON("json", "application/json;charset=UTF-8", "json");

    companion object {
        /** `format` 값에 맞는 형식이다. 비어 있으면 [fallback], 모르는 값이면 `null`이다. */
        fun of(text: String?, fallback: AuditFormat? = null): AuditFormat? =
            if (text.isNullOrBlank()) fallback else entries.firstOrNull { it.param.equals(text.trim(), ignoreCase = true) }
    }
}

/**
 * 감사 로그를 [AuditFormat] 형식으로 [OutputStream]에 쓴다(로드맵 X-9 A6). 시간순이고 [limit]건까지이다. CSV는 머리글이 있고, JSON은 `{"events": [...]}` 한 문서, JSON 줄은 한 줄에 한 건이다.
 */
@Component
class AuditExporter(private val query: AuditQueryService) {

    fun write(format: AuditFormat, filter: AuditFilter, scope: AuditScope, limit: Int, out: OutputStream) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        when (format) {
            AuditFormat.CSV -> {
                writer.write(AuditCsv.HEADER); writer.write("\n")
                query.stream(filter, scope, limit) { writer.write(AuditCsv.row(it)); writer.write("\n") }
            }
            AuditFormat.JSONL -> query.stream(filter, scope, limit) { writer.write(query.jsonLine(it)); writer.write("\n") }
            AuditFormat.JSON -> {
                writer.write("{\"events\":[")
                var first = true
                query.stream(filter, scope, limit) {
                    if (!first) writer.write(",")
                    first = false
                    writer.write(query.jsonLine(it))
                }
                writer.write("]}")
            }
        }
        writer.flush()
    }

    companion object {
        /** 내보내기 건수의 기본값과 상한이다. */
        const val DEFAULT_LIMIT = 10_000
        const val MAX_LIMIT = 100_000
    }
}
