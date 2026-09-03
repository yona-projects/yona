package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.LinkedAccount
import com.github.yonaprojects.yona.domain.user.LinkedAccountRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

// yona UserCredential.merge(otherUser) 대응 (P1-56). 병합되어 사라질 계정(otherUser)의
// LinkedAccount를 전부 유지할 계정(keepUser)으로 옮기고, otherUser는 다시 로그인할 수 없도록 잠근다.
class OAuth2AccountMergeServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val linkedAccountRepository = mockk<LinkedAccountRepository>()
    val service = OAuth2AccountMergeService(userRepository, linkedAccountRepository)

    beforeTest {
        clearMocks(userRepository, linkedAccountRepository, answers = false)
    }

    describe("OAuth2AccountMergeService.merge") {
        it("otherUser의 모든 LinkedAccount를 keepUser로 이전해야 한다") {
            val keepUser = User(id = 99L, loginId = "keep", name = "유지될유저", state = UserState.ACTIVE)
            val otherUser = User(id = 42L, loginId = "other", name = "병합될유저", state = UserState.ACTIVE)
            val account1 = LinkedAccount(id = 1L, user = otherUser, providerKey = "google", providerUserId = "g1")
            val account2 = LinkedAccount(id = 2L, user = otherUser, providerKey = "github", providerUserId = "gh1")

            every { userRepository.findById(99L) } returns Optional.of(keepUser)
            every { userRepository.findById(42L) } returns Optional.of(otherUser)
            every { linkedAccountRepository.findByUser(otherUser) } returns listOf(account1, account2)
            every { linkedAccountRepository.saveAll(any<List<LinkedAccount>>()) } answers { firstArg<List<LinkedAccount>>().toMutableList() }
            every { userRepository.save(any()) } answers { firstArg() }

            val result = service.merge(keepUserId = 99L, otherUserId = 42L)

            result.id shouldBe 99L
            account1.user shouldBe keepUser
            account2.user shouldBe keepUser
            verify(exactly = 1) { linkedAccountRepository.saveAll(listOf(account1, account2)) }
        }

        it("otherUser는 병합 후 LOCKED 상태가 되어(다시 로그인 불가) 저장되어야 한다") {
            val keepUser = User(id = 99L, loginId = "keep", name = "유지될유저", state = UserState.ACTIVE)
            val otherUser = User(id = 42L, loginId = "other", name = "병합될유저", state = UserState.ACTIVE)

            every { userRepository.findById(99L) } returns Optional.of(keepUser)
            every { userRepository.findById(42L) } returns Optional.of(otherUser)
            every { linkedAccountRepository.findByUser(otherUser) } returns emptyList()
            every { linkedAccountRepository.saveAll(any<List<LinkedAccount>>()) } answers { firstArg<List<LinkedAccount>>().toMutableList() }
            every { userRepository.save(any()) } answers { firstArg() }

            service.merge(keepUserId = 99L, otherUserId = 42L)

            otherUser.state shouldBe UserState.LOCKED
            verify(exactly = 1) { userRepository.save(otherUser) }
        }

        it("keepUserId와 otherUserId가 같으면 아무 것도 하지 않고 그대로 반환해야 한다") {
            val user = User(id = 99L, loginId = "same", name = "본인", state = UserState.ACTIVE)
            every { userRepository.findById(99L) } returns Optional.of(user)

            val result = service.merge(keepUserId = 99L, otherUserId = 99L)

            result.id shouldBe 99L
            verify(exactly = 0) { linkedAccountRepository.findByUser(any()) }
            verify(exactly = 0) { userRepository.save(any()) }
        }

        it("존재하지 않는 사용자 id면 IllegalArgumentException을 던져야 한다") {
            every { userRepository.findById(99L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.merge(keepUserId = 99L, otherUserId = 42L)
            }
        }

        it("존재하지 않는 otherUser id면 IllegalArgumentException을 던져야 한다") {
            val keepUser = User(id = 99L, loginId = "keep", name = "유지될유저", state = UserState.ACTIVE)
            every { userRepository.findById(99L) } returns Optional.of(keepUser)
            every { userRepository.findById(42L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.merge(keepUserId = 99L, otherUserId = 42L)
            }
        }
    }
})
