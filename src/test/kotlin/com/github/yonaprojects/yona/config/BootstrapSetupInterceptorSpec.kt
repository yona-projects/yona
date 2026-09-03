package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class BootstrapSetupInterceptorSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val interceptor = BootstrapSetupInterceptor(userRepository)

    describe("BootstrapSetupInterceptor") {
        it("should return true for excluded URIs") {
            val excludedUris = listOf(
                "/bootstrap-setup",
                "/css/style.css",
                "/js/app.js",
                "/images/logo.png",
                "/bootstrap/css/bootstrap.css",
                "/stylesheets/main.css",
                "/javascripts/main.js",
                "/webjars/jquery.js",
                "/error",
                "/favicon.ico"
            )

            excludedUris.forEach { uri ->
                val request = MockHttpServletRequest("GET", uri)
                val response = MockHttpServletResponse()

                interceptor.preHandle(request, response, Any()) shouldBe true
            }
        }

        it("should redirect to /bootstrap-setup and return false if user count is 0") {
            val request = MockHttpServletRequest("GET", "/some-url")
            val response = MockHttpServletResponse()
            every { userRepository.count() } returns 0L

            interceptor.preHandle(request, response, Any()) shouldBe false
            response.redirectedUrl shouldBe "/bootstrap-setup"
        }

        it("should return true if user count is greater than 0") {
            val request = MockHttpServletRequest("GET", "/some-url")
            val response = MockHttpServletResponse()
            every { userRepository.count() } returns 1L

            interceptor.preHandle(request, response, Any()) shouldBe true
            response.redirectedUrl shouldBe null
        }
    }
})
