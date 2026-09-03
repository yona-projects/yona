package com.github.yonaprojects.yona.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.test.util.ReflectionTestUtils

class YonaAuthenticationSuccessHandlerSpec : DescribeSpec({
    val requestCache = mockk<HttpSessionRequestCache>()
    val handler = YonaAuthenticationSuccessHandler().apply {
        ReflectionTestUtils.setField(this, "requestCache", requestCache)
    }
    val authentication = mockk<Authentication>()

    describe("YonaAuthenticationSuccessHandler") {
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
})
