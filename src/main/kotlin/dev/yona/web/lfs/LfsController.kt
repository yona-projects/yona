package dev.yona.web.lfs

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.lfs.LfsObjectRequest
import dev.yona.domain.lfs.LfsPlan
import dev.yona.domain.lfs.LfsService
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.util.SiteUrls
import dev.yona.web.access.BasicAuthResolver
import dev.yona.web.access.BasicCredentials
import dev.yona.web.access.BasicIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Git LFS 서버의 batch와 객체 전송(basic) 경로이다(추가 개발 항목 X-8, `decisions.md` D-79). git-lfs 클라이언트는 저장소 주소가 `.git`으로 끝나지 않으면
 * `.git/info/lfs`를 붙여 찾으므로 `프로젝트.git`과 `프로젝트` 둘 다 같은 프로젝트로 받는다. 권한은 git HTTP와 같다: 인증은 [BasicAuthResolver](개인용 토큰만,
 * 비밀번호는 거부), 토큰 범위는 읽기 `repo`나 `read:repo`와 쓰기 `repo`, 프로젝트 권한은 코드 읽기와 푸시 권한이다. 공개 프로젝트는 익명도 내려받을 수 있다.
 * 응답의 미디어 타입은 `application/vnd.git-lfs+json`이고 오류는 `{"message": …}`이다.
 */
