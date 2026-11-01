package dev.yona.web.api

import dev.yona.domain.site.DataService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.multipart.MultipartFile
import java.text.SimpleDateFormat
import java.util.Date
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 사이트 백업 REST이다(로드맵 X-37g, `decisions.md` D-52). 웹 `SiteDataController`의 내려받기와 올리기를 토큰으로 쓰게 한 것으로, 같은 [DataService]를 쓴다. 사이트 관리자이면서
 * `site_admin` 범위가 있는 토큰만 쓸 수 있다(D-36). 웹과 다른 점은 올리기의 응답뿐이다: 웹은 처음 화면으로 이동시키지만, 이 경로는 가져온 테이블과 실패한 테이블을 JSON으로 알려 준다.
 */
@Controller
@AnonymousCheck
@SiteManagerOnly
@ResponseBody
class ExternalSiteBackupApiController(
    private val data: DataService,
    private val requests: ApiRequests,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * DB 전체를 `yobi-data-<yyyyMMddHHmm>.json` 파일로 내려준다. 웹 내려받기와 같은 내용이다. 웹은 비동기 스트림을 쓰지만 토큰 인증은 비동기 재전달에서 인증 정보를 잃어
     * 뒤늦게 403 화면이 이어 붙으므로, 여기서는 요청 스레드에서 바로 응답에 쓴다. 쓰는 동안의 오류는 로그만 남아 파일이 잘릴 수 있다.
     */
    @ApiOperation(operationId = "exportSiteData", tags = [ApiTags.USER], summary = "사이트 데이터 내려받기(사이트 관리자)", description = "DB 전체를 `yobi-data-<yyyyMMddHHmm>.json` 파일로 내려준다. 웹 `/sites/export`와 같은 내용이다. Git 저장소 디렉터리와 첨부 파일 본문은 들어 있지 않다. 사이트 관리자이면서 `site_admin` 범위가 있는 토큰만 쓸 수 있다.")
    @ApiResponse(responseCode = "200", description = "JSON 파일(`application/x-download`)", content = [Content(mediaType = "application/x-download", schema = Schema(type = "string", format = "binary"))])
    @GetMapping("/-_-api/v1/admin/export")
    fun export(response: HttpServletResponse) {
        val filename = "yobi-data-" + SimpleDateFormat("yyyyMMddHHmm").format(Date()) + ".json"
        audit.record(dev.yona.domain.audit.AuditEvent("site.data_exported", targetType = "site", details = mapOf("via" to "api")))
        response.status = HttpStatus.OK.value()
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-download")
        response.setHeader("Content-disposition", "attachment; filename=$filename")
        try {
            data.export(response.outputStream)
            response.outputStream.flush()
        } catch (e: Exception) {
            log.error("Failed to export data", e)
        }
    }

    /**
     * `data` 파트로 올린 파일로 DB를 교체한다. 결과는 `{"imported": [테이블], "failed": [되돌린 테이블]}`이다. 파트가 없거나 첫 토큰부터 JSON이 아니면 400이다.
     * 기존 데이터를 교체하므로 [DataService.import]의 설명을 본다.
     */
    @ApiOperation(operationId = "importSiteData", tags = [ApiTags.USER], summary = "사이트 데이터 올리기(사이트 관리자)", description = "`data` 파트(multipart)로 올린 파일로 DB를 교체한다. 기존 데이터가 지워지므로 되돌릴 수 없다. 테이블마다 따로 확정하거나 되돌리며, 결과를 가져온 테이블과 되돌린 테이블로 알려 준다. 웹 `/sites/import`와 같은 서비스이다.")
    @ApiResponse(responseCode = "200", description = "가져오기 결과", content = [Content(schema = Schema(implementation = SiteImportResult::class))])
    @ApiResponse(responseCode = "400", description = "`data` 파트가 없거나 첫 토큰부터 JSON이 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/admin/import")
    fun import(@RequestParam(name = "data", required = false) file: MultipartFile?): Any {
        if (file == null) return requests.message(HttpStatus.BAD_REQUEST, "data file is required")
        return try {
            val result = file.inputStream.use { data.import(it) }
            audit.record(dev.yona.domain.audit.AuditEvent("site.data_imported", targetType = "site", details = mapOf("via" to "api", "imported" to result.imported, "failed" to result.failed)))
            ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(linkedMapOf("imported" to result.imported, "failed" to result.failed))
        } catch (e: Exception) {
            log.error("Failed to import data", e)
            requests.message(HttpStatus.BAD_REQUEST, "data file is not valid json")
        }
    }
}
