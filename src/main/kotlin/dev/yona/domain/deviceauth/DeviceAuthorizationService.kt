package dev.yona.domain.deviceauth

import dev.yona.access.AccessControl
import dev.yona.config.YonaProperties
import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Date

/** 기기 코드 발급 결과이다. 필드 이름은 RFC 8628의 응답과 같게 REST 계층에서 옮긴다. */
data class DeviceCode(val deviceCode: String, val userCode: String, val expiresIn: Int, val interval: Int)

/** 코드 요청의 결과이다. */
sealed interface DeviceStart {
    data class Issued(val code: DeviceCode) : DeviceStart

    /** 요청한 범위에 모르는 것이 있다. */
    data object InvalidScope : DeviceStart
}

/** 토큰 교환(폴링)의 결과이다. [DeviceAuthorizationService.exchange]. */
sealed interface DevicePoll {
    /** 사용자가 아직 승인하지 않았다. */
    data object Pending : DevicePoll

    /** 최소 간격보다 빨리 불렀다. 간격이 5초 늘어났다. */
    data class SlowDown(val interval: Int) : DevicePoll

    /** 사용자가 거절했다. */
    data object AccessDenied : DevicePoll

    /** 만료되었다. */
    data object Expired : DevicePoll

    /** 없거나 이미 쓴 코드이다. */
    data object Invalid : DevicePoll

    /** 토큰을 만들지 못했다(사용자당 개수 한도 등). [messageKey]는 사유의 메시지 키이다. */
    data class Rejected(val messageKey: String) : DevicePoll

    /** 승인되어 토큰을 만들었다. [token]은 원문이며 이 응답이 마지막으로 보인다. */
    data class Granted(val token: String, val scopes: Set<String>) : DevicePoll
}

/**
 * 기기 인증(Device Flow, RFC 8628) 서비스이다(추가 개발 항목 X-37i, `decisions.md` D-52, A-281). GitHub의 `gh auth login`처럼 CLI가 [start]로 코드를 받고, 사용자가 웹에서
 * [approve]하거나 [deny]하며, CLI가 [exchange]를 되풀이해 개인용 API 토큰을 받는다. 토큰은 [ApiTokenService.create]로 만들므로 사용자가 설정 화면에서 만든 토큰과 같은 규칙
 * (해시 저장, 범위, 목록과 폐기)을 따른다.
 */
