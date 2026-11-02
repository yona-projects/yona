---
title: 엔터프라이즈 SSO(X-16) 구현 계획
type: plan
updated: 2026-11-02
---

# 엔터프라이즈 SSO(X-16) 구현 계획

> **상태: 구현 완료(2026-11-02, 단계 0~7). 결과는 [enterprise-sso.md](enterprise-sso.md)에 있다. 계획 중 `auth.sso_login`은 `auth.login_success`의 `details.method`로 바꿨다(A-344).** 결정은 [decisions.md](../decisions.md) D-80에 있다.

## 1. 목적

회사의 신원 제공자(IdP: Okta, Microsoft Entra ID, Keycloak, Google Workspace 같은 곳)로 Yona에 로그인하게 한다. 지원하는 프로토콜은 **SAML 2.0**과 **범용 OIDC**(OpenID Connect) 둘이다. 사이트 관리자가 관리자 화면에서 IdP를 등록하면 로그인 화면에 "회사 계정으로 로그인" 단추가 생기고, 처음 로그인한 사용자는 자동으로 만들어지거나(JIT 프로비저닝) 이미 있는 계정에 연결된다. v1.6의 로그인(비밀번호, LDAP, GitHub와 Google 소셜 로그인)은 그대로 두고 **새 경로만 더하는** 추가 항목이다(D-28).

## 2. 현황 조사 (2026-11-02)

### 2.1 이 저장소(`next2`)의 로그인 구조

- 로그인은 스프링 시큐리티의 로그인 설정(`oauth2Login`, `saml2Login`)을 **쓰지 않는다.** `YonaAuthenticationFilter`가 세션, 로그인 유지 쿠키, 토큰을 직접 읽고, 로그인 성공은 `LoginSessionService.start`(세션 시작)와 로그인 유지 쿠키 발급으로 끝난다.
- **소셜 로그인(v1.6)**: `SocialLoginController`가 `/authenticate/{provider}`를 받고, `OAuth2SocialProvider`가 직접 OAuth 2를 구현한다(GitHub, Google 두 개). 신원은 `SocialIdentity(provider, providerUserId, email, name)`이고, `SocialAccountService`가 `UserCredential`과 `LinkedAccount`(`provider_key`, `provider_user_id`)로 연결한다. **같은 이메일의 로컬 사용자가 있으면 그 사용자로 로그인시키고**(v1.6 `setStatusLoggedIn`), 없으면 이메일 앞부분으로 `generateLoginId`를 만들어 활성 사용자로 가입시킨다(이메일 인증과 가입 승인은 거치지 않는다). 이름 동기화는 설정(`social.nameSync`)이다.
- **LDAP(v1.6)**: `LdapAuthService`가 로컬 사용자와 맞춘다(이메일로 찾고 이름 갱신, 없으면 가입).
- **2단계 인증(X-2)**: 소셜 로그인도 `twoFactorLogin.required`로 2단계를 거친다(켠 사용자에 한해). SSO도 같은 입구를 쓸 수 있다.
- 보안 필터 체인은 `SecurityConfig` 하나와 OAuth 인가 서버(`AuthorizationServerConfig`)와 MCP 보안 필터가 있다. 스프링 시큐리티의 `oauth2-client`와 `saml2-service-provider` 모듈은 **의존성에 없다.**
- 비밀 값 암호화는 2단계 인증이 쓰는 `SecretCipher`(AES-GCM, 사이트 비밀 키 `application.secret`에서 파생)가 이미 있다.
- 사이트 관리 화면과 설정 저장(`site_setting`, `SiteSettingService`), 감사 기록(`AuditRecorder`), 지표(`CollaborationMetrics`) 패턴이 이미 있다.

### 2.2 이전 구현(`next` 브랜치)의 시제품

`next`에는 계획서 `P3-06`과 구현이 있다(`config/sso`, `domain/sso`, `Saml2UserProvisioningService`, `SsoAdminController`, `site/sso.html`, 시험). 참고할 것과 가져오지 않을 것을 나눈다.

