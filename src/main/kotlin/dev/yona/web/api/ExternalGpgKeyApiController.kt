package dev.yona.web.api

import dev.yona.domain.gpgkey.GpgKey
import dev.yona.domain.gpgkey.GpgKeyAddition
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * GPG 키 REST이다(추가 개발 항목 X-4, `decisions.md` D-65). GitHub의 `/user/gpg_keys`에 해당하며 내 키 목록, 한 건 조회, 등록, 삭제와 다른 사용자의 공개키 목록이 있다.
 * 읽기는 `read:gpg_key`, 등록은 `write:gpg_key`, 삭제는 `admin:gpg_key`이고 앞의 범위는 뒤의 범위가 포함한다. 다른 사용자의 공개키 목록은 `read:user`이다(키 이름은 보이지 않는다).
 * 수정은 없다(설정 화면과 같이 추가와 삭제뿐이다).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalGpgKeyApiController(
    private val keys: GpgKeyService,
    private val users: UserRepository,
    private val requests: ApiRequests,
    private val json: PullRequestJson
) {

    @ApiOperation(operationId = "listMyGpgKeys", tags = [ApiTags.USER], summary = "내 GPG 키 목록", description = "토큰 주인이 등록한 GPG 공개키를 최근에 등록한 순서로 돌려준다. 하위 키와 키의 이메일을 담는다. 필요한 범위는 `read:gpg_key`이다.")
    @ApiResponse(responseCode = "200", description = "키 목록", content = [Content(schema = Schema(implementation = GpgKeyList::class))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자란다.")
    @GetMapping("/-_-api/v1/user/gpg_keys")
    @Transactional(readOnly = true)
    fun list(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("keys" to keys.list(user).map { own(it) }))
    }

    @ApiOperation(operationId = "getMyGpgKey", tags = [ApiTags.USER], summary = "내 GPG 키 한 건", description = "토큰 주인의 GPG 공개키 한 개를 돌려준다. 없거나 다른 사용자의 키이거나 하위 키 번호이면 404이다. 필요한 범위는 `read:gpg_key`이다.")
    @ApiResponse(responseCode = "200", description = "키", content = [Content(schema = Schema(implementation = GpgKeyResult::class))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자란다.")
    @ApiResponse(responseCode = "404", description = "키가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/user/gpg_keys/{id}")
    @Transactional(readOnly = true)
    fun get(@PathVariable id: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val key = keys.find(user, id) ?: return requests.message(HttpStatus.NOT_FOUND, "no gpg key by request")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to own(key)))
    }

    @ApiOperation(operationId = "createGpgKey", tags = [ApiTags.USER], summary = "GPG 키 등록", description = "본문 `{\"name\", \"publicKey\"(필수)}`로 GPG 공개키를 등록한다. 비밀키, 키 여러 개, 사용자 ID가 없는 키, 서명할 수 없는 키, 만료되었거나 폐기된 키는 400이고 이미 등록된 키(지문이 같은 키)는 409이다. 사용자당 50개까지이다. 필요한 범위는 `write:gpg_key`이다.")
    @BodyDoc(description = "등록할 키", required = true, content = [Content(schema = Schema(implementation = GpgKeyCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "등록한 키", content = [Content(schema = Schema(implementation = GpgKeyResult::class))])
    @ApiResponse(responseCode = "400", description = "받을 수 없는 키이거나 본문이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자란다.")
    @ApiResponse(responseCode = "409", description = "이미 등록된 키이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/user/gpg_keys")
    @Transactional
    fun create(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val body = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val armored = body.get("publicKey")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "publicKey is required")
        val name = body.get("name")?.takeIf { !it.isNull }?.let { if (it.isString) it.asString("") else return requests.message(HttpStatus.BAD_REQUEST, "name must be a string") }
        return when (val result = keys.add(user, name, armored)) {
            is GpgKeyAddition.Added -> ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to own(result.key)))
            is GpgKeyAddition.Rejected ->
                if (result.code == "gpgKey.error.duplicate") requests.message(HttpStatus.CONFLICT, "the gpg key is already registered")
                else requests.message(HttpStatus.BAD_REQUEST, result.code.removePrefix("gpgKey.error.").replace('_', ' '))
        }
    }

    @ApiOperation(operationId = "deleteGpgKey", tags = [ApiTags.USER], summary = "GPG 키 삭제", description = "토큰 주인의 GPG 공개키를 지운다(하위 키도 함께). 이 키로 서명한 커밋은 더 이상 Verified로 표시되지 않는다. 없거나 다른 사용자의 키이면 404이다. 필요한 범위는 `admin:gpg_key`이다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자란다.")
    @ApiResponse(responseCode = "404", description = "키가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/user/gpg_keys/{id}")
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        if (!keys.delete(user, id)) return requests.message(HttpStatus.NOT_FOUND, "no gpg key by request")
        return ResponseEntity.noContent().build<Void>()
    }

    @ApiOperation(operationId = "listUserGpgKeys", tags = [ApiTags.USER], summary = "사용자의 GPG 공개키 목록", description = "다른 사용자가 등록한 GPG 공개키를 돌려준다. 서명 검증에 쓰는 공개 정보라 키 이름은 보이지 않는다. 없는 사용자이면 404이다. 필요한 범위는 `read:user`이다.")
    @ApiResponse(responseCode = "200", description = "키 목록", content = [Content(schema = Schema(implementation = PublicGpgKeyList::class))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자란다.")
    @ApiResponse(responseCode = "404", description = "사용자가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/users/{user}/gpg_keys")
    @Transactional(readOnly = true)
    fun listOf(@PathVariable user: String, request: HttpServletRequest): Any {
        requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val target = users.findByLoginId(user) ?: return requests.message(HttpStatus.NOT_FOUND, "no user by request")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("keys" to keys.list(target).map { public(it) }))
    }

    private fun own(key: GpgKey): Map<String, Any?> = linkedMapOf<String, Any?>("id" to key.id, "name" to key.name).also { it.putAll(common(key)) }

    private fun public(key: GpgKey): Map<String, Any?> = linkedMapOf<String, Any?>("id" to key.id).also { it.putAll(common(key)) }

    private fun common(key: GpgKey): Map<String, Any?> = linkedMapOf(
        "keyId" to key.keyId, "fingerprint" to key.fingerprint, "emails" to key.emails.orEmpty().split(',').filter { it.isNotBlank() }, "canSign" to (key.canSign == true),
        "createdAt" to json.iso(key.createdDate), "expiresAt" to json.iso(key.expiresDate),
        "subkeys" to keys.subkeys(key).map { linkedMapOf("keyId" to it.keyId, "fingerprint" to it.fingerprint, "canSign" to (it.canSign == true), "expiresAt" to json.iso(it.expiresDate)) }
    )
}
