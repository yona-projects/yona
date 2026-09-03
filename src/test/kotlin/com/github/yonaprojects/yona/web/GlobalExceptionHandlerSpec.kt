package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.util.Optional

// yona error/requestTextEntityTooLarge.scala.html 대응 (P-템플릿 #53) — GlobalExceptionHandler 대응.
class GlobalExceptionHandlerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val handler = GlobalExceptionHandler(userRepository)

    beforeTest {
        SecurityContextHolder.clearContext()
    }

    afterTest {
        SecurityContextHolder.clearContext()
    }

    describe("handleMaxUploadSizeExceeded") {
        it("인증 정보가 없으면(authentication null) currentUser를 null로 채워야 한다") {
            SecurityContextHolder.getContext().authentication = null
            val model = ExtendedModelMap()
            val response = MockHttpServletResponse()

            val view = handler.handleMaxUploadSizeExceeded(
                MaxUploadSizeExceededException(1024L), model, response
            )

            view shouldBe "error/413"
            response.status shouldBe HttpStatus.PAYLOAD_TOO_LARGE.value()
            model["maxUploadSizeBytes"] shouldBe 1024L
            model["currentUser"] shouldBe null
        }

        it("인증됐지만 isAuthenticated가 false면 currentUser를 null로 채워야 한다") {
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")
            auth.isAuthenticated = false
            SecurityContextHolder.getContext().authentication = auth
            val model = ExtendedModelMap()
            val response = MockHttpServletResponse()

            handler.handleMaxUploadSizeExceeded(MaxUploadSizeExceededException(2048L), model, response)

            model["currentUser"] shouldBe null
        }

        it("익명 사용자(anonymousUser)면 currentUser를 null로 채워야 한다") {
            val auth = AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
            )
            SecurityContextHolder.getContext().authentication = auth
            val model = ExtendedModelMap()
            val response = MockHttpServletResponse()

            handler.handleMaxUploadSizeExceeded(MaxUploadSizeExceededException(4096L), model, response)

            model["currentUser"] shouldBe null
        }

        it("실제 로그인 사용자가 DB에 존재하면 currentUser를 채워야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            val auth = UsernamePasswordAuthenticationToken(
                "gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            SecurityContextHolder.getContext().authentication = auth
            val model = ExtendedModelMap()
            val response = MockHttpServletResponse()

            handler.handleMaxUploadSizeExceeded(MaxUploadSizeExceededException(8192L), model, response)

            model["currentUser"] shouldBe user
        }

        it("인증은 됐지만 DB에서 사용자를 찾을 수 없으면 currentUser를 null로 채워야 한다") {
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()
            val auth = UsernamePasswordAuthenticationToken(
                "ghost", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            SecurityContextHolder.getContext().authentication = auth
            val model = ExtendedModelMap()
            val response = MockHttpServletResponse()

            handler.handleMaxUploadSizeExceeded(MaxUploadSizeExceededException(16384L), model, response)

            model["currentUser"] shouldBe null
        }
    }
})