**참고할 것**
- 하나의 필터 체인에 `oauth2Login`과 `saml2Login`을 나란히 두면 되고(경로가 겹치지 않는다), 스프링 시큐리티가 `openid` 범위의 유무로 소셜(OAuth 2)과 엔터프라이즈(OIDC) 사용자 서비스를 자동으로 가른다.
- SAML 2는 JIT 프로비저닝을 끼우려면 `OpenSaml5AuthenticationProvider`에 응답 변환기를 등록한 인증 관리자를 명시해야 한다.
- **OpenSAML은 Maven Central에 없다.** Shibboleth 프로젝트의 자체 저장소가 필요하다(`build.shibboleth.net`).
- Spring Boot 4.1에는 SAML 2 자동 구성 모듈이 없어 `RelyingPartyRegistrationRepository`를 직접 빈으로 만들어야 한다. 사용자 정의 `ClientRegistrationRepository`를 빈으로 두면 부트의 `OAuth2ClientProperties` 등록까지 같이 사라지는 함정이 있었다.
- 관리자 화면은 GitHub Enterprise의 "SAML single sign-on" 화면(Sign on URL, Issuer, Public Certificate, Enable)을 따랐고, DB 값이 없으면 설정 파일 값을 쓰는 이중 구조였다.

**가져오지 않을 것(시제품의 약점)**
1. **이메일만 보고 기존 계정에 자동으로 붙는다.** OIDC의 `email_verified`를 확인하지 않아, 이메일을 마음대로 적을 수 있는 IdP나 검증되지 않은 이메일로 **다른 사람의 계정을 가로챌 수 있다.**
2. 안정 식별자(`iss`와 `sub`, SAML의 발급자와 NameID)를 저장하지 않는다. 이메일이 바뀌면 다른 사람이 된다.
3. 로그인 ID를 이메일 앞부분으로만 만들어 **충돌을 처리하지 않는다**(이미 `generateLoginId`가 있다).
4. 2단계 인증, 허용 이메일 도메인, 가입 규칙과의 관계를 다루지 않는다.
5. 시제품은 이 저장소의 로그인 구조(자체 세션)가 아니라 스프링 시큐리티 세션 위에 있다. 그대로 옮길 수 없다.

### 2.3 v1.6

v1.6에는 SAML과 OIDC가 없다. 이 항목은 v1.6 동작을 바꾸지 않는다. 로그인 화면에는 SSO를 설정했을 때만 단추가 더해진다.

## 3. 설계 개요

### 3.1 구성 방식 (질문 1, 2)

- **스프링 시큐리티의 `oauth2-client`(OIDC)와 `saml2-service-provider`(SAML 2)를 쓴다.** SAML의 서명 검증과 XML 서명 래핑 공격 방어, OIDC의 ID 토큰 검증(발급자, 대상, 만료, nonce, 서명)을 직접 만들면 위험하고 검증하기 어렵다. 직접 구현은 하지 않는다.
- 두 모듈은 **SSO 전용 필터 체인**(`@Order`로 앞에 두고 `/sso/**`, `/login/oauth2/code/*`, `/saml2/**`, `/login/saml2/sso/*`만 맡는다)에 둔다. 기존 `YonaAuthenticationFilter`와 섞이지 않는다.
- 이 체인의 성공 처리기가 **인증 결과를 우리 신원(`SsoIdentity`)으로 바꿔** `SsoAccountService`(소셜 로그인의 `SocialAccountService`와 같은 위치)에 넘기고, 결과 사용자를 `LoginSessionService.start`와 로그인 유지 쿠키, 2단계 인증 입구(`twoFactorLogin`)로 로그인시킨다. 스프링 시큐리티의 인증 객체는 세션에 남기지 않는다(우리 세션 하나만 쓴다).
- OpenSAML 저장소는 `org.opensaml*`와 `net.shibboleth*` 그룹에만 한정해(`content { includeGroup… }`) 추가한다. 빌드가 외부 저장소 하나에 기대게 되는 점은 위험으로 적는다(5절).

### 3.2 OIDC 흐름과 보안

