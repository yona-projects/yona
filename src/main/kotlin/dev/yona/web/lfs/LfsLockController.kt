package dev.yona.web.lfs

import dev.yona.domain.lfs.LfsLock
import dev.yona.domain.lfs.LfsLockService
import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Git LFS 파일 잠금 API이다(추가 개발 항목 X-8 단계 3, `decisions.md` D-79 2번). 규격의 `POST /locks`(201, 충돌은 409와 기존 잠금), `GET /locks`(경로와 번호로 거르기,
 * `cursor`와 `limit`), `POST /locks/verify`(`ours`, `theirs`), `POST /locks/{id}/unlock`이다. 목록은 읽기 권한, 나머지는 쓰기 권한이 필요하고(batch와 같다)
 * `force` 풀기는 프로젝트 관리자만 한다. 경로 규칙은 [LfsController]와 같다(`프로젝트.git`과 `프로젝트` 둘 다 받는다).
 */
@Controller
class LfsLockController(private val support: LfsRequestSupport, private val locks: LfsLockService, private val users: UserRepository) {

    private val mapper = JsonMapper.builder().build()

    /** 파일을 잠근다. */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/locks", "/{owner}/{project}.git/info/lfs/locks"])
    @ResponseBody
    fun create(@PathVariable owner: String, @PathVariable project: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> = support.measured("lock_create") {
        val context = support.resolve(owner, project, request, response, write = true)
        if (context.failure != null) return@measured context.failure
        val node = body(request) ?: return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid lock request")
        val path = node.get("path")?.takeIf { it.isString }?.asString("") ?: return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "path is required")
        val ref = node.get("ref")?.get("name")?.asString("")?.takeIf { it.isNotEmpty() }
        when (val result = locks.create(requireNotNull(context.project), requireNotNull(context.identity.user), path, ref)) {
            is LfsLockService.Created -> support.lfsJson(HttpStatus.CREATED, mapOf("lock" to lockJson(result.lock)))
            is LfsLockService.Conflict -> support.lfsJson(HttpStatus.CONFLICT, mapOf("lock" to lockJson(result.existing), "message" to "already created lock"))
            is LfsLockService.Invalid -> support.error(HttpStatus.UNPROCESSABLE_ENTITY, result.message)
        }
    }

    /** 잠금 목록이다. 읽기 권한이면 된다. */
    @GetMapping(value = ["/{owner}/{project}/info/lfs/locks", "/{owner}/{project}.git/info/lfs/locks"])
    @ResponseBody
    fun list(
        @PathVariable owner: String, @PathVariable project: String, @RequestParam(required = false) path: String?, @RequestParam(required = false) id: String?,
        @RequestParam(required = false) cursor: String?, @RequestParam(required = false, defaultValue = "100") limit: Int, request: HttpServletRequest, response: HttpServletResponse
    ): ResponseEntity<*> = support.measured("lock_list") {
        val context = support.resolve(owner, project, request, response, write = false)
        if (context.failure != null) return@measured context.failure
        val page = locks.list(requireNotNull(context.project), path?.takeIf { it.isNotEmpty() }, id?.toLongOrNull(), cursor, limit)
        val body = linkedMapOf<String, Any?>("locks" to page.locks.map { lockJson(it) })
        page.nextCursor?.let { body["next_cursor"] = it }
        support.lfsJson(HttpStatus.OK, body)
    }

    /** 푸시 전에 클라이언트가 부른다. 호출한 사용자의 잠금(`ours`)과 남의 잠금(`theirs`)을 나눠 준다. 쓰기 권한이 필요하다. */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/locks/verify", "/{owner}/{project}.git/info/lfs/locks/verify"])
    @ResponseBody
    fun verify(@PathVariable owner: String, @PathVariable project: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> = support.measured("lock_verify") {
        val context = support.resolve(owner, project, request, response, write = true)
        if (context.failure != null) return@measured context.failure
        val node = body(request)
        val result = locks.verify(requireNotNull(context.project), requireNotNull(context.identity.user), node?.get("cursor")?.asString("")?.takeIf { it.isNotEmpty() }, node?.get("limit")?.takeIf { it.isNumber }?.asInt() ?: 100)
        val body = linkedMapOf<String, Any?>("ours" to result.ours.map { lockJson(it) }, "theirs" to result.theirs.map { lockJson(it) })
        result.nextCursor?.let { body["next_cursor"] = it }
        support.lfsJson(HttpStatus.OK, body)
    }

    /** 잠금을 푼다. 잠근 사람이 하거나, `force`이면 프로젝트 관리자가 한다. */
    @PostMapping(value = ["/{owner}/{project}/info/lfs/locks/{id}/unlock", "/{owner}/{project}.git/info/lfs/locks/{id}/unlock"])
    @ResponseBody
    fun unlock(@PathVariable owner: String, @PathVariable project: String, @PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("lock_unlock") {
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project); val user = requireNotNull(context.identity.user)
            val lockId = id.toLongOrNull() ?: return@measured support.error(HttpStatus.NOT_FOUND, "Lock does not exist")
            val force = body(request)?.get("force")?.asBoolean(false) == true
            when (val result = locks.unlock(target, user, lockId, force, support.isManager(user, target))) {
                is LfsLockService.Released -> support.lfsJson(HttpStatus.OK, mapOf("lock" to lockJson(result.lock)))
                LfsLockService.NotFound -> support.error(HttpStatus.NOT_FOUND, "Lock does not exist")
                LfsLockService.Forbidden -> support.error(HttpStatus.FORBIDDEN, "Only the owner of the lock or a project manager can release it")
            }
        }

    private fun body(request: HttpServletRequest): JsonNode? = runCatching { mapper.readTree(request.inputStream) }.getOrNull()?.takeIf { it.isObject }

    /** 규격의 잠금 표현이다: `id`(문자열), `path`, `locked_at`(RFC 3339), `owner.name`. */
    private fun lockJson(lock: LfsLock): Map<String, Any?> = linkedMapOf(
        "id" to lock.id.toString(), "path" to lock.path,
        "locked_at" to lock.lockedDate?.toInstant()?.atOffset(ZoneOffset.UTC)?.truncatedTo(ChronoUnit.SECONDS)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "owner" to mapOf("name" to lock.ownerUserId?.let { users.findById(it).orElse(null)?.loginId })
    )
}
