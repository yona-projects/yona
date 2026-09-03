package com.github.yonaprojects.yona.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.AuthenticationException

class YonaAuthenticationFailureHandlerSpec : DescribeSpec({
    val handler = YonaAuthenticationFailureHandler()
    val exception = mockk<AuthenticationException>()

    describe("YonaAuthenticationFailureHandler") {
        it("isAjax with X-Requested-With should return JSON with 403") {
            val request = MockHttpServletRequest()
            request.addHeader("X-Requested-With", "XMLHttpRequest")
            val response = MockHttpServletResponse()

            handler.onAuthenticationFailure(request, response, exception)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            response.contentType shouldBe "application/json;charset=UTF-8"
            response.contentAsString shouldBe "{\"message\":\"user.login.invalid\"}"
        }

        it("isAjax with Accept application/json should return JSON with 403") {
            val request = MockHttpServletRequest()
            request.addHeader("Accept", "application/json")
            val response = MockHttpServletResponse()

            handler.onAuthenticationFailure(request, response, exception)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            response.contentType shouldBe "application/json;charset=UTF-8"
            response.contentAsString shouldBe "{\"message\":\"user.login.invalid\"}"
        }

        it("not Ajax should redirect to /users/loginform?error=true") {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()

            handler.onAuthenticationFailure(request, response, exception)

            response.redirectedUrl shouldBe "/users/loginform?error=true"
        }

        it("not Ajax with Accept text/html should redirect to /users/loginform?error=true") {
            val request = MockHttpServletRequest()
            request.addHeader("Accept", "text/html")
            val response = MockHttpServletResponse()

            handler.onAuthenticationFailure(request, response, exception)

            response.redirectedUrl shouldBe "/users/loginform?error=true"
        }
    }
})
