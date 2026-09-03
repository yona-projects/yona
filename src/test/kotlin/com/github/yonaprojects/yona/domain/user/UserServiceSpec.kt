package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.mail.MailService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class UserServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val emailRepository = mockk<EmailRepository>()
    val userVerificationRepository = mockk<UserVerificationRepository>()
    val mailService = mockk<MailService>()
    val userService = UserServiceImpl(userRepository, emailRepository, userVerificationRepository, mailService)

    beforeTest {
        clearMocks(userRepository, emailRepository, userVerificationRepository, mailService)
    }

    describe("UserService") {
        describe("findByLoginId") {
            it("로그인 ID가 존재할 때 사용자 객체를 반환해야 한다") {
                // Given
                val expectedUser = User(id = 1L, loginId = "gildong", name = "홍길동")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(expectedUser)

                // When
                val actualUser = userService.findByLoginId("gildong")

                // Then
                actualUser shouldNotBe null
                actualUser?.loginId shouldBe "gildong"
                actualUser?.name shouldBe "홍길동"
                verify(exactly = 1) { userRepository.findByLoginId("gildong") }
            }

            it("로그인 ID가 존재하지 않을 때 null을 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("unknown") } returns Optional.empty()

                // When
                val actualUser = userService.findByLoginId("unknown")

                // Then
                actualUser shouldBe null
                verify(exactly = 1) { userRepository.findByLoginId("unknown") }
            }
        }
    }
})
