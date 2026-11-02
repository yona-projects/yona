package dev.yona.domain.twofactor

import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 알려진 기기이다(추가 개발 항목 X-2, `decisions.md` D-69). 2단계 인증을 마친 사용자가 "이 기기에서 30일 동안 묻지 않기"를 고르면 무작위 값을 쿠키로 주고, 서버에는 해시만 둔다.
 * 쿠키가 있고 그 사용자의 유효한 기기이면 로그인할 때 2단계를 건너뛴다. 사용자마다 [MAX_DEVICES]대까지이고, 넘으면 가장 오래 쓰지 않은 기기부터 지운다.
 */
@Service
class KnownDeviceService(private val devices: KnownDeviceRepository) {

    private val random = SecureRandom()

    /** 기기를 등록하고 쿠키에 담을 값을 돌려준다. */
    @Transactional
    fun issue(user: User, name: String, ip: String, now: Instant = Instant.now()): String {
        val userId = requireNotNull(user.id)
        val token = ByteArray(32).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        devices.save(KnownDevice(user = user, tokenHash = hash(token), name = name.take(100), ip = ip.take(64), createdDate = Date.from(now), lastUsedDate = Date.from(now), expiresDate = Date.from(now.plus(VALID))))
        val all = devices.findByUserIdOrderByLastUsedDateDescIdDesc(userId)
        all.drop(MAX_DEVICES).forEach { devices.delete(it) }
        devices.flush()
        return token
    }

    /** [token]이 [user]의 유효한 기기이면 `true`이고 마지막 사용 시각을 갱신한다. */
    @Transactional
    fun isKnown(user: User, token: String?, now: Instant = Instant.now()): Boolean {
        if (token.isNullOrBlank()) return false
        val device = devices.findByTokenHash(hash(token)) ?: return false
        if (device.user?.id != user.id || (device.expiresDate?.toInstant() ?: return false) <= now) return false
        device.lastUsedDate = Date.from(now)
        devices.save(device)
        return true
    }

    @Transactional(readOnly = true)
    fun list(user: User): List<KnownDevice> = devices.findByUserIdOrderByLastUsedDateDescIdDesc(requireNotNull(user.id))

    /** 사용자 본인의 기기를 지운다. 없거나 남의 것이면 `false`이다. */
    @Transactional
    fun remove(user: User, deviceId: Long): Boolean {
        val device = devices.findById(deviceId).orElse(null) ?: return false
        if (device.user?.id != user.id) return false
        devices.delete(device)
        devices.flush()
        return true
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_DEVICES = 20
        val VALID: Duration = Duration.ofDays(30)

        /** 기기 쿠키 이름이다. */
        const val COOKIE = "yona_device"
    }
}