- 사이트 관리자가 발급자 주소(Issuer URL)를 넣으면 `/.well-known/openid-configuration`(Discovery)으로 엔드포인트와 공개키를 알아낸다. 로그인 시도마다 발견하면 느려지므로 **관리자가 저장할 때 한 번 확인하고 결과를 캐시한다.**
- 인가 코드 흐름 + PKCE + `state` + `nonce`. ID 토큰은 서명, `iss`, `aud`, `exp`, `nonce`를 확인한다(스프링 시큐리티). 범위는 `openid profile email`(설정 가능).
- 리디렉션 주소는 `사이트 주소/login/oauth2/code/<등록 이름>`이다. 사이트 주소는 `SiteUrls.origin()`(설정 `yona.hostname`, `scheme`)을 쓰고, 관리자 화면에서 IdP에 등록할 주소를 그대로 보여 준다.
- **포털에서 시작하는 OIDC 로그인**(선생님 결정 2026-11-02, D-80 14번): OIDC에는 "요청 없이 도착하는 토큰"이 없다. 대신 규격(OIDC Core 4절, third-party initiated login)의 **로그인 시작 주소**(`initiate_login_uri`)를 쓴다. 회사 포털의 Yona 아이콘이 `사이트 주소/sso/oidc/initiate?iss=<발급자>&login_hint=…&target_link_uri=…`를 열면, Yona가 `iss`가 설정한 발급자와 같은지 확인하고 **평소와 같은 인가 코드 흐름**(`state`, `nonce`, PKCE)을 새로 시작한다. 그래서 위조 응답을 받아들일 길이 열리지 않는다. `target_link_uri`는 같은 사이트의 경로만 받는다. 관리자 화면에 이 주소를 "IdP에 등록할 값"으로 보여 준다.
- 사용자 정보는 ID 토큰과 필요하면 사용자 정보 엔드포인트에서 읽는다. 이메일, 이름, 로그인 ID로 쓸 클레임 이름은 설정할 수 있다(기본 `email`, `name`, `preferred_username`).

### 3.3 SAML 2 흐름과 보안

- 관리자는 IdP 메타데이터 주소 또는 XML을 넣거나(권장), 로그인 주소(SSO URL), 발급자(Entity ID), 인증서를 직접 넣는다. SP(Yona) 쪽 정보(Entity ID, ACS 주소, 메타데이터 주소 `/saml2/service-provider-metadata/<등록 이름>`)는 화면에 보여 IdP에 등록하게 한다.
- **Assertion(또는 Response) 서명을 필수로** 확인한다. 대상(Audience), 수신 주소(Destination, Recipient), `InResponseTo`, 유효 시간(시계 오차 허용), 재사용(replay) 방지를 확인한다(스프링 시큐리티 기본 검증 + 재사용 캐시).
- **IdP가 먼저 시작하는 로그인(IdP-initiated)을 지원한다**(선생님 결정 2026-11-02, D-80 14번). 회사 포털의 Yona 아이콘에서 곧바로 들어오는 로그인이다. Yona가 요청한 적 없는 Response가 도착하므로 `InResponseTo`로 확인할 수 없어, 다음 보호 장치를 모두 적용한다: (1) 관리자 설정으로 켜고 끄며 기본은 끔이다(D-80 23번, 확정). (2) 설정한 IdP가 발급한 응답만 받고 **서명을 필수로** 확인한다. (3) **Assertion 재사용 방지**: 처리한 Assertion ID를 유효 시간 동안 기억해 같은 응답의 재전송을 거부한다(스프링 시큐리티에는 기본 장치가 없어 만든다). (4) 유효 시간(`NotBefore`, `NotOnOrAfter`)을 짧게 제한하고 시계 오차는 설정한다. (5) 대상(Audience), 수신 주소(Recipient, Destination)를 확인한다. (6) 로그인 뒤 보낼 곳(`RelayState`)은 **같은 사이트의 경로만** 받는다(열린 리디렉션 방지). (7) 요청 없이 도착한 응답은 감사 기록에 `details.initiated=idp`로 남긴다.
- **SP 키쌍을 관리한다**(선생님 결정 2026-11-02, D-80 15번): 요청(AuthnRequest) 서명과 암호화된 Assertion을 지원한다. 사이트 관리자가 관리자 화면에서 키쌍(자체 서명 인증서)을 **만들고, 인증서를 내려받고, 교체**한다. 비밀 키는 `SecretCipher`로 암호화해 저장한다. SP 메타데이터에는 서명용과 암호화용 인증서를 공개한다. 요청 서명은 설정으로 켜고 끈다(기본은 IdP가 요구할 때만 켠다). 암호화된 Assertion은 키가 있으면 자동으로 풀고, 서명된 Assertion(또는 Response)을 필수로 확인하는 것은 그대로이다. 키 교체는 새 키를 먼저 공개하고 이전 키를 일정 기간 함께 받는다(IdP가 인증서를 새로 올릴 시간을 준다).
- 이메일 속성 이름은 IdP마다 달라 설정한다(기본 `email`, NameID가 이메일 형식이면 NameID).

