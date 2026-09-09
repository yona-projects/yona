package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.test.util.ReflectionTestUtils

class YonaAuthenticationSuccessHandlerSpec : DescribeSpec({
    val requestCache = mockk<HttpSessionRequestCache>()
    val userRepository = mockk<UserRepository>()
    val twoFactorService = mockk<TwoFactorService>()
    val handler = YonaAuthenticationSuccessHandler(twoFactorService, userRepository).apply {
        ReflectionTestUtils.setField(this, "requestCache", requestCache)
    }
    // 2FA를 등록하지 않은 계정 — 기존과 동일하게 동작해야 한다(회귀 방지). authentication.principal이
    // YonaUserDetails가 아닌 경우도 커버하기 위해 순수 mock을 그대로 쓴다(loginId 조회는
    // authentication.name으로 폴백).
    val authentication = mockk<Authentication>()

    beforeTest {
        clearMocks(userRepository, twoFactorService, answers = false)
        every { authentication.name } returns "no-2fa-user"
        every { authentication.principal } returns "no-2fa-user"
        every { userRepository.findByLoginId("no-2fa-user") } returns java.util.Optional.empty()
    }

    afterTest {
        SecurityContextHolder.clearContext()
    }

    describe("YonaAuthenticationSuccessHandler") {
        describe("2FA 미등록 계정(회귀 방지 — 기존과 동일하게 동작해야 한다)") {
            it("isAjax with X-Requested-With should return JSON") {
                val request = MockHttpServletRequest()
                request.addHeader("X-Requested-With", "XMLHttpRequest")
                val response = MockHttpServletResponse()

                handler.onAuthenticationSuccess(request, response, authentication)

                response.status shouldBe HttpServletResponse.SC_OK
                response.contentType shouldBe "application/json;charset=UTF-8"
                response.contentAsString shouldBe "{}"
            }

            it("isAjax with Accept application/json should return JSON") {
                val request = MockHttpServletRequest()
                request.addHeader("Accept", "application/json, text/plain, */*")
                val response = MockHttpServletResponse()

                handler.onAuthenticationSuccess(request, response, authentication)

                response.status shouldBe HttpServletResponse.SC_OK
                response.contentType shouldBe "application/json;charset=UTF-8"
                response.contentAsString shouldBe "{}"
            }

            it("not Ajax with saved request should redirect to saved url") {
                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val savedRequest = mockk<SavedRequest>()
                every { savedRequest.redirectUrl } returns "/some-url"
                every { requestCache.getRequest(request, response) } returns savedRequest

                handler.onAuthenticationSuccess(request, response, authentication)

                response.redirectedUrl shouldBe "/some-url"
            }

            it("not Ajax with Accept text/html should redirect to saved url") {
                val request = MockHttpServletRequest()
                request.addHeader("Accept", "text/html")
                val response = MockHttpServletResponse()
                val savedRequest = mockk<SavedRequest>()
                every { savedRequest.redirectUrl } returns "/some-url"
                every { requestCache.getRequest(request, response) } returns savedRequest

                handler.onAuthenticationSuccess(request, response, authentication)

                response.redirectedUrl shouldBe "/some-url"
            }

            it("not Ajax without saved request should redirect to /") {
                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                every { requestCache.getRequest(request, response) } returns null

                handler.onAuthenticationSuccess(request, response, authentication)

                response.redirectedUrl shouldBe "/"
            }
        }

        describe("2FA 등록 계정") {
            it("완전한 인증을 세션에 심지 않고 2FA 검증 화면으로 리다이렉트해야 한다") {
                val user = User(id = 1L, loginId = "2fa-user", name = "2fa유저")
                val userDetails = YonaUserDetails(
                    id = 1L, loginId = "2fa-user", passwordVal = "hashed", passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
                val realAuthentication = UsernamePasswordAuthenticationToken(userDetails, "pw", userDetails.authorities)
                every { userRepository.findByLoginId("2fa-user") } returns java.util.Optional.of(user)
                every { twoFactorService.isTwoFactorEnabled(user) } returns true

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()

                handler.onAuthenticationSuccess(request, response, realAuthentication)

                response.redirectedUrl shouldBe "/users/login/2fa"
                val context = SecurityContextHolder.getContext().authentication
                (context is Pre2faAuthenticationToken) shouldBe true
                (context as Pre2faAuthenticationToken).originalAuthentication shouldBe realAuthentication
            }
        }
    }
})
