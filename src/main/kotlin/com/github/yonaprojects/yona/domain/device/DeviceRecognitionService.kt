package com.github.yonaprojects.yona.domain.device

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

// 폼 로그인이 완전한 로그인으로 확정되는 두 지점(2FA 미등록 계정은
// YonaAuthenticationSuccessHandler, 2FA 계정은 TwoFactorLoginController.finalizeLogin) 모두
// 이 서비스를 거친다. IP 기반 판별은 동적 IP/모바일 네트워크에서 매번 오탐하므로, GitHub 관례를
// 따라 장기 보관 쿠키로 기기를 식별한다. OAuth2/SAML2/LDAP/PAT 로그인 경로는 대상이 아니다.
@Service
class DeviceRecognitionService(
    private val knownDeviceRepository: UserKnownDeviceRepository,
    private val mailService: MailService,
    @Value("\${yona.site-name:Yona}") private val siteName: String
) {
    private val random = SecureRandom()

    @Transactional
    fun recognizeLogin(user: User, request: HttpServletRequest, response: HttpServletResponse) {
        val userId = user.id ?: return
        val cookieToken = request.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value
        val known = cookieToken?.let { knownDeviceRepository.findByUserIdAndDeviceTokenHash(userId, hash(it)) }

        if (known != null) {
            known.lastSeenAt = Instant.now()
            knownDeviceRepository.save(known)
            return
        }

        val label = summarizeUserAgent(request.getHeader("User-Agent"))
        val newToken = generateToken()
        knownDeviceRepository.save(
            UserKnownDevice(
                user = user,
                deviceTokenHash = hash(newToken),
                label = label,
                firstSeenAt = Instant.now(),
                lastSeenAt = Instant.now()
            )
        )
        setDeviceCookie(response, newToken)
        notifyNewDevice(user, label)
    }

    private fun setDeviceCookie(response: HttpServletResponse, token: String) {
        val cookie = ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofDays(365))
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    // 새 기기 로그인 알림 메일 발송 실패가 로그인 자체를 막아서는 안 된다 — 예외를 삼키고 로그만
    // 남긴다(TwoFactorServiceImpl.notifyTwoFactorDisabled와 동일한 패턴).
    private fun notifyNewDevice(user: User, label: String?) {
        if (user.email.isBlank()) return
        try {
            mailService.sendHtmlMail(
                user.email,
                user.name,
                "[$siteName] 새로운 기기에서 로그인했습니다",
                "계정 ${user.loginId}(으)로 처음 보는 기기에서 로그인했습니다.<br/>" +
                    "기기: ${label ?: "알 수 없는 기기"}<br/>" +
                    "본인이 직접 한 것이 아니라면 즉시 비밀번호를 변경하고 사이트 관리자에게 문의하세요."
            )
        } catch (e: Exception) {
            logger.warn("새 기기 로그인 알림 메일 발송 실패: loginId=${user.loginId}", e)
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(token.toByteArray(Charsets.UTF_8)))
    }

    private fun summarizeUserAgent(userAgent: String?): String? {
        if (userAgent.isNullOrBlank()) return null
        val browser = when {
            userAgent.contains("Edg/") -> "Edge"
            userAgent.contains("Chrome/") && !userAgent.contains("Chromium") -> "Chrome"
            userAgent.contains("Firefox/") -> "Firefox"
            userAgent.contains("Safari/") && !userAgent.contains("Chrome/") -> "Safari"
            else -> null
        }
        val os = when {
            userAgent.contains("Windows") -> "Windows"
            userAgent.contains("Mac OS X") -> "macOS"
            userAgent.contains("Android") -> "Android"
            userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
            userAgent.contains("Linux") -> "Linux"
            else -> null
        }
        return when {
            browser != null && os != null -> "$browser on $os"
            browser != null -> browser
            else -> userAgent.take(255)
        }
    }

    companion object {
        const val COOKIE_NAME = "yona_device"
        private val logger = LoggerFactory.getLogger(DeviceRecognitionService::class.java)
    }
}
