package com.github.yonaprojects.yona.domain.device

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.security.MessageDigest
import java.util.Base64

class DeviceRecognitionServiceSpec : DescribeSpec({
    val knownDeviceRepository = mockk<UserKnownDeviceRepository>()
    val mailService = mockk<MailService>(relaxed = true)
    val service = DeviceRecognitionService(knownDeviceRepository, mailService, "테스트사이트")

    val user = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")

    fun hashOf(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(rawToken.toByteArray(Charsets.UTF_8)))
    }

    fun deviceCookieFrom(response: MockHttpServletResponse): String {
        val setCookieHeader = response.getHeader("Set-Cookie") ?: error("Set-Cookie 헤더가 없습니다.")
        return setCookieHeader.substringAfter("${DeviceRecognitionService.COOKIE_NAME}=").substringBefore(";")
    }

    beforeTest {
        clearMocks(mailService, answers = false)
        every { knownDeviceRepository.save(any()) } answers { firstArg() }
    }

    describe("recognizeLogin") {
        it("쿠키가 없는 첫 로그인이면 새 기기로 등록하고 알림 메일을 보내며 응답에 쿠키를 심는다") {
            every { knownDeviceRepository.findByUserIdAndDeviceTokenHash(any(), any()) } returns null
            val request = MockHttpServletRequest()
            request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0")
            val response = MockHttpServletResponse()

            service.recognizeLogin(user, request, response)

            verify(exactly = 1) { mailService.sendHtmlMail("gildong@example.com", "홍길동", any(), any()) }
            val saved = slot<UserKnownDevice>()
            verify(exactly = 1) { knownDeviceRepository.save(capture(saved)) }
            saved.captured.label shouldBe "Chrome on Windows"
            deviceCookieFrom(response) shouldNotBe ""
        }

        it("이미 알고 있는 기기 쿠키로 재로그인하면 조용히 최근 사용 시각만 갱신하고 알림을 보내지 않는다") {
            val rawToken = "existing-device-token"
            val known = UserKnownDevice(id = 5L, user = user, deviceTokenHash = hashOf(rawToken), label = "Chrome on Windows")
            every { knownDeviceRepository.findByUserIdAndDeviceTokenHash(1L, hashOf(rawToken)) } returns known
            val request = MockHttpServletRequest()
            request.setCookies(Cookie(DeviceRecognitionService.COOKIE_NAME, rawToken))
            val response = MockHttpServletResponse()

            service.recognizeLogin(user, request, response)

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
            verify(exactly = 1) { knownDeviceRepository.save(known) }
            response.getHeader("Set-Cookie") shouldBe null
        }

        it("다른 계정이 등록한 기기 토큰은 이 계정에 알려진 기기로 취급하지 않는다(계정별로 조회가 분리됨)") {
            val otherUsersToken = "other-users-device-token"
            every { knownDeviceRepository.findByUserIdAndDeviceTokenHash(1L, hashOf(otherUsersToken)) } returns null
            val request = MockHttpServletRequest()
            request.setCookies(Cookie(DeviceRecognitionService.COOKIE_NAME, otherUsersToken))
            val response = MockHttpServletResponse()

            service.recognizeLogin(user, request, response)

            verify(exactly = 1) { knownDeviceRepository.findByUserIdAndDeviceTokenHash(1L, hashOf(otherUsersToken)) }
            verify(exactly = 1) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }
    }
})
