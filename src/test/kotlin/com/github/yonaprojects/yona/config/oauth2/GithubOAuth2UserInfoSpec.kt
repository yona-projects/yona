package com.github.yonaprojects.yona.config.oauth2

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GithubOAuth2UserInfoSpec : DescribeSpec({
    describe("GithubOAuth2UserInfo") {
        it("should map attributes correctly when all are present") {
            val attributes = mapOf<String, Any>(
                "id" to 12345,
                "name" to "John Doe",
                "email" to "john@example.com",
                "login" to "johndoe"
            )
            val userInfo = GithubOAuth2UserInfo(attributes)

            userInfo.id shouldBe "12345"
            userInfo.name shouldBe "John Doe"
            userInfo.email shouldBe "john@example.com"
            userInfo.loginId shouldBe "johndoe"
        }

        it("should use login for name if name is missing") {
            val attributes = mapOf<String, Any>(
                "id" to 12345L,
                "email" to "john@example.com",
                "login" to "johndoe"
            )
            val userInfo = GithubOAuth2UserInfo(attributes)

            userInfo.name shouldBe "johndoe"
        }

        it("should use empty string for email if email is missing") {
            val attributes = mapOf<String, Any>(
                "id" to 12345.0,
                "name" to "John Doe",
                "login" to "johndoe"
            )
            val userInfo = GithubOAuth2UserInfo(attributes)

            userInfo.email shouldBe ""
        }
    }
})
