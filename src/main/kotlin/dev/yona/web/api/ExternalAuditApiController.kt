package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditExport
import dev.yona.domain.audit.AuditExporter
import dev.yona.domain.audit.AuditFormat
import dev.yona.domain.audit.AuditQueryService
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.audit.AuditScope
import dev.yona.domain.audit.AuditVerifier
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.User
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.api.docs.*
import dev.yona.web.audit.AuditViewSupport
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import java.time.LocalDate
import java.time.ZoneOffset
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.Parameter as ParamDoc
import io.swagger.v3.oas.annotations.enums.ParameterIn

/**
 * 감사 로그 REST이다(로드맵 X-9 A6, `decisions.md` D-62). 사이트 전체(`site_admin` 범위이면서 사이트 관리자), 조직(`admin:org` 범위이면서 조직 관리자), 사용자 본인(`read:user`)이 있다.
 * 같은 필터(`actor`, `action`, `outcome`, `targetType`, `target`, `ip`, `projectId`, `from`, `to`)와 쪽 나누기(`page`는 1부터, `size`는 최대 200)를 쓴다. `format`이 `csv`나 `jsonl`이면
 * 최대 `limit`건(기본 1만, 최대 10만)을 시간순 파일로 내려받고, 내려받은 일도 감사 로그에 남는다. 기본은 `{"total", "page", "size", "events"}` JSON이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalAuditApiController(
    private val query: AuditQueryService,
    private val verifier: AuditVerifier,
    private val streamer: dev.yona.domain.audit.AuditStreamer,
    private val exporter: AuditExporter,
    private val recorder: AuditRecorder,
    private val support: AuditViewSupport,
    private val organizations: OrganizationService,
    private val accessControl: AccessControl,
    private val requests: ApiRequests
) {

    @ApiOperation(operationId = "listAuditEvents", tags = [ApiTags.AUDIT], summary = "감사 로그 조회와 내려받기(사이트 관리자)", description = "사이트 전체의 감사 로그이다. 최신순이다. `format=csv|jsonl|json`이면 파일로 내려받는다. 사이트 관리자이면서 `site_admin` 범위가 있는 토큰만 쓸 수 있다.")
    @ApiResponse(responseCode = "200", description = "감사 로그", content = [Content(mediaType = "application/json", schema = Schema(implementation = AuditEventList::class)), Content(mediaType = "text/csv", schema = Schema(type = "string")), Content(mediaType = "application/x-ndjson", schema = Schema(type = "string"))])
    @ApiResponse(responseCode = "400", description = "`format`이 `csv`, `jsonl`, `json`이 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ParamDoc(name = "actor", description = "행위자 로그인 ID에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "action", description = "조치 이름. 정확히 같거나 이 글로 시작하면 맞는다(`project.`는 프로젝트 이벤트 전부)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "outcome", description = "결과: `SUCCESS`, `FAILURE`, `DENIED`", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "targetType", description = "대상 종류(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "target", description = "대상 이름에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "ip", description = "원격 주소(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "projectId", description = "소속 프로젝트 번호", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "from", description = "이 날(UTC, `yyyy-MM-dd`)부터", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "to", description = "이 날(UTC, `yyyy-MM-dd`)까지(그날 끝 포함)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "page", description = "쪽 번호(1부터)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "size", description = "한 쪽의 건수(기본 50, 최대 200)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "format", description = "`csv`, `jsonl`, `json`이면 파일로 내려받는다. 없으면 목록 JSON", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "limit", description = "내려받을 최대 건수(기본 10000, 최대 100000)", `in` = ParameterIn.QUERY)
    @GetMapping("/-_-api/v1/admin/audit")
    @Transactional(readOnly = true)
    fun site(request: HttpServletRequest, response: HttpServletResponse): Any? {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        if (!accessControl.isSiteManager(user)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return respond(request, response, AuditScope.Site, "site")
    }

    @ApiOperation(operationId = "verifyAuditChain", tags = [ApiTags.AUDIT], summary = "감사 로그 무결성 검증(사이트 관리자)", description = "해시 사슬을 처음부터 다시 계산해 변조나 누락을 찾는다. 온전하면 `ok`가 `true`이고, 아니면 처음 어긋난 기록 번호와 이유가 있다.")
    @ApiResponse(responseCode = "200", description = "검증 결과", content = [Content(schema = Schema(implementation = AuditVerifyResult::class))])
    @GetMapping("/-_-api/v1/admin/audit/verify")
    fun verify(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        if (!accessControl.isSiteManager(user)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val result = verifier.verify()
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(linkedMapOf("ok" to result.ok, "checked" to result.checked, "brokenId" to result.brokenId, "reason" to result.reason))
    }

    @ApiOperation(operationId = "getAuditStreams", tags = [ApiTags.AUDIT], summary = "감사 로그 외부 스트리밍 상태(사이트 관리자)", description = "설정된 대상(웹훅, syslog)별로 마지막으로 보낸 기록 번호, 밀린 건수, 마지막 오류를 알려 준다. 대상이 설정되지 않았으면 빈 목록이다.")
    @ApiResponse(responseCode = "200", description = "스트리밍 상태", content = [Content(schema = Schema(implementation = AuditStreamList::class))])
    @GetMapping("/-_-api/v1/admin/audit/streams")
    fun streams(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        if (!accessControl.isSiteManager(user)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        fun time(millis: Long?) = millis?.let { java.time.Instant.ofEpochMilli(it).toString() }
        val streams = streamer.status().map {
            linkedMapOf("name" to it.name, "lastId" to it.lastId, "backlog" to it.backlog, "lastSentAt" to time(it.lastSentAt), "lastError" to it.lastError, "lastErrorAt" to time(it.lastErrorAt), "failures" to it.failures)
        }
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(linkedMapOf("streams" to streams))
    }

    @ApiOperation(operationId = "listOrganizationAuditEvents", tags = [ApiTags.AUDIT], summary = "조직의 감사 로그 조회와 내려받기(조직 관리자)", description = "조직 이벤트와 그 조직이 소유한 프로젝트의 이벤트만 담는다. 조직 관리자이면서 `admin:org` 범위가 있는 토큰만 쓸 수 있다.")
    @ApiResponse(responseCode = "200", description = "감사 로그", content = [Content(mediaType = "application/json", schema = Schema(implementation = AuditEventList::class)), Content(mediaType = "text/csv", schema = Schema(type = "string")), Content(mediaType = "application/x-ndjson", schema = Schema(type = "string"))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ParamDoc(name = "actor", description = "행위자 로그인 ID에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "action", description = "조치 이름. 정확히 같거나 이 글로 시작하면 맞는다(`project.`는 프로젝트 이벤트 전부)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "outcome", description = "결과: `SUCCESS`, `FAILURE`, `DENIED`", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "targetType", description = "대상 종류(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "target", description = "대상 이름에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "ip", description = "원격 주소(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "projectId", description = "소속 프로젝트 번호", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "from", description = "이 날(UTC, `yyyy-MM-dd`)부터", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "to", description = "이 날(UTC, `yyyy-MM-dd`)까지(그날 끝 포함)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "page", description = "쪽 번호(1부터)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "size", description = "한 쪽의 건수(기본 50, 최대 200)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "format", description = "`csv`, `jsonl`, `json`이면 파일로 내려받는다. 없으면 목록 JSON", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "limit", description = "내려받을 최대 건수(기본 10000, 최대 100000)", `in` = ParameterIn.QUERY)
    @GetMapping("/-_-api/v1/orgs/{org}/audit")
    @Transactional(readOnly = true)
    fun organization(@PathVariable org: String, request: HttpServletRequest, response: HttpServletResponse): Any? {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizations.findByName(org) ?: return requests.message(HttpStatus.NOT_FOUND, "no organization by request")
        if (!accessControl.isAllowed(user, organization.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return respond(request, response, AuditScope.Organization(requireNotNull(organization.id)), "organization")
    }

    @ApiOperation(operationId = "listMyAuditEvents", tags = [ApiTags.AUDIT], summary = "내 보안 기록 조회", description = "토큰 주인 본인의 로그인, 토큰, 계정 기록(`auth.`, `token.`, `account.`)이다. `read:user` 범위가 필요하다.")
    @ApiResponse(responseCode = "200", description = "감사 로그", content = [Content(schema = Schema(implementation = AuditEventList::class))])
    @ParamDoc(name = "actor", description = "행위자 로그인 ID에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "action", description = "조치 이름. 정확히 같거나 이 글로 시작하면 맞는다(`project.`는 프로젝트 이벤트 전부)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "outcome", description = "결과: `SUCCESS`, `FAILURE`, `DENIED`", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "targetType", description = "대상 종류(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "target", description = "대상 이름에 들어 있어야 하는 글(대소문자 무시)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "ip", description = "원격 주소(정확히)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "projectId", description = "소속 프로젝트 번호", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "from", description = "이 날(UTC, `yyyy-MM-dd`)부터", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "to", description = "이 날(UTC, `yyyy-MM-dd`)까지(그날 끝 포함)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "page", description = "쪽 번호(1부터)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "size", description = "한 쪽의 건수(기본 50, 최대 200)", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "format", description = "`csv`, `jsonl`, `json`이면 파일로 내려받는다. 없으면 목록 JSON", `in` = ParameterIn.QUERY)
    @ParamDoc(name = "limit", description = "내려받을 최대 건수(기본 10000, 최대 100000)", `in` = ParameterIn.QUERY)
    @GetMapping("/-_-api/v1/user/audit")
    @Transactional(readOnly = true)
    fun mine(request: HttpServletRequest, response: HttpServletResponse): Any? {
        val user: User = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        return respond(request, response, AuditScope.User(requireNotNull(user.id), user.loginId), "user")
    }

    /** 형식에 따라 목록 JSON이나 파일로 응답한다. 파일이면 응답에 바로 쓰고 `null`을 돌려준다. */
    private fun respond(request: HttpServletRequest, response: HttpServletResponse, scope: AuditScope, scopeName: String): Any? {
        val formatText = request.getParameter("format")
        if (!formatText.isNullOrBlank()) {
            val format = AuditFormat.of(formatText) ?: return requests.message(HttpStatus.BAD_REQUEST, "format must be csv, jsonl or json")
            val limit = (request.getParameter("limit")?.toIntOrNull() ?: AuditExporter.DEFAULT_LIMIT).coerceIn(1, AuditExporter.MAX_LIMIT)
            recorder.record(AuditEvent("audit.exported", targetType = "audit", details = mapOf("format" to format.param, "scope" to scopeName, "limit" to limit, "via" to "api")))
            response.status = HttpStatus.OK.value()
            response.contentType = format.contentType
            response.setHeader("Content-Disposition", "attachment; filename=audit-${LocalDate.now(ZoneOffset.UTC)}.${format.extension}")
            exporter.write(format, support.filterOf(request), scope, limit, response.outputStream)
            return null
        }
        val page = (request.getParameter("page")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val size = (request.getParameter("size")?.toIntOrNull() ?: AuditQueryService.DEFAULT_PAGE_SIZE)
        val result = query.search(support.filterOf(request), scope, page - 1, size)
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(linkedMapOf("total" to result.total, "page" to page, "size" to result.size, "events" to result.items.map { AuditExport.fields(it) }))
    }
}
