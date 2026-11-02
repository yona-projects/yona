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
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val basicAuth: BasicAuthResolver,
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
        measured("batch") {
            val context = resolve(owner, project, request, response, write = false)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            val body = parseBatch(request) ?: return@measured error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid batch request")
            if (body.transfers != null && "basic" !in body.transfers) return@measured error(HttpStatus.UNPROCESSABLE_ENTITY, "Only the basic transfer is supported")
            if (body.operation == LfsService.UPLOAD) authorize(context.identity, target, write = true, response)?.let { return@measured it }
            val base = "${siteUrls.origin()}${siteUrls.path("/${target.owner}/${target.name}.git/info/lfs")}"
            val objects = lfs.plan(target, body.operation, body.objects).map { plan ->
                val item = linkedMapOf<String, Any?>("oid" to plan.request.oid, "size" to plan.request.size)
                when (plan) {
                    is LfsPlan.Download -> item["actions"] = mapOf("download" to mapOf("href" to "$base/objects/${plan.request.oid}"))
                    is LfsPlan.Upload -> item["actions"] = mapOf(
                        "upload" to mapOf("href" to "$base/objects/${plan.request.oid}", "header" to mapOf("Content-Type" to MediaType.APPLICATION_OCTET_STREAM_VALUE)),
                        "verify" to mapOf("href" to "$base/verify")
                    )
                    is LfsPlan.Present -> {}
                    is LfsPlan.Failure -> item["error"] = mapOf("code" to plan.code, "message" to plan.message)
                }
                if (plan !is LfsPlan.Failure) item["authenticated"] = true
                item
            }
            lfsJson(HttpStatus.OK, linkedMapOf("transfer" to "basic", "objects" to objects, "hash_algo" to "sha256"))
        }

    /** 객체를 올린다. 내용의 SHA-256이 oid와 같아야 하고 한도를 넘으면 413이다. */
    @PutMapping(value = ["/{owner}/{project}/info/lfs/objects/{oid}", "/{owner}/{project}.git/info/lfs/objects/{oid}"])
    @ResponseBody
    fun upload(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        measured("upload") {
            val context = resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            if (!LfsStorage.validOid(oid)) return@measured error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid oid")
            val limit = lfs.maxUploadBytes(target)
            if (request.contentLengthLong > limit) return@measured error(HttpStatus.PAYLOAD_TOO_LARGE, "Object exceeds the maximum size")
            when (storage.store(target, oid, metrics.countedInput("git_lfs", "lfs", request.inputStream), limit)) {
                is LfsStorage.StoreResult.Stored -> ResponseEntity.ok().build<Void>()
                LfsStorage.StoreResult.HashMismatch -> error(HttpStatus.UNPROCESSABLE_ENTITY, "The content does not match the oid")
                LfsStorage.StoreResult.TooLarge -> error(HttpStatus.PAYLOAD_TOO_LARGE, "Object exceeds the maximum size")
            }
        }

    /** 객체를 내려받는다. `Range` 이어받기와 `HEAD`를 지원한다. */
    @GetMapping(value = ["/{owner}/{project}/info/lfs/objects/{oid}", "/{owner}/{project}.git/info/lfs/objects/{oid}"])
    @ResponseBody
    fun download(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        measured("download") {
            val context = resolve(owner, project, request, response, write = false)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            if (!LfsStorage.validOid(oid)) return@measured error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid oid")
            val file = storage.objectFile(target, oid).takeIf { it.isFile } ?: return@measured error(HttpStatus.NOT_FOUND, "Object does not exist")
            val resource: Resource = FileSystemResource(file)
            ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.length()).body(resource)
        }

    /** 올린 객체가 서버에 있고 크기가 맞는지 확인한다(올린 뒤 클라이언트가 부른다). */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/verify", "/{owner}/{project}.git/info/lfs/verify"])
    @ResponseBody
    fun verify(@PathVariable owner: String, @PathVariable project: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        measured("verify") {
            val context = resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            val node = runCatching { mapper.readTree(request.inputStream) }.getOrNull()
            val oid = node?.get("oid")?.asString("").orEmpty(); val size = node?.get("size")?.takeIf { it.isNumber }?.asLong() ?: -1
            if (!LfsStorage.validOid(oid) || size < 0) return@measured error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid verify request")
            val stored = storage.sizeOf(target, oid) ?: return@measured error(HttpStatus.NOT_FOUND, "Object does not exist")
            if (stored != size) error(HttpStatus.UNPROCESSABLE_ENTITY, "Object size does not match") else ResponseEntity.ok().build<Void>()
        }

    // --- 공통 ---

    private class BatchBody(val operation: String, val transfers: List<String>?, val objects: List<LfsObjectRequest>)

    private fun parseBatch(request: HttpServletRequest): BatchBody? {
        val node: JsonNode = runCatching { mapper.readTree(request.inputStream) }.getOrNull()?.takeIf { it.isObject } ?: return null
        val operation = node.get("operation")?.asString("")?.takeIf { it == LfsService.DOWNLOAD || it == LfsService.UPLOAD } ?: return null
        val objects = node.get("objects")?.takeIf { it.isArray }?.toList() ?: return null
        val transfers = node.get("transfers")?.takeIf { it.isArray }?.toList()?.map { it.asString("") }
        return BatchBody(operation, transfers, objects.map { LfsObjectRequest(it.get("oid")?.asString("").orEmpty(), it.get("size")?.takeIf { s -> s.isNumber }?.asLong() ?: -1) })
    }

    /** 요청이 가리키는 프로젝트와 신원을 확인한다. 실패하면 [Context.failure]에 응답이 있다. */
    private class Context(val project: Project?, val identity: BasicIdentity, val failure: ResponseEntity<*>?)

    private fun resolve(owner: String, rawProject: String, request: HttpServletRequest, response: HttpServletResponse, write: Boolean): Context {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return Context(null, BasicIdentity(null), error(HttpStatus.BAD_REQUEST, "Malformed credentials"))
        val identity = metrics.span("vcs.authenticate", "vcs" to "git_lfs") { basicAuth.identify(request, response, credentials) }
        if (identity.blocked) return Context(null, identity, error(HttpStatus.TOO_MANY_REQUESTS, "Too many failed authentication attempts"))
        val project = projectService.findByOwnerAndProjectName(owner, rawProject.removeSuffix(".git"))?.takeIf { lfs.available(it) }
            ?: return Context(null, identity, error(HttpStatus.NOT_FOUND, "Repository or object not found"))
        val failure = authorize(identity, project, write, response)
        return Context(project, identity, failure)
    }

    /** 권한이 없으면 응답을 돌려준다. 인증이 없으면 401과 인증 요청, 로그인했는데 권한이 없으면 403이다. */
    private fun authorize(identity: BasicIdentity, project: Project, write: Boolean, response: HttpServletResponse): ResponseEntity<*>? {
        if (!basicAuth.allowsRepository(identity, project, write)) return error(HttpStatus.FORBIDDEN, "The token scope does not allow repository access")
        var operation = if (write) Operation.UPDATE else Operation.READ
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, identity.user)) operation = Operation.UPDATE
        val allowed = accessControl.isAllowed(identity.user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation)
        if (allowed) return null
        if (identity.user == null) {
            return error(HttpStatus.UNAUTHORIZED, if (identity.passwordRefused) BasicIdentity.PASSWORD_REFUSED_MESSAGE else "Invalid username or password", "WWW-Authenticate" to "Basic realm=\"Yobi\"")
        }
        return error(HttpStatus.FORBIDDEN, "You do not have permission for this repository")
    }

    private fun lfsJson(status: HttpStatus, body: Any): ResponseEntity<Any> = ResponseEntity.status(status).contentType(LFS_JSON).body(body)

    private fun error(status: HttpStatus, message: String, vararg headers: Pair<String, String>): ResponseEntity<Any> =
        ResponseEntity.status(status).contentType(LFS_JSON).headers(HttpHeaders().also { h -> headers.forEach { h.add(it.first, it.second) } }).body(mapOf("message" to message))

    /** 요청의 시간과 결과를 지표로 남긴다(`vcs=git_lfs`). */
    private fun measured(service: String, block: () -> ResponseEntity<*>): ResponseEntity<*> {
        var out: ResponseEntity<*>? = null
        metrics.request("git_lfs", service, "lfs") { req ->
            out = block()
            req.result = dev.yona.observability.VcsMetrics.resultOf(out?.statusCode?.value() ?: 200)
        }
        return requireNotNull(out)
    }

    companion object {
        /** LFS 규격의 미디어 타입이다. */
        val LFS_JSON: MediaType = MediaType.parseMediaType("application/vnd.git-lfs+json")
    }
}
