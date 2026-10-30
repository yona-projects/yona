package dev.yona.web.api

import dev.yona.domain.deviceauth.DeviceAuthorizationService
import dev.yona.domain.deviceauth.DevicePoll
import dev.yona.domain.deviceauth.DeviceStart
import dev.yona.util.SiteUrls
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 기기 인증(Device Flow, RFC 8628) REST이다(로드맵 X-37i, `decisions.md` D-52, A-281). CLI가 토큰 없이 부르는 두 경로이다: [deviceCode]가 코드를 받고, [token]이 사용자가 웹
 * (`/users/device`)에서 승인할 때까지 기다리며 토큰을 받는다. 매개변수는 폼(`application/x-www-form-urlencoded`)이나 쿼리로 받고, 오류는 RFC의 `error` 값을 400에 담아 보낸다.
 */
@Controller
@ResponseBody
class ExternalDeviceAuthApiController(
    private val service: DeviceAuthorizationService,
    private val siteUrls: SiteUrls
) {

    /**
     * 기기 코드와 사용자 코드를 발급한다. 매개변수: `client_name`(기기 이름, 선택), `scope`(공백이나 쉼표로 나눈 범위, 비우면 사이트 관리자 범위를 뺀 모든 일반 범위).
     * 결과: `{"device_code", "user_code", "verification_uri", "verification_uri_complete", "expires_in", "interval"}`. 모르는 범위는 `invalid_scope`이다.
     */
    @ApiOperation(operationId = "requestDeviceCode", tags = [ApiTags.AUTH], summary = "기기 코드 요청", description = "CLI가 로그인을 시작한다. 토큰 없이 부른다. 기기 코드(CLI만 보관)와 사용자 코드(웹에서 입력)를 받고, 사용자가 `verification_uri`에서 로그인해 코드를 승인하는 동안 `POST /oauth/token`으로 기다린다. 코드는 15분 동안 유효하다. 승인한 사용자가 가질 수 있는 범위로 줄어든 토큰이 나간다.")
    @BodyDoc(description = "폼 매개변수. 쿼리로도 받는다", required = false, content = [Content(mediaType = "application/x-www-form-urlencoded", schema = Schema(implementation = DeviceCodeForm::class))])
    @ApiResponse(responseCode = "200", description = "기기 코드", content = [Content(schema = Schema(implementation = DeviceCodeResponse::class))])
    @ApiResponse(responseCode = "400", description = "모르는 범위이다(`invalid_scope`).", content = [Content(schema = Schema(implementation = DeviceAuthError::class))])
    @PostMapping("/-_-api/v1/oauth/device/code")
    fun deviceCode(request: HttpServletRequest): Any {
        val scopes = request.getParameter("scope").orEmpty().split(' ', ',', '\t').filter { it.isNotBlank() }
        return when (val started = service.start(request.getParameter("client_name"), scopes)) {
            DeviceStart.InvalidScope -> error("invalid_scope", "unknown scope")
            is DeviceStart.Issued -> {
                val uri = siteUrls.absolute(siteUrls.path("/users/device"))
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                    linkedMapOf(
                        "device_code" to started.code.deviceCode, "user_code" to started.code.userCode, "verification_uri" to uri,
                        "verification_uri_complete" to "$uri?user_code=${started.code.userCode}", "expires_in" to started.code.expiresIn, "interval" to started.code.interval
                    )
                )
            }
        }
    }

    /**
     * 기기 코드로 토큰을 교환한다. 매개변수: `grant_type`(`urn:ietf:params:oauth:grant-type:device_code`), `device_code`. 승인되었으면 200과
     * `{"access_token", "token_type": "bearer", "scope": "범위를 공백으로 이은 값"}`이다. 아니면 400과 `error`: `authorization_pending`(기다린다), `slow_down`(간격을 늘린다),
     * `access_denied`(거절했거나 토큰을 만들 수 없다), `expired_token`, `invalid_grant`(없거나 이미 쓴 코드), `unsupported_grant_type`, `invalid_request`이다.
     */
    @ApiOperation(operationId = "exchangeDeviceCode", tags = [ApiTags.AUTH], summary = "토큰 교환", description = "기기 코드로 개인용 API 토큰을 받는다. 토큰 없이 부른다. 사용자가 승인하기 전에는 400과 `authorization_pending`이므로 `interval`초 간격으로 되풀이해 부른다. 승인되면 토큰을 한 번만 주고 코드는 지워진다. 같은 기기 이름으로 이미 받은 토큰이 있으면 새 토큰으로 바뀐다(토큰 설정 화면에서 폐기할 수 있다).")
    @BodyDoc(description = "폼 매개변수. 쿼리로도 받는다", required = true, content = [Content(mediaType = "application/x-www-form-urlencoded", schema = Schema(implementation = DeviceTokenForm::class))])
    @ApiResponse(responseCode = "200", description = "개인용 API 토큰", content = [Content(schema = Schema(implementation = DeviceTokenResponse::class))])
    @ApiResponse(responseCode = "400", description = "아직 승인하지 않았거나 거절, 만료, 틀린 요청이다. 본문의 `error`로 구분한다.", content = [Content(schema = Schema(implementation = DeviceAuthError::class))])
    @PostMapping("/-_-api/v1/oauth/token")
    fun token(request: HttpServletRequest): Any {
        val grantType = request.getParameter("grant_type")?.takeIf { it.isNotEmpty() } ?: return error("invalid_request", "grant_type is required")
        if (grantType != DeviceAuthorizationService.GRANT_TYPE) return error("unsupported_grant_type", "only ${DeviceAuthorizationService.GRANT_TYPE} is supported")
        val code = request.getParameter("device_code")?.takeIf { it.isNotEmpty() } ?: return error("invalid_request", "device_code is required")
        return when (val result = service.exchange(code)) {
            DevicePoll.Pending -> error("authorization_pending", "the user has not approved yet")
            is DevicePoll.SlowDown -> error("slow_down", "polling too fast; interval is now ${result.interval} seconds")
            DevicePoll.AccessDenied -> error("access_denied", "the user denied the request")
            DevicePoll.Expired -> error("expired_token", "the device code has expired")
            DevicePoll.Invalid -> error("invalid_grant", "unknown or already used device code")
            is DevicePoll.Rejected -> error("access_denied", "could not issue a token: ${result.messageKey}")
            is DevicePoll.Granted -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store")
                .body(linkedMapOf("access_token" to result.token, "token_type" to "bearer", "scope" to result.scopes.joinToString(" ")))
        }
    }

    private fun error(code: String, description: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").body(linkedMapOf("error" to code, "error_description" to description))
}
