package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset

/**
 * 감사 기록을 커밋한 뒤 JSON 줄 파일에도 한 줄씩 더한다(`<데이터>/audit/audit-<UTC 날짜>.jsonl`, `decisions.md` D-62). 로그 수집기나 SIEM이 파일을 읽어 가게 하려는 것이다.
 * DB의 기록이 원본이고 파일 쓰기가 실패해도 요청은 막지 않는다(오류만 남긴다). 설정 `yona.audit-file-enabled`가 꺼져 있으면 쓰지 않는다.
 */
@Component
class AuditFileSink(private val dataDirectory: DataDirectory, private val properties: YonaProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = JsonMapper.builder().build()

    /** 줄 파일이 있는 디렉터리이다. 보존으로 옮긴 파일은 그 아래 `archive/`에 둔다. */
    fun directory(): File = File(dataDirectory.root(), "audit")

    @Synchronized
    fun append(entry: AuditLog) {
        if (!properties.auditFileEnabled) return
        try {
            val date = Instant.ofEpochMilli(entry.createdMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val file = File(directory(), "audit-$date.jsonl")
            file.parentFile.mkdirs()
            Files.writeString(file.toPath(), line(entry) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: IOException) {
            log.error("감사 로그 파일에 쓰지 못했습니다: id={}", entry.id, e)
        }
    }

    /** 기록 한 건을 JSON 한 줄로 만든다. 줄 파일과 보관 파일, 내보내기가 같은 모양을 쓴다. */
    fun line(entry: AuditLog): String = mapper.writeValueAsString(AuditExport.fields(entry))
}

/** 감사 기록을 밖으로 내보낼 때의 필드 이름과 순서이다(줄 파일, 보관 파일, REST 내보내기가 같다). */
object AuditExport {

    /** CSV 머리글이기도 한 필드 이름 순서이다. */
    val COLUMNS = listOf(
        "id", "time", "actor_type", "actor_id", "actor_login", "action", "outcome", "target_type", "target_id", "target_label", "organization_id", "project_id",
        "ip", "user_agent", "request_id", "token_id", "token_scopes", "details", "prev_hash", "hash", "content_hash", "redacted_at"
    )

    fun fields(e: AuditLog): Map<String, Any?> = linkedMapOf(
        "id" to e.id, "time" to Instant.ofEpochMilli(e.createdMillis).toString(), "actor_type" to e.actorType.name, "actor_id" to e.actorId, "actor_login" to e.actorLogin,
        "action" to e.action, "outcome" to e.outcome.name, "target_type" to e.targetType, "target_id" to e.targetId, "target_label" to e.targetLabel,
        "organization_id" to e.organizationId, "project_id" to e.projectId, "ip" to e.ip, "user_agent" to e.userAgent, "request_id" to e.requestId,
        "token_id" to e.tokenId, "token_scopes" to e.tokenScopes, "details" to e.details, "prev_hash" to e.prevHash, "hash" to e.hash,
        "content_hash" to e.contentHash, "redacted_at" to e.redactedAt?.let { Instant.ofEpochMilli(it).toString() }
    )
}
