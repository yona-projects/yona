package dev.yona.domain.audit

import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 로그의 조회 조건이다(로드맵 X-9 A6). 값이 `null`이면 그 조건은 걸지 않는다.
 *
 * @property actor 행위자 로그인 ID에 들어 있어야 하는 글(대소문자 무시)
 * @property action 조치 이름. 정확히 같거나 이 글로 시작하면 맞는다(`project.`는 프로젝트 이벤트 전부)
 * @property outcome 결과
 * @property targetType 대상 종류(정확히)
 * @property target 대상 이름에 들어 있어야 하는 글(대소문자 무시)
 * @property ip 원격 주소(정확히)
 * @property projectId 소속 프로젝트 번호
 * @property fromMillis 이 시각(에폭 밀리초) 이후(포함)
 * @property toMillis 이 시각 이전(미포함)
 */
data class AuditFilter(
    val actor: String? = null,
    val action: String? = null,
    val outcome: AuditOutcome? = null,
    val targetType: String? = null,
    val target: String? = null,
    val ip: String? = null,
    val projectId: Long? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null
)

/** 조회할 수 있는 범위이다. 호출하는 쪽(컨트롤러)이 권한을 확인한 뒤 알맞은 범위를 고른다. */
sealed interface AuditScope {
    /** 사이트 전체이다(사이트 관리자). */
    data object Site : AuditScope

    /** 한 조직에 속한 기록이다(조직 관리자). 조직 번호를 담은 조직 이벤트와 그 조직 소유 프로젝트의 이벤트이다. */
    data class Organization(val id: Long) : AuditScope

    /** 한 사용자 본인의 로그인, 토큰, 계정 기록이다. 행위자 번호나 로그인 ID가 본인이거나 대상이 본인인 것 가운데 `auth.`, `token.`, `account.` 범주만이다. */
    data class User(val id: Long, val login: String?) : AuditScope
}

/** 조회 결과 한 쪽이다. [page]는 0부터이다. */
data class AuditPage(val items: List<AuditLog>, val total: Long, val page: Int, val size: Int) {
    val pages: Int get() = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
}

/**
 * 감사 로그를 조건과 범위로 조회하고 내보낸다(로드맵 X-9 A6, `decisions.md` D-62). 조회는 최신순, 내보내기는 시간순이다(수집기가 이어 읽기 좋게).
 */
@Service
class AuditQueryService(private val logs: AuditLogRepository) {

    private val mapper = tools.jackson.databind.json.JsonMapper.builder().build()

    /** 쪽 크기는 1에서 [MAX_PAGE_SIZE] 사이로 맞추고 쪽 번호는 0 이상으로 맞춘다. */
    @Transactional(readOnly = true)
    fun search(filter: AuditFilter, scope: AuditScope, page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): AuditPage {
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val found = logs.findAll(specification(filter, scope), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")))
        return AuditPage(found.content, found.totalElements, safePage, safeSize)
    }

    /** 조건에 맞는 기록을 시간순으로 최대 [limit]건 [consumer]에 넘긴다. 쪽으로 나눠 읽어 메모리를 아낀다. */
    @Transactional(readOnly = true)
    fun stream(filter: AuditFilter, scope: AuditScope, limit: Int, consumer: (AuditLog) -> Unit) {
        val spec = specification(filter, scope)
        var remaining = limit.coerceAtLeast(0)
        var lastId = 0L
        while (remaining > 0) {
            val page = logs.findAll(spec.and { root, _, cb -> cb.greaterThan(root.get<Long>("id"), lastId) }, PageRequest.of(0, minOf(EXPORT_CHUNK, remaining), Sort.by(Sort.Direction.ASC, "id"))).content
            if (page.isEmpty()) return
            for (entry in page) {
                consumer(entry)
                lastId = entry.id
                remaining--
            }
        }
    }

    /** 기록 한 건을 JSON 한 줄로 만든다. 줄 파일과 같은 모양이다([AuditExport]). */
    fun jsonLine(entry: AuditLog): String = mapper.writeValueAsString(AuditExport.fields(entry))

    private fun specification(filter: AuditFilter, scope: AuditScope): Specification<AuditLog> = Specification { root, _, cb ->
        val all = mutableListOf<Predicate>()
        filter.actor?.takeIf { it.isNotBlank() }?.let { all += cb.like(cb.lower(root.get("actorLogin")), "%${escape(it.trim().lowercase())}%", '\\') }
        filter.action?.takeIf { it.isNotBlank() }?.let { all += cb.like(root.get("action"), "${escape(it.trim())}%", '\\') }
        filter.outcome?.let { all += cb.equal(root.get<AuditOutcome>("outcome"), it) }
        filter.targetType?.takeIf { it.isNotBlank() }?.let { all += cb.equal(root.get<String>("targetType"), it.trim()) }
        filter.target?.takeIf { it.isNotBlank() }?.let { all += cb.like(cb.lower(root.get("targetLabel")), "%${escape(it.trim().lowercase())}%", '\\') }
        filter.ip?.takeIf { it.isNotBlank() }?.let { all += cb.equal(root.get<String>("ip"), it.trim()) }
        filter.projectId?.let { all += cb.equal(root.get<Long>("projectId"), it) }
        filter.fromMillis?.let { all += cb.greaterThanOrEqualTo(root.get("createdMillis"), it) }
        filter.toMillis?.let { all += cb.lessThan(root.get("createdMillis"), it) }
        when (scope) {
            AuditScope.Site -> Unit
            is AuditScope.Organization -> all += cb.equal(root.get<Long>("organizationId"), scope.id)
            is AuditScope.User -> {
                val mine = mutableListOf(cb.equal(root.get<Long>("actorId"), scope.id), cb.and(cb.equal(root.get<String>("targetType"), "user"), cb.equal(root.get<String>("targetId"), scope.id.toString())))
                scope.login?.takeIf { it.isNotEmpty() }?.let { mine += cb.equal(root.get<String>("actorLogin"), it) }
                all += cb.or(*mine.toTypedArray())
                all += cb.or(*USER_CATEGORIES.map { cb.like(root.get("action"), "$it%") }.toTypedArray())
            }
        }
        cb.and(*all.toTypedArray())
    }

    /** LIKE의 특수 문자(`%`, `_`, `\`)를 그대로 찾도록 이스케이프한다. */
    private fun escape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 200
        private const val EXPORT_CHUNK = 500

        /** 일반 사용자가 자기 기록으로 볼 수 있는 범주이다. */
        val USER_CATEGORIES = listOf("auth.", "token.", "account.")
    }
}

/**
 * 감사 기록의 CSV 표현이다. 열 이름과 순서는 [AuditExport.COLUMNS]와 같다. 값은 큰따옴표로 감싸고 안의 큰따옴표는 두 번 쓴다. 스프레드시트가 수식으로 읽는 값(`=`, `+`, `-`, `@`로 시작)은
 * 앞에 작은따옴표를 붙여 실행되지 않게 한다(CSV 주입 방지).
 */
object AuditCsv {

    val HEADER: String = AuditExport.COLUMNS.joinToString(",")

    fun row(entry: AuditLog): String = AuditExport.fields(entry).values.joinToString(",") { cell(it) }

    private fun cell(value: Any?): String {
        val text = value?.toString().orEmpty()
        val safe = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || safe != text) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
