package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.LinkedAccount
import com.github.yonaprojects.yona.domain.user.LinkedAccountRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.util.Optional

class CustomOAuth2UserServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val linkedAccountRepository = mockk<LinkedAccountRepository>()
    val delegate = mockk<DefaultOAuth2UserService>()
    val accountMergeService = mockk<OAuth2AccountMergeService>()
    val customOAuth2UserService = CustomOAuth2UserService(
        userRepository, linkedAccountRepository, accountMergeService, delegate, ""
    )

    beforeTest {
        clearMocks(userRepository, linkedAccountRepository, delegate, accountMergeService)
        SecurityContextHolder.clearContext()
    }

    afterTest {
        SecurityContextHolder.clearContext()
    }

    describe("CustomOAuth2UserService") {
        it("소셜 로그인 성공 시 신규 사용자라면 자동 회원가입이 수행되어야 한다") {
            // Given
            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .tokenUri("https://token.uri")
                .authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri")
                .build()

            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf(
                "sub" to "google-sub-id",
                "name" to "홍길동",
                "email" to "gildong@example.com"
            )
            val defaultOAuth2User = DefaultOAuth2User(
                listOf(SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
            )

            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            val savedUserSlot = slot<User>()
            every { userRepository.save(capture(savedUserSlot)) } answers {
                val user = firstArg<User>()
                user.id = 100L
                user
            }
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            // When
            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            // Then
            oauth2User shouldNotBe null
            oauth2User.user.id shouldBe 100L
            oauth2User.user.name shouldBe "홍길동"
            oauth2User.user.email shouldBe "gildong@example.com"
            oauth2User.user.loginId shouldBe "gildong"
            oauth2User.authorities.map { it.authority } shouldBe listOf("ROLE_ACTIVE")

            verify(exactly = 1) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "google" && it.providerUserId == "google-sub-id" })
            }
        }

        it("이미 연결된 provider 계정으로 재로그인하면 신규 가입 없이 연결된 사용자로 로그인해야 한다") {
            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "홍길동", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            val existingLink = LinkedAccount(id = 1L, user = existingUser, providerKey = "google", providerUserId = "google-sub-id")
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.of(existingLink)

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { linkedAccountRepository.save(any()) }
        }

        it("다른 provider로 이미 가입된(이메일 일치) 사용자가 새 provider로 로그인하면 기존 계정에 연결(link)만 하고 새 계정을 만들지 않아야 한다") {
            val clientRegistration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("id" to 555, "login" to "gildong-gh", "name" to "홍길동", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "id")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("github", "555") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "github" && it.providerUserId == "555" && it.user.id == 42L })
            }
        }

        it("익명 인증 상태(로그인 안 함)면 로그인 중인 사용자로 취급하지 않고 일반 로그인/자동가입 분기를 타야 한다") {
            SecurityContextHolder.getContext().authentication =
                AnonymousAuthenticationToken(
                    "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
                )

            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "홍길동", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.findByLoginId(any()) }
        }

        it("허용된 이메일 도메인 설정이 있고 신규 가입자의 이메일이 그 목록에 없으면 OAuth2AuthenticationException을 던져야 한다") {
            val restrictedService = CustomOAuth2UserService(
                userRepository, linkedAccountRepository, accountMergeService, delegate, "allowed.com"
            )

            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "홍길동", "email" to "gildong@notallowed.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@notallowed.com") } returns Optional.empty()
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            shouldThrow<OAuth2AuthenticationException> {
                restrictedService.loadUser(userRequest)
            }
            verify(exactly = 0) { userRepository.save(any()) }
        }

        // yona YonaUserServicePlugin.link(oldUser, newUser) 대응 (P1-56). 로그인 중인 사용자가 아직
        // 아무 계정에도 연결되지 않은 provider로 인증하면, 이메일/loginId 매칭을 거치지 않고 곧바로
        // "현재 로그인 중인 계정"에 연결해야 한다.
        it("로그인 중인 사용자가 처음 보는 provider로 인증하면 이메일 매칭 없이 현재 로그인 계정에 바로 연결해야 한다") {
            val currentUser = User(id = 99L, loginId = "current-user", name = "로그인중", email = "current@example.com", state = UserState.ACTIVE)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(currentUser.loginId, "password", listOf(SimpleGrantedAuthority("ROLE_ACTIVE")))
            every { userRepository.findByLoginId("current-user") } returns Optional.of(currentUser)

            val clientRegistration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            // 이메일이 다른 기존 계정과 우연히 겹치더라도(예: gildong@example.com) 무시되고 currentUser에
            // 연결돼야 함을 함께 검증 — findByEmail이 아예 호출되지 않아야 한다.
            val attributes = mapOf("id" to 777, "login" to "gh-user", "name" to "GH유저", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "id")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("github", "777") } returns Optional.empty()
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 99L
            verify(exactly = 0) { userRepository.findByEmail(any()) }
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "github" && it.providerUserId == "777" && it.user.id == 99L })
            }
        }

        // yona YonaUserServicePlugin.merge(newUser, oldUser) 대응 (P1-56). yona는 Global.askMerge()를
        // null로 두어 확인 절차 없이 자동 병합한다 — 로그인 중인 사용자가 이미 다른 계정에 연결돼 있는
        // provider로 인증하면, 그 다른 계정(otherUser)을 로그인 중인 계정(currentUser)으로 즉시
        // 병합하고 currentUser로 로그인이 이어져야 한다.
        it("로그인 중인 사용자가 이미 다른 계정에 연결된 provider로 인증하면 그 계정을 currentUser로 자동 병합하고 로그인을 이어가야 한다") {
            val currentUser = User(id = 99L, loginId = "current-user", name = "로그인중", email = "current@example.com", state = UserState.ACTIVE)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(currentUser.loginId, "password", listOf(SimpleGrantedAuthority("ROLE_ACTIVE")))
            every { userRepository.findByLoginId("current-user") } returns Optional.of(currentUser)

            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "다른사람", "email" to "other@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val otherUser = User(id = 42L, loginId = "other-user", name = "다른사람", email = "other@example.com", state = UserState.ACTIVE)
            val existingLink = LinkedAccount(id = 1L, user = otherUser, providerKey = "google", providerUserId = "google-sub-id")
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.of(existingLink)
            every { accountMergeService.merge(keepUserId = 99L, otherUserId = 42L) } returns currentUser

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 99L
            verify(exactly = 1) { accountMergeService.merge(keepUserId = 99L, otherUserId = 42L) }
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { linkedAccountRepository.save(any()) }
        }

        it("로그인 중이더라도 인증한 provider가 이미 본인 계정에 연결돼 있으면 그대로 로그인해야 한다") {
            val currentUser = User(id = 99L, loginId = "current-user", name = "로그인중", email = "current@example.com", state = UserState.ACTIVE)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(currentUser.loginId, "password", listOf(SimpleGrantedAuthority("ROLE_ACTIVE")))
            every { userRepository.findByLoginId("current-user") } returns Optional.of(currentUser)

            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "로그인중", "email" to "current@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingLink = LinkedAccount(id = 1L, user = currentUser, providerKey = "google", providerUserId = "google-sub-id")
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.of(existingLink)

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 99L
            verify(exactly = 0) { linkedAccountRepository.save(any()) }
        }

        it("이메일로 찾을 수 없지만 loginId가 일치하는 경우 기존 사용자 계정과 연결해야 한다") {
            val clientRegistration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("id" to 888, "login" to "gildong", "name" to "홍길동", "email" to "new-email@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "id")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "old-email@example.com", state = UserState.ACTIVE)
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("github", "888") } returns Optional.empty()
            every { userRepository.findByEmail("new-email@example.com") } returns Optional.empty()
            every { userRepository.findByLoginId("gildong") } returns Optional.of(existingUser)
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "github" && it.providerUserId == "888" && it.user.id == 42L })
            }
        }
    }
})