### 3.4 계정 연결과 프로비저닝 (질문 6 ~ 10)

`SsoAccountService.signIn(SsoIdentity(provider, issuer, subject, email, emailVerified, name, loginHint))`의 규칙:
1. **안정 식별자로 먼저 찾는다.** `LinkedAccount`(`provider_key` = `sso-oidc` 또는 `sso-saml2`, `provider_user_id` = `발급자|subject`)가 있으면 그 사용자로 로그인한다. 이메일이 바뀌어도 같은 사람이다. 잠긴 사용자는 로그인시키지 않는다.
2. 처음 보는 신원이면 **같은 이메일의 로컬 사용자**를 찾는다. 있으면 **IdP가 이메일을 확인했다고 알린 경우에만**(OIDC `email_verified=true`, SAML은 사이트 관리자가 신뢰하기로 한 IdP의 속성) 그 사용자에게 연결한다. 확인되지 않은 이메일이면 거부하고 사유를 보인다(관리자 설정으로 허용할 수 있다, 기본 거부). 시제품과 달리 **확인 없는 이메일 연결을 하지 않는다.**
3. 로컬 사용자가 없으면(설정이 허용할 때) **새 사용자를 만든다.** 로그인 ID는 설정한 클레임(없으면 이메일 앞부분)을 정리해 `generateLoginId`로 충돌을 피한다. 이름은 HTML을 정리한다. 허용 이메일 도메인 규칙(`signup.isAllowedEmailDomain`)을 적용한다. 상태는 활성이고(IdP가 보증, 이메일 인증과 가입 승인은 거치지 않는다. 소셜 로그인과 같다), 비밀번호는 쓸 수 없는 임의 값이다(로컬 비밀번호 로그인 불가, 저장소는 개인용 토큰으로 쓴다, D-70).
4. 이름은 처음 만들 때만 넣고, 로그인마다 IdP 값으로 바꾸는 동기화는 설정(기본 끔)이다.
5. 테이블은 소셜 로그인과 같은 `user_credential`과 `linked_account`를 쓴다(사용자를 지울 때 함께 정리되고 새 연결 테이블이 필요 없다).

### 3.5 설정과 관리 화면 (질문 3, 4, 5)

- 사이트 관리에 **"SSO"** 메뉴(`/sites/sso`)를 더한다. 사이트 관리자만 쓴다. 두 구역(OIDC, SAML 2)이 있고 각각 사용, 표시 이름(로그인 단추 문구), 프로토콜 설정, **Yona가 IdP에 알려 줄 값**(리디렉션 주소, ACS 주소, Entity ID, 메타데이터 주소)을 복사하기 좋게 보인다.
- 저장하면 **연결을 확인**한다(OIDC는 Discovery 응답과 공개키, SAML은 메타데이터 파싱과 인증서 유효 기간). 오류는 사유와 함께 보이고, 확인이 실패하면 저장하되 사용을 켤 수 없다.
- 저장 테이블은 로드맵의 `oidc_sso_settings`와 `saml2_sso_settings`이다(각각 한 행). 클라이언트 비밀 값과 SAML SP 비밀 키는 `SecretCipher`로 암호화해 저장하고 화면에는 다시 보이지 않는다(비워 두면 유지). SP 키쌍은 `saml2_sso_settings`에 현재 키와 이전 키(교체 중)를 함께 둔다.
- 설정은 **관리자 화면(DB)만** 쓴다. 시제품의 설정 파일 이중 구조는 어느 값이 이기는지 헷갈리고 시험이 두 배가 되어 두지 않는다.
- 사이트 데이터 내보내기에는 두 테이블이 포함된다(비밀 값은 암호화된 채로, 사이트 비밀 키가 같아야 풀린다. 문서에 적는다).

### 3.6 로그인 화면과 모드 (질문 11, 12, 13)

