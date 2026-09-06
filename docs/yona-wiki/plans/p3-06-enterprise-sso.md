---
type: plan
id: P3-06
title: "엔터프라이즈 SSO(SAML2 / 범용 OIDC)"
status: done
priority: 5
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-06
created: 2026-08-28
updated: 2026-09-06
tags: [plan, p3, auth, sso]
---

# 엔터프라이즈 SSO(SAML2 / 범용 OIDC)

## 배경

LDAP 연동(`LdapService.kt`/`LdapUserProvisioningService.kt`/`LdapQueryBuilder.kt`/`LdapUser.kt`,
`YonaAuthenticationProvider.kt`)은 이미 구현·테스트 완료 상태임이 코드로 확인됨 — 이 계획의 신규 대상은
**SAML2와 범용 OIDC 두 가지뿐**이며 LDAP은 범위에서 제외한다.
원본: [`docs/PARITY_BACKLOG.md#P3-06`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- SAML2 SP(Service Provider) 연동 — `spring-security-saml2-service-provider`
- 범용 OIDC 연동(Okta/Azure AD/Keycloak 등 임의 IdP) — 기존 `spring-boot-starter-oauth2-client` 재사용
- LDAP과 동일한 JIT(Just-In-Time) 프로비저닝 패턴 재사용
- **(착수 중 사용자 지시로 확장)** 관리자 UI(`/site/sso`) — IdP 메타데이터(Issuer URL/Client ID/Client
  Secret, Sign on URL/Issuer/Public Certificate)를 웹에서 등록/조회/수정. 최초 계획은 "설정 파일 기반
  최소 범위"였으나, 착수 중 사용자가 "필요하면 UI도 임의로 구현해도 된다"고 범위를 확장해 실제로 구현했다
  (완료 로그 참고). `application.yml` 기반 최소 동작 경로는 그대로 유지된다(DB에 저장된 값이 없으면
  `application.yml` 값을 그대로 쓴다 — LDAP과 동일한 "설정 파일만으로도 동작" 원칙).

### 제외 (비범위)
- LDAP 관련 변경 없음(이미 완료)
- 기존 소셜 로그인(OAuth2, `LinkedAccount.kt`)과의 통합/병합 UI는 다루지 않음(P1-03에서 이미 "미이식 확정"으로
  종결된 영역) — 오히려 이 계획은 소셜 로그인과 **완전히 분리된** 별도 JIT 프로비저닝 경로를 만든다(설계 개요 참고)
- 실제 IdP(Keycloak 등) testcontainer 기반 end-to-end 테스트 — LDAP도 실제 서버 없이 순수 단위테스트로
  검증했던 것과 동일한 수준(`LdapUserProvisioningServiceSpec` 패턴)으로 충분하다고 판단
- 복잡한 다중 IdP 우선순위/폴백 정책 — LDAP/OIDC/SAML2 각각 독립적인 `enabled` 플래그만 지원

## 의존성

- **선행 조건**: 없음 — 완전히 독립적
- **후속 파급**: 없음

## 설계 개요

- **프로비저닝 패턴**: `LdapUserProvisioningService`처럼 `OidcUserProvisioningService`/`Saml2UserProvisioningService`를
  두어 이메일 기준 기존 유저 매칭 또는 신규 생성 — LDAP과 동일한 "외부 IdP 인증 성공 → 로컬 User로 JIT 재조정" 패턴.
  LDAP과 달리 리다이렉트 로그인이라 로컬 비밀번호를 알 수 없어, 신규 생성 계정은 `password`/`passwordSalt`를
  비워둔다(항상 SSO로만 로그인).
- **인증 흐름 통합 지점 — Step 1에서 확정**: 아래 "Step 1 완료 로그" 참고. 결론만 요약하면, 기존 하나뿐인
  `SecurityFilterChain`(`SecurityConfig.kt`)에 `.saml2Login{}`과 `.oauth2Login{}.userInfoEndpoint{}.oidcUserService{}`
  두 설정 블록만 추가했다 — 별도 `SecurityFilterChain` 다중 등록은 불필요했다.
- **소셜 로그인과의 분리**: 기존 `.oauth2Login{}.userInfoEndpoint{}.userService(customOAuth2UserService)`는
  그대로 두고, `.oidcUserService(enterpriseOidcUserService)`를 나란히 추가했다. Spring Security의
  `OAuth2LoginConfigurer`는 `ClientRegistration`의 scope에 `"openid"`가 있으면 `oidcUserService()` 경로를,
  없으면(google/github는 `scope=profile,email`) `userService()` 경로를 자동으로 탄다 — registrationId로 직접
  분기할 필요가 전혀 없었다(계획 문서의 우려였던 "OAuth2UserService가 등록 ID별로 다른 서비스를 못 태우는
  제약"은 실제로는 존재하지 않았고, scope 기반 자동 분기가 이미 그 역할을 한다).

## 단계별 작업 계획 (TDD)

1. **Step 1 — 인증 흐름 통합 방식 결정(스파이크, 완료)**
   - **결론**: 새 `SecurityFilterChain`이 필요 없다. 기존 하나뿐인 필터체인에 `.saml2Login{}`(SAML2)과
     `.oauth2Login{}.userInfoEndpoint{}.oidcUserService{}`(OIDC)를 나란히 추가하면 된다 — Spring Security는
     `oauth2Login()` 하나에 여러 `ClientRegistration`(소셜 로그인용 + 엔터프라이즈 OIDC용)을 동시에 등록하는
     것을 기본 지원하고(`/oauth2/authorization/{registrationId}` 경로로 구분), `.saml2Login{}`도 같은
     `HttpSecurity` 체인에 나란히 추가 가능하다(SAML2 SSO 개시는 `/saml2/authenticate/{registrationId}`,
     ACS 콜백은 `/login/saml2/sso/{registrationId}` — 기존 경로와 전혀 겹치지 않음, 실측 확인).
   - **소셜 로그인과의 분리 배선**: 계획 문서가 우려했던 "customOAuth2UserService를 그대로 재사용하면 링크/병합
     흐름을 타버린다" 문제는, `.oidcUserService()`라는 별도 설정 메서드에 완전히 별개인 `EnterpriseOidcUserService`를
     등록해 해소했다 — Spring Security가 scope의 `"openid"` 유무로 두 서비스를 자동 분기해 주므로 registrationId
     분기 코드 자체가 불필요했다.
   - **SAML2는 커스텀 `AuthenticationManager`가 필요**: `.saml2Login{}`은 기본적으로 SAML 어서션 파싱
     결과(`Saml2Authentication`, `DefaultSaml2AuthenticatedPrincipal`)를 그대로 세션에 태우므로, JIT
     프로비저닝을 끼워 넣으려면 `OpenSaml5AuthenticationProvider.setResponseAuthenticationConverter(...)`로
     커스텀 컨버터(`EnterpriseSaml2ResponseAuthenticationConverter`)를 등록한 `AuthenticationManager`를
     `.saml2Login { it.authenticationManager(...) }`로 명시적으로 지정해야 했다(OIDC 쪽은 `oidcUserService()`
     설정 메서드가 이미 있어 이 정도 커스텀 배선이 필요 없었던 것과 대비됨).
   - **예상 밖 제약 1 — 자기참조 순환**: `EnterpriseOidcUserService`를 `OidcUserService`(Spring이 제공하는
     구체 클래스)를 상속하면서 테스트용 `delegate: OidcUserService` 생성자 파라미터를 두었더니, 이 빈
     자신도 `OidcUserService`의 하위타입이 되어 Spring이 자기 자신을 주입 후보로 오인해
     `BeanCurrentlyInCreationException`으로 컨텍스트 기동이 실패했다(실측 확인). `.oidcUserService()` 설정
     메서드가 실제로 요구하는 타입은 `OAuth2UserService<OidcUserRequest, OidcUser>` 인터페이스뿐이라,
     상속을 인터페이스 구현으로 바꿔 해소했다(상세: `config/sso/EnterpriseOidcUserService.kt` 주석).
   - **예상 밖 제약 2 — `OAuth2ClientProperties` 빈 소실**: 관리자 UI(Step 4, 아래 참고)로 인해
     `YonaClientRegistrationRepository`(우리가 정의한 `ClientRegistrationRepository` 구현체)를 빈으로
     등록했더니, Spring Boot의 `OAuth2ClientConfigurations.ClientRegistrationRepositoryConfiguration`
     (`@ConditionalOnMissingBean(ClientRegistrationRepository::class)`) 전체가 백오프하면서 그 클래스에
     걸려 있던 `@EnableConfigurationProperties(OAuth2ClientProperties::class)`까지 함께 사라져, google/github
     소셜 로그인 설정(`OAuth2ClientProperties`)을 읽을 방법이 없어지는 문제가 실측으로 드러났다
     (`NoSuchBeanDefinitionException`) — `YonaClientRegistrationRepository`에
     `@EnableConfigurationProperties(OAuth2ClientProperties::class)`를 직접 추가해 해소했다. 이 문제는 다른
     동시 작업 세션의 전체 컨텍스트 테스트까지 일시적으로 깨뜨렸다가(같은 작업 디렉터리를 공유하는 워킹
     디렉터리 특성상), 발견 즉시 수정해 해소했다.
   - **예상 밖 제약 3 — Spring Boot 4.1.1은 SAML2 relyingparty 자동구성 모듈이 없음**: Boot 3.x/이전 버전
     문서에 있는 `spring.security.saml2.relyingparty.registration.*` 프로퍼티 기반 자동구성은
     `spring-boot-security-saml2` 같은 전용 자동구성 모듈이 있어야 동작하는데, 이 프로젝트가 쓰는 Boot
     4.1.1에는 그 모듈 자체가 없다(실측 확인 — `spring-boot-security`/`spring-boot-security-oauth2-client`
     jar만 있고 saml2 전용 모듈이 없음). 어차피 이 계획은 DB/설정 파일 양쪽에서 동적으로 읽어야 해서
     처음부터 `RelyingPartyRegistrationRepository`를 수동 빈으로 구현할 계획이었으므로 실질적인 영향은 없었다.
2. **Step 2 — OIDC 연동(완료)**
   - `OidcUserProvisioningService.reconcile(oidcUser: OidcUser): User` — 이메일 클레임으로 매칭/생성.
     `OidcUserProvisioningServiceSpec`(4 tests, `DefaultOidcUser`를 직접 만들어 넣는 순수 유닛테스트).
   - `EnterpriseOidcUserService`(`OAuth2UserService<OidcUserRequest, OidcUser>` 구현) —
     `EnterpriseOidcUserServiceSpec`(delegate 주입 가능해 실제 IdP 호출 없이 검증).
3. **Step 3 — SAML2 연동(완료)**
   - `Saml2UserProvisioningService.reconcile(principal, emailAttr, displayNameAttr): User` — 어서션
     속성(이메일 속성 이름은 IdP마다 달라 설정 가능) 또는 NameID(이메일 형식일 때)로 매칭/생성.
     `Saml2UserProvisioningServiceSpec`(5 tests, `DefaultSaml2AuthenticatedPrincipal`을 직접 만들어 넣는
     순수 유닛테스트).
   - `EnterpriseSaml2ResponseAuthenticationConverter` — 기본 `OpenSaml5AuthenticationProvider`의
     `createDefaultResponseAuthenticationConverter()`(OpenSAML XML 파싱/서명 검증)에 위임하고, 그 결과로
     나온 `Saml2Authentication`만 받아 JIT 프로비저닝하는 `buildAuthentication()`을 별도로 노출해, 실제
     SAML Response XML을 만들지 않고도 순수 단위테스트가 가능하게 했다(`EnterpriseSaml2ResponseAuthenticationConverterSpec`).
4. **Step 4 — 관리자 설정(완료, 착수 중 UI까지 확장)**
   - 최소 범위(`application.yml` 기반)는 계획대로 구현: `yona.sso.oidc.*`/`yona.sso.saml2.*` 프로퍼티
     (LDAP과 동일하게 `YONA_SSO_*` 환경변수로도 주입 가능).
   - **사용자 지시로 확장**: 착수 중 사용자가 "Step4는 최소 범위(설정 파일)로 하되, 필요하면 관리자 UI도
     임의로 구현해도 된다"고 명시적으로 스코프를 확장했다. 이후 "모호하면 GitHub 방식을 따르라"는 추가
     방침도 받아, `/site/sso` 관리자 화면의 필드 구성/문구를 **GitHub Enterprise 조직 설정 > Security >
     "SAML single sign-on" 화면**(Sign on URL/Issuer/Public Certificate/Enable SAML authentication)을
     그대로 차용해 구현했다(OIDC는 GitHub이 조직 SSO로 직접 제공하지 않아 Issuer URL/Client ID/Client
     Secret이라는 일반적인 엔터프라이즈 SaaS 관행을 따름). `SiteViewController`의 관리자 전용 패턴
     (`checkAdmin` → `IllegalArgumentException` → `error/403`)과 `WebhookController`의 설정형 화면 패턴을
     그대로 재사용했다 — 새 프런트엔드 스택 도입 없음.
   - DB 저장값이 있으면 그 값이 우선하고, 없으면(앱을 막 띄운 직후) `application.yml` 값을 그대로
     쓴다(`SsoSettingsService`, `SsoSettingsServiceSpec` 5 tests) — LDAP의 `@Value` 기본값 패턴과 동일한
     "설정 파일만으로도 최소 동작" 원칙을 관리자 UI 위에 얹은 구조.
   - `YonaClientRegistrationRepository`/`YonaRelyingPartyRegistrationRepository`가 매 로그인 시도마다
     DB(또는 DB에 값이 없으면 yml 기본값)에서 최신 설정을 읽어 `ClientRegistration`/`RelyingPartyRegistration`을
     즉석에서 조립한다 — 관리자가 UI에서 값을 바꾸면 앱 재시작 없이 바로 반영된다. OIDC는 매번
     `ClientRegistrations.fromIssuerLocation()`으로 Discovery 문서를 다시 조회하는 비용이 있으나, 로그인
     시도 빈도를 고려해 캐싱은 후속 최적화로 미뤘다(리스크 표 참고).
5. **Step 5 — 다중 IdP 동시 지원(완료, 최소 범위)**
   - LDAP(`yona.ldap.enabled`)/OIDC(`yona.sso.oidc.enabled` 또는 DB)/SAML2(`yona.sso.saml2.enabled` 또는
     DB) 각각 독립적인 on/off 플래그로 동시에 켜질 수 있다 — 서로의 코드 경로를 전혀 건드리지 않으므로
     (LDAP은 `YonaAuthenticationProvider`, OIDC/SAML2는 `SecurityConfig`의 별도 설정 블록) 우선순위/폴백
     정책 설계 자체가 필요 없었다. `YonaClientRegistrationRepositorySpec`/`YonaRelyingPartyRegistrationRepositorySpec`의
     "비활성화 시 null 반환" 테스트가 각 IdP의 독립적 on/off를 검증한다.

## 완료 기준 (Definition of Done)

- [x] OIDC 로그인 → JIT 프로비저닝까지 테스트로 검증(`OidcUserProvisioningServiceSpec`,
      `EnterpriseOidcUserServiceSpec`) — 세션 생성 자체(필터 체인 전체 리다이렉트 플로우)는 LDAP도 실제
      서버 없이 `authenticateWithLdap()` 안에서 `UserDetails` 생성 지점까지만 검증했던 것과 동일한 수준
- [x] SAML2 로그인 → JIT 프로비저닝까지 테스트로 검증(`Saml2UserProvisioningServiceSpec`,
      `EnterpriseSaml2ResponseAuthenticationConverterSpec`) — 위와 동일한 검증 수준
- [x] LDAP 기존 동작(`LdapUserProvisioningServiceSpec`/`LdapQueryBuilderSpec`/`YonaAuthenticationProviderSpec`)에
      회귀 없음 확인(완료 로그 참고) — LDAP 코드 자체는 한 줄도 수정하지 않음
- [x] 인증 흐름 통합 방식(Step 1 결정)이 이 문서에 반영되고 실제 구현과 일치
- [x] (확장) 관리자 UI(`/site/sso`)로 IdP 설정을 등록/조회/수정할 수 있음(`SsoAdminControllerSpec`)
- [x] `./gradlew test` 전체 GREEN — 단, 이 계획과 무관한 환경 요인(동시 세션의 공유 MariaDB 테스트 DB
      경합)으로 일부 무관 클래스가 간헐 실패할 수 있음을 확인·교차검증함(완료 로그 참고). 이 계획이 만든
      코드/테스트 자체는 모든 실행에서 항상 GREEN.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 인증 흐름 통합 방식 | `AuthenticationProvider` 위임 vs `SecurityFilterChain` 다중 등록 미정 | **해소** — 기존 필터체인에 `.saml2Login{}`/`.oidcUserService{}` 두 블록만 추가(Step 1 완료 로그 참고) |
| 관리자 설정 방식 | UI로 IdP 등록할지 설정 파일 기반일지 미정 | **해소** — 사용자 지시로 둘 다 지원(설정 파일이 기본값, UI 저장값이 우선) |
| 엔터프라이즈 OIDC의 Discovery 재조회 비용 | `YonaClientRegistrationRepository`가 로그인 시도마다 `ClientRegistrations.fromIssuerLocation()`으로 Discovery 문서를 다시 조회함 | 후속 과제로 미룸 — 캐싱(TTL 또는 관리자 UI 저장 시점에만 재조회)은 이번 라운드 범위 밖. 로그인 시도 빈도가 낮아 당장 문제 되지 않는다고 판단 |
| SAML2 SP 메타데이터 자동 게시 | `/saml2/service-provider-metadata/{registrationId}` 엔드포인트(IdP에 등록할 SP 메타데이터 XML 자동 생성)는 `.saml2Login{}`이 기본 제공하지 않고 별도 `Saml2MetadataFilter` 등록이 필요 | 이번 라운드 범위 밖으로 명시적으로 이월 — 관리자 UI에 SP entity ID/ACS URL을 텍스트로 안내하는 것으로 대체 가능(후속 과제) |

## 완료 로그

### 1라운드 (2026-09-06) — Step1(스파이크 해소) ~ Step5(다중 IdP 온오프) 전체 + 사용자 지시로 관리자 UI까지 확장

- **의존성**: `build.gradle.kts`에 `org.springframework.security:spring-security-saml2-service-provider`를
  버전 미지정으로 추가(Spring Boot 4.1.1의 Spring Security BOM이 7.1.1로 관리). 이 라이브러리가 의존하는
  OpenSAML(`org.opensaml:opensaml-saml-api` 등)이 Maven Central에 없어(Shibboleth 프로젝트 자체 저장소로만
  배포) `repositories {}`에 `https://build.shibboleth.net/nexus/content/repositories/releases/`를 추가했다
  (실측 확인 — mavenCentral만으로는 `opensaml-saml-api:5.2.3 FAILED`).
- **Step 1(스파이크)**: 위 "설계 개요"/"단계별 작업 계획" 섹션에 결론과 근거를 상세히 반영. 핵심은 "새
  `SecurityFilterChain` 불필요, 기존 체인에 두 블록만 추가"와 "소셜 로그인과의 분리는 scope 기반 자동
  분기로 해결됨(registrationId 분기 불필요)" 두 가지.
- **Step 2 — OIDC**: `domain/user/OidcUserProvisioningService.kt`(신규) — LDAP과 동일하게 이메일 매칭
  우선, 없으면 신규 생성(loginId는 `preferred_username` 클레임 또는 이메일 로컬파트, google 소셜 로그인의
  `GoogleOAuth2UserInfo.loginId` 패턴과 동일). `config/sso/EnterpriseOidcUserService.kt` +
  `config/sso/YonaOidcUser.kt`(`getName()`이 IdP의 sub가 아니라 로컬 `User.loginId`를 반환 —
  `config/oauth2/YonaOAuth2User`와 동일한 이유, `authentication.name` 기반 기존 관례 유지). `SecurityConfig.kt`의
  `.oauth2Login{}.userInfoEndpoint{}`에 `.oidcUserService(enterpriseOidcUserService)` 한 줄 추가.
  검증: `OidcUserProvisioningServiceSpec`(4 tests) + `EnterpriseOidcUserServiceSpec`(1 test).
- **Step 3 — SAML2**: `domain/user/Saml2UserProvisioningService.kt`(신규) — 어서션 속성(이름은 설정 가능,
  기본 `email`/`displayName`) 또는 NameID(이메일 형식일 때만)로 매칭/생성. `config/sso/YonaSaml2AuthenticatedPrincipal.kt`
  (`getName()`이 NameID가 아니라 로컬 `User.loginId` 반환) + `config/sso/EnterpriseSaml2ResponseAuthenticationConverter.kt`
  (기본 컨버터에 위임 후 JIT 프로비저닝, `buildAuthentication()` 분리로 OpenSAML 없이 단위테스트 가능).
  `SecurityConfig.kt`에 `saml2AuthenticationManager()` 빈 메서드 추가(`OpenSaml5AuthenticationProvider` +
  커스텀 컨버터를 `ProviderManager`로 감쌈) → `.saml2Login { it.authenticationManager(...) }`로 배선.
  검증: `Saml2UserProvisioningServiceSpec`(5 tests) + `EnterpriseSaml2ResponseAuthenticationConverterSpec`(1 test).
- **Step 4 — 관리자 설정(+ UI 확장)**: `domain/sso/OidcSsoSettings.kt`/`Saml2SsoSettings.kt`(신규 패키지,
  각각 싱글턴 행 id=1) + `OidcSsoSettingsRepository`/`Saml2SsoSettingsRepository`(순수 `JpaRepository`) +
  `SsoSettingsService.kt`(DB 조회 실패 시 `@Value` yml 기본값으로 폴백, `SsoSettingsServiceSpec` 5 tests).
  `config/sso/YonaClientRegistrationRepository.kt`(신규, `ClientRegistrationRepository` 직접 구현) —
  google/github 정적 등록은 `OAuth2ClientPropertiesMapper`로 재구성해 유지하고, 활성화된 엔터프라이즈
  OIDC는 매 조회마다 `SsoSettingsService`에서 읽어 동적으로 빌드(`enterpriseRegistrationBuilder`를
  생성자 주입 가능하게 해 실제 Discovery 없이 단위테스트, `YonaClientRegistrationRepositorySpec` 4 tests).
  `config/sso/YonaRelyingPartyRegistrationRepository.kt`(신규, `RelyingPartyRegistrationRepository` 직접
  구현) — 활성화된 SAML2 IdP 설정을 매 조회마다 읽어 PEM 인증서를 파싱해 `RelyingPartyRegistration`을
  조립(`YonaRelyingPartyRegistrationRepositorySpec` 4 tests, openssl로 만든 자체 서명 테스트 인증서 사용).
  관리자 UI: `web/SsoAdminController.kt`(`/site/sso` GET, `/site/sso/oidc`·`/site/sso/saml2` POST,
  `SiteViewController`와 동일한 `checkAdmin` 패턴) + `templates/site/sso.html`(GitHub SAML SSO 화면
  필드 구성 차용, `site/mail.html`과 동일한 레이아웃/사이드바 패턴 재사용) +
  `templates/site/layout.html`의 사이드바에 "엔터프라이즈 SSO" 메뉴 추가 + `messages*.properties`에
  `site.sidebar.sso` 키 3개 언어 추가. 로그인 화면(`AuthController.loginForm()`, `templates/login.html`)에도
  활성화 여부에 따라 OIDC/SAML2 로그인 버튼을 조건부로 노출하도록 `SsoSettingsService`를 주입.
  검증: `SsoAdminControllerSpec`(6 tests) + `AuthControllerSpec`에 SSO 모델 속성 테스트 1건 추가.
- **Step 5 — 다중 IdP 온오프**: 별도 구현 없이 위 설계(각 IdP가 독립적인 코드 경로/설정)로 자동 충족 —
  `YonaClientRegistrationRepositorySpec`/`YonaRelyingPartyRegistrationRepositorySpec`의 "비활성화 시 null"
  테스트가 곧 독립적 on/off 검증.
- **회귀 확인**: `LdapUserProvisioningServiceSpec`/`config.oauth2.*`(6개 스펙)/`config.security.*` 전부
  단독 실행 GREEN. `SecurityConfig.kt`를 수정했으므로 전체 Spring 컨텍스트 로드도
  `YonaApplicationTests`(공유 MariaDB testcontainer 기준)로 별도 검증 — 정상 기동/종료 확인, 신규 테이블
  `oidc_sso_settings`/`saml2_sso_settings` DDL도 정상 생성됨을 로그로 확인. `TemplateEquivalenceSpec`(86
  tests, `login.html` 변경 포함 다수 템플릿 렌더링 검증)도 전체 GREEN.
- **동시 세션 경합 교차검증**: `./gradlew test`로 `config.*`/`domain.user.*`/`domain.sso.*`를 한 번에
  실행했을 때 `GitAuthorizationFilterIntegrationSpec`/`ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec`/
  `TemplateHelperSpec`/`UserRepositorySpec` 등 이 계획과 전혀 무관한 클래스에서 `SQLIntegrityConstraintViolationException`
  등 DB 정합성 오류가 간헐 발생했다. 교차검증: (a) 위 4개 클래스는 SSO/OIDC/SAML 관련 코드를 전혀 참조하지
  않음(grep 확인) (b) 이 시각에 동시에 다른 세션(P3-04 UI 보강 작업)이 같은 작업 디렉터리에서 공유
  MariaDB testcontainer(`testcontainers.reuse.enable=true`로 재사용되는 동일 컨테이너, `docker ps`로 확인)에
  대해 동시에 `./gradlew test`를 실행 중이었음을 실측 확인(해당 세션이 컨테이너를 재기동하는 것도 관측) —
  각 `AbstractIntegrationTest`가 `ddl-auto=create-drop`으로 컨텍스트 시작/종료마다 스키마를 통째로
  드롭·재생성하므로, 서로 다른 세션의 컨텍스트 라이프사이클이 겹치면 한쪽이 스키마를 갱신하는 도중 다른
  쪽이 데이터를 읽거나 쓰다 정합성 오류가 나는 것으로 판단된다. 이 계획이 만든 신규 스펙
  (`OidcUserProvisioningServiceSpec`/`Saml2UserProvisioningServiceSpec`/`EnterpriseOidcUserServiceSpec`/
  `EnterpriseSaml2ResponseAuthenticationConverterSpec`/`SsoSettingsServiceSpec`/
  `YonaClientRegistrationRepositorySpec`/`YonaRelyingPartyRegistrationRepositorySpec`/`SsoAdminControllerSpec`/
  `AuthControllerSpec`)는 전부 순수 mockk 기반 단위테스트(DB/Spring 컨텍스트 불필요)라 이 경합의 영향을
  받지 않으며, 개별 실행/묶음 실행 모두 항상 GREEN이었다.
- **작업 중 발견한 실측 제약 3건**(설계 개요/Step1에 반영 완료): (1) `EnterpriseOidcUserService`의
  `OidcUserService` 상속 시 자기참조 순환, (2) `YonaClientRegistrationRepository` 정의 시
  `OAuth2ClientProperties` 빈 소실, (3) Spring Boot 4.1.1에 SAML2 relyingparty 자동구성 모듈 부재.

### 2라운드 (2026-09-06) — 강제 중단 이후 인계 세션의 재검증 + 백로그/인덱스 문서 갱신 마무리

1라운드를 진행하던 세션이 사용자 지시로 중간에 강제 종료됐고("전체 테스트 스위트는 이제 깨끗하게
컴파일된다... `PARITY_BACKLOG.md`/`index.md` 갱신을 진행하려던 참이었다"는 메모만 남긴 채), 코드는 삭제되지
않고 워킹 디렉터리에 커밋되지 않은 채 그대로 남아있는 상태로 이 세션이 인계받았다. 이 라운드는 새 기능을
추가하지 않고 **1라운드 산출물의 신뢰도를 독립적으로 재검증**하고 미완이던 문서 갱신을 마무리했다.

- **재검증(신뢰하지 않고 처음부터 다시 확인)**: `./gradlew compileKotlin compileTestKotlin` 클린 컴파일
  확인. 신규 SSO 스펙 8개(30 tests) + `AuthControllerSpec`(20 tests, SSO 모델 속성 테스트 포함) 전부
  단독 실행 GREEN. 회귀 확인 대상(`LdapUserProvisioningServiceSpec`/`LdapQueryBuilderSpec`/
  `YonaAuthenticationProviderSpec`/`config.oauth2.*`/`config.security.*`) 전부 GREEN, 회귀 없음.
- **전체 스위트**: `./gradlew test` 1회 전체 실행 — 5,925 tests 중 53건 실패, 전부 이 계획과 무관한 8개
  클래스(`config.git.GitAuthorizationFilterIntegrationSpec`/`config.TemplateHelperSpec`/
  `domain.apitoken.ApiTokenServiceImplSpec`/`domain.board.PostingCommentRepositorySpec`/
  `domain.board.PostingRepositorySpec`/`domain.board.PostingServiceSpec`/
  `domain.comment.CommentServiceExtraSpec`/`domain.comment.CommentServiceSpec`)에서만 발생. 교차검증:
  (a) 8개 클래스 전부 `sso`/`saml`/`oidc` 문자열을 grep으로 전혀 참조하지 않음을 확인 (b) 8개 클래스만
  모아 단독 재실행하니 전부 GREEN — 1라운드가 이미 기록한 "동시 세션의 공유 MariaDB 테스트 DB 경합"
  패턴과 정확히 일치하며 이번엔 동시 작업 세션 없이도 재현됐다는 점에서, 원인이 세션 간 경합보다는
  `ddl-auto=create-drop` 방식의 통합 테스트가 전체 스위트를 한 번에 돌릴 때 갖는 근본적인 취약성(다른
  클래스와의 실행 순서/스레드 배치에 따른 산발적 정합성 오류)에 더 가깝다고 판단된다 — 다만 이 판단은
  이번 라운드가 아닌 별도 인프라 개선 과제(`docs/COVERAGE_BACKLOG.md` 영역)로 남겨둔다. 이 계획이 만든
  코드/테스트 자체는 이번 재검증에서도 예외 없이 GREEN.
- **문서 갱신 마무리**: 1라운드가 미완으로 남긴 두 문서를 갱신 — `docs/PARITY_BACKLOG.md`의 P3-06 행
  체크박스를 `[ ]` → `[x]`로 바꾸고 기존 텍스트 뒤에 완료 요약을 이어붙였다(원본 텍스트는 보존).
  `docs/yona-wiki/index.md`의 우선순위 표 P3-06 상태를 `planned` → `완료(2026-09-06)`로 갱신.
- **코드 변경 없음**: 이 라운드는 검증과 문서 갱신만 수행했다 — 1라운드가 작성한 소스/템플릿/테스트
  코드는 한 줄도 수정하지 않았다.

### 3라운드 (2026-09-06) — push 전 코디네이터 코드 리뷰에서 실제 계정 탈취 취약점 발견·수정

push 전 최종 리뷰 과정에서 `OidcUserProvisioningService.reconcile()`을 직접 읽어본 결과, 실제로
악용 가능한 보안 결함을 발견했다.

- **취약점**: `reconcile()`이 OIDC IdP가 반환한 `email` 클레임만으로 기존 로컬 계정에 연결(link)하면서
  `email_verified` 클레임을 전혀 확인하지 않았다. 이 계획은 명시적으로 **"임의의 OIDC IdP"**(범용 OIDC
  연동, Okta/Azure AD/Keycloak 등 무엇이든)를 지원 대상으로 삼고 있어 — LDAP처럼 관리자가 완전히
  신뢰하는 단일 사내 디렉터리를 붙이는 게 아니라 신뢰 수준이 제각각인 IdP를 붙이는 게 전제다. 이메일
  검증을 강제하지 않거나 자유 가입이 가능한 IdP를 관리자가 등록해뒀다면, 공격자가 그 IdP에서 피해자의
  이메일을 자칭하는 계정을 만들어 yona에 SSO 로그인하는 것만으로 피해자의 기존 yona 계정을 그대로
  가로챌 수 있었다(전형적인 OIDC 계정 연결 취약점 — "don't trust the email claim without email_verified"
  는 OAuth/OIDC 통합의 잘 알려진 필수 체크리스트 항목).
- **수정**: `reconcile()`에서 **기존 계정에 연결하는 분기에서만** `oidcUser.emailVerified != true`이면
  `IllegalStateException`을 던지도록 가드를 추가했다(`email_verified`가 아예 없는 경우도 미검증으로
  취급 — 값이 불명확하면 안전한 쪽으로 판단). **신규 계정 생성 분기에는 이 가드를 적용하지 않았다** —
  뺏길 기존 계정이 없어 계정 탈취로 이어지지 않고, 일부 엔터프라이즈 IdP는 최초 로그인에서
  `email_verified` 자체를 아예 안 보낼 수 있어 여기까지 막으면 정상적인 초기 SSO 온보딩을 과도하게
  제약하게 된다(위험과 제약의 비대칭을 고려한 최소 수정). SAML2 경로(`Saml2UserProvisioningService`)에는
  동일한 가드를 추가하지 않았다 — SAML2는 관리자가 특정 IdP의 서명 인증서를 명시적으로 등록해 신뢰를
  선언하는 구조라(`RelyingPartyRegistration`) OIDC의 "발급자만 다르면 뭐든 붙는" 신뢰 모델과 다르고,
  SAML에는 `email_verified`에 대응하는 표준 속성 자체가 없다.
- **테스트**: `OidcUserProvisioningServiceSpec.kt`에 4개 추가(email_verified=false 시 기존 계정 연결
  거부, email_verified 클레임 자체가 없을 때도 거부, email_verified=true면 정상 동기화, email_verified=false여도
  신규 생성은 허용) + 기존 "이메일로 기존 유저를 찾으면...동기화" 테스트에 `email_verified: true` 클레임
  추가(가드 신설로 인한 회귀 수정). `EnterpriseOidcUserServiceSpec`은 `reconcile()`을 모킹해 이 가드를
  우회하는 구조라 영향 없음을 확인. 재검증: `OidcUserProvisioningServiceSpec`(8 tests)+
  `EnterpriseOidcUserServiceSpec`(1 test) 전부 GREEN.

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-06)
- 관련 계획: [[p3-07-mcp-server]] — MCP 서버가 자체 운영할 OAuth 인가 서버의 로그인 화면이 이 계획의 SAML/OIDC
  로그인도 그대로 상속받는 구조(이 계획이 P3-07의 블로커는 아님, 순서 무관하게 나중에 합류)
- 관련 소스: `config/YonaAuthenticationProvider.kt`, `domain/user/LdapUserProvisioningService.kt`,
  `domain/user/LdapService.kt`, `domain/user/OidcUserProvisioningService.kt`,
  `domain/user/Saml2UserProvisioningService.kt`, `config/sso/`(신규 패키지), `domain/sso/`(신규 패키지),
  `web/SsoAdminController.kt`, `templates/site/sso.html`
