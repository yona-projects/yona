package com.github.yonaprojects.yona.config.oauth2server

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

// RFC8707(Resource Indicators) 검증의 리소스 서버 쪽 절반. ResourceIndicatorTokenCustomizer가
// 발급 시점에 스탬핑한 aud 클레임이 이 리소스 서버 자신을 가리키는지 확인한다 — 다른 리소스
// 서버용으로 발급된 토큰을 그대로 받아주는 "토큰 패스스루"를 막는다. MCP 전용이 아니라 리소스
// 서버 일반(현재 MCP/API 두 종류)에 재사용하도록 리소스 무관하게 설계돼 있다(requiredAudience만
// 다르게 주입).
class AudienceValidator(private val requiredAudience: String) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        return if (token.audience.orEmpty().contains(requiredAudience)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    "invalid_token",
                    "이 토큰은 이 리소스 서버($requiredAudience)를 대상으로 발급되지 않았습니다.",
                    null
                )
            )
        }
    }
}