- 로그인 화면(와 로그인 대화상자)에 사용 중인 SSO마다 단추("{표시 이름}으로 로그인")를 더한다. 원래 가려던 주소를 지키고(열린 리디렉션을 막으려고 같은 사이트의 주소만 받는다) 로그인 뒤 그 주소로 보낸다.
- **SSO 전용 모드**(관리자 설정, 기본 끔): 켜면 로컬 비밀번호 로그인과 LDAP을 막고 SSO 단추만 보인다. 사이트 관리자가 IdP 장애로 잠기지 않게 **사이트 관리자 계정만 로컬 로그인을 쓸 수 있는 비상 경로**를 남긴다. v1.6의 "소셜 로그인만 허용"(`socialLoginOnly`) 설정과는 별개이고 둘 다 켜면 모두 적용한다.
- 2단계 인증: **SAML로 인증한 로그인은 Yona의 2단계 인증을 거치지 않는다**(선생님 결정 2026-11-02, IdP가 MFA를 맡는다). OIDC 로그인도 같게 면제한다(D-80 11번, 확정). 로컬 비밀번호, LDAP, 소셜 로그인은 지금처럼 사용자가 켠 2단계를 거친다.
- 로그아웃은 Yona 세션만 끝낸다. IdP 단일 로그아웃(OIDC RP-initiated logout, SAML SLO)은 추가 논의다.

### 3.7 사용자 화면: 연결된 SSO 계정 (선생님 결정 2026-11-02, D-80 18번)

- 사용자 설정에 **"연결된 계정"** 화면을 더한다. 이 사용자에게 연결된 SSO(와 소셜 로그인) 계정을 보이고(제공자, 표시 이름, 연결된 ID의 일부), **해제**할 수 있다. v1.6 `linked_account` 테이블에는 연결한 시각이 없어(테이블을 바꾸지 않는다) 시각은 보이지 않는다.
- 해제하면 그 신원의 연결만 지운다. **SSO 전용 모드가 꺼져 있을 때만** 해제할 수 있다(전용 모드에서는 로컬 로그인이 막혀 해제하면 잠긴다). 해제한 사람은 다음 로그인 때 처음 보는 신원으로 다시 연결 규칙을 거치고, 비밀번호로 쓰려면 "비밀번호 찾기" 메일로 정한다(SSO로 만든 계정의 비밀번호는 쓸 수 없는 값이다). 해제 전에 이 안내를 확인 대화상자에 보인다.
- 사이트 관리자는 사용자 관리 화면에서 다른 사용자의 연결을 해제할 수 있다(퇴사자 처리, 잘못 연결된 계정 정정).
- 감사: `auth.sso_unlinked`(`details.by`: 본인, 관리자).

### 3.8 감사와 지표

- 감사: `auth.sso_login`(성공, `details.provider`, 새 사용자인지, 연결인지), `auth.sso_failure`(실패, 사유 코드: 서명 오류, 이메일 미확인, 잠긴 사용자, 허용되지 않은 도메인 등), `site.sso_changed`(설정 변경, 비밀 값은 남기지 않는다). 새 사용자는 기존 가입 기록(`user.created`)에 출처를 남긴다.
- 지표: 로그인 시도와 결과를 프로토콜별로 센다.

## 4. 시험 전략

- **가짜 IdP(자동 시험)**: OIDC는 시험 안에서 작은 HTTP 서버가 Discovery, 공개키(JWKS), 토큰 엔드포인트를 흉내 낸다(이미 있는 Nimbus로 ID 토큰 서명). SAML은 OpenSAML로 서명한 Response를 만들어 ACS에 보낸다. 서명 오류, 만료, 대상 불일치, 재사용, 이메일 미확인, `email_verified` 거짓, 잠긴 사용자, 허용되지 않은 도메인, 로그인 ID 충돌, 2단계 인증, 열린 리디렉션, IdP 시작 로그인 거부를 시험으로 고정한다.
- **실제 IdP(수동 확인)**: **Keycloak 컨테이너**(OIDC와 SAML 2를 모두 낸다)를 띄우고 Playwright로 브라우저 로그인을 확인한다. Keycloak 이미지는 수백 MB라 내려받기 전에 **별도로 허락을 구한다**(파일 이름, 출처, 크기를 알리고).
- 관리자 화면은 Playwright로 한 번 확인한다(캡처를 보여 드린다).

## 5. 위험과 주의

