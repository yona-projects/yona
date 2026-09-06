package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.security.jackson.SecurityJacksonModules
import tools.jackson.databind.json.JsonMapper

// yona-wiki P3-07(MCP 서버) Step2 — JdbcOAuth2AuthorizationService(Spring 공식 구현)가 attributes/
// 토큰 metadata를 JSON으로 직렬화할 때 쓰는 것과 동일한 구성이다(공식 소스 직접 확인,
// spring-security-oauth2-authorization-server:7.1.1의 JdbcOAuth2AuthorizationService.Jackson3 내부
// 클래스 — Jackson2 기반 구버전(OAuth2AuthorizationServerJackson2Module)은 7.0부터 deprecated돼
// Jackson3 기반으로 교체했다, 이 프로젝트가 이미 tools.jackson(Jackson 3)로 전환돼 있는 것과도 일치).
// SecurityJacksonModules.getModules()가 classpath에 있는 OAuth2AuthorizationServerJacksonModule을
// 자동으로 포함하므로 별도로 등록하지 않는다 — 직접 만든 직렬화 규칙이 아니라 Spring이 공식 배포하는
// 화이트리스트 기반 Jackson 모듈을 그대로 재사용한다.
object OAuthObjectMapper {
    val instance: JsonMapper by lazy {
        val classLoader = OAuthObjectMapper::class.java.classLoader
        JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(classLoader))
            .build()
    }
}