@Controller
class LfsController(
    private val support: LfsRequestSupport,
    private val lfs: LfsService,
    private val storage: LfsStorage,
    private val siteUrls: SiteUrls,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    private val mapper = JsonMapper.builder().build()

    /** 어떤 객체를 올리거나 내려받을지 묻고, 객체마다 할 일(동작)을 돌려준다. */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/objects/batch", "/{owner}/{project}.git/info/lfs/objects/batch"])
    @ResponseBody
    fun batch(@PathVariable owner: String, @PathVariable project: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("batch") {
            val context = support.resolve(owner, project, request, response, write = false)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            val body = parseBatch(request) ?: return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid batch request")
            if (body.transfers != null && "basic" !in body.transfers) return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Only the basic transfer is supported")
            if (body.operation == LfsService.UPLOAD) support.authorize(context.identity, target, write = true)?.let { return@measured it }
            val base = "${siteUrls.origin()}${siteUrls.path("/${target.owner}/${target.name}.git/info/lfs")}"
            // SSH의 git-lfs-authenticate가 준 토큰으로 온 요청이면 클라이언트가 객체 전송에는 그 헤더를 따로 붙이지 않으므로 동작마다 같은 헤더를 실어 준다
            val bearer = request.getHeader(HttpHeaders.AUTHORIZATION)?.takeIf { it.startsWith("Bearer ${dev.yona.domain.lfs.LfsTokens.PREFIX}") }
            val actionHeader = bearer?.let { mapOf(HttpHeaders.AUTHORIZATION to it) }.orEmpty()
            val objects = lfs.plan(target, body.operation, body.objects).map { plan ->
                val item = linkedMapOf<String, Any?>("oid" to plan.request.oid, "size" to plan.request.size)
                when (plan) {
                    is LfsPlan.Download -> item["actions"] = mapOf("download" to withHeader(mapOf("href" to "$base/objects/${plan.request.oid}"), actionHeader))
                    is LfsPlan.Upload -> item["actions"] = mapOf(
                        "upload" to mapOf("href" to "$base/objects/${plan.request.oid}", "header" to (mapOf("Content-Type" to MediaType.APPLICATION_OCTET_STREAM_VALUE) + actionHeader)),
                        "verify" to withHeader(mapOf("href" to "$base/verify"), actionHeader)
                    )
                    is LfsPlan.Present -> {}
                    is LfsPlan.Failure -> item["error"] = mapOf("code" to plan.code, "message" to plan.message)
                }
                // `authenticated`를 주지 않는다: true이면 클라이언트가 동작 주소에 자격을 보내지 않는다(미리 서명한 주소용). 생략해야 같은 자격(Basic)을 객체 전송에도 쓴다
                item
            }
            support.lfsJson(HttpStatus.OK, linkedMapOf("transfer" to "basic", "objects" to objects, "hash_algo" to "sha256"))
        }

    /** 객체를 올린다. 내용의 SHA-256이 oid와 같아야 하고 한도를 넘으면 413이다. */
    @PutMapping(value = ["/{owner}/{project}/info/lfs/objects/{oid}", "/{owner}/{project}.git/info/lfs/objects/{oid}"])
    @ResponseBody
    fun upload(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("upload") {
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            if (!LfsStorage.validOid(oid)) return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid oid")
            val limit = lfs.maxUploadBytes(target)
            if (request.contentLengthLong > limit) return@measured support.error(HttpStatus.PAYLOAD_TOO_LARGE, "Object exceeds the maximum size")
            when (storage.store(target, oid, metrics.countedInput("git_lfs", "lfs", request.inputStream), limit)) {
                is LfsStorage.StoreResult.Stored -> ResponseEntity.ok().build<Void>()
                LfsStorage.StoreResult.HashMismatch -> support.error(HttpStatus.UNPROCESSABLE_ENTITY, "The content does not match the oid")
                LfsStorage.StoreResult.TooLarge -> support.error(HttpStatus.PAYLOAD_TOO_LARGE, "Object exceeds the maximum size")
            }
        }

    /** 객체를 내려받는다. `Range` 이어받기와 `HEAD`를 지원한다. */
    @GetMapping(value = ["/{owner}/{project}/info/lfs/objects/{oid}", "/{owner}/{project}.git/info/lfs/objects/{oid}"])
    @ResponseBody
    fun download(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("download") {
            val context = support.resolve(owner, project, request, response, write = false)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            if (!LfsStorage.validOid(oid)) return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid oid")
            val holder = lfs.locate(target, oid) ?: return@measured support.error(HttpStatus.NOT_FOUND, "Object does not exist")
            val file = storage.objectFile(holder, oid)
            val resource: Resource = FileSystemResource(file)
            ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.length()).body(resource)
        }

    /** 올린 객체가 서버에 있고 크기가 맞는지 확인한다(올린 뒤 클라이언트가 부른다). */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/verify", "/{owner}/{project}.git/info/lfs/verify"])
    @ResponseBody
    fun verify(@PathVariable owner: String, @PathVariable project: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("verify") {
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            val node = runCatching { mapper.readTree(request.inputStream) }.getOrNull()
            val oid = node?.get("oid")?.asString("").orEmpty(); val size = node?.get("size")?.takeIf { it.isNumber }?.asLong() ?: -1
            if (!LfsStorage.validOid(oid) || size < 0) return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid verify request")
            val stored = lfs.sizeOf(target, oid) ?: return@measured support.error(HttpStatus.NOT_FOUND, "Object does not exist")
            if (stored != size) support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Object size does not match") else ResponseEntity.ok().build<Void>()
        }

    /** [headers]가 있으면 동작에 `header`로 싣는다. */
    private fun withHeader(action: Map<String, Any?>, headers: Map<String, String>): Map<String, Any?> = if (headers.isEmpty()) action else action + ("header" to headers)

    private class BatchBody(val operation: String, val transfers: List<String>?, val objects: List<LfsObjectRequest>)

    private fun parseBatch(request: HttpServletRequest): BatchBody? {
        val node: JsonNode = runCatching { mapper.readTree(request.inputStream) }.getOrNull()?.takeIf { it.isObject } ?: return null
        val operation = node.get("operation")?.asString("")?.takeIf { it == LfsService.DOWNLOAD || it == LfsService.UPLOAD } ?: return null
        val objects = node.get("objects")?.takeIf { it.isArray }?.toList() ?: return null
        val transfers = node.get("transfers")?.takeIf { it.isArray }?.toList()?.map { it.asString("") }
        return BatchBody(operation, transfers, objects.map { LfsObjectRequest(it.get("oid")?.asString("").orEmpty(), it.get("size")?.takeIf { s -> s.isNumber }?.asLong() ?: -1) })
    }
}