- **계정 가로채기**: 이메일로 기존 계정에 붙는 규칙이 가장 위험하다. 확인된 이메일만 허용하고, 그래도 IdP가 잘못 설정되면(누구나 이메일을 쓸 수 있는 IdP) 가로챌 수 있어 **이메일로 연결하는 것 자체를 설정으로 끌 수 있게** 한다.
- **빌드가 외부 저장소에 기댄다**(Shibboleth). 저장소가 막히면 빌드가 실패한다. 그룹을 한정하고 문서에 적는다. 오프라인 빌드는 의존성 캐시가 필요하다.
- **역방향 프록시 뒤에서 주소**: 리디렉션 주소와 ACS 주소는 사용자가 보는 바깥 주소와 정확히 같아야 한다. `yona.hostname`, `scheme`, 전달 헤더 설정을 문서에 적고, 관리자 화면에 실제 쓰일 값을 보여 준다.
- **시계 오차**: SAML과 OIDC 토큰의 시간 검증이 서버와 IdP의 시계 차이에 민감하다. 허용 오차를 설정으로 둔다(기본 60초).
- **IdP 시작 로그인의 재전송**: 스프링 시큐리티에 Assertion 재사용 방지가 없어 직접 만든다. 여러 서버로 확장하면 기억 장소를 공유해야 하는데 이 프로젝트는 단일 서버가 기본이라 메모리 기억(유효 시간만큼)으로 시작한다. 확장 때 다시 본다.
- **IdP 장애**: SSO 전용 모드에서 IdP가 멈추면 사이트 관리자의 비상 경로만 남는다.
- **SSO로 만든 사용자의 비밀번호**: 쓸 수 없는 값이라 "비밀번호 찾기"와 "비밀번호 변경"이 이 사용자에게 의미가 없다. 안내 문구를 넣을지는 구현 때 확인한다.
- **인증서 만료**: SAML IdP 인증서가 만료되면 로그인이 멈춘다. 관리자 화면에 만료일과 임박 경고를 보인다.

## 6. 단계 (시험 먼저)

| 단계 | 내용 | 확인 |
|---|---|---|
| 0 | 조사(스파이크): 의존성(OpenSAML 저장소, 스프링 시큐리티 버전과 부트 4.1), SSO 전용 필터 체인과 성공 처리기가 기존 세션 로그인으로 이어지는지, 부트 자동 구성과 충돌이 없는지 | 작은 가짜 IdP로 OIDC 한 번 |
| 1 | 신원과 계정 서비스(`SsoIdentity`, `SsoAccountService`): 안정 식별자, 이메일 연결 규칙, 프로비저닝, 로그인 ID 충돌, 허용 도메인, 2단계 입구 | 단위와 통합 시험 |
| 2 | OIDC: 설정 테이블, Discovery 캐시, 필터 체인, 로그인과 콜백, 검증 오류 처리 | 가짜 OIDC IdP 시험 |
| 3 | SAML 2: 설정 테이블, 메타데이터, SP 메타데이터, ACS, 검증(서명, 대상, 재사용), 이메일 속성 | OpenSAML 서명 Response 시험 |
| 4 | 관리자 화면(`/sites/sso`), 비밀 값 암호화, 연결 확인, IdP에 알려 줄 값 | 화면 시험 + Playwright |
| 4b | SAML SP 키쌍: 만들기, 인증서 내려받기, 교체(이전 키 병행), 요청 서명, 암호화된 Assertion 풀기, SP 메타데이터의 인증서 | OpenSAML 서명과 암호화 시험 |
| 5 | 로그인 화면 단추, 원래 주소 복귀, SSO 전용 모드와 비상 경로, SAML 로그인의 2단계 인증 면제, **IdP 시작 로그인(SAML 요청 없는 응답, OIDC 로그인 시작 주소)과 재사용 방지**, 감사와 지표 | 시험 |
| 5b | 사용자 설정의 "연결된 계정"(보기, 해제, 관리자의 해제), 감사 | 화면 시험 + Playwright |
| 6 | 실제 IdP 확인: Keycloak(OIDC와 SAML 2, 서명과 암호화 포함) 컨테이너와 Playwright | 수동 확인과 캡처 |
| 7 | 문서(`enterprise-sso.md`), 구성 키, 로드맵 갱신 | |

## 7. 범위 밖 (추가 논의 후보)

IdP 그룹을 사이트 관리자나 프로젝트와 조직 구성원으로 매핑(**별도 로드맵 항목 X-42**, 할지는 나중에 결정. `next` 브랜치에도 없다), 단일 로그아웃(OIDC와 SAML), 여러 IdP 동시 등록, SCIM 사용자 동기화. (SAML 요청 서명과 Assertion 암호화, 사용자 화면의 연결 계정은 이제 이 항목의 범위이다.)