@Service
class DeviceAuthorizationService(
    private val devices: DeviceAuthorizationRepository,
    private val apiTokens: ApiTokenService,
    private val tokenRepository: ApiTokenRepository,
    private val accessControl: AccessControl,
    private val properties: YonaProperties
) {

    private val random = SecureRandom()

    companion object {
        /** 교환 요청의 `grant_type` 값이다(RFC 8628 3.4). */
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

        /** 기기 코드와 사용자 코드의 유효 시간(초)이다. */
        const val EXPIRES_IN = 900

        /** 교환 요청의 최소 간격(초)이다. */
        const val INTERVAL = 5

        /** 토큰 이름의 머리말이다. 같은 기기에서 다시 로그인하면 같은 이름의 이전 토큰을 바꾼다. */
        const val TOKEN_NAME_PREFIX = "Yona CLI: "

        private const val DEFAULT_CLIENT_NAME = "yona-cli"
        private const val DEVICE_CODE_LENGTH = 40
        private const val DEVICE_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

        /** 사용자 코드에 쓰는 글자이다. 모음과 헷갈리는 글자를 빼서 단어로 읽히거나 잘못 읽히지 않게 한다(GitHub와 같다). */
        private const val USER_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ"
        private const val USER_CODE_GROUP = 4
        private const val SLOW_DOWN_STEP = 5
        private const val EXPIRED_KEEP_MILLIS = 86_400_000L
    }

    /**
     * 기기 코드와 사용자 코드를 발급한다. [scopes]가 비어 있으면 사이트 관리자 범위를 뺀 모든 일반 범위를 요청한 것으로 본다(CLI가 개인이 쓸 수 있는 기능을 모두 쓰게 한다, D-52).
     * 모르는 범위가 있으면 [DeviceStart.InvalidScope]이다. 만료된 지 하루 넘은 요청은 이때 함께 지운다.
     */
    @Transactional
    fun start(clientName: String?, scopes: Collection<String>, now: Date = Date()): DeviceStart {
        val known = ApiScopes.selectable(true).toSet()
        if (scopes.any { it !in known }) return DeviceStart.InvalidScope
        val requested = scopes.toSet().ifEmpty { ApiScopes.selectable(false).toSet() }
        devices.deleteExpiredBefore(Date(now.time - EXPIRED_KEEP_MILLIS))
        val deviceCode = randomString(DEVICE_ALPHABET, DEVICE_CODE_LENGTH)
        val userCode = generateUserCode()
        devices.save(
            DeviceAuthorization(
                deviceCodeHash = TokenHasher.sha256Hex(deviceCode), userCode = userCode, clientName = clientName?.trim()?.take(ApiTokenService.NAME_MAX - TOKEN_NAME_PREFIX.length)?.ifEmpty { null } ?: DEFAULT_CLIENT_NAME,
                scopes = ApiScopes.format(requested), createdDate = now, expiresDate = Date(now.time + EXPIRES_IN * 1000L), intervalSeconds = INTERVAL
            )
        )
        return DeviceStart.Issued(DeviceCode(deviceCode, userCode, EXPIRES_IN, INTERVAL))
    }

    /**
     * 사용자 코드로 요청을 찾는다. 하이픈과 대소문자와 공백은 가리지 않는다. 없거나 만료되었거나 이미 승인, 거절한 요청이면 `null`이다.
     */
    @Transactional(readOnly = true)
    fun findByUserCode(raw: String?, now: Date = Date()): DeviceAuthorization? {
        val letters = raw.orEmpty().filter { it.isLetter() }.uppercase()
        if (letters.length != USER_CODE_GROUP * 2) return null
        val found = devices.findByUserCode(letters.substring(0, USER_CODE_GROUP) + "-" + letters.substring(USER_CODE_GROUP)) ?: return null
        return found.takeIf { it.expiresDate?.after(now) == true && it.status == DeviceAuthorizationStatus.PENDING.name }
    }

    /** [auth]가 [user]의 승인으로 받게 될 범위이다. 요청한 범위에서 [user]가 가질 수 없는 것(사이트 관리자 범위 등)을 뺀다. */
    fun grantableScopes(auth: DeviceAuthorization, user: User): Set<String> =
        ApiScopes.parse(auth.scopes).filter { it in ApiScopes.selectable(accessControl.isSiteManager(user)) }.toSet()

    /** [user]가 승인한다. 이후 교환 요청이 토큰을 만든다. */
    @Transactional
    fun approve(auth: DeviceAuthorization, user: User) {
        auth.user = user
        auth.scopes = ApiScopes.format(grantableScopes(auth, user))
        auth.status = DeviceAuthorizationStatus.APPROVED.name
        devices.save(auth)
    }

    /** 사용자가 거절한다. 다음 교환 요청이 `access_denied`를 받고 요청은 지워진다. */
    @Transactional
    fun deny(auth: DeviceAuthorization) {
        auth.status = DeviceAuthorizationStatus.DENIED.name
        devices.save(auth)
    }

    /**
     * 기기 코드로 토큰을 교환한다(RFC 8628 3.4, 3.5). 승인과 거절은 간격과 상관없이 바로 알리고, 승인 전의 교환 요청만 간격을 검사한다: 간격보다 빨리 부르면 간격을 5초 늘리고
     * [DevicePoll.SlowDown]이다. 토큰을 내주거나 거절, 만료를 알리면 요청은 지워진다. 같은 기기 이름으로 이미 받은 토큰이 있으면 바꾼다(다시 로그인).
     */
    @Transactional
    fun exchange(deviceCode: String, now: Date = Date()): DevicePoll {
        val auth = devices.findByDeviceCodeHash(TokenHasher.sha256Hex(deviceCode)) ?: return DevicePoll.Invalid
        if (auth.expiresDate?.after(now) != true) return DevicePoll.Expired.also { devices.delete(auth) }
        return when (auth.status) {
            DeviceAuthorizationStatus.DENIED.name -> DevicePoll.AccessDenied.also { devices.delete(auth) }
            DeviceAuthorizationStatus.APPROVED.name -> grant(auth, now)
            else -> {
                val last = auth.lastPolledDate
                auth.lastPolledDate = now
                if (last != null && now.time - last.time < auth.intervalSeconds * 1000L) {
                    auth.intervalSeconds += SLOW_DOWN_STEP
                    devices.save(auth)
                    DevicePoll.SlowDown(auth.intervalSeconds)
                } else {
                    devices.save(auth)
                    DevicePoll.Pending
                }
            }
        }
    }

    private fun grant(auth: DeviceAuthorization, now: Date): DevicePoll {
        val user = requireNotNull(auth.user)
        val name = TOKEN_NAME_PREFIX + auth.clientName
        val scopes = ApiScopes.parse(auth.scopes)
        tokenRepository.findByUserIdOrderByCreatedDateDescIdDesc(requireNotNull(user.id)).filter { it.name == name }.forEach { apiTokens.delete(user, requireNotNull(it.id)) }
        val maxDays = properties.apiTokenMaxDays
        val expires = if (maxDays > 0) Date(now.time + maxDays * 86_400_000L) else null
        return when (val created = apiTokens.create(user, name, scopes, expires, accessControl.isSiteManager(user), maxDays, now)) {
            is TokenCreation.Created -> DevicePoll.Granted(created.value, scopes).also { devices.delete(auth) }
            is TokenCreation.Rejected -> DevicePoll.Rejected(created.messageKey)
        }
    }

    /** 이미 쓰는 사용자 코드와 겹치지 않는 `BCDF-GHJK` 모양의 코드를 만든다. */
    private fun generateUserCode(): String {
        while (true) {
            val code = randomString(USER_ALPHABET, USER_CODE_GROUP) + "-" + randomString(USER_ALPHABET, USER_CODE_GROUP)
            if (devices.findByUserCode(code) == null) return code
        }
    }

    private fun randomString(alphabet: String, length: Int): String = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
}
