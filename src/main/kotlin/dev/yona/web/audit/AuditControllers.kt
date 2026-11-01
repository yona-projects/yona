package dev.yona.web.audit

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditExporter
import dev.yona.domain.audit.AuditFormat
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.audit.AuditScope
import dev.yona.domain.audit.AuditVerification
import dev.yona.domain.audit.AuditVerifier
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.OrganizationService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.UrlPaths
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import dev.yona.web.organization.OrganizationViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 감사 로그를 내려받는 공통 처리이다. 형식과 건수를 읽고, 내려받은 일도 감사 로그에 남기고(`audit.exported`), 응답에 바로 쓴다. 형식이 틀리면 `false`이다.
 */
internal fun writeAuditExport(
    request: HttpServletRequest, response: HttpServletResponse, support: AuditViewSupport, exporter: AuditExporter, recorder: AuditRecorder, scope: AuditScope, scopeName: String
): Boolean {
    val format = AuditFormat.of(request.getParameter("format"), AuditFormat.CSV) ?: return false
    val limit = (request.getParameter("limit")?.toIntOrNull() ?: AuditExporter.DEFAULT_LIMIT).coerceIn(1, AuditExporter.MAX_LIMIT)
    recorder.record(AuditEvent("audit.exported", targetType = "audit", details = mapOf("format" to format.param, "scope" to scopeName, "limit" to limit)))
    response.contentType = format.contentType
    response.setHeader("Content-Disposition", "attachment; filename=audit-${LocalDate.now(ZoneOffset.UTC)}.${format.extension}")
    exporter.write(format, support.filterOf(request), scope, limit, response.outputStream)
    return true
}

/** 사이트 관리자의 감사 로그 화면이다(로드맵 X-9 A6, `decisions.md` D-62): 전체 조회, 내려받기, 해시 사슬 검증. */
@Controller
@SiteManagerOnly
@AnonymousCheck
class SiteAuditController(
    private val support: AuditViewSupport,
    private val exporter: AuditExporter,
    private val verifier: AuditVerifier,
    private val recorder: AuditRecorder,
    private val streamer: dev.yona.domain.audit.AuditStreamer,
    private val helper: dev.yona.web.TemplateHelper
) {

    @GetMapping("/sites/audit")
    @Transactional(readOnly = true)
    fun list(request: HttpServletRequest): ModelAndView =
        ModelAndView("site/audit").addObject("title", "site.sidebar.audit").addObject("streams", streamRows())
            .addAllObjects(support.model(request, "/sites/audit", AuditScope.Site))

    /** 외부 스트리밍 대상별 상태 표의 줄이다. 시각은 화면과 같은 형식이고 값이 없으면 `-`이다. */
    private fun streamRows() = streamer.status().map {
        fun time(millis: Long?) = millis?.let { m -> helper.dateString(java.util.Date(m), "yyyy-MM-dd HH:mm:ss") } ?: "-"
        AuditStreamRow(it.name, it.lastId, it.backlog, time(it.lastSentAt), it.lastError ?: "-", it.failures)
    }

    @GetMapping("/sites/audit/export")
    fun export(request: HttpServletRequest, response: HttpServletResponse): Any? =
        if (writeAuditExport(request, response, support, exporter, recorder, AuditScope.Site, "site")) null else ErrorViews.badRequest()

    /** 해시 사슬을 처음부터 다시 계산한 결과이다(`{"ok", "checked", "brokenId", "reason"}`). */
    @GetMapping("/sites/audit/verify")
    @ResponseBody
    fun verify(): AuditVerification = verifier.verify()
}

/** 조직 관리자의 조직 범위 감사 로그 화면이다. 조직 이벤트와 그 조직 소유 프로젝트의 이벤트만 보인다. */
@Controller
@AnonymousCheck
class OrganizationAuditController(
    private val organizations: OrganizationService,
    private val orgViews: OrganizationViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val support: AuditViewSupport,
    private val exporter: AuditExporter,
    private val recorder: AuditRecorder
) {

    @GetMapping("/organizations/{organizationName}/audit")
    @Transactional(readOnly = true)
    fun list(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val organization = organizations.findByName(organizationName) ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        if (!accessControl.isAllowed(currentUserProvider.get().user, organization.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", orgViews.of(organization))
        return ModelAndView("organization/audit").addObject("title", organization.name).addObject("orgView", orgViews.of(organization))
            .addAllObjects(support.model(request, UrlPaths.organizationPath(organization.name) + "/audit", AuditScope.Organization(requireNotNull(organization.id))))
    }

    @GetMapping("/organizations/{organizationName}/audit/export")
    fun export(@PathVariable organizationName: String, request: HttpServletRequest, response: HttpServletResponse): Any? {
        val organization = organizations.findByName(organizationName) ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        if (!accessControl.isAllowed(currentUserProvider.get().user, organization.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden")
        val scope = AuditScope.Organization(requireNotNull(organization.id))
        return if (writeAuditExport(request, response, support, exporter, recorder, scope, "organization")) null else ErrorViews.badRequest()
    }
}

/** 사용자 본인의 보안 기록(로그인, 토큰, 계정) 화면이다. 설정 탭에 있다. */
@Controller
class UserAuditController(private val currentUserProvider: CurrentUserProvider, private val support: AuditViewSupport) {

    @GetMapping("/user/editform/audit")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(request: HttpServletRequest): ModelAndView {
        val user = requireNotNull(currentUserProvider.get().user)
        return ModelAndView("user/edit-audit").addObject("title", user.loginId).addObject("tabId", "audit")
            .addAllObjects(support.model(request, "/user/editform/audit", AuditScope.User(requireNotNull(user.id), user.loginId)))
    }
}

/** 사이트 감사 화면의 외부 스트리밍 상태 표 한 줄이다. */
data class AuditStreamRow(val name: String, val lastId: Long, val backlog: Long, val lastSent: String, val error: String, val failures: Int)
