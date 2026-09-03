package com.github.yonaprojects.yona.config.oauth2

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class OAuth2UserInfoFactorySpec : DescribeSpec({
    describe("OAuth2UserInfoFactory") {
        // 모든 로직은 companion object에 있어 인스턴스화가 불필요하지만, Kotlin이 자동 생성하는
        // 바깥 클래스의 기본 생성자가 JaCoCo에는 별도 클래스(OAuth2UserInfoFactory, $Companion 제외)로
        // 잡혀 LINE/METHOD 0%로 집계된다 — 실제 기능 검증과 무관한 생성자 호출로 이 격차만 닫는다.
        it("companion object 전용 클래스이지만 인스턴스화 자체는 가능해야 한다") {
            OAuth2UserInfoFactory() shouldNotBe null
        }

        it("google registrationId를 전달하면 GoogleOAuth2UserInfo를 반환해야 한다") {
            val attributes = mapOf<String, Any>("sub" to "google-sub")
            val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes)
            userInfo.id shouldBe "google-sub"
        }

        it("github registrationId를 전달하면 GithubOAuth2UserInfo를 반환해야 한다") {
            val attributes = mapOf<String, Any>("id" to 12345)
            val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("github", attributes)
            userInfo.id shouldBe "12345"
        }

        it("지원하지 않는 registrationId를 전달하면 IllegalArgumentException이 발생해야 한다") {
            val attributes = mapOf<String, Any>()
            val exception = shouldThrow<IllegalArgumentException> {
                OAuth2UserInfoFactory.getOAuth2UserInfo("kakao", attributes)
            }
            exception.message shouldBe "지원하지 않는 소셜 로그인 제공자입니다: kakao"
        }
    }
})
