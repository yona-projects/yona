package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import org.springframework.security.jackson.SecurityJacksonModules
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator

// yona-wiki P3-07(MCP 서버) Step2 — JdbcOAuth2AuthorizationService(Spring 공식 구현)가 attributes/
// 토큰 metadata를 JSON으로 직렬화할 때 쓰는 것과 동일한 구성이다(공식 소스 직접 확인,
// spring-security-oauth2-authorization-server:7.1.1의 JdbcOAuth2AuthorizationService.Jackson3 내부
// 클래스 — Jackson2 기반 구버전(OAuth2AuthorizationServerJackson2Module)은 7.0부터 deprecated돼
// Jackson3 기반으로 교체했다, 이 프로젝트가 이미 tools.jackson(Jackson 3)로 전환돼 있는 것과도 일치).
// SecurityJacksonModules.getModules()가 classpath에 있는 OAuth2AuthorizationServerJacksonModule을
// 자동으로 포함하므로 별도로 등록하지 않는다 — 직접 만든 직렬화 규칙이 아니라 Spring이 공식 배포하는
// 화이트리스트 기반 Jackson 모듈을 그대로 재사용한다.
//
// yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — Spring이 동의(consent) 처리 과정에서
// OAuth2Authorization의 attributes 맵에 인증된 Principal(java.security.Principal 키)을 그대로 담아
// JSON으로 직렬화하는데, 이 프로젝트의 커스텀 UserDetails 구현체 YonaUserDetails는 Spring이 기본
// 제공하는 PolymorphicTypeValidator 화이트리스트에 없어(Spring 자신의 User 클래스만 기본 허용)
// 역직렬화 시점에 InvalidTypeIdException으로 거부된다(실제 authorization code flow 통합테스트로
// 처음 발견 — DCR/등록/토큰 발급 자체는 통과하지만 동의 처리 단계에서 저장한 attributes를 나중에
// 다시 읽을 때 터진다). Spring이 공식적으로 제공하는 확장 지점
// (SecurityJacksonModules.getModules(ClassLoader, BasicPolymorphicTypeValidator.Builder))을 그대로
// 써서 이 클래스 하나만 화이트리스트에 추가한다 — 화이트리스트 검증 메커니즘 자체를 우회/비활성화하지
// 않는다(보안 유지).
object OAuthObjectMapper {
    val instance: JsonMapper by lazy {
        val classLoader = OAuthObjectMapper::class.java.classLoader
        val polymorphicTypeValidatorBuilder = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(YonaUserDetails::class.java)
        JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(classLoader, polymorphicTypeValidatorBuilder))
            .build()
    }
}
