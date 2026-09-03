package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.core.authority.SimpleGrantedAuthority

class YonaOAuth2UserSpec : DescribeSpec({
    describe("YonaOAuth2User") {
        it("getName, getAttributes, getAuthorities가 정상적으로 값을 반환해야 한다") {
            val user = User(loginId = "testUser", name = "Test User", email = "test@example.com", state = UserState.ACTIVE)
            val attributes = mapOf<String, Any>("key" to "value")
            val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

            val oAuth2User = YonaOAuth2User(user, attributes, authorities)

            oAuth2User.name shouldBe "testUser"
            oAuth2User.attributes shouldBe attributes
            oAuth2User.authorities shouldBe authorities
            oAuth2User.user shouldBe user
        }
    }
})
